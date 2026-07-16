package io.github.admin0330.liquidmusic.core.designsystem.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberArtworkPalette(
    artworkUri: String?,
    dark: Boolean,
): LiquidPalette {
    val fallback = if (dark) LiquidPalette.DarkDefault else LiquidPalette.LightDefault
    val context = LocalContext.current
    val engine = remember { ArtworkPaletteEngine() }
    val palette by produceState(fallback, artworkUri, dark) {
        value = fallback
        if (artworkUri.isNullOrBlank()) return@produceState
        val bitmap = withContext(Dispatchers.IO) {
            val uri = runCatching { artworkUri.toUri() }.getOrNull() ?: return@withContext null
            runCatching { decodeSampledArtwork(context, uri) }.getOrNull()
        } ?: return@produceState
        try {
            value = engine.extract(artworkUri, bitmap, dark)
        } finally {
            bitmap.recycle()
        }
    }
    return palette
}

private fun decodeSampledArtwork(context: android.content.Context, uri: Uri): Bitmap? {
    fun open() = when (uri.scheme) {
        "file" -> File(requireNotNull(uri.path)).inputStream()
        "content" -> context.contentResolver.openInputStream(uri)
        else -> null
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > PALETTE_BITMAP_EDGE * 2 ||
        bounds.outHeight / sampleSize > PALETTE_BITMAP_EDGE * 2
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return open()?.use { BitmapFactory.decodeStream(it, null, options) }
}

private const val PALETTE_BITMAP_EDGE = 256
