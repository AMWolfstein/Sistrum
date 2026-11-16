import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/disc_separator_chip.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';

import 'support/fixed_settings.dart';

// Ported from the online album page's disc groups: the library album shows a
// separator per disc and keeps the rows selectable.
class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

void main() {
  final albumKey = LibraryDatabase.albumKeyFor(
    'Example Album',
    null,
    'Example Artist',
  );

  for (final discs in [1, 2]) {
    testWidgets('library album disc groups (discs: $discs)', (tester) async {
      SharedPreferences.setMockInitialValues({});
      await tester.binding.setSurfaceSize(const Size(430, 1200));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final container = ProviderContainer(
        overrides: [
          settingsProvider.overrideWith(FixedSettingsNotifier.new),
          downloadHistoryProvider.overrideWith(_History.new),
          libraryAlbumItemsProvider(albumKey).overrideWith(
            (ref) async => [
              for (var index = 0; index < 2; index++)
                UnifiedLibraryItem.fromDownloadHistory(
                  DownloadHistoryItem(
                    id: 'song-$index',
                    trackName: 'Song $index',
                    artistName: 'Example Artist',
                    albumName: 'Example Album',
                    filePath: '/music/song-$index.flac',
                    service: 'example-provider',
                    downloadedAt: DateTime.utc(2026),
                    discNumber: index == 0 ? 1 : discs,
                    totalDiscs: discs,
                    trackNumber: discs == 1 ? index + 1 : 1,
                    duration: 180,
                  ),
                ),
            ],
          ),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(null),
          ),
          playbackPlayingProvider.overrideWith((ref) => false),
          playbackLoadingProvider.overrideWith((ref) => false),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: SelectionOverlayHost(
              child: AlbumScreen.library(albumKey: albumKey),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      if (discs == 1) {
        expect(find.byType(DiscSeparatorChip), findsNothing);
      } else {
        final headers = tester.widgetList<DiscSeparatorChip>(
          find.byType(DiscSeparatorChip),
        );
        expect(headers.map((header) => header.discNumber), [1, 2]);
        expect(
          tester.getTopLeft(find.text('Disc 2')).dy,
          inExclusiveRange(
            tester.getBottomLeft(find.text('Song 0')).dy,
            tester.getTopLeft(find.text('Song 1')).dy,
          ),
        );
      }
      await tester.longPress(find.text('Song 1'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widgetList<AlbumTrackTile>(find.byType(AlbumTrackTile))
            .map((tile) => tile.isSelected),
        [false, true],
      );
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
    });
  }
}
