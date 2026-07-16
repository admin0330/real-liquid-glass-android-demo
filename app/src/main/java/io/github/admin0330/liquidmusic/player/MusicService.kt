package io.github.admin0330.liquidmusic.player

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.admin0330.liquidmusic.R
import io.github.admin0330.liquidmusic.app.MainActivity
import io.github.admin0330.liquidmusic.di.PlaybackApplicationScope
import io.github.admin0330.liquidmusic.domain.repository.MusicLibraryRepository
import io.github.admin0330.liquidmusic.domain.repository.PlaybackHistoryRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject
    lateinit var musicLibraryRepository: MusicLibraryRepository

    @Inject
    lateinit var playbackHistoryRepository: PlaybackHistoryRepository

    @Inject
    internal lateinit var playbackQueueStore: PlaybackQueueStore

    @Inject
    @PlaybackApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var historyTracker: PlaybackHistoryTracker
    private var mediaSession: MediaSession? = null
    private var queueWriteJob: Job? = null
    private var lastQueuePositionSaveRealtimeMs: Long = 0

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            historyTracker.onMediaItemTransition(player, mediaItem, reason)
            persistQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            historyTracker.onIsPlayingChanged(player, isPlaying)
            persistQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            historyTracker.onPlaybackStateChanged(player, playbackState)
            persistQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            historyTracker.onTimelineChanged(player)
            persistQueue()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            historyTracker.onPositionDiscontinuity(player, oldPosition, newPosition, reason)
            persistQueue()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            persistQueue()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            persistQueue()
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sameApplication = controller.uid == applicationInfo.uid
            return if (sameApplication || controller.isTrusted) {
                MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                )
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val usedEntryIds = buildSet {
                val sessionPlayer = mediaSession.player
                for (index in 0 until sessionPlayer.mediaItemCount) {
                    add(sessionPlayer.getMediaItemAt(index).mediaId)
                }
            }
            return Futures.immediateFuture(sanitizeMediaItems(mediaItems, usedEntryIds).map { it.second })
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val sanitized = sanitizeMediaItems(mediaItems, emptySet())
            if (sanitized.isEmpty()) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        emptyList(),
                        C.INDEX_UNSET,
                        C.TIME_UNSET,
                    ),
                )
            }
            val sanitizedStartIndex = sanitized
                .indexOfFirst { (originalIndex, _) -> originalIndex == startIndex }
                .takeIf { it != C.INDEX_UNSET }
                ?: startIndex.coerceIn(sanitized.indices)
            val safeStartPosition = if (startPositionMs == C.TIME_UNSET) {
                C.TIME_UNSET
            } else {
                startPositionMs.coerceAtLeast(0)
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    sanitized.map { it.second },
                    sanitizedStartIndex,
                    safeStartPosition,
                ),
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            applicationScope.launch {
                runCatching { loadRestoredQueue() }
                    .onSuccess { restoredQueue ->
                        if (restoredQueue == null) {
                            future.setException(NoSuchElementException("No saved playback queue"))
                        } else {
                            withContext(Dispatchers.Main.immediate) {
                                player.shuffleModeEnabled = restoredQueue.shuffleEnabled
                                player.repeatMode = restoredQueue.repeatMode.toPlayerRepeatMode()
                            }
                            future.set(restoredQueue.toMediaItemsWithStartPosition())
                        }
                    }
                    .onFailure { error -> future.setException(error) }
            }
            return future
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider(
            this,
            DefaultMediaNotificationProvider.NotificationIdProvider { PLAYBACK_NOTIFICATION_ID },
            PLAYBACK_NOTIFICATION_CHANNEL_ID,
            R.string.app_name,
        )
        setMediaNotificationProvider(notificationProvider)
        @Suppress("DEPRECATION")
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
        historyTracker = PlaybackHistoryTracker(
            repository = playbackHistoryRepository,
            writeScope = applicationScope,
        )
        player.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, player)
            .setId(PLAYBACK_SESSION_ID)
            .setSessionActivity(createSessionActivity())
            .setCallback(sessionCallback)
            .build()

        serviceScope.launch { restoreSavedQueueIfPlayerIsEmpty() }
        serviceScope.launch {
            while (isActive) {
                delay(HISTORY_TICK_INTERVAL_MS)
                historyTracker.tick(player)
                val now = SystemClock.elapsedRealtime()
                if (player.isPlaying &&
                    now - lastQueuePositionSaveRealtimeMs >= POSITION_SAVE_INTERVAL_MS
                ) {
                    persistQueue()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueue()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            historyTracker.finish(
                player = player,
                completed = player.playbackState == Player.STATE_ENDED,
            )
            persistQueue()
            player.removeListener(playerListener)
        }
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun restoreSavedQueueIfPlayerIsEmpty() {
        val restoredQueue = withContext(Dispatchers.IO) { loadRestoredQueue() } ?: return
        if (player.mediaItemCount != 0) return
        player.shuffleModeEnabled = restoredQueue.shuffleEnabled
        player.repeatMode = restoredQueue.repeatMode.toPlayerRepeatMode()
        player.setMediaItems(
            restoredQueue.mediaItems,
            restoredQueue.startIndex,
            restoredQueue.startPositionMs,
        )
        player.prepare()
        player.playWhenReady = false
    }

    private suspend fun loadRestoredQueue(): RestoredQueue? {
        val storedQueue = playbackQueueStore.read() ?: return null
        val tracksById = musicLibraryRepository
            .getTracks(storedQueue.entries.map(StoredQueueEntry::trackId).distinct())
            .associateBy { it.id }
        val restoredEntries = storedQueue.entries.mapNotNull { storedEntry ->
            val track = tracksById[storedEntry.trackId] ?: return@mapNotNull null
            QueueEntry(queueEntryId = storedEntry.queueEntryId, track = track)
        }
        val mediaItems = restoredEntries.mapNotNull { entry ->
            runCatching { TrackMediaItemMapper.toMediaItem(entry) }.getOrNull()
        }
        if (mediaItems.isEmpty()) return null
        val currentIndex = storedQueue.currentQueueEntryId
            ?.let { currentId -> mediaItems.indexOfFirst { it.mediaId == currentId } }
            ?.takeIf { it != C.INDEX_UNSET }
            ?: 0
        return RestoredQueue(
            mediaItems = mediaItems,
            startIndex = currentIndex,
            startPositionMs = storedQueue.positionMs.coerceAtLeast(0),
            shuffleEnabled = storedQueue.shuffleEnabled,
            repeatMode = storedQueue.repeatMode,
        )
    }

    private fun persistQueue() {
        if (!::player.isInitialized) return
        val snapshot = captureQueue()
        lastQueuePositionSaveRealtimeMs = SystemClock.elapsedRealtime()
        queueWriteJob?.cancel()
        queueWriteJob = applicationScope.launch {
            playbackQueueStore.write(snapshot)
        }
    }

    private fun captureQueue(): StoredQueue? {
        val entries = buildList {
            for (index in 0 until player.mediaItemCount) {
                val mediaItem = player.getMediaItemAt(index)
                val trackId = TrackMediaItemMapper.trackId(mediaItem) ?: continue
                val entryId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: continue
                add(StoredQueueEntry(queueEntryId = entryId, trackId = trackId))
            }
        }
        if (entries.isEmpty()) return null
        return StoredQueue(
            entries = entries,
            currentQueueEntryId = player.currentMediaItem?.mediaId,
            positionMs = player.currentPosition.validTimeOrZero(),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = RepeatMode.fromPlayerRepeatMode(player.repeatMode),
        )
    }

    private fun createSessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun sanitizeMediaItems(
        mediaItems: List<MediaItem>,
        existingEntryIds: Set<String>,
    ): List<Pair<Int, MediaItem>> {
        val usedEntryIds = existingEntryIds.toMutableSet()
        return mediaItems.mapIndexedNotNull { index, mediaItem ->
            if (!TrackMediaItemMapper.isPlayableLocal(mediaItem)) return@mapIndexedNotNull null
            val currentId = mediaItem.mediaId
            val safeItem = if (currentId.isBlank() || !usedEntryIds.add(currentId)) {
                val uniqueId = generateUniqueQueueEntryId(usedEntryIds)
                mediaItem.buildUpon().setMediaId(uniqueId).build()
            } else {
                mediaItem
            }
            index to safeItem
        }
    }

    private fun generateUniqueQueueEntryId(usedEntryIds: MutableSet<String>): String {
        while (true) {
            val candidate = UUID.randomUUID().toString()
            if (usedEntryIds.add(candidate)) return candidate
        }
    }

    private data class RestoredQueue(
        val mediaItems: List<MediaItem>,
        val startIndex: Int,
        val startPositionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: RepeatMode,
    ) {
        fun toMediaItemsWithStartPosition(): MediaSession.MediaItemsWithStartPosition =
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
    }

    companion object {
        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "io.github.admin0330.liquid_music.playback"
        private const val PLAYBACK_NOTIFICATION_ID = 7_102
        private const val PLAYBACK_SESSION_ID = "liquid_music_playback"
        const val ACTION_OPEN_PLAYER = "io.github.admin0330.liquidmusic.action.OPEN_PLAYER"
        private const val SEEK_INCREMENT_MS = 10_000L
        private const val HISTORY_TICK_INTERVAL_MS = 1_000L
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}

private fun Long.validTimeOrZero(): Long = if (this == C.TIME_UNSET || this < 0) 0 else this
