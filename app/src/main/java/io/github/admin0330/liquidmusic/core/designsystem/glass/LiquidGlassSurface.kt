package io.github.admin0330.liquidmusic.core.designsystem.glass

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.admin0330.liquidmusic.core.designsystem.theme.LocalLiquidPalette
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidMotion

private val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
private val LocalGlassLight = staticCompositionLocalOf { 0.5f }

@Composable
fun LiquidGlassHost(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.(Modifier) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = androidx.compose.runtime.remember { HazeState() }
    val transition = rememberInfiniteTransition(label = "shared-glass-light")
    val light by transition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(LiquidMotion.deliberate * 8),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shared-glass-light-position",
    )
    CompositionLocalProvider(LocalHazeState provides hazeState, LocalGlassLight provides light) {
        Box(modifier = modifier) {
            background(Modifier.hazeSource(hazeState))
            content()
        }
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 24.dp,
    opacity: Float = 0.62f,
    cornerRadius: Dp = 24.dp,
    tintColor: Color = LocalLiquidPalette.current.glassTint,
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    val hazeState = LocalHazeState.current
    val light = LocalGlassLight.current
    val fallback = Modifier.background(tintColor.copy(alpha = opacity.coerceIn(0f, 1f)), shape)
    val haze = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = tintColor.copy(alpha = 0.01f),
                tints = listOf(HazeTint(tintColor.copy(alpha = opacity.coerceIn(0f, 1f)))),
                blurRadius = blurRadius,
                noiseFactor = 0.08f,
            ),
        )
    } else {
        fallback
    }
    Box(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .then(haze)
            .border(BorderStroke(0.7.dp, Color.White.copy(alpha = 0.22f)), shape)
            .background(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        light.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
                shape = shape,
            ),
        content = content,
    )
}
