import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/track_provider.dart';
import 'package:sistrum/screens/artist_screen.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _albums = [
  for (var i = 0; i < 5; i++)
    ArtistAlbum(
      id: 'album-$i',
      name: 'Album $i',
      releaseDate: '2020',
      totalTracks: 1,
      // A different shelf must not enter the range between albums 0 and 3.
      albumType: i == 1 ? 'single' : 'album',
      artists: 'Example Artist',
    ),
];

Future<void> _pumpArtist(
  WidgetTester tester, {
  List<ArtistAlbum>? albums,
}) async {
  SharedPreferences.setMockInitialValues({});
  await tester.binding.setSurfaceSize(const Size(1200, 1800));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(channel, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ArtistScreen(
          artistId: 'example-artist',
          artistName: 'Example Artist',
          albums: albums ?? _albums,
          topTracks: const [
            Track(
              id: 'example-track',
              name: 'Example Track',
              artistName: 'Example Artist',
              albumName: 'Example Album',
              duration: 180,
            ),
          ],
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Finder _card(int index) => find.byKey(ValueKey('album-$index'));

void _expectSelection(WidgetTester tester, Set<int> selected) {
  expect(
    tester
        .widget<SelectionBottomBar>(find.byType(SelectionBottomBar))
        .selectedCount,
    selected.length,
  );
  for (var i = 0; i < _albums.length; i++) {
    final checkbox = find.descendant(
      of: _card(i),
      matching: find.byType(AnimatedSelectionCheckbox),
    );
    expect(
      tester.widget<AnimatedSelectionCheckbox>(checkbox).selected,
      selected.contains(i),
    );
  }
}

Future<void> _disposeArtist(WidgetTester tester) async {
  expect(tester.takeException(), isNull);
  await tester.pumpWidget(const SizedBox());
  await tester.pumpAndSettle();
}

void main() {
  tearDown(() {});

  final style = 'Mornye';

  testWidgets('$style: touch long press still selects albums', (tester) async {
    await _pumpArtist(tester);
    await tester.longPress(_card(2));
    await tester.pumpAndSettle();
    _expectSelection(tester, {2});
    await tester.tap(_card(3));
    await tester.pumpAndSettle();
    _expectSelection(tester, {2, 3});
    await _disposeArtist(tester);
  });

  testWidgets('$style: select all toggle clears but keeps selection mode', (
    tester,
  ) async {
    await _pumpArtist(tester);
    await tester.longPress(_card(2));
    await tester.pumpAndSettle();
    SelectionBottomBar bar() =>
        tester.widget<SelectionBottomBar>(find.byType(SelectionBottomBar));
    expect(bar().allSelected, isFalse);
    bar().onToggleSelectAll();
    await tester.pumpAndSettle();
    _expectSelection(tester, {0, 1, 2, 3, 4});
    expect(bar().allSelected, isTrue);
    bar().onToggleSelectAll();
    await tester.pumpAndSettle();
    _expectSelection(tester, {});
    expect(bar().allSelected, isFalse);
    await _disposeArtist(tester);
  });
}
