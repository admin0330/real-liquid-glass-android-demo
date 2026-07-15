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
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : AudioServiceActivity() {
    private val channelName = "real_liquid_glass_demo/updater"
    private val usbAudioChannelName = "liquid_music/usb_audio"
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
                installDownloadedApk(completedId)
                activeDownloadId = null
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
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getAppVersion" -> {
                        val info = packageManager.getPackageInfo(packageName, 0)
                        result.success(info.versionName ?: "0.0.0")
                    }

                    "downloadAndInstall" -> {
                        val url = call.argument<String>("url")
                        if (url.isNullOrBlank()) {
                            result.error("INVALID_URL", "Missing APK URL", null)
                        } else if (requiresInstallPermission()) {
                            openInstallPermissionSettings()
                            result.success("permissionRequired")
                        } else {
                            startDownload(url)
                            result.success("started")
                        }
                    }

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

    private fun startDownload(url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Real Liquid Glass 更新")
            .setDescription("正在从 GitHub Releases 下载新版 APK")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                this,
                Environment.DIRECTORY_DOWNLOADS,
                "real-liquid-glass-update-${System.currentTimeMillis()}.apk",
            )

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        activeDownloadId = manager.enqueue(request)
    }

    private fun installDownloadedApk(downloadId: Long) {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
            )
            if (status != DownloadManager.STATUS_SUCCESSFUL) return
        }

        val apkUri = manager.getUriForDownloadedFile(downloadId) ?: return
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }
}
