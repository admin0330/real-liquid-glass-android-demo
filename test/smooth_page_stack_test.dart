import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:real_liquid_glass_demo/main.dart';

void main() {
  testWidgets('a deselected page finishes fading instead of covering home', (
    tester,
  ) async {
    Widget app(int index) => MaterialApp(
      home: SmoothPageStack(
        index: index,
        children: const [
          ColoredBox(color: Colors.red),
          ColoredBox(color: Colors.blue),
        ],
      ),
    );

    await tester.pumpWidget(app(1));
    await tester.pumpAndSettle();

    await tester.pumpWidget(app(0));
    await tester.pump(const Duration(milliseconds: 400));

    final home = tester.renderObject<RenderAnimatedOpacity>(
      find.byKey(const ValueKey('smooth-page-opacity-0')),
    );
    final previous = tester.renderObject<RenderAnimatedOpacity>(
      find.byKey(const ValueKey('smooth-page-opacity-1')),
    );
    expect(home.opacity.value, 1);
    expect(previous.opacity.value, 0);
  });
}
