package io.github.admin0330.liquidmusic.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class LiquidPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val onBackground: Color,
    val glassTint: Color,
) {
    companion object {
        val DarkDefault = LiquidPalette(
            primary = Color(0xFFFF375F),
            secondary = Color(0xFF7D5CFF),
            tertiary = Color(0xFF1FC9FF),
            background = Color(0xFF09090B),
            onBackground = Color(0xFFF7F7FA),
            glassTint = Color(0xFFDDD8E7),
        )

        val LightDefault = LiquidPalette(
            primary = Color(0xFFFA2D55),
            secondary = Color(0xFF6954D7),
            tertiary = Color(0xFF007FA8),
            background = Color(0xFFF5F3F7),
            onBackground = Color(0xFF151419),
            glassTint = Color.White,
        )
    }
}
