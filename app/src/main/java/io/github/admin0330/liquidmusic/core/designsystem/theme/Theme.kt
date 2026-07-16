package io.github.admin0330.liquidmusic.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalLiquidPalette = staticCompositionLocalOf { LiquidPalette.DarkDefault }

private val LiquidTypography = Typography(
    displayLarge = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6f).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4f).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2f).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LiquidMusicTheme(
    darkTheme: Boolean,
    palette: LiquidPalette = if (darkTheme) LiquidPalette.DarkDefault else LiquidPalette.LightDefault,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = Color(0xFF16151A),
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            error = Color(0xFFFF453A),
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = Color.White,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground,
            error = Color(0xFFFF3B30),
        )
    }
    CompositionLocalProvider(LocalLiquidPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = LiquidTypography, content = content)
    }
}
