package io.github.admin0330.real_liquid_glass_demo

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class MainActivity : AudioServiceActivity() {
    private val channelName = "real_liquid_glass_demo/updater"
    private val usbAudioChannelName = "liquid_music/usb_audio"
    private val updatePrefs by lazy {
        getSharedPreferences("liquid_music_updater", Context.MODE_PRIVATE)
    }
    private val mediaAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var activeDownloadId: Long? = null
    private var preferredUsbDeviceId: Int? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (completedId == activeDownloadId) {
                handleCompletedDownload(completedId)
            }
        }
    }

    private fun usbDevices(): List<AudioDeviceInfo> {
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
    }

    private fun usbAudioStatus(): Map<String, Any?> {
        val device = usbDevices().firstOrNull()
            ?: return usbResult(message = "未检测到 USB DAC，请连接后刷新")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return usbResult(
                connected = true,
                deviceName = device.productName?.toString(),
                message = "USB 独占需要 Android 14 或更高版本",
            )
        }
        val bitPerfect = supportedBitPerfect(device)
        if (bitPerfect == null) {
            return usbResult(
                connected = true,
                deviceName = device.productName?.toString(),
                message = "该 USB DAC 或系统音频驱动未提供 bit-perfect 模式",
            )
        }
        return mixerResult(device, bitPerfect, preferredUsbDeviceId == device.id,
            if (preferredUsbDeviceId == device.id) "USB 独占已启用" else "可启用 USB 独占")
    }

    private fun enableUsbExclusive(): Map<String, Any?> {
        val device = usbDevices().firstOrNull()
            ?: return usbResult(message = "未检测到 USB DAC，请连接后重试")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return usbResult(connected = true, deviceName = device.productName?.toString(),
                message = "USB 独占需要 Android 14 或更高版本")
        }
        val mixer = supportedBitPerfect(device)
            ?: return usbResult(connected = true, deviceName = device.productName?.toString(),
                message = "该 USB DAC 或系统音频驱动未提供 bit-perfect 模式")
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        val enabled = manager.setPreferredMixerAttributes(mediaAudioAttributes, device, mixer)
        preferredUsbDeviceId = if (enabled) device.id else null
        return mixerResult(device, mixer, enabled,
            if (enabled) "USB 独占已启用" else "系统拒绝启用 USB 独占")
    }

    private fun disableUsbExclusive(): Map<String, Any?> {
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            usbDevices().forEach { device ->
                runCatching { manager.clearPreferredMixerAttributes(mediaAudioAttributes, device) }
            }
        }
        preferredUsbDeviceId = null
        val device = usbDevices().firstOrNull()
        return usbResult(
            connected = device != null,
            supported = device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                supportedBitPerfect(device) != null,
            deviceName = device?.productName?.toString(),
            message = "USB 独占已关闭",
        )
    }

    private fun supportedBitPerfect(device: AudioDeviceInfo): AudioMixerAttributes? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        return manager.getSupportedMixerAttributes(device).firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        }
    }

    private fun mixerResult(
        device: AudioDeviceInfo,
        mixer: AudioMixerAttributes,
        enabled: Boolean,
        message: String,
    ): Map<String, Any?> = usbResult(
        connected = true,
        supported = true,
        enabled = enabled,
        deviceName = device.productName?.toString(),
        sampleRate = mixer.format.sampleRate,
        encoding = encodingName(mixer.format.encoding),
        message = message,
    )

    private fun usbResult(
        connected: Boolean = false,
        supported: Boolean = false,
        enabled: Boolean = false,
        deviceName: String? = null,
        sampleRate: Int? = null,
        encoding: String? = null,
        message: String,
    ): Map<String, Any?> = mapOf(
        "connected" to connected,
        "supported" to supported,
        "enabled" to enabled,
        "deviceName" to deviceName,
        "sampleRate" to sampleRate,
        "encoding" to encoding,
        "message" to message,
    )

    private fun encodingName(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
        else -> "PCM"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        activeDownloadId = updatePrefs.getLong("download_id", -1L).takeIf { it >= 0 }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getAppVersion" -> {
                        val info = packageManager.getPackageInfo(packageName, 0)
                        result.success(info.versionName ?: "0.0.0")
                    }

                    "downloadAndInstall" -> {
                        val url = call.argument<String>("url")
                        val version = call.argument<String>("version")
                        val sha256 = call.argument<String>("sha256") ?: ""
                        if (url.isNullOrBlank() || version.isNullOrBlank()) {
                            result.error("INVALID_UPDATE", "Missing APK URL or version", null)
                        } else {
                            result.success(startOrReuseDownload(url, version, sha256))
                        }
                    }

                    "getUpdateDownloadStatus" -> result.success(updateDownloadStatus())

                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, usbAudioChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getUsbAudioStatus" -> result.success(usbAudioStatus())
                    "enableUsbExclusive" -> result.success(enableUsbExclusive())
                    "disableUsbExclusive" -> result.success(disableUsbExclusive())
                    else -> result.notImplemented()
                }
            }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(downloadReceiver) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val pendingPath = updatePrefs.getString("pending_install_path", null)
        val version = updatePrefs.getString("version", null)
        val sha256 = updatePrefs.getString("sha256", "") ?: ""
        if (
            !pendingPath.isNullOrBlank() &&
            !version.isNullOrBlank() &&
            !requiresInstallPermission()
        ) {
            val file = File(pendingPath)
            if (isUsableUpdate(file, version, sha256)) {
                updatePrefs.edit().remove("pending_install_path").apply()
                launchInstaller(file)
            }
        }
    }

    private fun requiresInstallPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
    }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun startOrReuseDownload(url: String, version: String, sha256: String): String {
        val normalizedVersion = version.removePrefix("v").removePrefix("V")
        val target = updateFile(normalizedVersion)
        if (isUsableUpdate(target, normalizedVersion, sha256)) {
            return requestInstall(target, normalizedVersion, sha256)
        }

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val savedId = updatePrefs.getLong("download_id", -1L)
        val savedVersion = updatePrefs.getString("version", null)
        if (savedId >= 0 && savedVersion == normalizedVersion) {
            manager.query(DownloadManager.Query().setFilterById(savedId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PAUSED -> {
                            activeDownloadId = savedId
                            return "downloadInProgress"
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (isUsableUpdate(target, normalizedVersion, sha256)) {
                                return requestInstall(target, normalizedVersion, sha256)
                            }
                            manager.remove(savedId)
                        }

                        else -> manager.remove(savedId)
                    }
                }
            }
        }

        if (target.exists()) target.delete()
        target.parentFile?.mkdirs()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Liquid Music v$normalizedVersion")
            .setDescription("正在应用内下载更新，完成后打开系统安装器")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                this,
                Environment.DIRECTORY_DOWNLOADS,
                target.name,
            )

        val id = manager.enqueue(request)
        activeDownloadId = id
        updatePrefs.edit()
            .putLong("download_id", id)
            .putString("version", normalizedVersion)
            .putString("sha256", sha256.lowercase())
            .putString("path", target.absolutePath)
            .putString("url", url)
            .remove("pending_install_path")
            .apply()
        return "downloadStarted"
    }

    private fun handleCompletedDownload(downloadId: Long) {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
            )
            if (status != DownloadManager.STATUS_SUCCESSFUL) return
        }

        val version = updatePrefs.getString("version", null) ?: return
        val sha256 = updatePrefs.getString("sha256", "") ?: ""
        val target = updateFile(version)
        if (!isUsableUpdate(target, version, sha256)) {
            target.delete()
            return
        }
        requestInstall(target, version, sha256)
    }

    private fun requestInstall(file: File, version: String, sha256: String): String {
        if (!isUsableUpdate(file, version, sha256)) return "failed"
        if (requiresInstallPermission()) {
            updatePrefs.edit().putString("pending_install_path", file.absolutePath).apply()
            openInstallPermissionSettings()
            return "permissionRequired"
        }
        updatePrefs.edit().remove("pending_install_path").apply()
        launchInstaller(file)
        return "installStarted"
    }

    private fun launchInstaller(file: File) {
        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
        startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            },
        )
    }

    private fun updateFile(version: String): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(filesDir, "updates")
        return File(directory, "liquid-music-v$version.apk")
    }

    private fun isUsableUpdate(file: File, version: String, sha256: String): Boolean {
        if (!file.isFile || file.length() <= 0) return false
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        if (info.packageName != packageName) return false
        val archiveVersion = info.versionName?.removePrefix("v")?.removePrefix("V") ?: return false
        if (archiveVersion != version.removePrefix("v").removePrefix("V")) return false
        return sha256.isBlank() || fileSha256(file).equals(sha256, ignoreCase = true)
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 128)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateDownloadStatus(): Map<String, Any> {
        val version = updatePrefs.getString("version", "") ?: ""
        val sha256 = updatePrefs.getString("sha256", "") ?: ""
        if (version.isBlank()) return mapOf("state" to "idle")
        val target = updateFile(version)
        if (isUsableUpdate(target, version, sha256)) {
            return mapOf(
                "state" to "ready",
                "version" to version,
                "progress" to 1.0,
                "downloadedBytes" to target.length(),
                "totalBytes" to target.length(),
            )
        }

        val id = updatePrefs.getLong("download_id", -1L)
        if (id < 0) return mapOf("state" to "idle", "version" to version)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return mapOf("state" to "failed", "version" to version)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
            val state = when (status) {
                DownloadManager.STATUS_PENDING -> "pending"
                DownloadManager.STATUS_RUNNING -> "running"
                DownloadManager.STATUS_PAUSED -> "paused"
                DownloadManager.STATUS_SUCCESSFUL -> "verifying"
                else -> "failed"
            }
            return mapOf(
                "state" to state,
                "version" to version,
                "progress" to if (total > 0) downloaded.toDouble() / total else 0.0,
                "downloadedBytes" to downloaded,
                "totalBytes" to total,
            )
        }
    }
}
