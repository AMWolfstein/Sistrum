import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/playlist_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';

import 'support/fixed_settings.dart';
import 'support/re_enrich_settings.dart';
import 'package:sistrum/providers/download_history_provider.dart';

final _tracks = [
  for (var i = 0; i < 4; i++)
    Track(
      id: 'example-track-$i',
      name: 'Example song $i',
      artistName: 'Example artist',
      albumName: 'Example album',
      duration: 180,
      trackNumber: i + 1,
    ),
];

final _albumKey = LibraryDatabase.albumKeyFor(
  'Example album',
  null,
  'Example artist',
);

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    playlists: [
      UserPlaylistCollection(
        id: 'example-playlist',
        name: 'Example playlist',
        createdAt: DateTime(2026),
        updatedAt: DateTime(2026),
        tracks: [
          for (final track in _tracks)
            CollectionTrackEntry(
              key: track.id,
              track: track,
              addedAt: DateTime(2026),
            ),
        ],
      ),
    ],
  );

  @override
  Future<void> ensurePlaylistLoaded(String playlistId) async {}
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

void main() {
  for (final album in [true, false]) {
    final page = album ? 'album' : 'playlist';
    testWidgets(
      'library $page select all toggle selects every track, then exits',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        tester.view.physicalSize = const Size(430, 1000);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final container = ProviderContainer(
          overrides: [
            settingsProvider.overrideWith(
              album ? ReEnrichTestSettings.new : FixedSettingsNotifier.new,
            ),
            libraryCollectionsProvider.overrideWith(_Collections.new),
            downloadHistoryProvider.overrideWith(_History.new),
            libraryAlbumItemsProvider(_albumKey).overrideWith(
              (ref) async => [
                for (final (index, track) in _tracks.indexed)
                  UnifiedLibraryItem.fromDownloadHistory(
                    DownloadHistoryItem(
                      id: track.id,
                      trackName: track.name,
                      artistName: track.artistName,
                      albumName: track.albumName,
                      filePath: '/music/${track.id}.flac',
                      service: 'example-provider',
                      downloadedAt: DateTime(2026),
                      trackNumber: index + 1,
                      duration: 180,
                    ),
                  ),
              ],
            ),
            libraryPlaylistEntriesProvider('example-playlist').overrideWith(
              (ref) async => [
                for (final track in _tracks)
                  CollectionTrackEntry(
                    key: track.id,
                    track: track,
                    addedAt: DateTime(2026),
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
              theme: MornyeTheme.build(Brightness.light),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: album
                  ? SelectionOverlayHost(
                      child: AlbumScreen.library(albumKey: _albumKey),
                    )
                  : const PlaylistScreen.libraryPlaylist(
                      libraryPlaylistId: 'example-playlist',
                    ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.longPress(find.text('Example song 1'));
        await tester.pumpAndSettle();

        // The album shows its own bar; the playlist uses the shared one.
        ({int selectedCount, bool allSelected, VoidCallback toggleAll}) bar() {
          if (album) {
            final bar = tester.widget<LibraryTrackSelectionBar>(
              find.byType(LibraryTrackSelectionBar),
            );
            return (
              selectedCount: bar.selectedCount,
              allSelected: bar.allSelected,
              toggleAll: bar.onToggleSelectAll,
            );
          }
          final bar = tester.widget<SelectionBottomBar>(
            find.byType(SelectionBottomBar),
          );
          return (
            selectedCount: bar.selectedCount,
            allSelected: bar.allSelected,
            toggleAll: bar.onToggleSelectAll,
          );
        }

        List<bool> selected() => [
          for (final tile in tester.widgetList<AlbumTrackTile>(
            find.byType(AlbumTrackTile),
          ))
            tile.isSelected,
        ];
        expect(bar().selectedCount, 1);
        expect(bar().allSelected, isFalse);
        bar().toggleAll();
        await tester.pumpAndSettle();
        expect(bar().selectedCount, 4);
        expect(bar().allSelected, isTrue);
        expect(selected(), [true, true, true, true]);
        bar().toggleAll();
        await tester.pumpAndSettle();
        expect(selected(), [false, false, false, false]);
        expect(
          tester
              .widgetList<AlbumTrackTile>(find.byType(AlbumTrackTile))
              .any((tile) => tile.isSelectionMode),
          isFalse,
        );
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
}
