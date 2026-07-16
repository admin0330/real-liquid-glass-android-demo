package io.github.admin0330.liquidmusic.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidMotion
import io.github.admin0330.liquidmusic.feature.browse.BrowseScreen
import io.github.admin0330.liquidmusic.feature.home.HomeScreen
import io.github.admin0330.liquidmusic.feature.library.LibraryScreen
import io.github.admin0330.liquidmusic.feature.library.AlbumDetailScreen
import io.github.admin0330.liquidmusic.feature.radio.RadioScreen
import io.github.admin0330.liquidmusic.feature.search.SearchScreen
import io.github.admin0330.liquidmusic.feature.settings.SettingsScreen
import io.github.admin0330.liquidmusic.feature.settings.OpenSourceLicensesScreen
import io.github.admin0330.liquidmusic.app.LibraryScanUiState
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.player.PlaybackState
import io.github.admin0330.liquidmusic.core.lyrics.ParsedLyrics
import io.github.admin0330.liquidmusic.feature.player.PlaybackActions
import io.github.admin0330.liquidmusic.feature.player.PlayerScreen
import io.github.admin0330.liquidmusic.feature.playlist.PlaylistDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.net.Uri

private fun enter(): EnterTransition = fadeIn(tween(LiquidMotion.standard)) +
    scaleIn(initialScale = 0.985f, animationSpec = tween(LiquidMotion.standard))

private fun exit(): ExitTransition = fadeOut(tween(LiquidMotion.quick)) +
    scaleOut(targetScale = 1.01f, animationSpec = tween(LiquidMotion.quick))

@Composable
fun SharedTransitionScope.AppNavHost(
    navController: NavHostController,
    bottomPadding: Dp,
    library: AppLibrarySnapshot,
    scanState: LibraryScanUiState,
    onRescan: () -> Unit,
    onRequestPermission: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayQueue: (Track, List<Track>) -> Unit,
    onOpenSettings: () -> Unit,
    playerState: PlaybackState,
    lyrics: ParsedLyrics?,
    playbackActions: PlaybackActions,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.ListenNow.route,
        modifier = modifier,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { enter() },
        popExitTransition = { exit() },
    ) {
        composable(TopLevelDestination.ListenNow.route) {
            val visibilityScope = this
            HomeScreen(
                bottomPadding = bottomPadding,
                library = library,
                scanState = scanState,
                onRequestPermission = onRequestPermission,
                onRescan = onRescan,
                onTrackClick = onTrackClick,
                onPlayQueue = onPlayQueue,
                onOpenSettings = onOpenSettings,
                onOpenAlbum = { albumId -> navController.navigate("album/${Uri.encode(albumId)}") },
                albumArtworkModifier = { albumId ->
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("album-artwork-$albumId"),
                        animatedVisibilityScope = visibilityScope,
                    )
                },
            )
        }
        composable(TopLevelDestination.Browse.route) {
            val visibilityScope = this
            BrowseScreen(
                bottomPadding = bottomPadding,
                albums = library.albums,
                tracks = library.tracks,
                recentlyAdded = library.recentlyAdded,
                onPlayQueue = onPlayQueue,
                onOpenAlbum = { albumId -> navController.navigate("album/${Uri.encode(albumId)}") },
                albumArtworkModifier = { albumId ->
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("album-artwork-$albumId"),
                        animatedVisibilityScope = visibilityScope,
                    )
                },
            )
        }
        composable(TopLevelDestination.Radio.route) {
            RadioScreen(bottomPadding, library.artists, library.tracks, onPlayQueue = onPlayQueue)
        }
        composable(TopLevelDestination.Library.route) {
            val visibilityScope = this
            LibraryScreen(
                bottomPadding = bottomPadding,
                tracks = library.tracks,
                albums = library.albums,
                artists = library.artists,
                playlists = library.playlists,
                scanState = scanState,
                onRescan = onRescan,
                onRequestPermission = onRequestPermission,
                onTrackClick = onTrackClick,
                onPlayQueue = onPlayQueue,
                onOpenPlaylist = { playlistId -> navController.navigate("playlist/${Uri.encode(playlistId)}") },
                onOpenAlbum = { albumId -> navController.navigate("album/${Uri.encode(albumId)}") },
                albumArtworkModifier = { albumId ->
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("album-artwork-$albumId"),
                        animatedVisibilityScope = visibilityScope,
                    )
                },
            )
        }
        composable(TopLevelDestination.Search.route) {
            SearchScreen(
                bottomPadding = bottomPadding,
                onTrackClick = onTrackClick,
                allTracks = library.tracks,
                onPlayQueue = onPlayQueue,
                onOpenPlaylist = { playlistId -> navController.navigate("playlist/${Uri.encode(playlistId)}") },
                onOpenAlbum = { albumId -> navController.navigate("album/${Uri.encode(albumId)}") },
            )
        }
        composable("settings") {
            SettingsScreen(
                bottomPadding = bottomPadding,
                onBack = { navController.popBackStack() },
                onRescan = onRescan,
                onOpenLicenses = { navController.navigate("licenses") { launchSingleTop = true } },
            )
        }
        composable("licenses") {
            OpenSourceLicensesScreen(
                bottomPadding = bottomPadding,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "player",
            enterTransition = { fadeIn(tween(LiquidMotion.standard)) },
            exitTransition = { fadeOut(tween(LiquidMotion.quick)) },
            popEnterTransition = { fadeIn(tween(LiquidMotion.quick)) },
            popExitTransition = { fadeOut(tween(LiquidMotion.quick)) },
        ) {
            val artworkKey = "now-playing-artwork-${playerState.currentTrackId}"
            PlayerScreen(
                state = playerState,
                lyrics = lyrics,
                actions = playbackActions,
                onDismiss = { navController.popBackStack() },
                artworkModifier = Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = artworkKey),
                    animatedVisibilityScope = this,
                ),
            )
        }
        composable(
            route = "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            enterTransition = { fadeIn(tween(LiquidMotion.standard)) + scaleIn(initialScale = 0.94f, animationSpec = tween(LiquidMotion.standard)) },
            popExitTransition = { fadeOut(tween(LiquidMotion.quick)) + scaleOut(targetScale = 0.94f, animationSpec = tween(LiquidMotion.quick)) },
        ) { entry ->
            val albumId = entry.arguments?.getString("albumId")
            val album = library.albums.firstOrNull { it.id == albumId }
            val tracks = album?.let { selected ->
                library.tracks.filter { track ->
                    track.albumId?.let { selected.id.endsWith(":album:$it") } == true ||
                        (track.albumId == null && track.album.equals(selected.title, ignoreCase = true))
                }
            }.orEmpty()
            AlbumDetailScreen(
                album = album,
                tracks = tracks,
                bottomPadding = bottomPadding,
                onBack = { navController.popBackStack() },
                onPlayQueue = onPlayQueue,
                artworkModifier = Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState("album-artwork-$albumId"),
                    animatedVisibilityScope = this,
                ),
            )
        }
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
        ) {
            PlaylistDetailScreen(
                bottomPadding = bottomPadding,
                onBack = { navController.popBackStack() },
                onPlayQueue = onPlayQueue,
            )
        }
    }
}
