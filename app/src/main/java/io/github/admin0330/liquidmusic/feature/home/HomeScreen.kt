package io.github.admin0330.liquidmusic.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import io.github.admin0330.liquidmusic.app.LibraryScanUiState
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.AlbumArtworkCard
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold
import io.github.admin0330.liquidmusic.core.ui.LibraryEmptyState
import io.github.admin0330.liquidmusic.core.ui.MusicSectionHeader
import io.github.admin0330.liquidmusic.core.ui.TrackCarousel
import io.github.admin0330.liquidmusic.core.ui.ArtistArtworkCard
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.navigation.AppLibrarySnapshot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.domain.model.LibraryScanFailure

@Composable
fun HomeScreen(
    bottomPadding: Dp,
    library: AppLibrarySnapshot,
    scanState: LibraryScanUiState,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayQueue: (Track, List<Track>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    albumArtworkModifier: @Composable (String) -> Modifier = { Modifier },
) {
    val greeting = greetingForHour(java.time.LocalTime.now().hour)
    FeatureScaffold(
        title = greeting,
        subtitle = "只根据设备上的音乐与播放历史生成",
        bottomContentPadding = bottomPadding,
        modifier = modifier,
        actions = {
            Box(Modifier.size(44.dp).liquidClickable(onClick = onOpenSettings), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置")
            }
        },
    ) {
        if (library.tracks.isEmpty()) {
            item(key = "empty-home") {
                val permissionNeeded = scanState is LibraryScanUiState.PermissionRequired ||
                    scanState is LibraryScanUiState.PermissionDenied
                LibraryEmptyState(
                    title = when (scanState) {
                        LibraryScanUiState.Scanning -> "正在扫描音乐"
                        is LibraryScanUiState.Failed -> "无法读取系统音乐库"
                        else -> "让音乐住进这里"
                    },
                    message = when {
                        permissionNeeded -> "授权读取设备音乐后，会自动显示最近播放、最近添加和你的本地精选。"
                        scanState is LibraryScanUiState.Failed -> scanState.reason.toUserMessage()
                        else -> "没有在 MediaStore 中找到受支持的 MP3、FLAC、WAV、M4A 或 OGG 文件。"
                    },
                    actionLabel = when {
                        permissionNeeded -> "允许访问音乐"
                        scanState is LibraryScanUiState.Failed -> "重新扫描"
                        else -> null
                    },
                    onAction = when {
                        permissionNeeded -> onRequestPermission
                        scanState is LibraryScanUiState.Failed -> onRescan
                        else -> null
                    },
                )
            }
        } else {
            if (library.recentlyPlayed.isNotEmpty()) {
                item(key = "recent-header") { MusicSectionHeader("最近播放") }
                item(key = "recent-tracks") {
                    TrackCarousel(library.recentlyPlayed, onTrackClick)
                }
            }
            val historyRecommendations = library.tracks
                .filter { it.playCount > 0 }
                .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedAtMs ?: 0L })
            val madeForYou = (library.favorites + historyRecommendations).distinctBy(Track::id).take(12)
            if (madeForYou.isNotEmpty()) {
                item(key = "made-header") { MusicSectionHeader("为你推荐") }
                item(key = "made-tracks") { TrackCarousel(madeForYou, onTrackClick) }
            }
            if (library.recentlyAdded.isNotEmpty()) {
                item(key = "added-header") { MusicSectionHeader("最近添加") }
                item(key = "added-tracks") { TrackCarousel(library.recentlyAdded, onTrackClick) }
            }
            val recentlyPlayedArtists = library.recentlyPlayed
                .map(Track::artist)
                .filter(String::isNotBlank)
                .distinctBy(String::lowercase)
                .mapNotNull { recentName ->
                    library.artists.firstOrNull { it.name.equals(recentName, ignoreCase = true) }
                }
                .take(10)
            if (recentlyPlayedArtists.isNotEmpty()) {
                item(key = "recent-artists-header") { MusicSectionHeader("最近播放的艺人") }
                item(key = "recent-artists") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                        items(recentlyPlayedArtists, key = { it.id }) { artist ->
                            ArtistArtworkCard(
                                artist = artist,
                                onClick = {
                                    val artistTracks = library.tracks.filter { track ->
                                        track.artistId?.let { artist.id.endsWith(":artist:$it") } == true ||
                                            (track.artistId == null && track.artist.equals(artist.name, ignoreCase = true))
                                    }
                                    artistTracks.firstOrNull()?.let { onPlayQueue(it, artistTracks) }
                                },
                            )
                        }
                    }
                }
            }
            val favoriteAlbumIds = library.favorites.mapNotNull { track ->
                track.albumId?.let { ":album:$it" }
            }.toSet()
            val favoriteAlbums = library.albums.filter { album -> favoriteAlbumIds.any(album.id::endsWith) }
            if (favoriteAlbums.isNotEmpty()) {
                item(key = "albums-header") { MusicSectionHeader("喜爱的专辑") }
                item(key = "albums-row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                        items(favoriteAlbums, key = { it.id }) { album ->
                            AlbumArtworkCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id) },
                                artworkModifier = albumArtworkModifier(album.id),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LibraryScanFailure.toUserMessage(): String = when (this) {
    LibraryScanFailure.MEDIA_PROVIDER_UNAVAILABLE -> "系统媒体服务暂时不可用，请稍后重新打开应用。"
    LibraryScanFailure.QUERY_FAILED -> "扫描音乐时发生错误，请检查权限后重试。"
}

internal fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}
