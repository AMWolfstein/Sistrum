import 'package:material_ui/material_ui.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/playback_seek_slider.dart';

Widget _host({
  bool playing = true,
  bool reduceMotion = false,
  bool visible = true,
  TextDirection direction = TextDirection.ltr,
  Duration position = const Duration(seconds: 30),
  Duration duration = const Duration(seconds: 100),
  Future<void> Function(Duration)? onSeek,
}) => MaterialApp(
  theme: MornyeTheme.build(Brightness.dark),
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduceMotion),
    child: Directionality(
      textDirection: direction,
      child: TickerMode(
        enabled: visible,
        child: Scaffold(
          body: Center(
            child: PlaybackSeekSlider(
              playing: playing,
              position: position,
              duration: duration,
              onSeek: onSeek ?? (_) async {},
            ),
          ),
        ),
      ),
    ),
  ),
);

void main() {
  testWidgets('Mornye keeps its continuous track without a wave or handle', (
    tester,
  ) async {
    await tester.pumpWidget(_host());
    await tester.pumpAndSettle();
    final theme = SliderTheme.of(tester.element(find.byType(Slider)));
    expect(theme.trackShape, isA<RoundedRectSliderTrackShape>());
    expect(theme.thumbShape, SliderComponentShape.noThumb);
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('RTL seeking and screen reader adjustment remain available', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final seeks = <Duration>[];
    await tester.pumpWidget(
      _host(
        direction: TextDirection.rtl,
        onSeek: (target) async => seeks.add(target),
      ),
    );
    final bounds = tester.getRect(find.byType(Slider));
    await tester.tapAt(
      Offset(bounds.left + bounds.width * 0.2, bounds.center.dy),
    );
    await tester.pump();
    expect(seeks.single.inSeconds, greaterThan(70));
    final node = tester.semantics.simulatedAccessibilityTraversal().singleWhere(
      (node) => node.getSemanticsData().flagsCollection.isSlider,
    );
    expect(node.getSemanticsData().hasAction(SemanticsAction.increase), isTrue);
    node.owner!.performAction(node.id, SemanticsAction.increase);
    await tester.pump();
    expect(seeks, hasLength(2));
    expect(seeks.last.inSeconds, greaterThan(30));
    semantics.dispose();
  });
}
