import 'package:flutter/services.dart';

class UsbAudioStatus {
  const UsbAudioStatus({
    this.connected = false,
    this.supported = false,
    this.enabled = false,
    this.deviceName,
    this.sampleRate,
    this.encoding,
    this.message = '未检测到 USB 音频设备',
  });

  factory UsbAudioStatus.fromMap(Map<Object?, Object?> value) => UsbAudioStatus(
    connected: value['connected'] == true,
    supported: value['supported'] == true,
    enabled: value['enabled'] == true,
    deviceName: value['deviceName'] as String?,
    sampleRate: value['sampleRate'] as int?,
    encoding: value['encoding'] as String?,
    message: value['message'] as String? ?? 'USB 音频状态未知',
  );

  final bool connected;
  final bool supported;
  final bool enabled;
  final String? deviceName;
  final int? sampleRate;
  final String? encoding;
  final String message;

  String get detail {
    if (!connected || !supported) return message;
    final values = <String>[];
    if (sampleRate != null) values.add('${sampleRate! ~/ 1000} kHz');
    if (encoding != null) values.add(encoding!);
    final format = values.join(' · ');
    return '${deviceName ?? 'USB DAC'}${format.isEmpty ? '' : ' · $format'}';
  }
}

class UsbAudioService {
  static const _channel = MethodChannel('liquid_music/usb_audio');

  Future<UsbAudioStatus> status() async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      'getUsbAudioStatus',
    );
    return UsbAudioStatus.fromMap(value ?? const {});
  }

  Future<UsbAudioStatus> setExclusive(bool enabled) async {
    final value = await _channel.invokeMapMethod<Object?, Object?>(
      enabled ? 'enableUsbExclusive' : 'disableUsbExclusive',
    );
    return UsbAudioStatus.fromMap(value ?? const {});
  }
}
