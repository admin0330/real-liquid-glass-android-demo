import 'dart:convert';
import 'dart:io';

import 'package:audio_metadata_reader/audio_metadata_reader.dart';
import 'package:crypto/crypto.dart';
import 'package:file_picker/file_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/music_models.dart';

class LocalLibraryService {
  static const _storageKey = 'local_music_library_v2';
  static const supportedExtensions = [
    'flac',
    'mp3',
    'm4a',
    'aac',
    'ogg',
    'opus',
    'wav',
    'aiff',
    'aif',
    'ape',
  ];

  Future<List<MusicTrack>> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null) return [];
    try {
      final tracks = (jsonDecode(raw) as List<dynamic>)
          .map((e) => MusicTrack.fromJson(Map<String, dynamic>.from(e as Map)))
          .where(
            (track) =>
                track.localPath != null && File(track.localPath!).existsSync(),
          )
          .toList();
      return tracks;
    } catch (_) {
      return [];
    }
  }

  Future<List<MusicTrack>> pickAndImport() async {
    final result = await FilePicker.platform.pickFiles(
      allowMultiple: true,
      // Some Android file managers (including MIUI) hide FLAC when a custom
      // extension filter is supplied. Show all files, then validate locally.
      type: FileType.any,
      withData: false,
    );
    if (result == null) return [];
    final imported = <MusicTrack>[];
    for (final item in result.files) {
      if (item.path == null) continue;
      final extension = item.path!.split('.').last.toLowerCase();
      if (!supportedExtensions.contains(extension)) continue;
      try {
        imported.add(await _import(File(item.path!)));
      } catch (_) {
        // Skip unreadable or unsupported files while preserving successful imports.
      }
    }
    if (imported.isNotEmpty) {
      final existing = await load();
      final byId = {for (final track in existing) track.id: track};
      for (final track in imported) {
        byId[track.id] = track;
      }
      await save(byId.values.toList());
    }
    return imported;
  }

  Future<List<MusicTrack>> importInbox() async {
    final support = await getApplicationSupportDirectory();
    final inbox = Directory('${support.path}${Platform.pathSeparator}inbox');
    if (!await inbox.exists()) return [];
    final imported = <MusicTrack>[];
    await for (final entry in inbox.list()) {
      if (entry is! File) continue;
      final extension = entry.path.split('.').last.toLowerCase();
      if (!supportedExtensions.contains(extension)) continue;
      try {
        imported.add(await _import(entry));
        await entry.delete();
      } catch (_) {
        // Keep failed files in the inbox so they can be inspected or retried.
      }
    }
    if (imported.isNotEmpty) {
      final existing = await load();
      final byId = {for (final track in existing) track.id: track};
      for (final track in imported) {
        byId[track.id] = track;
      }
      await save(byId.values.toList());
    }
    return imported;
  }

  Future<void> save(List<MusicTrack> tracks) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _storageKey,
      jsonEncode(tracks.map((e) => e.toJson()).toList()),
    );
  }

  Future<MusicTrack> _import(File source) async {
    final support = await getApplicationSupportDirectory();
    final musicDir = Directory(
      '${support.path}${Platform.pathSeparator}library',
    );
    final coverDir = Directory(
      '${support.path}${Platform.pathSeparator}covers',
    );
    await musicDir.create(recursive: true);
    await coverDir.create(recursive: true);

    final stat = await source.stat();
    final id = sha1
        .convert(
          utf8.encode(
            '${source.path}|${stat.size}|${stat.modified.millisecondsSinceEpoch}',
          ),
        )
        .toString();
    final extension = source.path.split('.').last.toLowerCase();
    final destination = File(
      '${musicDir.path}${Platform.pathSeparator}$id.$extension',
    );
    if (!await destination.exists()) await source.copy(destination.path);

    final metadata = readMetadata(destination, getImage: true);
    String? coverPath;
    if (metadata.pictures.isNotEmpty) {
      final picture = metadata.pictures.first;
      final coverExt = picture.mimetype.contains('png') ? 'png' : 'jpg';
      final cover = File(
        '${coverDir.path}${Platform.pathSeparator}$id.$coverExt',
      );
      await cover.writeAsBytes(picture.bytes, flush: true);
      coverPath = cover.path;
    }
    final fallback = source.uri.pathSegments.last.replaceFirst(
      RegExp(r'\.[^.]+$'),
      '',
    );
    return MusicTrack(
      id: id,
      title: metadata.title?.trim().isNotEmpty == true
          ? metadata.title!.trim()
          : fallback,
      artist: metadata.artist?.trim().isNotEmpty == true
          ? metadata.artist!.trim()
          : '未知艺人',
      album: metadata.album?.trim().isNotEmpty == true
          ? metadata.album!.trim()
          : '未知专辑',
      source: MusicSourceKind.local,
      albumId:
          'local-${metadata.album ?? 'unknown'}-${metadata.artist ?? 'unknown'}',
      localPath: destination.path,
      localCoverPath: coverPath,
      suffix: extension,
      duration: metadata.duration ?? Duration.zero,
      bitRate: metadata.bitrate,
      sampleRate: metadata.sampleRate,
      trackNumber: metadata.trackNumber,
      size: stat.size,
      lyrics: metadata.lyrics,
    );
  }
}
