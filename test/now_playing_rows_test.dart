import 'dart:math' as math;

import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/app_content_card.dart';
import 'package:sistrum/widgets/decorative_phase.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/now_playing_indicator.dart';
import 'package:sistrum/widgets/song_row_tap.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/tooltip_finder.dart';

/// Long enough for the effect and spatial springs to settle.
const _settle = Duration(seconds: 1);

Widget _app(
  Widget child, {
  String? currentId,
  String? artUri,
  bool playing = true,
  bool reduceMotion = false,
  bool? tapPlays,
}) => ProviderScope(
  overrides: [
    // Without a choice these tests play songs with a tap on the row.
    librarySongTapPlaysProvider.overrideWithValue(tapPlays ?? true),
    currentMediaItemProvider.overrideWithBuild(
      (ref, notifier) => Stream.value(
        currentId == null
            ? null
            : MediaItem(
                id: currentId,
                title: 'Song',
                artUri: artUri == null ? null : Uri.parse(artUri),
              ),
      ),
    ),
    playbackPlayingProvider.overrideWith((ref) => playing),
    playbackLoadingProvider.overrideWith((ref) => false),
  ],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.light),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: reduceMotion),
      child: child!,
    ),
    home: Scaffold(body: child),
  ),
);

/// Pumps in bounded steps: a playing indicator loops, so the tree never
/// settles.
Future<void> _pumpFor(WidgetTester tester, Duration duration) async {
  const step = Duration(milliseconds: 50);
  for (var elapsed = Duration.zero; elapsed < duration; elapsed += step) {
    await tester.pump(step);
  }
}

ColorScheme _scheme(WidgetTester tester, Finder finder) =>
    Theme.of(tester.element(finder)).colorScheme;

/// The surface of the table row that shows [title].
Material _rowSurface(WidgetTester tester, String title) => tester
    .widgetList<Material>(
      find.ancestor(of: find.text(title), matching: find.byType(Material)),
    )
    .first;

TextStyle? _textStyle(WidgetTester tester, String text) =>
    tester.widget<Text>(find.text(text)).style;

const _columns = [
  TrackTableColumn.number,
  TrackTableColumn.title,
  TrackTableColumn.artist,
  TrackTableColumn.duration,
];

List<TrackTableRow> _rows({Widget? leading}) => [
  for (final (index, title) in ['Bravo', 'Alpha', 'Charlie'].indexed)
    TrackTableRow(
      id: 'row-$index',
      playback: mediaItemPlaybackUiProvider((id: 'media-$index', path: '')),
      number: index + 1,
      title: title,
      artist: 'Artist',
      duration: const Duration(minutes: 3),
      leading: leading,
    ),
];

Widget _table({
  List<TrackTableColumn> columns = _columns,
  List<TrackTableRow>? rows,
  Set<String> selectedIds = const {},
}) => CustomScrollView(
  slivers: [
    SliverTrackTable(
      rows: rows ?? _rows(),
      columns: columns,
      selectedIds: selectedIds,
      onSelect: (_, _, _) {},
      onActivate: (_) {},
    ),
  ],
);

void main() {
  group('NowPlayingIndicator', () {
    Widget host({required bool playing, bool reduceMotion = false}) =>
        MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          home: MediaQuery(
            data: MediaQueryData(disableAnimations: reduceMotion),
            child: Center(child: NowPlayingIndicator(playing: playing)),
          ),
        );

    testWidgets('moves while playing and freezes while paused', (tester) async {
      // The route caches its page, so playback changes come from inside it.
      final playing = ValueNotifier(true);
      addTearDown(playing.dispose);
      await tester.pumpWidget(
        MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          home: Center(
            child: ValueListenableBuilder<bool>(
              valueListenable: playing,
              builder: (context, value, _) =>
                  NowPlayingIndicator(playing: value),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));
      expect(DecorativePhase.debugClockRunning, isTrue);

      playing.value = false;
      await tester.pump();
      await tester.pump();
      expect(DecorativePhase.debugClockRunning, isFalse);

      playing.value = true;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(DecorativePhase.debugClockRunning, isTrue);
    });

    testWidgets('stays still under reduced motion', (tester) async {
      await tester.pumpWidget(host(playing: true, reduceMotion: true));
      await tester.pump();
      expect(DecorativePhase.debugClockRunning, isFalse);
    });

    testWidgets('stops its ticker while hidden', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          home: const TickerMode(
            enabled: false,
            child: Center(child: NowPlayingIndicator(playing: true)),
          ),
        ),
      );
      await tester.pump();
      expect(DecorativePhase.debugClockRunning, isFalse);
    });

    testWidgets('is a 16dp glyph outside the semantics tree', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(host(playing: false));

      final indicator = find.byType(NowPlayingIndicator);
      expect(tester.getSize(indicator), const Size.square(16));
      expect(
        find.descendant(of: indicator, matching: find.byType(ExcludeSemantics)),
        findsOneWidget,
      );
      handle.dispose();
    });
  });

  group('Songs table', () {
    testWidgets('Mornye keeps the plain row', (tester) async {
      await tester.pumpWidget(_app(_table(), currentId: 'media-0'));
      await tester.pump();

      expect(find.byType(NowPlayingIndicator), findsNothing);
      expect(find.text('1'), findsOneWidget);
      expect(_rowSurface(tester, 'Bravo').color, Colors.transparent);
      expect(_textStyle(tester, 'Bravo')?.fontWeight, FontWeight.w500);
    });
  });

  group('TrackCard', () {
    Widget card({
      bool current = false,
      bool selected = false,
      bool playing = true,
    }) => TrackCard(
      leading: const SizedBox.square(dimension: 56),
      title: 'Song',
      trailing: const SizedBox.square(dimension: 48),
      isCurrent: current,
      isPlaying: playing,
      isSelected: selected,
      leadingArtwork: true,
    );

    testWidgets('Mornye shows the current track in its title colour alone', (
      tester,
    ) async {
      await tester.pumpWidget(_app(card(current: true)));
      await tester.pump();

      expect(find.byType(NowPlayingIndicator), findsNothing);
      final surface = tester.widget<AppContentCard>(
        find.byType(AppContentCard),
      );
      expect(surface.color, Colors.transparent);
      expect(_textStyle(tester, 'Song')?.fontWeight, FontWeight.w500);
      final accent = _scheme(tester, find.text('Song')).primary;
      expect(_textStyle(tester, 'Song')?.color, accent);

      await tester.pumpWidget(_app(card()));
      await tester.pump();
      expect(_textStyle(tester, 'Song')?.color, isNot(accent));
    });

    testWidgets('Mornye keeps its selection tint', (tester) async {
      await tester.pumpWidget(_app(card(selected: true)));
      await tester.pump();

      final scheme = _scheme(tester, find.text('Song'));
      final surface = tester.widget<AppContentCard>(
        find.byType(AppContentCard),
      );
      expect(surface.color, scheme.primaryContainer.withValues(alpha: 0.3));
    });

    testWidgets('a Mornye flat row takes its selection tint without dark', (
      tester,
    ) async {
      final selected = ValueNotifier(false);
      addTearDown(selected.dispose);
      await tester.pumpWidget(
        _app(
          ValueListenableBuilder<bool>(
            valueListenable: selected,
            builder: (context, value, _) => TrackCard(
              leading: const SizedBox.square(dimension: 56),
              title: 'Song',
              style: TrackCardStyle.flat,
              isSelectionMode: true,
              isSelected: value,
            ),
          ),
        ),
      );
      await tester.pump();
      final scheme = _scheme(tester, find.text('Song'));
      final target = Color.alphaBlend(
        scheme.primaryContainer.withValues(alpha: 0.3),
        scheme.surface,
      );
      final low = math.min(
        target.computeLuminance(),
        scheme.surface.computeLuminance(),
      );
      final high = math.max(
        target.computeLuminance(),
        scheme.surface.computeLuminance(),
      );
      Color fill() {
        final box = tester.renderObject<RenderPhysicalShape>(
          find
              .descendant(
                of: find.byType(AppContentCard),
                matching: find.byType(PhysicalShape),
              )
              .first,
        );
        return Color.alphaBlend(box.color, scheme.surface);
      }

      selected.value = true;
      await tester.pump();
      // The Mornye card does not animate its colour: the tint cuts in, as
      // an iOS selection does, so there is no frame between the two.
      expect(fill(), target);
      for (var frame = 0; frame < 20; frame++) {
        expect(fill().computeLuminance(), inInclusiveRange(low, high));
        await tester.pump(const Duration(milliseconds: 16));
      }
      expect(fill(), target);
    });
  });

  group('AlbumTrackTile', () {
    Widget tile({required bool current}) => AlbumTrackTile(
      trackNumber: 1,
      trackName: 'Song',
      subtitle: const Text('Artist'),
      isSelectionMode: false,
      isSelected: false,
      onToggleSelection: () {},
      onOpen: () {},
      onEnterSelectionMode: () {},
      onPlay: () {},
      isCurrent: current,
      isPlaying: current,
    );

    testWidgets('marks the current track in its title colour and offers pause', (
      tester,
    ) async {
      await tester.pumpWidget(_app(tile(current: true), tapPlays: false));
      await _pumpFor(tester, _settle);

      // Mornye marks the current row by its title colour alone, with no glyph.
      expect(find.byType(NowPlayingIndicator), findsNothing);
      expect(
        _textStyle(tester, 'Song')?.color,
        _scheme(tester, find.text('Song')).primary,
      );
      expect(findByTooltip('Pause'), findsOneWidget);
      final button = tester.widget<IconButton>(find.byType(IconButton));
      expect(
        button.style?.backgroundColor?.resolve(const {}),
        Colors.transparent,
      );
    });

    testWidgets('other tracks show no glyph', (tester) async {
      await tester.pumpWidget(_app(tile(current: false), tapPlays: false));
      await tester.pump();

      expect(find.byType(NowPlayingIndicator), findsNothing);
      expect(findByTooltip('Play'), findsOneWidget);
    });

    testWidgets(
      'a tap that plays keeps the current track in its title colour',
      (tester) async {
        await tester.pumpWidget(_app(tile(current: true)));
        await _pumpFor(tester, _settle);

        expect(find.byType(NowPlayingIndicator), findsNothing);
        expect(
          _textStyle(tester, 'Song')?.color,
          _scheme(tester, find.text('Song')).primary,
        );
        expect(findByTooltip('Show details'), findsOneWidget);
        expect(findByTooltip('Pause'), findsNothing);
      },
    );
  });
}
