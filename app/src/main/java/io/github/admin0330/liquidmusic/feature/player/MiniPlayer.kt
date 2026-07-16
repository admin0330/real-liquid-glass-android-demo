package io.github.admin0330.liquidmusic.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.player.PlaybackState

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0) {
        state.positionMs.toFloat() / state.durationMs.toFloat()
    } else 0f
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(LiquidSpacing.miniPlayerHeight)
            .liquidClickable(onClick = onOpen),
        cornerRadius = 24.dp,
        blurRadius = 28.dp,
        opacity = 0.56f,
        elevation = 12.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = LiquidSpacing.xs, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artworkUri, track.album, artworkModifier.size(52.dp), 14.dp)
                Spacer(Modifier.width(LiquidSpacing.sm))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist.ifBlank { "未知艺人" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(48.dp).liquidClickable(onClick = onTogglePlayPause), contentAlignment = Alignment.Center) {
                    if (state.isBuffering && state.playWhenReady) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playWhenReady) "暂停" else "播放",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Box(Modifier.size(44.dp).liquidClickable(onClick = onNext), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", modifier = Modifier.size(25.dp))
                }
            }
            val progressColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color = progressColor.copy(alpha = 0.82f),
                    start = Offset(18.dp.toPx(), size.height - 1.2.dp.toPx()),
                    end = Offset(18.dp.toPx() + (size.width - 36.dp.toPx()) * progress.coerceIn(0f, 1f), size.height - 1.2.dp.toPx()),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
