package io.github.admin0330.liquidmusic.update.manifest

import io.github.admin0330.liquidmusic.update.model.UpdateManifest
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestParserTest {
    @Test
    fun parsesCompleteSecureManifest() {
        val manifest = UpdateManifestParser.parse(
            """
            {
              "versionCode": 20,
              "versionName": "3.0.0",
              "apkUrl": "https://ym3861.cn/liquid-music/app.apk",
              "sha256": "${"ab".repeat(32)}",
              "size": 123456,
              "changelog": "修复播放\n更新界面"
            }
            """.trimIndent(),
            MANIFEST_URI,
        )

        assertEquals(20L, manifest.versionCode)
        assertEquals("3.0.0", manifest.versionName)
        assertEquals("修复播放\n更新界面", manifest.changelog)
        assertEquals("ab".repeat(32), manifest.sha256)
    }

    @Test
    fun parsesCurrentAliyunLegacyManifest() {
        val manifest = UpdateManifestParser.parse(
            """
            {
              "version": "2.4.9",
              "apk_url": "liquid-music-v2.4.9.apk",
              "sha256": "d4efcce20ee637a82323f0f78b178240629fda7602a838361e20f533c4c24514",
              "size": 55901896,
              "notes": "Liquid Music v2.4.9"
            }
            """.trimIndent(),
            MANIFEST_URI,
        )

        assertEquals(18L, manifest.versionCode)
        assertEquals("2.4.9", manifest.versionName)
        assertEquals(
            "https://ym3861.cn/liquid-music-updates/liquid-music-v2.4.9.apk",
            manifest.apkUrl,
        )
        assertEquals(55_901_896L, manifest.size)
        assertEquals("Liquid Music v2.4.9", manifest.changelog)
    }

    @Test
    fun combinedSchemaUsesNewFieldsWhenLegacyFieldsAgree() {
        val manifest = UpdateManifestParser.parse(combinedManifestJson(), MANIFEST_URI)

        assertEquals(21L, manifest.versionCode)
        assertEquals("3.0.0", manifest.versionName)
        assertEquals(
            "https://ym3861.cn/liquid-music-updates/liquid-music-v3.0.0.apk",
            manifest.apkUrl,
        )
        assertEquals("Native rewrite", manifest.changelog)
    }

    @Test
    fun combinedSchemaRejectsDisagreement() {
        listOf(
            combinedManifestJson().replace("\"version\":\"3.0.0\"", "\"version\":\"3.0.1\""),
            combinedManifestJson().replace(
                "\"apk_url\":\"liquid-music-v3.0.0.apk\"",
                "\"apk_url\":\"different.apk\"",
            ),
            combinedManifestJson().replace("\"notes\":\"Native rewrite\"", "\"notes\":\"other\""),
        ).forEach { json ->
            assertThrows(InvalidUpdateManifestException::class.java) {
                UpdateManifestParser.parse(json, MANIFEST_URI)
            }
        }
    }

    @Test
    fun legacySchemaRejectsUnknownVersionAndInsecureUrl() {
        val current = legacyManifestJson()
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.parse(current.replace("2.4.9", "2.5.0"), MANIFEST_URI)
        }
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.parse(
                current.replace("liquid-music-v2.4.9.apk", "http://example.com/app.apk"),
                MANIFEST_URI,
            )
        }
    }

    @Test
    fun rejectsHttpApkUrl() {
        assertInvalid(validManifest().copy(apkUrl = "http://example.com/app.apk"))
    }

    @Test
    fun rejectsCredentialsInApkUrl() {
        assertInvalid(validManifest().copy(apkUrl = "https://user:secret@example.com/app.apk"))
    }

    @Test
    fun rejectsMissingOrAdditionalFields() {
        val missing = manifestJson().replace(",\"changelog\":\"changes\"", "")
        val additional = manifestJson().dropLast(1) + ",\"token\":\"secret\"}"
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.parse(missing, MANIFEST_URI)
        }
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.parse(additional, MANIFEST_URI)
        }
    }

    @Test
    fun rejectsDuplicateFieldsAndMalformedUnicode() {
        val duplicate = manifestJson().replace(
            "\"versionCode\":20",
            "\"versionCode\":20,\"versionCode\":21",
        )
        val malformedUnicode = manifestJson().replace("changes", "\\uD800")
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.parse(duplicate, MANIFEST_URI)
        }
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.parse(malformedUnicode, MANIFEST_URI)
        }
    }

    private fun assertInvalid(manifest: UpdateManifest) {
        assertThrows(InvalidUpdateManifestException::class.java) {
            UpdateManifestParser.validate(manifest)
        }
    }

    private fun validManifest() = UpdateManifest(
        versionCode = 20,
        versionName = "3.0.0",
        apkUrl = "https://example.com/app.apk",
        sha256 = "ab".repeat(32),
        size = 1024,
        changelog = "changes",
    )

    private fun manifestJson(): String =
        "{\"versionCode\":20,\"versionName\":\"3.0.0\",\"apkUrl\":\"https://example.com/app.apk\"," +
            "\"sha256\":\"${"ab".repeat(32)}\",\"size\":1024,\"changelog\":\"changes\"}"

    private fun combinedManifestJson(): String =
        "{\"versionCode\":21,\"versionName\":\"3.0.0\"," +
            "\"apkUrl\":\"https://ym3861.cn/liquid-music-updates/liquid-music-v3.0.0.apk\"," +
            "\"sha256\":\"${"cd".repeat(32)}\",\"size\":2048,\"changelog\":\"Native rewrite\"," +
            "\"version\":\"3.0.0\",\"apk_url\":\"liquid-music-v3.0.0.apk\"," +
            "\"notes\":\"Native rewrite\"}"

    private fun legacyManifestJson(): String =
        "{\"version\":\"2.4.9\",\"apk_url\":\"liquid-music-v2.4.9.apk\"," +
            "\"sha256\":\"${"ab".repeat(32)}\",\"size\":1024,\"notes\":\"changes\"}"

    private companion object {
        val MANIFEST_URI: URI = URI("https://ym3861.cn/liquid-music-updates/latest.json")
    }
}
