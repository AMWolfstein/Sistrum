import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_player_queue.dart';
import 'package:sistrum/widgets/swipe_progress_haptic.dart';

class _Player extends MusicPlayerController {
  final removed = <String>[];
  final playedNext = <MediaItem>[];

  @override
  Future<bool> playQueuedItemNext(MediaItem item) async {
    playedNext.add(item);
    return true;
  }

  @override
  void removeQueuedItem(MediaItem item) => removed.add(item.id);
}

class _Settings extends SettingsNotifier {
  _Settings(this.initial);

  final AppSettings initial;

  @override
  AppSettings build() => initial;
}

final _queue = [
  for (final name in ['Current', 'Next', 'Last'])
    MediaItem(id: name, title: name, artist: 'Artist'),
];

void main() {
  const hapticsChannel = MethodChannel('com.amwolfstein.sistrum/haptics');
  late List<double> haptics;

  setUp(() {
    haptics = [];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(hapticsChannel, (call) async {
          if (call.method == 'pulse') {
            haptics.add((call.arguments as Map)['intensity'] as double);
          }
          return null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(hapticsChannel, null);
  });

  Future<_Player> pump(
    WidgetTester tester, {
    TextDirection direction = TextDirection.ltr,
    bool locked = false,
    bool swipeToQueue = true,
  }) async {
    final player = _Player();
    final theme = MornyeTheme.build(Brightness.dark);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(
            () => _Settings(
              AppSettings(queueLocked: locked, swipeToQueue: swipeToQueue),
            ),
          ),
          musicPlayerControllerProvider.overrideWithValue(player),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(_queue.first),
          ),
          playQueueProvider.overrideWith((ref) => Stream.value(_queue)),
          playbackStateProvider.overrideWith(
            (ref) => Stream.value(PlaybackState(queueIndex: 0)),
          ),
        ],
        child: MaterialApp(
          theme: theme,
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Directionality(
            textDirection: direction,
            child: Scaffold(
              body: MornyePlayerQueue(colorScheme: theme.colorScheme),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return player;
  }

  /// A slow drag, so its release is not a fling.
  Future<void> slowDrag(WidgetTester tester, String title, double dx) async {
    await tester.timedDrag(
      find.text(title),
      Offset(dx, 0),
      const Duration(milliseconds: 600),
    );
    await tester.pumpAndSettle();
  }

  for (final direction in TextDirection.values) {
    // Toward the row's end: rightward left to right, leftward right to
    // left.
    final towardEnd = direction == TextDirection.ltr ? 260.0 : -260.0;

    testWidgets('${direction.name}: toward the end plays a copy '
        'next, toward the start still removes', (tester) async {
      final player = await pump(tester, direction: direction);
      final rest = tester.getTopLeft(find.text('Last'));

      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Last')),
      );
      for (var i = 0; i < 26; i++) {
        await gesture.moveBy(Offset(towardEnd / 26, 0));
        await tester.pump(const Duration(milliseconds: 16));
      }
      // The song rows' strip and commit pulse.
      expect(find.text('Play next'), findsOneWidget);
      expect(
        haptics.where((value) => value >= SwipeProgressHaptic.commitIntensity),
        hasLength(1),
      );
      await gesture.up();
      await tester.pumpAndSettle();

      expect(player.playedNext.map((item) => item.id), ['Last']);
      expect(player.playedNext.single, same(_queue[2]));
      expect(player.removed, isEmpty);
      expect(find.text('Playing next'), findsOneWidget);
      // The entry springs back in place.
      expect(tester.getTopLeft(find.text('Last')), rest);

      await slowDrag(tester, 'Next', -towardEnd * 2.4);
      expect(player.removed, ['Next']);
      expect(player.playedNext, hasLength(1));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a locked queue refuses the swipe', (tester) async {
    final player = await pump(tester, locked: true);
    await slowDrag(tester, 'Last', 260);
    await slowDrag(tester, 'Next', -600);
    expect(player.playedNext, isEmpty);
    expect(player.removed, isEmpty);
    expect(find.text('Playing next'), findsNothing);
  });

  testWidgets('screen readers get Play next on the row', (tester) async {
    final semantics = tester.ensureSemantics();
    final player = await pump(tester);
    final node = tester.getSemantics(find.text('Last'));
    final labels = [
      for (final id in node.getSemanticsData().customSemanticsActionIds!)
        CustomSemanticsAction.getAction(id)!.label,
    ];
    expect(labels, contains('Play next'));
    expect(labels, isNot(contains('Add to queue')));
    final id = node.getSemanticsData().customSemanticsActionIds!.firstWhere(
      (id) => CustomSemanticsAction.getAction(id)!.label == 'Play next',
    );
    node.owner!.performAction(node.id, SemanticsAction.customAction, id);
    await tester.pumpAndSettle();
    expect(player.playedNext.map((item) => item.id), ['Last']);
    semantics.dispose();
  });

  testWidgets('the playing song has no row to swipe to play next', (
    tester,
  ) async {
    final player = await pump(tester);
    // Mornye's queue lists the songs after the playing one only.
    expect(find.text('Current'), findsNothing);
    expect(find.text('Next'), findsOneWidget);
    expect(player.playedNext, isEmpty);
  });

  testWidgets('with Swipe songs to queue off, only removal swipes', (
    tester,
  ) async {
    final player = await pump(tester, swipeToQueue: false);
    await slowDrag(tester, 'Last', 260);
    expect(player.playedNext, isEmpty);
    await slowDrag(tester, 'Next', -600);
    expect(player.removed, ['Next']);
  });
}
