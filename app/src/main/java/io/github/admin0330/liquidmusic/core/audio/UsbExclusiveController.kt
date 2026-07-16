package io.github.admin0330.liquidmusic.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbAudioState(
    val connected: Boolean = false,
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val requested: Boolean = false,
    val deviceName: String? = null,
    val sampleRate: Int? = null,
    val encoding: String? = null,
    val message: String = "未检测到 USB DAC",
)

@Singleton
class UsbExclusiveController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var preferredDeviceId: Int? = null
    private var requested = false
    private val _state = MutableStateFlow(readState())
    val state = _state.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (requested) applyRequestedState() else refresh()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == preferredDeviceId }) preferredDeviceId = null
            refresh()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(callback, null)
    }

    @Synchronized
    fun refresh() {
        _state.value = readState()
    }

    @Synchronized
    fun setExclusive(enabled: Boolean): UsbAudioState {
        requested = enabled
        return applyRequestedState()
    }

    @Synchronized
    private fun applyRequestedState(): UsbAudioState {
        val device = usbDevices().firstOrNull()
        if (!requested) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                usbDevices().forEach { runCatching { audioManager.clearPreferredMixerAttributes(attributes, it) } }
            }
            preferredDeviceId = null
            return readState(overrideMessage = "USB 独占已关闭").also { _state.value = it }
        }
        if (device == null) return readState(overrideMessage = "正在等待 USB DAC 连接").also { _state.value = it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return readState(overrideMessage = "USB bit-perfect 独占需要 Android 14 或更高版本").also { _state.value = it }
        }
        val mixer = supportedBitPerfect(device)
            ?: return readState(overrideMessage = "该 USB DAC 或系统驱动未提供 bit-perfect 模式").also { _state.value = it }
        val success = audioManager.setPreferredMixerAttributes(attributes, device, mixer)
        preferredDeviceId = device.id.takeIf { success }
        return mixer.toState(
            device = device,
            enabled = success,
            message = if (success) "USB bit-perfect 独占已启用" else "系统拒绝启用 USB 独占",
        ).also { _state.value = it }
    }

    private fun readState(overrideMessage: String? = null): UsbAudioState {
        val device = usbDevices().firstOrNull()
            ?: return UsbAudioState(
                requested = requested,
                message = overrideMessage ?: if (requested) "正在等待 USB DAC 连接" else "未检测到 USB DAC",
            )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return UsbAudioState(
                connected = true,
                requested = requested,
                deviceName = device.productName?.toString(),
                message = overrideMessage ?: "USB bit-perfect 独占需要 Android 14 或更高版本",
            )
        }
        val mixer = supportedBitPerfect(device)
            ?: return UsbAudioState(
                connected = true,
                requested = requested,
                deviceName = device.productName?.toString(),
                message = overrideMessage ?: "该 USB DAC 或系统驱动未提供 bit-perfect 模式",
            )
        val enabled = preferredDeviceId == device.id
        return mixer.toState(
            device = device,
            enabled = enabled,
            message = overrideMessage ?: if (enabled) "USB bit-perfect 独占已启用" else "可启用 USB bit-perfect 独占",
        )
    }

    private fun usbDevices(): List<AudioDeviceInfo> = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { device ->
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }

    private fun supportedBitPerfect(device: AudioDeviceInfo): AudioMixerAttributes? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return audioManager.getSupportedMixerAttributes(device).firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun AudioMixerAttributes.toState(
        device: AudioDeviceInfo,
        enabled: Boolean,
        message: String,
    ) = UsbAudioState(
        connected = true,
        supported = true,
        enabled = enabled,
        requested = requested,
        deviceName = device.productName?.toString(),
        sampleRate = format.sampleRate.takeIf { it > 0 },
        encoding = when (format.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
            AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
            AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
            else -> "PCM"
        },
        message = message,
    )
}
