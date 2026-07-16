package io.github.admin0330.liquidmusic.core.designsystem.tokens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object LiquidMotion {
    const val quick = 180
    const val standard = 320
    const val deliberate = 520

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessLow,
    )
}
