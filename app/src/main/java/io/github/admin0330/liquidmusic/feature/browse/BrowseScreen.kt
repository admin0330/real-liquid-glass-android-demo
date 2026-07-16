package io.github.admin0330.liquidmusic.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.AlbumArtworkCard
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold
import io.github.admin0330.liquidmusic.core.ui.LibraryEmptyState
import io.github.admin0330.liquidmusic.core.ui.MusicSectionHeader
import io.github.admin0330.liquidmusic.core.ui.TrackCarousel
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.Track

@Composable
fun BrowseScreen(
    bottomPadding: Dp,
    albums: List<Album>,
    tracks: List<Track>,
    recentlyAdded: List<Track>,
    modifier: Modifier = Modifier,
    onPlayQueue: (Track, List<Track>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    albumArtworkModifier: @Composable (String) -> Modifier = { Modifier },
) {
    FeatureScaffold(
        title = "新发现",
        subtitle = "从你的本地资料库重新发现音乐",
        bottomContentPadding = bottomPadding,
        modifier = modifier,
    ) {
        if (albums.isEmpty() && recentlyAdded.isEmpty()) {
            item(key = "empty-browse") {
                LibraryEmptyState(
                    title = "暂无本地精选",
                    message = "扫描音乐并积累播放记录后，这里会按年代和最近添加自动整理，不连接在线曲库。",
                )
            }
        } else {
            if (albums.isNotEmpty()) {
                item(key = "albums-header") { MusicSectionHeader("专辑精选") }
                item(key = "albums") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                        items(albums.take(20), key = { it.id }) { album ->
                            AlbumArtworkCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id) },
                                artworkModifier = albumArtworkModifier(album.id),
                            )
                        }
                    }
                }
            }
            if (recentlyAdded.isNotEmpty()) {
                item(key = "fresh-header") { MusicSectionHeader("刚刚加入资料库") }
                item(key = "fresh-tracks") {
                    TrackCarousel(
                        tracks = recentlyAdded,
                        onTrackClick = { onPlayQueue(it, recentlyAdded) },
                    )
                }
            }
        }
    }
}
