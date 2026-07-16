package io.github.admin0330.liquidmusic.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMetadataMultipleTimestampsAndOffset() {
        val result = LrcParser.parse(
            """
            [ti:Local Song]
            [ar:Local Artist]
            [offset:150]
            [00:01.25][00:03.250]First line
            [00:02]Second line
            """.trimIndent(),
        )

        assertEquals("Local Song", result.title)
        assertEquals("Local Artist", result.artist)
        assertEquals(listOf(1_400L, 2_150L, 3_400L), result.lines.map { it.timeMs })
        assertEquals(1, result.activeLineIndex(2_500L))
    }

    @Test
    fun clampsNegativeOffsetAndIgnoresMalformedLines() {
        val result = LrcParser.parse("[offset:-500]\n[00:00.10]Start\nnot timed")
        assertEquals(0L, result.lines.single().timeMs)
    }
}
