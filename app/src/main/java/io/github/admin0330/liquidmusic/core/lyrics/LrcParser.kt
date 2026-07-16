package io.github.admin0330.liquidmusic.core.lyrics

import java.util.Locale

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class ParsedLyrics(
    val lines: List<LyricLine>,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val offsetMs: Long = 0,
) {
    fun activeLineIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timeMs <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }
}

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val metadata = Regex("^\\[([a-zA-Z]+):(.*)]$")

    fun parse(raw: String): ParsedLyrics {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L
        val parsed = mutableListOf<LyricLine>()

        raw.lineSequence().forEach { sourceLine ->
            val line = sourceLine.removePrefix("\uFEFF").trimEnd()
            val meta = metadata.matchEntire(line.trim())
            if (meta != null && timestamp.find(line) == null) {
                val value = meta.groupValues[2].trim().takeIf(String::isNotEmpty)
                when (meta.groupValues[1].lowercase(Locale.ROOT)) {
                    "ti" -> title = value
                    "ar" -> artist = value
                    "al" -> album = value
                    "offset" -> offset = value?.toLongOrNull() ?: offset
                }
                return@forEach
            }
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = timestamp.replace(line, "").trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionText = match.groupValues[3]
                val fractionMs = when (fractionText.length) {
                    1 -> fractionText.toLongOrNull()?.times(100)
                    2 -> fractionText.toLongOrNull()?.times(10)
                    3 -> fractionText.toLongOrNull()
                    else -> 0L
                } ?: 0L
                parsed += LyricLine(
                    timeMs = (minutes * 60_000 + seconds * 1_000 + fractionMs),
                    text = text,
                )
            }
        }

        val adjusted = parsed
            .map { it.copy(timeMs = (it.timeMs + offset).coerceAtLeast(0)) }
            .sortedWith(compareBy<LyricLine> { it.timeMs }.thenBy { it.text })
            .distinctBy { it.timeMs to it.text }
        return ParsedLyrics(
            lines = adjusted,
            title = title,
            artist = artist,
            album = album,
            offsetMs = offset,
        )
    }
}
