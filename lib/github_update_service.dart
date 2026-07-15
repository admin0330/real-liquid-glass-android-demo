import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class GitHubUpdateService {
  static const _channel = MethodChannel('real_liquid_glass_demo/updater');
  static const _mirrorKey = 'update_manifest_url_v1';
  static const _builtInMirror = String.fromEnvironment('UPDATE_MANIFEST_URL');
  static const _latestRelease =
      'https://api.github.com/repos/admin0330/real-liquid-glass-android-demo/releases/latest';

  Future<String> currentVersion() async =>
      await _channel.invokeMethod<String>('getAppVersion') ?? '0.0.0';

  Future<String> mirrorUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return (prefs.getString(_mirrorKey) ?? _builtInMirror).trim();
  }

  Future<void> setMirrorUrl(String value) async {
    final prefs = await SharedPreferences.getInstance();
    final normalized = normalizeManifestUrl(value);
    if (normalized.isEmpty) {
      await prefs.remove(_mirrorKey);
    } else {
      await prefs.setString(_mirrorKey, normalized);
    }
  }

  Future<UpdateCheckResult> check() async {
    final current = await currentVersion();
    final mirror = await mirrorUrl();
    Object? mirrorError;
    if (mirror.isNotEmpty) {
      try {
        final manifestUri = Uri.parse(mirror);
        final json = await _getJson(manifestUri, source: '阿里云更新服务器');
        final release = GitHubRelease.fromMirrorJson(json, manifestUri);
        return UpdateCheckResult(
          currentVersion: current,
          release: release,
          hasUpdate: compareVersions(release.version, current) > 0,
        );
      } catch (error) {
        mirrorError = error;
      }
    }

    try {
      final json = await _getJson(Uri.parse(_latestRelease), source: 'GitHub');
      final release = GitHubRelease.fromJson(json);
      return UpdateCheckResult(
        currentVersion: current,
        release: release,
        hasUpdate: compareVersions(release.version, current) > 0,
        fallbackReason: mirrorError?.toString(),
      );
    } catch (githubError) {
      if (mirrorError != null) {
        throw UpdateException(
          '阿里云更新服务器不可用，GitHub 回退也失败。\n'
          '镜像：$mirrorError\nGitHub：$githubError',
        );
      }
      rethrow;
    }
  }

  Future<Map<String, dynamic>> _getJson(
    Uri uri, {
    required String source,
  }) async {
    if (uri.scheme != 'https' && uri.scheme != 'http') {
      throw UpdateException('$source地址必须使用 HTTP 或 HTTPS');
    }
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 12);
    try {
      final request = await client.getUrl(uri);
      request.headers
        ..set(HttpHeaders.acceptHeader, 'application/json')
        ..set(HttpHeaders.userAgentHeader, 'liquid-music-android');
      final response = await request.close().timeout(
        const Duration(seconds: 20),
      );
      final body = await utf8.decoder.bind(response).join();
      if (response.statusCode != HttpStatus.ok) {
        throw UpdateException('$source返回 HTTP ${response.statusCode}');
      }
      return Map<String, dynamic>.from(jsonDecode(body) as Map);
    } on SocketException {
      throw UpdateException('无法连接$source');
    } on FormatException {
      throw UpdateException('$source返回的数据格式无效');
    } finally {
      client.close(force: true);
    }
  }

  Future<InstallStartState> downloadAndInstall(GitHubRelease release) async {
    final value = await _channel.invokeMethod<String>('downloadAndInstall', {
      'url': release.apkUrl,
      'version': release.version,
      'sha256': release.sha256,
      'size': release.size,
    });
    return switch (value) {
      'downloadStarted' => InstallStartState.downloadStarted,
      'downloadInProgress' => InstallStartState.downloadInProgress,
      'installStarted' => InstallStartState.installStarted,
      'permissionRequired' => InstallStartState.permissionRequired,
      _ => InstallStartState.failed,
    };
  }

  Future<UpdateDownloadStatus> downloadStatus() async {
    final value = await _channel.invokeMapMethod<String, dynamic>(
      'getUpdateDownloadStatus',
    );
    return UpdateDownloadStatus.fromMap(value ?? const {});
  }
}

String normalizeManifestUrl(String value) {
  final trimmed = value.trim();
  if (trimmed.isEmpty) return '';
  final uri = Uri.tryParse(trimmed);
  if (uri == null) return trimmed;
  final path = uri.path.replaceFirst(RegExp(r'/+$'), '');
  if (path.toLowerCase().endsWith('.json')) return uri.toString();
  return uri.replace(path: '$path/latest.json').toString();
}

class GitHubRelease {
  const GitHubRelease({
    required this.version,
    required this.notes,
    required this.apkUrl,
    required this.source,
    this.sha256 = '',
    this.size = 0,
  });

  final String version;
  final String notes;
  final String apkUrl;
  final String source;
  final String sha256;
  final int size;

  factory GitHubRelease.fromJson(Map<String, dynamic> json) {
    final tag = (json['tag_name'] as String? ?? '').trim();
    final assets = (json['assets'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>();
    Map<String, dynamic>? apk;
    for (final asset in assets) {
      if ((asset['name'] as String? ?? '').toLowerCase().endsWith('.apk')) {
        apk = asset;
        break;
      }
    }
    final url = apk?['browser_download_url'] as String? ?? '';
    if (tag.isEmpty || url.isEmpty) {
      throw const FormatException('Release is missing a tag or APK asset');
    }
    return GitHubRelease(
      version: _cleanVersion(tag),
      notes: json['body'] as String? ?? '',
      apkUrl: url,
      source: 'GitHub',
      size: (apk?['size'] as num?)?.toInt() ?? 0,
    );
  }

  factory GitHubRelease.fromMirrorJson(
    Map<String, dynamic> json,
    Uri manifestUri,
  ) {
    final version = _cleanVersion('${json['version'] ?? ''}'.trim());
    final rawUrl = '${json['apk_url'] ?? json['apkUrl'] ?? ''}'.trim();
    final sha256 = '${json['sha256'] ?? ''}'.trim().toLowerCase();
    if (version.isEmpty || rawUrl.isEmpty) {
      throw const FormatException('Manifest is missing version or apk_url');
    }
    if (sha256.isNotEmpty && !RegExp(r'^[0-9a-f]{64}$').hasMatch(sha256)) {
      throw const FormatException('Manifest sha256 is invalid');
    }
    final resolved = manifestUri.resolve(rawUrl);
    if (resolved.scheme != 'https' && resolved.scheme != 'http') {
      throw const FormatException('Manifest APK URL is invalid');
    }
    return GitHubRelease(
      version: version,
      notes: '${json['notes'] ?? ''}',
      apkUrl: resolved.toString(),
      source: '阿里云镜像',
      sha256: sha256,
      size: (json['size'] as num?)?.toInt() ?? 0,
    );
  }

  static String _cleanVersion(String value) =>
      value.replaceFirst(RegExp(r'^[vV]'), '');
}

class UpdateCheckResult {
  const UpdateCheckResult({
    required this.currentVersion,
    required this.hasUpdate,
    this.release,
    this.fallbackReason,
  });

  final String currentVersion;
  final bool hasUpdate;
  final GitHubRelease? release;
  final String? fallbackReason;
}

class UpdateDownloadStatus {
  const UpdateDownloadStatus({
    required this.state,
    this.version = '',
    this.progress = 0,
    this.downloadedBytes = 0,
    this.totalBytes = 0,
  });

  final String state;
  final String version;
  final double progress;
  final int downloadedBytes;
  final int totalBytes;

  bool get downloading =>
      state == 'pending' || state == 'running' || state == 'paused';
  bool get ready => state == 'ready' || state == 'installing';

  factory UpdateDownloadStatus.fromMap(Map<String, dynamic> value) =>
      UpdateDownloadStatus(
        state: '${value['state'] ?? 'idle'}',
        version: '${value['version'] ?? ''}',
        progress: ((value['progress'] as num?)?.toDouble() ?? 0).clamp(0, 1),
        downloadedBytes: (value['downloadedBytes'] as num?)?.toInt() ?? 0,
        totalBytes: (value['totalBytes'] as num?)?.toInt() ?? 0,
      );
}

enum InstallStartState {
  downloadStarted,
  downloadInProgress,
  installStarted,
  permissionRequired,
  failed,
}

class UpdateException implements Exception {
  const UpdateException(this.message);
  final String message;
  @override
  String toString() => message;
}

int compareVersions(String left, String right) {
  List<int> parts(String value) => value
      .split('-')
      .first
      .split('.')
      .map((part) => int.tryParse(part) ?? 0)
      .toList();
  final a = parts(left);
  final b = parts(right);
  final length = mathMax(a.length, b.length);
  for (var i = 0; i < length; i++) {
    final av = i < a.length ? a[i] : 0;
    final bv = i < b.length ? b[i] : 0;
    if (av != bv) return av.compareTo(bv);
  }
  return 0;
}

int mathMax(int a, int b) => a > b ? a : b;
