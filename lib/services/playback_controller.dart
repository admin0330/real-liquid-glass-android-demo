import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';

import '../models/music_models.dart';

class PlaybackController extends ChangeNotifier {
  PlaybackController() {
    _subscriptions.add(
      player.currentIndexStream.listen((index) {
        if (index != null && index >= 0 && index < queue.length) {
          currentIndex = index;
        }
        notifyListeners();
      }),
    );
    _subscriptions.add(
      player.playerStateStream.listen((_) => notifyListeners()),
    );
    _subscriptions.add(
      player.shuffleModeEnabledStream.listen((_) => notifyListeners()),
    );
    _subscriptions.add(player.loopModeStream.listen((_) => notifyListeners()));
  }

  final AudioPlayer player = AudioPlayer();
  final List<StreamSubscription<dynamic>> _subscriptions = [];
  List<MusicTrack> queue = [];
  int currentIndex = 0;

  MusicTrack? get current => queue.isEmpty || currentIndex >= queue.length
      ? null
      : queue[currentIndex];
  bool get playing => player.playing;
  bool get shuffle => player.shuffleModeEnabled;
  LoopMode get loopMode => player.loopMode;
  Stream<Duration> get positionStream => player.positionStream;
  Stream<Duration?> get durationStream => player.durationStream;

  Future<void> initialize() async {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.music());
  }

  Future<void> playTracks(
    List<MusicTrack> tracks, {
    int initialIndex = 0,
  }) async {
    if (tracks.isEmpty) return;
    queue = List.of(tracks);
    currentIndex = initialIndex.clamp(0, tracks.length - 1);
    final sources = tracks
        .map(
          (track) => AudioSource.uri(
            track.playbackUri,
            tag: MediaItem(
              id: track.id,
              title: track.title,
              artist: track.artist,
              album: track.album,
              duration: track.duration == Duration.zero ? null : track.duration,
              artUri: track.artworkUri,
              extras: {
                'quality': track.qualityLabel,
                'source': track.source.name,
              },
            ),
          ),
        )
        .toList();
    await player.setAudioSources(
      sources,
      initialIndex: currentIndex,
      preload: true,
    );
    await player.play();
    notifyListeners();
  }

  Future<void> toggle() => playing ? player.pause() : player.play();
  Future<void> next() => player.seekToNext();
  Future<void> previous() async {
    if (player.position > const Duration(seconds: 3)) {
      await player.seek(Duration.zero);
    } else {
      await player.seekToPrevious();
    }
  }

  Future<void> seek(Duration position) => player.seek(position);
  Future<void> toggleShuffle() => player.setShuffleModeEnabled(!shuffle);
  Future<void> cycleRepeat() => player.setLoopMode(
    loopMode == LoopMode.off
        ? LoopMode.all
        : loopMode == LoopMode.all
        ? LoopMode.one
        : LoopMode.off,
  );
  Future<void> playAt(int index) =>
      player.seek(Duration.zero, index: index).then((_) => player.play());

  Future<void> reopenAudioSink() async {
    if (current == null) return;
    final position = player.position;
    final index = currentIndex;
    final resume = playing;
    await player.stop();
    await player.seek(position, index: index);
    if (resume) await player.play();
  }

  Future<void> reorderQueue(int oldIndex, int newIndex) async {
    if (oldIndex == newIndex ||
        oldIndex < 0 ||
        newIndex < 0 ||
        oldIndex >= queue.length ||
        newIndex >= queue.length) {
      return;
    }
    final track = queue.removeAt(oldIndex);
    queue.insert(newIndex, track);
    await player.moveAudioSource(oldIndex, newIndex);
    notifyListeners();
  }

  @override
  void dispose() {
    for (final subscription in _subscriptions) {
      subscription.cancel();
    }
    player.dispose();
    super.dispose();
  }
}
