package io.github.admin0330.liquidmusic.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassHost
import io.github.admin0330.liquidmusic.core.designsystem.theme.LiquidMusicTheme
import io.github.admin0330.liquidmusic.core.designsystem.theme.rememberArtworkPalette
import io.github.admin0330.liquidmusic.core.preferences.AppearanceMode
import io.github.admin0330.liquidmusic.navigation.MainNavigation
import io.github.admin0330.liquidmusic.navigation.AppLibrarySnapshot
import io.github.admin0330.liquidmusic.feature.player.PlaybackViewModel
import io.github.admin0330.liquidmusic.feature.player.PlaybackActions
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidMotion

@Composable
fun LiquidMusicApp(
    openPlayerRequest: Int = 0,
    viewModel: RootViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()
    val lyrics by playbackViewModel.lyrics.collectAsStateWithLifecycle()
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var automaticRequestMade by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onPermissionResult,
    )
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    fun ensurePlaybackNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(scanState) {
        if (scanState == LibraryScanUiState.PermissionRequired && !automaticRequestMade) {
            automaticRequestMade = true
            permissionLauncher.launch(permission)
        }
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (preferences.appearance) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val palette = rememberArtworkPalette(playbackState.currentTrack?.artworkUri, dark)
    val background by animateColorAsState(palette.background, tween(LiquidMotion.deliberate), label = "app-background")
    val primary by animateColorAsState(palette.primary, tween(LiquidMotion.deliberate), label = "app-primary-glow")
    val secondary by animateColorAsState(palette.secondary, tween(LiquidMotion.deliberate), label = "app-secondary-glow")
    LiquidMusicTheme(darkTheme = dark, palette = palette) {
        LiquidGlassHost(
            modifier = Modifier.fillMaxSize(),
            background = { sourceModifier ->
                Box(
                    sourceModifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    primary.copy(alpha = if (dark) 0.24f else 0.10f),
                                    background,
                                    secondary.copy(alpha = if (dark) 0.16f else 0.08f),
                                ),
                            ),
                        ),
                )
            },
        ) {
            MainNavigation(
                library = AppLibrarySnapshot(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    favorites = favorites,
                    recentlyAdded = recentlyAdded,
                    recentlyPlayed = recentlyPlayed,
                    playlists = playlists,
                ),
                scanState = scanState,
                onRescan = viewModel::refresh,
                onRequestPermission = { permissionLauncher.launch(permission) },
                playerState = playbackState,
                lyrics = lyrics,
                playbackActions = PlaybackActions(
                    togglePlayPause = playbackViewModel::togglePlayPause,
                    next = playbackViewModel::next,
                    previous = playbackViewModel::previous,
                    seekTo = playbackViewModel::seekTo,
                    toggleShuffle = playbackViewModel::toggleShuffle,
                    cycleRepeat = playbackViewModel::cycleRepeat,
                    attachLyrics = playbackViewModel::attachLyrics,
                    playQueueEntry = playbackViewModel::playQueueEntry,
                    removeQueueEntry = playbackViewModel::removeQueueEntry,
                    moveQueueEntry = playbackViewModel::moveQueueEntry,
                    clearError = playbackViewModel::clearError,
                ),
                openPlayerRequest = openPlayerRequest,
                onTrackClick = { track ->
                    ensurePlaybackNotificationPermission()
                    playbackViewModel.play(track, tracks)
                },
                onPlayQueue = { track, queue ->
                    ensurePlaybackNotificationPermission()
                    playbackViewModel.play(track, queue)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
