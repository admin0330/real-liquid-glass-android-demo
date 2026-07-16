package io.github.admin0330.liquidmusic.feature.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold
import io.github.admin0330.liquidmusic.core.ui.LibraryEmptyState
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.Track

@Composable
fun RadioScreen(
    bottomPadding: Dp,
    artists: List<Artist>,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    onPlayQueue: (Track, List<Track>) -> Unit,
) {
    FeatureScaffold(
        title = "广播",
        subtitle = "由本地歌曲生成连续播放电台",
        bottomContentPadding = bottomPadding,
        modifier = modifier,
    ) {
        if (tracks.isEmpty()) {
            item(key = "empty-radio") {
                LibraryEmptyState(
                    title = "还没有可生成的电台",
                    message = "添加歌曲后，可以从喜爱的艺人、专辑或随机曲目生成完全离线的电台队列。",
                )
            }
        } else {
            val stations = artists.sortedByDescending { it.trackCount }.take(12)
            items(stations, key = { "station:${it.id}" }) { artist ->
                val stationTracks = tracks.filter { track ->
                    track.artistId?.let { artist.id.endsWith(":artist:$it") } == true ||
                        (track.artistId == null && track.artist.equals(artist.name, true))
                }
                val firstTrack = stationTracks.firstOrNull()
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidClickable(enabled = firstTrack != null) {
                            firstTrack?.let { onPlayQueue(it, stationTracks) }
                        },
                    opacity = 0.34f,
                    elevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LiquidSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
                    ) {
                        Artwork(artist.artworkUri, null, Modifier.size(64.dp), 14.dp)
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text("${artist.name.ifBlank { "未知艺人" }}电台", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${artist.trackCount} 首本地歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                        }
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
