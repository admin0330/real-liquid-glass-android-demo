package io.github.admin0330.liquidmusic.feature.player

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassHost
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidMotion
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.lyrics.ParsedLyrics
import io.github.admin0330.liquidmusic.core.ui.formatDuration
import io.github.admin0330.liquidmusic.player.PlaybackState
import io.github.admin0330.liquidmusic.player.RepeatMode
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    state: PlaybackState,
    lyrics: ParsedLyrics?,
    actions: PlaybackActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    if (track == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragStartedAtTop by remember { mutableStateOf(false) }
    val translation by animateFloatAsState(dragY, LiquidMotion.responsiveSpring(), label = "player-pull")
    val context = LocalContext.current
    ForceDarkPlayerSystemBars()
    BackHandler {
        when {
            showQueue -> showQueue = false
            showLyrics -> showLyrics = false
            else -> onDismiss()
        }
    }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            actions.attachLyrics(uri)
        }
    }

    LiquidGlassHost(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = translation }
            .pointerInput(showLyrics, showQueue) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragStartedAtTop = !showQueue && offset.y <= 140.dp.toPx()
                    },
                    onVerticalDrag = { change, amount ->
                        if (dragStartedAtTop && amount > 0f) {
                            change.consume()
                            dragY = (dragY + amount).coerceAtMost(size.height * 0.45f)
                        }
                    },
                    onDragCancel = { dragY = 0f; dragStartedAtTop = false },
                    onDragEnd = {
                        val dismiss = dragY > 96.dp.toPx()
                        dragY = 0f
                        dragStartedAtTop = false
                        if (dismiss) {
                            if (showLyrics) showLyrics = false else onDismiss()
                        }
                    },
                )
            },
        background = { source -> PlayerBackdrop(track.artworkUri, source) },
    ) {
        AnimatedContent(
            targetState = showLyrics,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(LiquidMotion.standard)) + slideInHorizontally { if (targetState) it / 8 else -it / 8 } + scaleIn(initialScale = 0.985f)) togetherWith
                    (fadeOut(tween(LiquidMotion.quick)) + slideOutHorizontally { if (targetState) -it / 10 else it / 10 } + scaleOut(targetScale = 0.99f))
            },
            label = "lyrics-transition",
        ) { lyricsVisible ->
            if (lyricsVisible) {
                LyricsPlayerPage(
                    state = state,
                    lyrics = lyrics,
                    actions = actions,
                    onCloseLyrics = { showLyrics = false },
                    onChooseLyrics = { lyricsPicker.launch(arrayOf("text/*", "application/octet-stream")) },
                )
            } else {
                ArtworkPlayerPage(
                    state = state,
                    actions = actions,
                    onDismiss = onDismiss,
                    onLyrics = { showLyrics = true },
                    onQueue = { showQueue = true },
                    modifier = artworkModifier,
                )
            }
        }
        AnimatedVisibility(
            visible = showQueue,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn() + scaleIn(initialScale = 0.98f),
            exit = fadeOut() + scaleOut(targetScale = 0.98f),
        ) {
            QueueOverlay(state = state, actions = actions, onClose = { showQueue = false })
        }
    }
}

@Composable
private fun PlayerBackdrop(artworkUri: String?, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF17131A))) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }.blur(64.dp).alpha(0.74f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.72f)),
                ),
            ),
        )
    }
}

@Composable
private fun ArtworkPlayerPage(
    state: PlaybackState,
    actions: PlaybackActions,
    onDismiss: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier,
) {
    val track = requireNotNull(state.currentTrack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = LiquidSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).liquidClickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起播放器", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("正在播放", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val artworkSize = (maxWidth - 20.dp).coerceAtMost(360.dp).coerceAtLeast(300.dp)
            val pulse = rememberInfiniteTransition(label = "artwork-breathe")
            val breathing by pulse.animateFloat(
                initialValue = 0.992f,
                targetValue = if (state.isPlaying) 1.008f else 1f,
                animationSpec = infiniteRepeatable(tween(3_800), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
                label = "artwork-scale",
            )
            Artwork(
                artworkUri = track.artworkUri,
                contentDescription = track.album,
                modifier = modifier
                    .size(artworkSize)
                    .graphicsLayer {
                        scaleX = breathing
                        scaleY = breathing
                        shadowElevation = 22.dp.toPx()
                    },
                cornerRadius = 24.dp,
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = LiquidSpacing.md)) {
            Text(track.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { "未知艺人" }, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.64f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.album.ifBlank { "未知专辑" }, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.46f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerProgress(state, actions.seekTo)
        MainPlaybackControls(state, actions)
        SecondaryControls(state, actions, onLyrics, onQueue)
        Spacer(Modifier.height(LiquidSpacing.sm))
    }
}

@Composable
private fun PlayerProgress(state: PlaybackState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var localProgress by remember { mutableFloatStateOf(0f) }
    val actual = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val value = if (dragging) localProgress else actual.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = LiquidSpacing.md)) {
        Slider(
            value = value,
            onValueChange = { dragging = true; localProgress = it },
            onValueChangeFinished = {
                onSeek((localProgress * state.durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(if (dragging) (localProgress * state.durationMs).toLong() else state.positionMs), color = Color.White.copy(alpha = 0.54f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))}", color = Color.White.copy(alpha = 0.54f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MainPlaybackControls(state: PlaybackState, actions: PlaybackActions) {
    Row(
        Modifier.fillMaxWidth().height(88.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIcon(Icons.Rounded.SkipPrevious, "上一首", 38.dp, actions.previous)
        PlayPauseControl(state, 62.dp, actions.togglePlayPause)
        PlayerIcon(Icons.Rounded.SkipNext, "下一首", 38.dp, actions.next)
    }
}

@Composable
private fun SecondaryControls(
    state: PlaybackState,
    actions: PlaybackActions,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        PlayerIcon(Icons.Rounded.Shuffle, "随机播放", 24.dp, actions.toggleShuffle, state.shuffleEnabled)
        PlayerIcon(if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "循环模式", 24.dp, actions.cycleRepeat, state.repeatMode != RepeatMode.OFF)
        PlayerIcon(Icons.Rounded.GraphicEq, "歌词", 25.dp, onLyrics)
        PlayerIcon(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", 25.dp, onQueue)
    }
}

@Composable
private fun PlayerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(Modifier.size(52.dp).liquidClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = if (active) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(size))
    }
}

@Composable
private fun PlayPauseControl(
    state: PlaybackState,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(Modifier.size(72.dp).liquidClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (state.isBuffering && state.playWhenReady) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize * 0.68f),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else {
            Icon(
                imageVector = if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.playWhenReady) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun LyricsPlayerPage(
    state: PlaybackState,
    lyrics: ParsedLyrics?,
    actions: PlaybackActions,
    onCloseLyrics: () -> Unit,
    onChooseLyrics: () -> Unit,
) {
    val track = requireNotNull(state.currentTrack)
    val active = lyrics?.activeLineIndex(state.positionMs) ?: -1
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) {
            val centerOffset = -(listState.layoutInfo.viewportSize.height * 0.34f).roundToInt()
            listState.animateScrollToItem(active, scrollOffset = centerOffset)
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(76.dp).padding(horizontal = LiquidSpacing.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(track.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist.ifBlank { "未知艺人" }, color = Color.White.copy(alpha = 0.56f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Spacer(Modifier.width(LiquidSpacing.sm))
                Artwork(track.artworkUri, null, Modifier.size(48.dp), 12.dp)
            }
            if (lyrics == null || lyrics.lines.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(horizontal = LiquidSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("没有找到同步歌词", color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text("可选择与当前歌曲匹配的本地 .lrc 文件", color = Color.White.copy(alpha = 0.58f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = LiquidSpacing.sm))
                    Text("选择歌词文件", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = LiquidSpacing.lg).liquidClickable(onClick = onChooseLyrics))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = LiquidSpacing.lg, top = 88.dp, end = LiquidSpacing.lg, bottom = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(lyrics.lines.size, key = { index -> "${lyrics.lines[index].timeMs}:$index" }) { index ->
                        val line = lyrics.lines[index]
                        val distance = abs(index - active)
                        val alpha by animateFloatAsState(if (index == active) 1f else if (distance <= 2) 0.54f else 0.28f, tween(LiquidMotion.quick), label = "lyric-alpha")
                        val scale by animateFloatAsState(if (index == active) 1.025f else 1f, tween(LiquidMotion.standard), label = "lyric-scale")
                        val weight by animateFloatAsState(if (index == active) 700f else 600f, tween(LiquidMotion.standard), label = "lyric-weight")
                        val blurRadius by animateDpAsState(if (index == active || distance <= 1) 0.dp else 1.1.dp, tween(LiquidMotion.standard), label = "lyric-blur")
                        Text(
                            text = line.text.ifBlank { "♪" },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight(weight.roundToInt())),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .alpha(alpha)
                                .blur(blurRadius)
                                .liquidClickable { actions.seekTo(line.timeMs) },
                        )
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(184.dp).background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f), Color.Black.copy(alpha = 0.84f))),
            ),
        ) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = LiquidSpacing.screen, vertical = LiquidSpacing.sm)) {
                PlayerProgress(state, actions.seekTo)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlayerIcon(Icons.Rounded.SkipPrevious, "上一首", 30.dp, actions.previous)
                    PlayPauseControl(state, 62.dp, actions.togglePlayPause)
                    PlayerIcon(Icons.Rounded.SkipNext, "下一首", 30.dp, actions.next)
                    PlayerIcon(Icons.Rounded.GraphicEq, "关闭歌词", 24.dp, onCloseLyrics)
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(width = 36.dp, height = 5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.32f)))
    }
}

@Composable
private fun ForceDarkPlayerSystemBars() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun QueueOverlay(state: PlaybackState, actions: PlaybackActions, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)).liquidClickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xF51A181D), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(LiquidSpacing.md),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("接下来播放", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).liquidClickable(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White) }
            }
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(state.queue.size, key = { state.queue[it].queueEntryId }) { index ->
                    val entry = state.queue[index]
                    Row(
                        Modifier.fillMaxWidth().liquidClickable { actions.playQueueEntry(entry.queueEntryId) }.padding(vertical = LiquidSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(entry.track.artworkUri, null, Modifier.size(46.dp), 10.dp)
                        Column(Modifier.weight(1f).padding(horizontal = LiquidSpacing.sm)) {
                            Text(entry.track.title, color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.track.artist.ifBlank { "未知艺人" }, color = Color.White.copy(alpha = 0.52f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        if (index > 0) {
                            QueueAction(Icons.Rounded.KeyboardArrowUp, "上移") { actions.moveQueueEntry(entry.queueEntryId, index - 1) }
                        }
                        if (index < state.queue.lastIndex) {
                            QueueAction(Icons.Rounded.KeyboardArrowDown, "下移") { actions.moveQueueEntry(entry.queueEntryId, index + 1) }
                        }
                        QueueAction(Icons.Rounded.Close, "移除") { actions.removeQueueEntry(entry.queueEntryId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(Modifier.size(34.dp).liquidClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = 0.66f), modifier = Modifier.size(19.dp))
    }
}
