import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:real_liquid_glass_demo/main.dart';
import 'package:real_liquid_glass_demo/models/music_models.dart';

void main() {
  const album = MusicAlbum(id: 'album-1', name: '测试专辑', artist: '测试艺人');

  testWidgets('album play control does not open the album', (tester) async {
    var opened = 0;
    var played = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AlbumTile(
            album: album,
            onTap: () => opened++,
            onPlay: () => played++,
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('album-play-album-1')));
    await tester.pumpAndSettle();

    expect(played, 1);
    expect(opened, 0);
  });

  testWidgets('tapping the artwork opens the album', (tester) async {
    var opened = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AlbumTile(album: album, onTap: () => opened++, onPlay: () {}),
        ),
      ),
    );

    await tester.tapAt(const Offset(40, 40));
    await tester.pumpAndSettle();

    expect(opened, 1);
  });
}
