import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/models/track.dart';
import 'package:mornye/providers/track_provider.dart';
import 'package:mornye/screens/artist_screen.dart';
import 'package:mornye/theme/app_theme.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/animation_utils.dart';
import 'package:mornye/widgets/selection_bottom_bar.dart';
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
  required bool mornye,
  List<ArtistAlbum>? albums,
}) async {
  SharedPreferences.setMockInitialValues({});
  await tester.binding.setSurfaceSize(const Size(1200, 1800));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  const channel = MethodChannel('com.zarz.mornye/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(channel, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        theme: mornye ? MornyeTheme.build(Brightness.light) : AppTheme.light(),
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
  tearDown(() {
  });

  for (final mornye in [false, true]) {
    final style = mornye ? 'Mornye' : 'Material';


testWidgets('$style: touch long press still selects albums', (
      tester,
    ) async {
      await _pumpArtist(tester, mornye: mornye);
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
      await _pumpArtist(tester, mornye: mornye);
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
}
