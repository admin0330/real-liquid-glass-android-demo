package io.github.admin0330.liquidmusic.core.designsystem.theme

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ArtworkPaletteEngine @Inject constructor() {
    private val cache = LruCache<String, LiquidPalette>(32)

    suspend fun extract(
        cacheKey: String,
        bitmap: Bitmap,
        dark: Boolean,
    ): LiquidPalette = cache["$cacheKey:$dark"] ?: withContext(Dispatchers.Default) {
        val fallback = if (dark) LiquidPalette.DarkDefault else LiquidPalette.LightDefault
        val palette = Palette.from(bitmap)
            .maximumColorCount(20)
            .resizeBitmapArea(48_000)
            .generate()
        val primaryInt = palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: fallback.primary.toArgbCompat()
        val secondaryInt = palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: shiftLightness(primaryInt, if (dark) 0.16f else -0.12f)
        val tertiaryInt = palette.mutedSwatch?.rgb
            ?: shiftHue(primaryInt, 34f)
        val backgroundInt = blend(
            color = palette.darkMutedSwatch?.rgb ?: primaryInt,
            target = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            ratio = if (dark) 0.70f else 0.84f,
        )
        LiquidPalette(
            primary = Color(primaryInt),
            secondary = Color(secondaryInt),
            tertiary = Color(tertiaryInt),
            background = Color(backgroundInt),
            onBackground = if (ColorUtils.calculateLuminance(backgroundInt) < 0.42) Color.White else Color(0xFF151419),
            glassTint = Color(blend(primaryInt, if (dark) android.graphics.Color.DKGRAY else android.graphics.Color.WHITE, 0.72f)),
        ).also { cache.put("$cacheKey:$dark", it) }
    }

    fun clear() = cache.evictAll()

    private fun blend(color: Int, target: Int, ratio: Float): Int =
        ColorUtils.blendARGB(color, target, ratio.coerceIn(0f, 1f))

    private fun shiftLightness(color: Int, amount: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] + amount).coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[0] = (hsl[0] + degrees).mod(360f)
        return ColorUtils.HSLToColor(hsl)
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
