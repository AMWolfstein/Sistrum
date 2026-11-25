import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/song_menu.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/widgets/track_list_tile.dart';

import 'support/app_icon_finder.dart';

const _track = Track(
  id: 'track-1',
  name: 'Song',
  artistName: 'Artist',
  albumName: 'Album',
  duration: 200,
  trackNumber: 7,
);

/// A library with no local copies, so the tile has no play circle.
class _EmptyLibrary extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState(trackKeySet: <String>{});

  @override
  Future<LocalLibraryItem?> findExistingAsync({
    String? id,
    String? isrc,
    String? trackName,
    String? artistName,
  }) async => null;
}

Future<void> _pump(WidgetTester tester, Widget child) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(900, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [localLibraryProvider.overrideWith(_EmptyLibrary.new)],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: Center(child: child)),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

TrackCard _card({
  required Widget leading,
  String title = 'Song',
  String artist = 'Artist',
  bool isExplicit = false,
  bool isPlaying = false,
  bool selectionMode = false,
  VoidCallback? onPlay,
  VoidCallback? onTap,
  Widget? menu,
}) => TrackCard(
  style: TrackCardStyle.flat,
  leading: leading,
  title: title,
  subtitle: Text(artist, maxLines: 1, overflow: TextOverflow.ellipsis),
  isExplicit: isExplicit,
  isPlaying: isPlaying,
  isSelectionMode: selectionMode,
  onPlay: onPlay,
  menu: menu,
  onTap: onTap,
);

void main() {
  testWidgets('a track number leads the row, before the title', (tester) async {
    await _pump(tester, _card(leading: const Text('7')));

    expect(
      tester.getCenter(find.text('7')).dx,
      lessThan(tester.getCenter(find.text('Song')).dx),
    );
  });

  testWidgets('artwork leads the row when the caller gives artwork', (
    tester,
  ) async {
    await _pump(
      tester,
      _card(
        leading: const SizedBox.square(
          key: Key('artwork'),
          dimension: 48,
          child: ColoredBox(color: Colors.blue),
        ),
      ),
    );

    expect(find.byKey(const Key('artwork')), findsOneWidget);
    expect(find.text('7'), findsNothing);
    expect(
      tester.getCenter(find.byKey(const Key('artwork'))).dx,
      lessThan(tester.getCenter(find.text('Song')).dx),
    );
  });

  testWidgets('the row has two text lines: title and artists only', (
    tester,
  ) async {
    await _pump(tester, _card(leading: const Text('7')));

    // The track number, the title and the artists are the only text.
    expect(find.byType(Text), findsNWidgets(3));
    expect(find.text('Artist'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Artist')).dy,
      greaterThan(tester.getTopLeft(find.text('Song')).dy),
    );
  });

  testWidgets(
    'the Explicit chip shows only for explicit songs, beside the title',
    (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, _card(leading: const Text('7')));
      expect(find.bySemanticsLabel(RegExp('Explicit')), findsNothing);
      expect(find.text('E'), findsNothing);

      await _pump(tester, _card(leading: const Text('7'), isExplicit: true));
      expect(find.bySemanticsLabel(RegExp('Explicit')), findsOneWidget);
      expect(find.text('E'), findsOneWidget);
      // The chip shares the title's Row and sits after the title, not in
      // the artists line below it.
      final titleRow = find.ancestor(
        of: find.text('Song'),
        matching: find.byType(Row),
      );
      expect(
        find.descendant(of: titleRow.first, matching: find.text('E')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: titleRow.first, matching: find.text('Artist')),
        findsNothing,
      );
      expect(
        tester.getTopLeft(find.text('E')).dx,
        greaterThan(tester.getTopRight(find.text('Song')).dx),
      );
      semantics.dispose();
    },
  );

  testWidgets('the play circle plays the song and leaves the row tap alone', (
    tester,
  ) async {
    var played = 0;
    var tapped = 0;
    await _pump(
      tester,
      _card(
        leading: const Text('7'),
        onPlay: () => played++,
        onTap: () => tapped++,
      ),
    );

    expect(find.byIcon(Icons.play_circle_outline), findsOneWidget);
    await tester.tap(find.byIcon(Icons.play_circle_outline));
    expect(played, 1);
    expect(tapped, 0);

    await tester.tap(find.text('Song'));
    expect(tapped, 1);
  });

  testWidgets('the play circle shows pause while the song plays', (
    tester,
  ) async {
    await _pump(
      tester,
      _card(leading: const Text('7'), onPlay: () {}, isPlaying: true),
    );

    expect(find.byIcon(Icons.pause_circle_outline), findsOneWidget);
    expect(find.byIcon(Icons.play_circle_outline), findsNothing);
  });

  testWidgets('the menu sits last, after the play circle', (tester) async {
    await _pump(
      tester,
      _card(
        leading: const Text('7'),
        onPlay: () {},
        menu: const SizedBox.square(key: Key('menu'), dimension: 20),
      ),
    );

    expect(
      tester.getCenter(find.byKey(const Key('menu'))).dx,
      greaterThan(tester.getCenter(find.byIcon(Icons.play_circle_outline)).dx),
    );
  });

  testWidgets('selection mode keeps the checkbox and hides play and menu', (
    tester,
  ) async {
    await _pump(
      tester,
      _card(
        leading: const Text('7'),
        selectionMode: true,
        onPlay: () {},
        menu: const SizedBox(key: Key('menu')),
      ),
    );

    expect(find.byType(AnimatedSelectionCheckbox), findsOneWidget);
    expect(find.byIcon(Icons.play_circle_outline), findsNothing);
    expect(find.byKey(const Key('menu')), findsNothing);
  });

  testWidgets('the three-dots button opens the song menu', (tester) async {
    await _pump(tester, const TrackListTile(track: _track, leading: Text('7')));

    await tester.tap(findAppIcon(CupertinoIcons.ellipsis));
    await tester.pumpAndSettle();

    final labels = [
      for (final text in tester.widgetList<Text>(
        find.descendant(
          of: find.byType(MornyeContextMenu),
          matching: find.byType(Text),
        ),
      ))
        if (text.data != null) text.data!,
    ];
    // A song without a local copy still has the library actions: the menu
    // has no play, queue or file items.
    expect(labels, [
      'Add to Loved',
      'Add to playlist',
      'Go to Album',
      'Go to Artist',
    ]);
  });

  testWidgets('the tile without a local copy shows no play circle', (
    tester,
  ) async {
    await _pump(tester, const TrackListTile(track: _track, leading: Text('7')));

    expect(find.byType(SongMenuButton), findsOneWidget);
    expect(find.byIcon(Icons.play_circle_outline), findsNothing);
  });
}
