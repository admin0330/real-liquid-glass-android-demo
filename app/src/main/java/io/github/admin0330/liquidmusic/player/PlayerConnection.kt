package io.github.admin0330.liquidmusic.player

import android.content.Context
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.admin0330.liquidmusic.di.PlaybackSessionToken
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.repository.MusicLibraryRepository
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PlaybackSessionToken private val sessionToken: SessionToken,
    private val musicLibraryRepository: MusicLibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val _state = MutableStateFlow(PlaybackState())
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var reconnectJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishFullState(player)
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            _state.update { state -> state.copy(error = error?.toPlaybackFailure()) }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            controller.removeListener(playerListener)
            if (this@PlayerConnection.controller === controller) {
                this@PlayerConnection.controller = null
                controllerFuture = null
            }
            _state.update { state ->
                state.copy(
                    connectionState = PlayerConnectionState.DISCONNECTED,
                    isPlaying = false,
                    playWhenReady = false,
                    isBuffering = false,
                    error = disconnectedFailure(),
                )
            }
            scheduleReconnect()
        }

        override fun onError(controller: MediaController, sessionError: SessionError) {
            _state.update { state -> state.copy(error = sessionError.toPlaybackFailure()) }
        }
    }

    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    val connectionState: StateFlow<PlayerConnectionState> =
        derived(PlayerConnectionState.CONNECTING, PlaybackState::connectionState)
    val currentQueueEntryId: StateFlow<String?> =
        derived(null, PlaybackState::currentQueueEntryId)
    val currentTrackId: StateFlow<String?> = derived(null, PlaybackState::currentTrackId)
    val currentTrack: StateFlow<Track?> = derived(null, PlaybackState::currentTrack)
    val isPlaying: StateFlow<Boolean> = derived(false, PlaybackState::isPlaying)
    val playWhenReady: StateFlow<Boolean> = derived(false, PlaybackState::playWhenReady)
    val positionMs: StateFlow<Long> = derived(0, PlaybackState::positionMs)
    val durationMs: StateFlow<Long> = derived(0, PlaybackState::durationMs)
    val bufferedPositionMs: StateFlow<Long> = derived(0, PlaybackState::bufferedPositionMs)
    val isBuffering: StateFlow<Boolean> = derived(false, PlaybackState::isBuffering)
    val queue: StateFlow<List<QueueEntry>> = derived(emptyList(), PlaybackState::queue)
    val shuffleEnabled: StateFlow<Boolean> = derived(false, PlaybackState::shuffleEnabled)
    val repeatMode: StateFlow<RepeatMode> = derived(RepeatMode.OFF, PlaybackState::repeatMode)
    val error: StateFlow<PlaybackFailure?> = derived(null, PlaybackState::error)

    init {
        connect()
        scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                controller?.let(::publishPosition)
            }
        }
    }

    suspend fun setQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
        playWhenReady: Boolean = false,
    ): Boolean = runCatchingCancellable {
        val entries = tracks.map(TrackMediaItemMapper::createQueueEntry)
        replaceQueue(entries, startIndex, startPositionMs, playWhenReady)
    }.getOrElse {
        publishFailure(it)
        false
    }

    suspend fun setQueueByTrackIds(
        trackIds: List<String>,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
        playWhenReady: Boolean = false,
    ): Boolean {
        if (trackIds.isEmpty()) {
            return setQueue(emptyList(), playWhenReady = false)
        }
        val tracks = runCatchingCancellable {
            withContext(Dispatchers.IO) { musicLibraryRepository.getTracks(trackIds) }
        }.getOrElse {
            publishFailure(it)
            return false
        }
        if (tracks.isEmpty()) {
            publishFailure(
                PlaybackFailure(
                    code = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    codeName = PlaybackException.getErrorCodeName(
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    ),
                    message = "The requested local tracks are no longer available",
                ),
            )
            return false
        }
        return setQueue(tracks, startIndex, startPositionMs, playWhenReady)
    }

    suspend fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
    ): Boolean = setQueue(
        tracks = tracks,
        startIndex = startIndex,
        startPositionMs = startPositionMs,
        playWhenReady = true,
    )

    suspend fun playQueueByTrackIds(
        trackIds: List<String>,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
    ): Boolean = setQueueByTrackIds(
        trackIds = trackIds,
        startIndex = startIndex,
        startPositionMs = startPositionMs,
        playWhenReady = true,
    )

    fun play() = submitCommand { player ->
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekToDefaultPosition()
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() = submitCommand(Player::pause)

    fun togglePlayPause() = submitCommand { player ->
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) = submitCommand { player ->
        val maximum = player.duration.validPositionOrZero().takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMs.coerceIn(0, maximum))
    }

    fun seekBy(deltaMs: Long) = submitCommand { player ->
        val maximum = player.duration.validPositionOrZero().takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, maximum))
    }

    fun next() = submitCommand(Player::seekToNext)

    fun previous() = submitCommand(Player::seekToPrevious)

    fun stop(clearQueue: Boolean = false) = submitCommand { player ->
        player.stop()
        if (clearQueue) player.clearMediaItems()
    }

    fun clearQueue() = submitCommand { player ->
        player.stop()
        player.clearMediaItems()
    }

    fun setShuffleEnabled(enabled: Boolean) = submitCommand { player ->
        player.shuffleModeEnabled = enabled
    }

    fun toggleShuffle() = submitCommand { player ->
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun setRepeatMode(repeatMode: RepeatMode) = submitCommand { player ->
        player.repeatMode = repeatMode.toPlayerRepeatMode()
    }

    fun cycleRepeatMode() = submitCommand { player ->
        val nextMode = RepeatMode.fromPlayerRepeatMode(player.repeatMode).next()
        player.repeatMode = nextMode.toPlayerRepeatMode()
    }

    fun playNext(track: Track) = submitCommand { player ->
        val insertionIndex = (player.currentMediaItemIndex + 1)
            .coerceIn(0, player.mediaItemCount)
        player.addMediaItem(
            insertionIndex,
            TrackMediaItemMapper.toMediaItem(TrackMediaItemMapper.createQueueEntry(track)),
        )
    }

    fun playLater(track: Track) = submitCommand { player ->
        player.addMediaItem(
            TrackMediaItemMapper.toMediaItem(TrackMediaItemMapper.createQueueEntry(track)),
        )
    }

    fun removeQueueEntry(queueEntryId: String) = submitCommand { player ->
        val index = player.indexOfQueueEntry(queueEntryId)
        if (index != C.INDEX_UNSET) player.removeMediaItem(index)
    }

    fun moveQueueEntry(queueEntryId: String, toIndex: Int) = submitCommand { player ->
        val fromIndex = player.indexOfQueueEntry(queueEntryId)
        if (fromIndex == C.INDEX_UNSET || player.mediaItemCount < 2) return@submitCommand
        val destination = toIndex.coerceIn(0, player.mediaItemCount - 1)
        if (fromIndex != destination) player.moveMediaItem(fromIndex, destination)
    }

    fun playQueueEntry(queueEntryId: String) = submitCommand { player ->
        val index = player.indexOfQueueEntry(queueEntryId)
        if (index != C.INDEX_UNSET) {
            player.seekToDefaultPosition(index)
            player.play()
        }
    }

    fun clearError() {
        _state.update { state -> state.copy(error = null) }
    }

    fun release() {
        scope.launch {
            reconnectJob?.cancel()
            controller?.removeListener(playerListener)
            controller = null
            controllerFuture?.let(MediaController::releaseFuture)
            controllerFuture = null
            scope.cancel()
        }
    }

    private suspend fun replaceQueue(
        entries: List<QueueEntry>,
        startIndex: Int,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        val activeController = awaitController()
        if (entries.isEmpty()) {
            activeController.stop()
            activeController.clearMediaItems()
            _state.update { state -> state.copy(error = null) }
            return@withContext true
        }

        val mediaItems = entries.map(TrackMediaItemMapper::toMediaItem)
        val safeStartIndex = startIndex.coerceIn(mediaItems.indices)
        activeController.setMediaItems(
            mediaItems,
            safeStartIndex,
            startPositionMs.coerceAtLeast(0),
        )
        activeController.prepare()
        activeController.playWhenReady = playWhenReady
        _state.update { state -> state.copy(error = null) }
        true
    }

    private fun submitCommand(command: (MediaController) -> Unit) {
        scope.launch {
            runCatchingCancellable {
                command(awaitController())
            }.onFailure(::publishFailure)
        }
    }

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        val future = connect()
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { connectedController ->
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(connectedController))
                            }
                        }
                        .onFailure { error ->
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.failure(error.unwrapExecutionException()))
                            }
                        }
                },
                mainExecutor,
            )
        }
    }

    private fun connect(): ListenableFuture<MediaController> {
        controller?.let { connected ->
            return com.google.common.util.concurrent.Futures.immediateFuture(connected)
        }
        controllerFuture?.let { return it }
        _state.update { it.copy(connectionState = PlayerConnectionState.CONNECTING) }
        val future = MediaController.Builder(context, sessionToken)
            .setApplicationLooper(Looper.getMainLooper())
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                runCatching { future.get() }
                    .onSuccess { connectedController ->
                        reconnectJob?.cancel()
                        reconnectJob = null
                        controller = connectedController
                        connectedController.addListener(playerListener)
                        publishFullState(connectedController)
                    }
                    .onFailure { error ->
                        controllerFuture = null
                        publishFailure(error.unwrapExecutionException())
                        scheduleReconnect()
                    }
            },
            mainExecutor,
        )
        return future
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || !scope.isActive) return
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            reconnectJob = null
            connect()
        }
    }

    private fun publishFullState(player: Player) {
        val queue = buildList {
            for (index in 0 until player.mediaItemCount) {
                TrackMediaItemMapper.toQueueEntry(player.getMediaItemAt(index))?.let(::add)
            }
        }
        val currentMediaItem = player.currentMediaItem
        val currentEntryId = currentMediaItem?.mediaId?.takeIf(String::isNotBlank)
        val currentTrack = queue.firstOrNull { it.queueEntryId == currentEntryId }?.track
            ?: currentMediaItem?.let(TrackMediaItemMapper::toQueueEntry)?.track
        val playerError = player.playerError

        _state.update { state ->
            state.copy(
                connectionState = PlayerConnectionState.CONNECTED,
                currentQueueEntryId = currentEntryId,
                currentTrackId = currentTrack?.id,
                currentTrack = currentTrack,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                positionMs = player.currentPosition.validPositionOrZero(),
                durationMs = player.duration.validPositionOrZero()
                    .takeIf { it > 0 }
                    ?: currentTrack?.durationMs.orZero(),
                bufferedPositionMs = player.bufferedPosition.validPositionOrZero(),
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                queue = queue,
                currentIndex = if (currentMediaItem == null) C.INDEX_UNSET else player.currentMediaItemIndex,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = RepeatMode.fromPlayerRepeatMode(player.repeatMode),
                error = playerError?.toPlaybackFailure() ?: state.error,
            )
        }
    }

    private fun publishPosition(player: Player) {
        _state.update { state ->
            state.copy(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                positionMs = player.currentPosition.validPositionOrZero(),
                durationMs = player.duration.validPositionOrZero()
                    .takeIf { it > 0 }
                    ?: state.currentTrack?.durationMs.orZero(),
                bufferedPositionMs = player.bufferedPosition.validPositionOrZero(),
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
            )
        }
    }

    private fun publishFailure(throwable: Throwable) {
        publishFailure(throwable.toPlaybackFailure())
    }

    private fun publishFailure(failure: PlaybackFailure) {
        _state.update { state -> state.copy(error = failure) }
    }

    private fun <T> derived(
        initialValue: T,
        transform: (PlaybackState) -> T,
    ): StateFlow<T> = state
        .map(transform)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialValue)

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 100L
        const val RECONNECT_DELAY_MS = 1_000L
    }
}

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    Result.failure(error)
}

private fun Player.indexOfQueueEntry(queueEntryId: String): Int {
    for (index in 0 until mediaItemCount) {
        if (getMediaItemAt(index).mediaId == queueEntryId) return index
    }
    return C.INDEX_UNSET
}

private fun Long.validPositionOrZero(): Long = if (this == C.TIME_UNSET || this < 0) 0 else this

private fun Long?.orZero(): Long = this?.coerceAtLeast(0) ?: 0

private fun Throwable.unwrapExecutionException(): Throwable =
    if (this is ExecutionException && cause != null) cause!! else this

private fun Throwable.toPlaybackFailure(): PlaybackFailure {
    val cause = unwrapExecutionException()
    if (cause is PlaybackException) return cause.toPlaybackFailure()
    val code = PlaybackException.ERROR_CODE_UNSPECIFIED
    return PlaybackFailure(
        code = code,
        codeName = PlaybackException.getErrorCodeName(code),
        message = cause.message ?: cause.javaClass.simpleName,
    )
}

private fun PlaybackException.toPlaybackFailure(): PlaybackFailure = PlaybackFailure(
    code = errorCode,
    codeName = errorCodeName,
    message = message ?: errorCodeName,
)

@androidx.annotation.OptIn(UnstableApi::class)
private fun SessionError.toPlaybackFailure(): PlaybackFailure = PlaybackFailure(
    code = code,
    codeName = SessionError.getErrorCodeName(code),
    message = message,
)

private fun disconnectedFailure(): PlaybackFailure {
    val code = PlaybackException.ERROR_CODE_DISCONNECTED
    return PlaybackFailure(
        code = code,
        codeName = PlaybackException.getErrorCodeName(code),
        message = "The playback service disconnected",
    )
}
