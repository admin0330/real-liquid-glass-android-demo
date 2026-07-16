package io.github.admin0330.liquidmusic.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold
import io.github.admin0330.liquidmusic.core.ui.LibraryEmptyState
import io.github.admin0330.liquidmusic.core.ui.TrackRow
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.Track

@Composable
fun AlbumDetailScreen(
    album: Album?,
    tracks: List<Track>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlayQueue: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    val orderedTracks = tracks.sortedWith(
        compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy(Track::title),
    )
    FeatureScaffold(
        title = "专辑",
        subtitle = album?.artist?.ifBlank { "未知艺人" },
        bottomContentPadding = bottomPadding,
        modifier = modifier,
        actions = {
            Box(Modifier.size(44.dp).liquidClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回")
            }
        },
    ) {
        if (album == null) {
            item(key = "album-missing") {
                LibraryEmptyState(
                    title = "找不到这张专辑",
                    message = "音乐文件可能已被移动，请返回资料库重新扫描。",
                    actionLabel = "返回",
                    onAction = onBack,
                )
            }
            return@FeatureScaffold
        }
        item(key = "album-hero") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    artworkUri = album.artworkUri,
                    contentDescription = album.title,
                    modifier = artworkModifier.fillMaxWidth(0.76f).aspectRatio(1f),
                    cornerRadius = 24.dp,
                )
                Text(
                    album.title.ifBlank { "未知专辑" },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = LiquidSpacing.md),
                )
                Text(
                    buildString {
                        append(album.artist.ifBlank { "未知艺人" })
                        album.year?.let { append(" · $it") }
                        if (tracks.any { it.mimeType?.contains("flac", true) == true }) append(" · 无损")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }
        }
        if (orderedTracks.isNotEmpty()) {
            item(key = "album-controls") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                    AlbumAction("播放", Icons.Rounded.PlayArrow, Modifier.weight(1f)) {
                        onPlayQueue(orderedTracks.first(), orderedTracks)
                    }
                    AlbumAction("随机播放", Icons.Rounded.Shuffle, Modifier.weight(1f)) {
                        val shuffled = orderedTracks.shuffled()
                        onPlayQueue(shuffled.first(), shuffled)
                    }
                }
            }
            items(orderedTracks, key = Track::id) { track ->
                TrackRow(track, { onPlayQueue(track, orderedTracks) })
            }
        }
    }
}

@Composable
private fun AlbumAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    LiquidGlassSurface(modifier.liquidClickable(onClick = onClick), opacity = 0.42f, elevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = LiquidSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = LiquidSpacing.xs))
        }
    }
}
