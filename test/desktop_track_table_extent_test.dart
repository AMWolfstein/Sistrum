import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/fixed_settings.dart';

final _current = Provider<MediaPlaybackUi>(
  (ref) => (isCurrent: true, isPlaying: false, isLoading: false),
);

class _FocusAction extends StatefulWidget {
  const _FocusAction(this.nodes);

  final List<FocusNode> nodes;

  @override
  State<_FocusAction> createState() => _FocusActionState();
}

class _FocusActionState extends State<_FocusAction> {
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    widget.nodes.add(_focus);
  }

  @override
  void dispose() {
    widget.nodes.remove(_focus);
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      TextButton(focusNode: _focus, onPressed: () {}, child: const Text('Go'));
}

Widget _app(
  List<TrackTableRow> rows, {
  required double scale,
  required bool uniform,
  ScrollController? controller,
  GlobalKey? capture,
  bool album = false,
}) => ProviderScope(
  overrides: [
    settingsProvider.overrideWith(
      () =>
          FixedSettingsNotifier(const AppSettings(localLibraryEnabled: false)),
    ),
  ],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.light),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
      child: child!,
    ),
    home: Scaffold(
      body: RepaintBoundary(
        key: capture,
        child: CustomScrollView(
          controller: controller,
          slivers: [
            SliverTrackTable(
              rows: rows,
              columns: TrackTableColumn.values,
              selectedIds: const {'1'},
              onSelect: (_, _, _) {},
              onActivate: (_) {},
              uniformRowHeight: uniform,
              albumLayout: album,
              albumArtist: 'Artist',
            ),
          ],
        ),
      ),
    ),
  ),
);

Future<Uint8List> _pixels(WidgetTester tester, GlobalKey key) async =>
    (await tester.runAsync(() async {
      final boundary =
          key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final image = await boundary.toImage();
      try {
        return (await image.toByteData(
          format: ui.ImageByteFormat.rawRgba,
        ))!.buffer.asUint8List();
      } finally {
        image.dispose();
      }
    }))!;

void main() {
  setUp(() => debugDesktopPlatformOverride = true);
  tearDown(() => debugDesktopPlatformOverride = null);

  void configure(WidgetTester tester) {
    tester.view.physicalSize = const Size(1500, 650);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  testWidgets('large column jumps build only visible rows', (tester) async {
    configure(tester);
    var builds = 0;
    final controller = ScrollController();
    addTearDown(controller.dispose);
    final rows = [
      for (var i = 0; i < 5000; i++)
        TrackTableRow(
          id: '$i',
          title: 'Track $i',
          leading: Builder(
            builder: (context) {
              builds++;
              return const SizedBox.square(dimension: 32);
            },
          ),
        ),
    ];
    await tester.pumpWidget(
      _app(rows, scale: 1, uniform: true, controller: controller),
    );
    await tester.pump();
    builds = 0;
    controller.jumpTo(controller.position.maxScrollExtent);
    await tester.pump();
    expect(find.text('Track 4999'), findsOneWidget);
    expect(builds, lessThan(40));
    expect(tester.takeException(), isNull);
  });

  for (final scale in [1.0, 2.0]) {
    testWidgets('column extent preserves row pixels ($scale)', (tester) async {
      configure(tester);
      final capture = GlobalKey();
      final rows = [
        for (var i = 0; i < 5; i++)
          TrackTableRow(
            id: '$i',
            title: i == 2 ? '日本語 العربية' : 'Track $i',
            number: i + 1,
            artist: 'Artist',
            album: 'Album',
            quality: 'FLAC 24-bit',
            duration: const Duration(minutes: 3),
            leading: i.isEven
                ? const SizedBox.square(
                    dimension: 32,
                    child: ColoredBox(color: Colors.blue),
                  )
                : null,
            titleTrailing: i == 1 || i == 4 ? const ExplicitChip() : null,
            trailing: i.isEven
                ? const SizedBox.square(
                    dimension: 32,
                    child: Icon(Icons.more_horiz),
                  )
                : null,
            playback: i == 2 ? _current : null,
          ),
      ];
      await tester.pumpWidget(
        _app(rows, scale: scale, uniform: false, capture: capture),
      );
      await tester.pumpAndSettle();
      final before = await _pixels(tester, capture);
      final heights = [
        for (var i = 0; i < rows.length; i++)
          tester.getSize(find.byKey(ValueKey('$i'))).height,
      ];
      expect(heights.toSet(), hasLength(1));
      await tester.pumpWidget(
        _app(rows, scale: scale, uniform: true, capture: capture),
      );
      await tester.pumpAndSettle();
      expect(await _pixels(tester, capture), before);
      expect(find.byType(SliverPrototypeExtentList), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('the measurement row adds no keyboard focus target', (
    tester,
  ) async {
    configure(tester);
    final nodes = <FocusNode>[];
    await tester.pumpWidget(
      _app(
        [
          for (var i = 0; i < 2; i++)
            TrackTableRow(
              id: '$i',
              title: 'Track $i',
              trailing: _FocusAction(nodes),
            ),
        ],
        scale: 1,
        uniform: true,
      ),
    );
    await tester.pumpAndSettle();
    expect(nodes, hasLength(3));
    expect(nodes.where((node) => node.canRequestFocus), hasLength(2));
    expect(tester.takeException(), isNull);
  });

  for (final scenario in ['custom', 'large text', 'album', 'empty']) {
    testWidgets('keeps variable layout for $scenario', (tester) async {
      configure(tester);
      final rows = scenario == 'empty'
          ? <TrackTableRow>[]
          : [
              const TrackTableRow(id: '0', title: 'First', artist: 'Artist'),
              const TrackTableRow(
                id: '1',
                title: 'Second',
                artist: 'Guest',
                leading: SizedBox(width: 32, height: 90),
              ),
            ];
      await tester.pumpWidget(
        _app(
          rows,
          scale: scenario == 'large text' ? 3 : 1,
          uniform: scenario != 'custom',
          album: scenario == 'album',
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(SliverPrototypeExtentList), findsNothing);
      if (scenario != 'empty') {
        expect(find.byType(SliverList), findsOneWidget);
      }
      expect(tester.takeException(), isNull);
    });
  }
}
