import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/music_models.dart';
import 'download_service.dart';
import 'local_library_service.dart';
import 'playback_controller.dart';
import 'subsonic_service.dart';
import 'usb_audio_service.dart';

class MusicController extends ChangeNotifier {
  MusicController() {
    playback.addListener(_relayPlaybackState);
  }

  static const _serverKey = 'subsonic_server';
  static const _userKey = 'subsonic_user';
  static const _passwordKey = 'subsonic_password';
  static const _offlineKey = 'offline_tracks_v2';
  static const _playlistsKey = 'personal_playlists_v2';

  final PlaybackController playback = PlaybackController();
  final LocalLibraryService _localService = LocalLibraryService();
  final DownloadService _downloadService = DownloadService();
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();
  final UsbAudioService _usbAudioService = UsbAudioService();

  List<MusicTrack> localTracks = [];
  List<MusicTrack> remoteFavorites = [];
  List<MusicTrack> offlineTracks = [];
  List<MusicAlbum> remoteAlbums = [];
  List<MusicPlaylist> remotePlaylists = [];
  List<MusicPlaylist> personalPlaylists = [];
  MusicSearchResult searchResult = const MusicSearchResult();
  SubsonicConfig? config;
  SubsonicService? _subsonic;
  bool loading = true;
  bool importing = false;
  bool searching = false;
  bool usbAudioBusy = false;
  UsbAudioStatus usbAudioStatus = const UsbAudioStatus();
  String? error;
  final Map<String, double> downloadProgress = {};
  Timer? _searchDebounce;

  bool get connected => _subsonic != null;

  void _relayPlaybackState() => notifyListeners();

  List<MusicTrack> get allTracks {
    final byId = <String, MusicTrack>{};
    for (final track in [
      ...localTracks,
      ...offlineTracks,
      ...remoteFavorites,
    ]) {
      byId['${track.source.name}:${track.id}'] = track;
    }
    return byId.values.toList();
  }

  List<MusicTrack> get favorites => allTracks.where((e) => e.favorite).toList();

  List<MusicAlbum> get localAlbums {
    final grouped = <String, List<MusicTrack>>{};
    for (final track in localTracks) {
      (grouped[track.albumId ?? track.album] ??= []).add(track);
    }
    return grouped.entries.map((entry) {
      final tracks = entry.value
        ..sort(
          (a, b) => (a.trackNumber ?? 999).compareTo(b.trackNumber ?? 999),
        );
      final first = tracks.first;
      return MusicAlbum(
        id: entry.key,
        name: first.album,
        artist: first.artist,
        coverUrl: first.localCoverPath == null
            ? null
            : Uri.file(first.localCoverPath!).toString(),
        songCount: tracks.length,
        duration: tracks.fold(
          Duration.zero,
          (sum, item) => sum + item.duration,
        ),
        tracks: tracks,
      );
    }).toList();
  }

  Future<void> initialize() async {
    loading = true;
    notifyListeners();
    await playback.initialize();
    await refreshUsbAudio(silent: true);
    final prefs = await SharedPreferences.getInstance();
    await _localService.importInbox();
    localTracks = await _localService.load();
    offlineTracks = _decodeTracks(prefs.getString(_offlineKey));
    personalPlaylists = _decodePlaylists(prefs.getString(_playlistsKey));
    final server = prefs.getString(_serverKey);
    final user = prefs.getString(_userKey);
    final password = await _secureStorage.read(key: _passwordKey);
    if (server != null && user != null && password != null) {
      config = SubsonicConfig(
        serverUrl: server,
        username: user,
        password: password,
      );
      _subsonic = SubsonicService(config!);
      await refreshRemote(silent: true);
    }
    loading = false;
    notifyListeners();
  }

  Future<void> refreshUsbAudio({bool silent = false}) async {
    if (!silent) {
      usbAudioBusy = true;
      notifyListeners();
    }
    try {
      usbAudioStatus = await _usbAudioService.status();
    } catch (e) {
      usbAudioStatus = UsbAudioStatus(message: '读取 USB 音频状态失败：$e');
    } finally {
      usbAudioBusy = false;
      if (!silent) notifyListeners();
    }
  }

  Future<UsbAudioStatus> setUsbExclusive(bool enabled) async {
    usbAudioBusy = true;
    notifyListeners();
    try {
      usbAudioStatus = await _usbAudioService.setExclusive(enabled);
      if (usbAudioStatus.enabled || !enabled) {
        await playback.reopenAudioSink();
      }
      return usbAudioStatus;
    } finally {
      usbAudioBusy = false;
      notifyListeners();
    }
  }

  Future<int> importLocal() async {
    importing = true;
    error = null;
    notifyListeners();
    try {
      final imported = await _localService.pickAndImport();
      localTracks = await _localService.load();
      return imported.length;
    } finally {
      importing = false;
      notifyListeners();
    }
  }

  Future<void> connect(SubsonicConfig newConfig) async {
    final candidate = SubsonicService(newConfig);
    await candidate.ping();
    config = newConfig;
    _subsonic = candidate;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_serverKey, newConfig.normalizedServer);
    await prefs.setString(_userKey, newConfig.username);
    await _secureStorage.write(key: _passwordKey, value: newConfig.password);
    await refreshRemote();
  }

  Future<void> disconnect() async {
    _subsonic = null;
    config = null;
    remoteAlbums = [];
    remoteFavorites = [];
    remotePlaylists = [];
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_serverKey);
    await prefs.remove(_userKey);
    await _secureStorage.delete(key: _passwordKey);
    notifyListeners();
  }

  Future<void> refreshRemote({bool silent = false}) async {
    if (_subsonic == null) return;
    if (!silent) {
      loading = true;
      error = null;
      notifyListeners();
    }
    try {
      final values = await Future.wait([
        _subsonic!.albums(),
        _subsonic!.favorites(),
        _subsonic!.playlists(),
      ]);
      remoteAlbums = values[0] as List<MusicAlbum>;
      remoteFavorites = _applyOffline(values[1] as List<MusicTrack>);
      remotePlaylists = values[2] as List<MusicPlaylist>;
    } catch (e) {
      error = '$e';
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<MusicAlbum> loadAlbum(MusicAlbum album) async {
    if (album.tracks.isNotEmpty || _subsonic == null) return album;
    return _withOfflineAlbum(await _subsonic!.album(album.id));
  }

  Future<MusicPlaylist> loadPlaylist(MusicPlaylist playlist) async {
    if (playlist.tracks.isNotEmpty ||
        _subsonic == null ||
        playlist.id.startsWith('personal-')) {
      return playlist;
    }
    final loaded = await _subsonic!.playlist(playlist.id);
    return loaded.copyWith(tracks: _applyOffline(loaded.tracks));
  }

  void search(String query) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(
      const Duration(milliseconds: 350),
      () => _performSearch(query),
    );
  }

  Future<void> _performSearch(String query) async {
    final normalized = query.trim().toLowerCase();
    if (normalized.isEmpty) {
      searchResult = const MusicSearchResult();
      searching = false;
      notifyListeners();
      return;
    }
    searching = true;
    notifyListeners();
    final local = localTracks
        .where(
          (track) => '${track.title} ${track.artist} ${track.album}'
              .toLowerCase()
              .contains(normalized),
        )
        .toList();
    try {
      final remote = _subsonic == null
          ? const MusicSearchResult()
          : await _subsonic!.search(query);
      searchResult = MusicSearchResult(
        tracks: [...local, ..._applyOffline(remote.tracks)],
        albums: [
          ...localAlbums.where(
            (a) => '${a.name} ${a.artist}'.toLowerCase().contains(normalized),
          ),
          ...remote.albums,
        ],
        artists: remote.artists,
      );
    } catch (e) {
      error = '$e';
      searchResult = MusicSearchResult(tracks: local);
    } finally {
      searching = false;
      notifyListeners();
    }
  }

  Future<void> toggleFavorite(MusicTrack track) async {
    final value = !track.favorite;
    if (track.source == MusicSourceKind.subsonic && _subsonic != null) {
      await _subsonic!.setFavorite(track.id, value);
      remoteFavorites.removeWhere((e) => e.id == track.id);
      if (value) remoteFavorites.insert(0, track.copyWith(favorite: true));
    } else {
      localTracks = localTracks
          .map((e) => e.id == track.id ? e.copyWith(favorite: value) : e)
          .toList();
      await _localService.save(localTracks);
    }
    notifyListeners();
  }

  Future<String?> lyricsFor(MusicTrack track) async {
    if (track.lyrics?.trim().isNotEmpty == true) return track.lyrics;
    if (track.source == MusicSourceKind.subsonic && _subsonic != null) {
      return _subsonic!.lyricsFor(track);
    }
    return null;
  }

  Future<MusicTrack> download(MusicTrack track) async {
    downloadProgress[track.id] = 0;
    notifyListeners();
    try {
      final downloaded = await _downloadService.download(
        track,
        onProgress: (value) {
          downloadProgress[track.id] = value;
          notifyListeners();
        },
      );
      offlineTracks.removeWhere((e) => e.id == track.id);
      offlineTracks.add(downloaded);
      await _saveOffline();
      return downloaded;
    } finally {
      downloadProgress.remove(track.id);
      notifyListeners();
    }
  }

  Future<MusicPlaylist> createPlaylist(String name) async {
    final playlist = MusicPlaylist(
      id: 'personal-${DateTime.now().millisecondsSinceEpoch}',
      name: name.trim().isEmpty ? '新建歌单' : name.trim(),
    );
    personalPlaylists.add(playlist);
    await _savePlaylists();
    notifyListeners();
    return playlist;
  }

  Future<void> addToPlaylist(MusicPlaylist playlist, MusicTrack track) async {
    final index = personalPlaylists.indexWhere((e) => e.id == playlist.id);
    if (index < 0 ||
        personalPlaylists[index].tracks.any((e) => e.id == track.id)) {
      return;
    }
    personalPlaylists[index] = personalPlaylists[index].copyWith(
      tracks: [...personalPlaylists[index].tracks, track],
    );
    await _savePlaylists();
    notifyListeners();
  }

  Future<void> _saveOffline() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _offlineKey,
      jsonEncode(offlineTracks.map((e) => e.toJson()).toList()),
    );
  }

  Future<void> _savePlaylists() async {
    final prefs = await SharedPreferences.getInstance();
    final data = personalPlaylists
        .map(
          (p) => {
            'id': p.id,
            'name': p.name,
            'tracks': p.tracks.map((e) => e.toJson()).toList(),
          },
        )
        .toList();
    await prefs.setString(_playlistsKey, jsonEncode(data));
  }

  List<MusicTrack> _applyOffline(List<MusicTrack> tracks) {
    final offline = {for (final item in offlineTracks) item.id: item};
    return tracks
        .map(
          (track) => offline[track.id] == null
              ? track
              : track.copyWith(localPath: offline[track.id]!.localPath),
        )
        .toList();
  }

  MusicAlbum _withOfflineAlbum(MusicAlbum album) =>
      album.copyWith(tracks: _applyOffline(album.tracks));

  List<MusicTrack> _decodeTracks(String? raw) {
    if (raw == null) return [];
    try {
      return (jsonDecode(raw) as List)
          .map((e) => MusicTrack.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (_) {
      return [];
    }
  }

  List<MusicPlaylist> _decodePlaylists(String? raw) {
    if (raw == null) return [];
    try {
      return (jsonDecode(raw) as List).map((value) {
        final json = Map<String, dynamic>.from(value as Map);
        final tracks = (json['tracks'] as List? ?? [])
            .map(
              (e) => MusicTrack.fromJson(Map<String, dynamic>.from(e as Map)),
            )
            .toList();
        return MusicPlaylist(
          id: json['id'] as String,
          name: json['name'] as String,
          tracks: tracks,
          songCount: tracks.length,
        );
      }).toList();
    } catch (_) {
      return [];
    }
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    playback.removeListener(_relayPlaybackState);
    playback.dispose();
    super.dispose();
  }
}
