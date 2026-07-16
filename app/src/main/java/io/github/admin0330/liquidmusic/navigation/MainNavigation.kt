package io.github.admin0330.liquidmusic.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.admin0330.liquidmusic.core.designsystem.components.FloatingTabBar
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.app.LibraryScanUiState
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.Playlist
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.player.PlaybackState
import io.github.admin0330.liquidmusic.core.lyrics.ParsedLyrics
import io.github.admin0330.liquidmusic.feature.player.PlaybackActions
import io.github.admin0330.liquidmusic.feature.player.MiniPlayer
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface

data class AppLibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

@Composable
fun MainNavigation(
    library: AppLibrarySnapshot,
    scanState: LibraryScanUiState,
    onRescan: () -> Unit,
    onRequestPermission: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayQueue: (Track, List<Track>) -> Unit,
    playerState: PlaybackState,
    lyrics: ParsedLyrics?,
    playbackActions: PlaybackActions,
    openPlayerRequest: Int,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    var handledOpenPlayerRequest by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableIntStateOf(0)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = TopLevelDestination.entries.firstOrNull {
        it.route == backStackEntry?.destination?.route
    } ?: TopLevelDestination.ListenNow
    val isTopLevel = TopLevelDestination.entries.any { it.route == backStackEntry?.destination?.route }
    val hasMiniPlayer = isTopLevel && playerState.currentTrack != null
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockBottom = navigationInset + LiquidSpacing.xs
    val contentBottom = if (isTopLevel) {
        dockBottom + LiquidSpacing.tabBarHeight + LiquidSpacing.md +
            if (hasMiniPlayer) LiquidSpacing.miniPlayerHeight + LiquidSpacing.xs else 0.dp
    } else {
        navigationInset + LiquidSpacing.lg
    }

    androidx.compose.runtime.LaunchedEffect(openPlayerRequest, playerState.currentTrackId) {
        if (openPlayerRequest > handledOpenPlayerRequest && playerState.currentTrack != null) {
            handledOpenPlayerRequest = openPlayerRequest
            if (backStackEntry?.destination?.route == "player") return@LaunchedEffect
            navController.navigate("player") { launchSingleTop = true }
        }
    }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            bottomPadding = contentBottom,
            library = library,
            scanState = scanState,
            onRescan = onRescan,
            onRequestPermission = onRequestPermission,
            onTrackClick = onTrackClick,
            onPlayQueue = onPlayQueue,
            onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } },
            playerState = playerState,
            lyrics = lyrics,
            playbackActions = playbackActions,
            modifier = Modifier.fillMaxSize(),
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = hasMiniPlayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = LiquidSpacing.screen,
                    end = LiquidSpacing.screen,
                    bottom = dockBottom + LiquidSpacing.tabBarHeight + LiquidSpacing.xs,
                ),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
        ) {
            val artworkKey = "now-playing-artwork-${playerState.currentTrackId}"
            MiniPlayer(
                state = playerState,
                onOpen = { navController.navigate("player") { launchSingleTop = true } },
                onTogglePlayPause = playbackActions.togglePlayPause,
                onNext = playbackActions.next,
                artworkModifier = Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = artworkKey),
                    animatedVisibilityScope = this,
                ),
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
        ) {
        FloatingTabBar(
            selected = selected,
            onSelect = { destination ->
                if (destination == selected) return@FloatingTabBar
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .padding(
                    start = LiquidSpacing.screen,
                    end = LiquidSpacing.screen,
                    bottom = dockBottom,
                ),
        )
        }
        AnimatedVisibility(
            visible = playerState.error != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = LiquidSpacing.screen, vertical = LiquidSpacing.xs),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val error = playerState.error
            if (error != null) {
                PlaybackErrorBanner(
                    message = error.message.toPlaybackErrorMessage(),
                    onDismiss = playbackActions.clearError,
                )
            }
        }
    }
    }
}

@Composable
private fun PlaybackErrorBanner(message: String, onDismiss: () -> Unit) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        opacity = 0.70f,
        cornerRadius = 18.dp,
        elevation = 10.dp,
        tintColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = LiquidSpacing.md, end = LiquidSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f).padding(vertical = LiquidSpacing.sm),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier = Modifier.size(44.dp).liquidClickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭播放错误",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private fun String.toPlaybackErrorMessage(): String = when {
    contains("not available", ignoreCase = true) || contains("not found", ignoreCase = true) ->
        "找不到音乐文件，它可能已被移动或删除。请重新扫描资料库。"
    contains("format", ignoreCase = true) || contains("decoder", ignoreCase = true) ->
        "无法解码这个音频文件，格式可能不受设备支持。"
    else -> "播放失败：${take(120)}"
}
