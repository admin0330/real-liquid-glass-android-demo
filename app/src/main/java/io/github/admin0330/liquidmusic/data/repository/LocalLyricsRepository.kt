package io.github.admin0330.liquidmusic.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.admin0330.liquidmusic.core.lyrics.LrcParser
import io.github.admin0330.liquidmusic.core.lyrics.ParsedLyrics
import io.github.admin0330.liquidmusic.data.local.database.dao.LyricsDao
import io.github.admin0330.liquidmusic.data.local.database.entity.LyricsEntity
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.repository.LyricsRepository
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class LocalLyricsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lyricsDao: LyricsDao,
) : LyricsRepository {
    override fun observeLyrics(trackId: String): Flow<ParsedLyrics?> = lyricsDao.observe(trackId).map {
        it?.rawText?.let(LrcParser::parse)
    }

    override suspend fun discoverSidecar(track: Track): ParsedLyrics? = withContext(Dispatchers.IO) {
        lyricsDao.get(track.id)?.let { return@withContext LrcParser.parse(it.rawText) }
        val uri = findSidecar(track) ?: return@withContext null
        val raw = readText(uri)
        lyricsDao.upsert(LyricsEntity(track.id, raw, uri.toString(), System.currentTimeMillis()))
        LrcParser.parse(raw)
    }

    override suspend fun attach(trackId: String, uri: Uri): ParsedLyrics = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported lyrics URI" }
        val raw = readText(uri)
        val parsed = LrcParser.parse(raw)
        require(parsed.lines.isNotEmpty()) { "歌词文件中没有有效的 LRC 时间标签" }
        lyricsDao.upsert(LyricsEntity(trackId, raw, uri.toString(), System.currentTimeMillis()))
        parsed
    }

    override suspend fun remove(trackId: String): Boolean = withContext(Dispatchers.IO) {
        lyricsDao.delete(trackId) > 0
    }

    private fun findSidecar(track: Track): Uri? {
        val audioUri = runCatching { track.contentUri.toUri() }.getOrNull() ?: return null
        if (audioUri.scheme == "file") {
            val audio = File(requireNotNull(audioUri.path))
            return sequenceOf("lrc", "LRC")
                .map { extension -> File(audio.parentFile, "${audio.nameWithoutExtension}.$extension") }
                .firstOrNull(File::isFile)
                ?.let(Uri::fromFile)
        }
        val baseName = track.displayName.substringBeforeLast('.', track.displayName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && audioUri.scheme == "content") {
            @Suppress("DEPRECATION")
            context.contentResolver.query(
                audioUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    @Suppress("DEPRECATION")
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val audioPath = if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null
                    if (!audioPath.isNullOrBlank()) {
                        val audio = File(audioPath)
                        sequenceOf("lrc", "LRC")
                            .map { extension -> File(audio.parentFile, "${audio.nameWithoutExtension}.$extension") }
                            .firstOrNull(File::isFile)
                            ?.let(Uri::fromFile)
                            ?.let { return it }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !track.relativePath.isNullOrBlank()) {
            val volume = runCatching { MediaStore.getVolumeName(audioUri) }.getOrDefault(MediaStore.VOLUME_EXTERNAL)
            val files = MediaStore.Files.getContentUri(volume)
            context.contentResolver.query(
                files,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME),
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND (${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?)",
                arrayOf(track.relativePath, "$baseName.lrc", "$baseName.LRC"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(files, cursor.getLong(0))
                }
            }
        }
        return null
    }

    private fun readText(uri: Uri): String {
        val bytes = when (uri.scheme) {
            "file" -> File(requireNotNull(uri.path)).readBytes()
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取歌词文件")
        }
        require(bytes.size <= MAX_LYRICS_BYTES) { "歌词文件过大" }
        val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val replacementRatio = utf8.count { it == '\uFFFD' }.toFloat() / utf8.length.coerceAtLeast(1)
        return if (replacementRatio > 0.01f) bytes.toString(Charset.forName("GB18030")) else utf8
    }

    private companion object {
        const val MAX_LYRICS_BYTES = 2 * 1024 * 1024
    }
}
