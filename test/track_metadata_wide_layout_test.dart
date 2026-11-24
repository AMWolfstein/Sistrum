import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';

void main() {
  for (final (width, twoColumns) in [(1400.0, true), (800.0, false)]) {
    testWidgets('metadata cards at ${width.toInt()} wide use '
        '${twoColumns ? 'two columns' : 'one column'}', (tester) async {
      tester.view.physicalSize = Size(width, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final item = LocalLibraryItem(
        id: '/music/track.flac',
        filePath: '/music/track.flac',
        trackName: 'Track',
        artistName: 'Artist',
        albumName: 'Album',
        scannedAt: DateTime(2026),
      );
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: TrackMetadataScreen(localItem: item),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      final metadata = tester.getTopLeft(find.text('Metadata'));
      final lyrics = tester.getTopLeft(find.text('Lyrics'));
      if (twoColumns) {
        expect(lyrics.dx, greaterThan(metadata.dx + 400));
        expect(lyrics.dy, moreOrLessEquals(metadata.dy, epsilon: 1));
      } else {
        expect(lyrics.dx, moreOrLessEquals(metadata.dx, epsilon: 1));
        expect(lyrics.dy, greaterThan(metadata.dy));
      }
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    });
  }
}
