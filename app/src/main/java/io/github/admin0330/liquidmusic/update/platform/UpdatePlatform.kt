package io.github.admin0330.liquidmusic.update.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

fun interface InstalledAppInfoProvider {
    fun versionCode(): Long
}

enum class ApkIdentityFailure {
    INVALID_APK,
    PACKAGE,
    SIGNATURE,
    VERSION,
}

fun interface ApkIdentityVerifier {
    fun verify(apk: File, expectedVersionCode: Long, expectedVersionName: String): ApkIdentityFailure?
}

sealed interface InstallLaunch {
    data class Installer(val intent: Intent) : InstallLaunch
    data class PermissionSettings(val intent: Intent) : InstallLaunch
}

fun interface InstallIntentFactory {
    fun create(apk: File): InstallLaunch
}

@Singleton
class AndroidInstalledAppInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstalledAppInfoProvider {
    override fun versionCode(): Long = currentPackageInfo(context).compatLongVersionCode
}

@Singleton
class AndroidApkIdentityVerifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApkIdentityVerifier {
    override fun verify(
        apk: File,
        expectedVersionCode: Long,
        expectedVersionName: String,
    ): ApkIdentityFailure? {
        val packageManager = context.packageManager
        val archive = archivePackageInfo(packageManager, apk) ?: return ApkIdentityFailure.INVALID_APK
        if (archive.packageName != context.packageName) return ApkIdentityFailure.PACKAGE
        if (archive.compatLongVersionCode != expectedVersionCode || archive.versionName != expectedVersionName) {
            return ApkIdentityFailure.VERSION
        }

        val installed = runCatching { currentPackageInfo(context) }.getOrNull()
            ?: return ApkIdentityFailure.INVALID_APK
        val archiveSigners = signerDigests(archive)
        val installedSigners = signerDigests(installed)
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) return ApkIdentityFailure.INVALID_APK
        return if (archiveSigners == installedSigners) null else ApkIdentityFailure.SIGNATURE
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(packageManager: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }

    @Suppress("DEPRECATION")
    private fun signerDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }
}

@Singleton
class AndroidInstallIntentFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstallIntentFactory {
    override fun create(apk: File): InstallLaunch {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return InstallLaunch.PermissionSettings(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return InstallLaunch.Installer(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

@Suppress("DEPRECATION")
private fun currentPackageInfo(context: Context): PackageInfo =
    if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        context.packageManager.getPackageInfo(context.packageName, flags)
    }

@Suppress("DEPRECATION")
private val PackageInfo.compatLongVersionCode: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
