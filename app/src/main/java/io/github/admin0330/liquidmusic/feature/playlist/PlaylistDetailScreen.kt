package io.github.admin0330.liquidmusic.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.domain.model.Track

@Composable
fun PlaylistDetailScreen(
    bottomPadding: Dp,
    onBack: () -> Unit,
    onPlayQueue: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val playlist = detail?.playlist
    val entries = detail?.entries.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = LiquidSpacing.screen,
            end = LiquidSpacing.screen,
            top = LiquidSpacing.lg,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
    ) {
        item(key = "playlist-back") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).liquidClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回")
                }
                Text("播放列表", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item(key = "playlist-hero") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Artwork(
                    artworkUri = entries.firstOrNull()?.track?.artworkUri ?: playlist?.artworkUri,
                    contentDescription = playlist?.name,
                    modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f),
                    cornerRadius = 24.dp,
                )
                Text(
                    text = playlist?.name ?: "播放列表",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = LiquidSpacing.md),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${entries.size} 首歌曲", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (entries.isNotEmpty()) {
            item(key = "playlist-controls") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                    PlaylistPlayButton("播放", Icons.Rounded.PlayArrow, Modifier.weight(1f)) {
                        val queue = entries.map { it.track }
                        onPlayQueue(queue.first(), queue)
                    }
                    PlaylistPlayButton("随机播放", Icons.Rounded.Shuffle, Modifier.weight(1f)) {
                        val queue = entries.map { it.track }.shuffled()
                        onPlayQueue(queue.first(), queue)
                    }
                }
            }
            itemsIndexed(entries, key = { _, entry -> entry.entryId }) { index, entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(entry.track.artworkUri, null, Modifier.size(52.dp), 10.dp)
                    Column(Modifier.weight(1f).padding(horizontal = LiquidSpacing.sm)) {
                        Text(entry.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.track.artist.ifBlank { "未知艺人" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f), maxLines = 1)
                    }
                    if (index > 0) {
                        SmallAction(Icons.Rounded.KeyboardArrowUp, "上移") { viewModel.move(entry.entryId, -1) }
                    }
                    if (index < entries.lastIndex) {
                        SmallAction(Icons.Rounded.KeyboardArrowDown, "下移") { viewModel.move(entry.entryId, 1) }
                    }
                    SmallAction(Icons.Rounded.Close, "移除") { viewModel.remove(entry.entryId) }
                }
            }
        } else {
            item(key = "playlist-empty") {
                Text("此播放列表还没有歌曲。可在资料库的歌曲菜单中添加。", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f), modifier = Modifier.padding(vertical = LiquidSpacing.xl))
            }
        }
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
    }
}

@Composable
private fun PlaylistPlayButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    LiquidGlassSurface(modifier.liquidClickable(onClick = onClick), opacity = 0.42f, elevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = LiquidSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = LiquidSpacing.xs))
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).liquidClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
    }
}
