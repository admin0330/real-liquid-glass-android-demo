import 'package:flutter_test/flutter_test.dart';
import 'package:real_liquid_glass_demo/github_update_service.dart';

void main() {
  group('compareVersions', () {
    test('compares semantic numeric parts', () {
      expect(compareVersions('1.0.1', '1.0.0'), isPositive);
      expect(compareVersions('1.0.0', '1.0.0'), 0);
      expect(compareVersions('1.2.0', '1.10.0'), isNegative);
      expect(compareVersions('2.0', '1.9.9'), isPositive);
    });
  });

  test('parses the first APK asset from a GitHub release', () {
    final release = GitHubRelease.fromJson({
      'tag_name': 'v1.2.3',
      'body': 'Changes',
      'assets': [
        {
          'name': 'demo.apk',
          'browser_download_url': 'https://example.com/demo.apk',
        },
      ],
    });

    expect(release.version, '1.2.3');
    expect(release.notes, 'Changes');
    expect(release.apkUrl, 'https://example.com/demo.apk');
    expect(release.source, 'GitHub');
  });

  test('parses an Aliyun manifest and resolves a relative APK URL', () {
    final sha256 = List.filled(64, 'a').join();
    final release = GitHubRelease.fromMirrorJson({
      'version': 'v2.4.0',
      'apk_url': 'liquid-music-v2.4.0.apk',
      'sha256': sha256,
      'size': 123,
      'notes': 'Mirror release',
    }, Uri.parse('https://updates.example.com/music/latest.json'));

    expect(release.version, '2.4.0');
    expect(
      release.apkUrl,
      'https://updates.example.com/music/liquid-music-v2.4.0.apk',
    );
    expect(release.sha256, sha256);
    expect(release.source, '阿里云镜像');
  });

  test('normalizes an update mirror directory', () {
    expect(
      normalizeManifestUrl('https://updates.example.com/music/'),
      'https://updates.example.com/music/latest.json',
    );
    expect(
      normalizeManifestUrl('https://updates.example.com/latest.json'),
      'https://updates.example.com/latest.json',
    );
    expect(
      normalizeManifestUrl('https://updates.example.com/latest.json?token=1'),
      'https://updates.example.com/latest.json?token=1',
    );
  });
}
