package io.github.admin0330.liquidmusic.update.manifest

import io.github.admin0330.liquidmusic.update.model.UpdateManifest
import io.github.admin0330.liquidmusic.update.security.SecureUpdateUri
import java.net.URI

class InvalidUpdateManifestException : IllegalArgumentException("Invalid update manifest")

object UpdateManifestParser {
    private val newKeys = setOf(
        "versionCode",
        "versionName",
        "apkUrl",
        "sha256",
        "size",
        "changelog",
    )
    private val legacyKeys = setOf(
        "version",
        "apk_url",
        "sha256",
        "size",
        "notes",
    )
    private val combinedKeys = newKeys + legacyKeys

    fun parse(json: String, manifestUri: URI): UpdateManifest {
        val secureManifestUri = runCatching {
            SecureUpdateUri.parse(manifestUri.toASCIIString())
        }.getOrElse { throw InvalidUpdateManifestException() }
        val values = runCatching { FlatJsonObjectReader(json).read() }
            .getOrElse { throw InvalidUpdateManifestException() }
        return when (values.keys) {
            newKeys -> parseNew(values)
            legacyKeys -> parseLegacy(values, secureManifestUri)
            combinedKeys -> parseCombined(values, secureManifestUri)
            else -> throw InvalidUpdateManifestException()
        }
    }

    private fun parseNew(values: Map<String, JsonPrimitive>): UpdateManifest {
        val versionCode = values.number("versionCode")?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: throw InvalidUpdateManifestException()
        val size = values.number("size")?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: throw InvalidUpdateManifestException()
        val versionName = values.string("versionName")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 128 }
            ?: throw InvalidUpdateManifestException()
        val apkUrl = values.string("apkUrl")
            ?.let { runCatching { SecureUpdateUri.parse(it).toASCIIString() }.getOrNull() }
            ?: throw InvalidUpdateManifestException()
        val sha256 = values.string("sha256")
            ?.lowercase()
            ?.takeIf { SHA256_REGEX.matches(it) }
            ?: throw InvalidUpdateManifestException()
        val changelog = values.string("changelog")
            ?.takeIf { it.length <= MAX_CHANGELOG_LENGTH }
            ?: throw InvalidUpdateManifestException()

        return validate(UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            size = size,
            changelog = changelog,
        ))
    }

    private fun parseLegacy(
        values: Map<String, JsonPrimitive>,
        manifestUri: URI,
    ): UpdateManifest {
        val versionName = values.string("version")
            ?.let(::normalizeLegacyVersion)
            ?: throw InvalidUpdateManifestException()
        val versionCode = LEGACY_VERSION_CODES[versionName]
            ?: throw InvalidUpdateManifestException()
        val apkUrl = values.string("apk_url")
            ?.let { resolveLegacyApkUrl(it, manifestUri) }
            ?: throw InvalidUpdateManifestException()
        val sha256 = parseSha256(values)
        val size = parseSize(values)
        val changelog = values.string("notes")
            ?.takeIf { it.length <= MAX_CHANGELOG_LENGTH }
            ?: throw InvalidUpdateManifestException()
        return validate(
            UpdateManifest(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                sha256 = sha256,
                size = size,
                changelog = changelog,
            ),
        )
    }

    private fun parseCombined(
        values: Map<String, JsonPrimitive>,
        manifestUri: URI,
    ): UpdateManifest {
        val current = parseNew(values)
        val legacyVersion = values.string("version")
            ?.let(::normalizeLegacyVersion)
            ?: throw InvalidUpdateManifestException()
        val legacyApkUrl = values.string("apk_url")
            ?.let { resolveLegacyApkUrl(it, manifestUri) }
            ?: throw InvalidUpdateManifestException()
        val legacyNotes = values.string("notes")
            ?.takeIf { it.length <= MAX_CHANGELOG_LENGTH }
            ?: throw InvalidUpdateManifestException()
        if (
            current.versionName != legacyVersion ||
            URI(current.apkUrl) != URI(legacyApkUrl) ||
            current.changelog != legacyNotes
        ) {
            throw InvalidUpdateManifestException()
        }
        return current
    }

    private fun parseSize(values: Map<String, JsonPrimitive>): Long =
        values.number("size")?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: throw InvalidUpdateManifestException()

    private fun parseSha256(values: Map<String, JsonPrimitive>): String =
        values.string("sha256")
            ?.lowercase()
            ?.takeIf { SHA256_REGEX.matches(it) }
            ?: throw InvalidUpdateManifestException()

    private fun normalizeLegacyVersion(value: String): String = value.trim()
        .removePrefix("v")
        .removePrefix("V")
        .takeIf { it.isNotEmpty() && it.length <= 128 }
        ?: throw InvalidUpdateManifestException()

    private fun resolveLegacyApkUrl(value: String, manifestUri: URI): String {
        if (value.isBlank() || value != value.trim()) throw InvalidUpdateManifestException()
        val raw = runCatching { URI(value) }.getOrElse { throw InvalidUpdateManifestException() }
        if (raw.rawUserInfo != null || raw.rawFragment != null) throw InvalidUpdateManifestException()
        val resolved = if (raw.isAbsolute) {
            raw
        } else {
            if (raw.rawAuthority != null) throw InvalidUpdateManifestException()
            manifestUri.resolve(raw)
        }
        return runCatching { SecureUpdateUri.parse(resolved.toASCIIString()).toASCIIString() }
            .getOrElse { throw InvalidUpdateManifestException() }
    }

    fun validate(manifest: UpdateManifest): UpdateManifest {
        if (manifest.versionCode <= 0L || manifest.size <= 0L) {
            throw InvalidUpdateManifestException()
        }
        val versionName = manifest.versionName.trim()
            .takeIf { it.isNotEmpty() && it.length <= 128 }
            ?: throw InvalidUpdateManifestException()
        val apkUrl = runCatching { SecureUpdateUri.parse(manifest.apkUrl).toASCIIString() }
            .getOrNull()
            ?: throw InvalidUpdateManifestException()
        val sha256 = manifest.sha256.lowercase()
            .takeIf { SHA256_REGEX.matches(it) }
            ?: throw InvalidUpdateManifestException()
        if (manifest.changelog.length > MAX_CHANGELOG_LENGTH) {
            throw InvalidUpdateManifestException()
        }
        return manifest.copy(
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
        )
    }

    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    private val LEGACY_VERSION_CODES = mapOf("2.4.9" to 18L)
    private const val MAX_CHANGELOG_LENGTH = 64 * 1024
}

private sealed interface JsonPrimitive {
    data class Text(val value: String) : JsonPrimitive
    data class Number(val value: String) : JsonPrimitive
}

private fun Map<String, JsonPrimitive>.string(key: String): String? =
    (get(key) as? JsonPrimitive.Text)?.value

private fun Map<String, JsonPrimitive>.number(key: String): String? =
    (get(key) as? JsonPrimitive.Number)?.value

/** A deliberately small, strict JSON reader for the flat update-manifest schema. */
private class FlatJsonObjectReader(private val source: String) {
    private var index = 0

    fun read(): Map<String, JsonPrimitive> {
        skipWhitespace()
        expect('{')
        skipWhitespace()
        val result = linkedMapOf<String, JsonPrimitive>()
        if (takeIfPresent('}')) return finish(result)
        while (true) {
            val key = readString()
            if (result.containsKey(key)) fail()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            result[key] = when (peek()) {
                '"' -> JsonPrimitive.Text(readString())
                '-', in '0'..'9' -> JsonPrimitive.Number(readNumber())
                else -> fail()
            }
            skipWhitespace()
            when {
                takeIfPresent(',') -> skipWhitespace()
                takeIfPresent('}') -> return finish(result)
                else -> fail()
            }
        }
    }

    private fun finish(result: Map<String, JsonPrimitive>): Map<String, JsonPrimitive> {
        skipWhitespace()
        if (index != source.length) fail()
        return result
    }

    private fun readString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when {
                character == '"' -> return result.toString()
                character == '\\' -> result.append(readEscape())
                character.code < 0x20 -> fail()
                character.isHighSurrogate() -> {
                    if (index >= source.length || !source[index].isLowSurrogate()) fail()
                    result.append(character)
                    result.append(source[index++])
                }
                character.isLowSurrogate() -> fail()
                else -> result.append(character)
            }
        }
        fail()
    }

    private fun readEscape(): String = when (val escaped = take()) {
        '"', '\\', '/' -> escaped.toString()
        'b' -> "\b"
        'f' -> "\u000c"
        'n' -> "\n"
        'r' -> "\r"
        't' -> "\t"
        'u' -> readEscapedUnicode()
        else -> fail()
    }

    private fun readEscapedUnicode(): String {
        val first = readHexCodeUnit()
        if (first.isLowSurrogate()) fail()
        if (!first.isHighSurrogate()) return first.toString()
        if (take() != '\\' || take() != 'u') fail()
        val second = readHexCodeUnit()
        if (!second.isLowSurrogate()) fail()
        return "$first$second"
    }

    private fun readHexCodeUnit(): Char {
        if (index + 4 > source.length) fail()
        val value = source.substring(index, index + 4).toIntOrNull(16) ?: fail()
        index += 4
        return value.toChar()
    }

    private fun readNumber(): String {
        val start = index
        takeIfPresent('-')
        when {
            takeIfPresent('0') -> Unit
            peek() in '1'..'9' -> while (peek() in '0'..'9') index++
            else -> fail()
        }
        // Manifest integer fields intentionally reject fractions and exponents.
        if (peek() == '.' || peek() == 'e' || peek() == 'E') fail()
        return source.substring(start, index)
    }

    private fun skipWhitespace() {
        while (peek() == ' ' || peek() == '\t' || peek() == '\n' || peek() == '\r') index++
    }

    private fun expect(expected: Char) {
        if (take() != expected) fail()
    }

    private fun takeIfPresent(expected: Char): Boolean {
        if (peek() != expected) return false
        index++
        return true
    }

    private fun take(): Char = if (index < source.length) source[index++] else fail()

    private fun peek(): Char = source.getOrNull(index) ?: '\u0000'

    private fun fail(): Nothing = throw InvalidUpdateManifestException()
}
