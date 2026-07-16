package io.github.admin0330.liquidmusic.domain.repository

import android.net.Uri
import io.github.admin0330.liquidmusic.core.lyrics.ParsedLyrics
import io.github.admin0330.liquidmusic.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun observeLyrics(trackId: String): Flow<ParsedLyrics?>
    suspend fun discoverSidecar(track: Track): ParsedLyrics?
    suspend fun attach(trackId: String, uri: Uri): ParsedLyrics
    suspend fun remove(trackId: String): Boolean
}
