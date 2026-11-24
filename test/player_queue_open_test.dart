import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_player_queue.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();
}

final _defaultQueue = [
  for (var i = 0; i < 40; i++)
    MediaItem(id: 'song$i', title: 'Song $i', artist: 'Artist'),
];

/// A queue entry that counts the rows built for it: only a real row reads
/// the title.
class _CountedItem extends MediaItem {
  const _CountedItem(this.index, this.builds)
    : super(id: 'song$index', title: 'Song $index', artist: 'Artist');

  final int index;
  final Set<int> builds;

  @override
  String get title {
    builds.add(index);
    return super.title;
  }
}

/// Shows the Mornye queue for a player on [playing], scrolled by [scroll].
Future<(ScrollController, StreamController<MediaItem?>)> _pump(
  WidgetTester tester, {
  required int playing,
  List<MediaItem>? queue,
}) async {
  final songs = queue ?? _defaultQueue;
  final current = StreamController<MediaItem?>();
  addTearDown(current.close);
  final scroll = ScrollController();
  addTearDown(scroll.dispose);
  final container = ProviderContainer(
    overrides: [
      settingsProvider.overrideWith(_Settings.new),
      musicPlayerControllerProvider.overrideWithValue(MusicPlayerController()),
      currentMediaItemProvider.overrideWithBuild(
        (ref, notifier) => current.stream,
      ),
      playQueueProvider.overrideWith((ref) => Stream.value(songs)),
      playbackStateProvider.overrideWith(
        (ref) => Stream.value(PlaybackState(queueIndex: playing)),
      ),
    ],
  );
  addTearDown(container.dispose);
  current.add(songs[playing]);
  final theme = MornyeTheme.build(Brightness.dark);
  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        theme: theme,
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: PrimaryScrollController(
            controller: scroll,
            child: MornyePlayerQueue(colorScheme: theme.colorScheme),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (scroll, current);
}

Finder _row(int index) => find.widgetWithText(ListTile, 'Song $index');

void main() {
  // The Mornye queue lists only the songs after the playing one.
  testWidgets('the queue opens on the songs after the playing one', (
    tester,
  ) async {
    final (scroll, _) = await _pump(tester, playing: 20);

    expect(_row(20), findsNothing);
    expect(_row(21), findsOneWidget);
    expect(scroll.offset, 0);
    expect(tester.takeException(), isNull);
  });

  for (final playing in [0, 1]) {
    testWidgets('song ${playing + 1} opens at the top', (tester) async {
      final (scroll, _) = await _pump(tester, playing: playing);
      expect(scroll.offset, 0);
      expect(_row(playing + 1), findsOneWidget);
    });
  }

  testWidgets('the last song leaves the queue empty without overscroll', (
    tester,
  ) async {
    final (scroll, _) = await _pump(tester, playing: 39);
    expect(scroll.offset, 0);
    expect(_row(39), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a long queue builds only the songs on screen', (tester) async {
    final builds = <int>{};
    final queue = [for (var i = 0; i < 3000; i++) _CountedItem(i, builds)];
    final (scroll, _) = await _pump(tester, playing: 2500, queue: queue);

    expect(scroll.offset, 0);
    expect(_row(2501), findsOneWidget);
    expect(builds.length, lessThan(80));
    expect(builds.where((index) => index <= 2500), isEmpty);
    expect(tester.takeException(), isNull);
  });
}
