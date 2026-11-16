import 'dart:ui' show PointerDeviceKind;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_tracks_folder_screen.dart';
import 'package:sistrum/screens/playlist_screen.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/collection_reorder_sliver.dart';

import 'support/fixed_settings.dart';

final _entries = [
  for (final (id, name) in [('a', 'Alpha'), ('b', 'Beta'), ('c', 'Gamma')])
    CollectionTrackEntry(
      key: id,
      addedAt: DateTime(2026),
      track: Track(
        id: id,
        name: name,
        artistName: 'Artist',
        albumName: 'Album',
        duration: 180,
      ),
    ),
];

/// Records the edits the page asks for and applies them to its state, as the
/// real notifier does.
class _Collections extends LibraryCollectionsNotifier {
  _Collections(this._initial);

  final LibraryCollectionsState _initial;
  final calls = <String>[];

  @override
  LibraryCollectionsState build() => _initial;

  @override
  Future<void> ensurePlaylistLoaded(String id) async {}

  @override
  Future<void> renamePlaylist(String playlistId, String newName) async {
    calls.add('rename:$newName');
    final playlist = state.playlistById(playlistId)!;
    state = state.copyWith(playlists: [playlist.copyWith(name: newName)]);
  }

  @override
  Future<bool> reorderPlaylistTracks(String id, List<String> keys) async {
    calls.add('reorder:${keys.join(',')}');
    final playlist = state.playlistById(id)!;
    final byKey = {for (final entry in playlist.tracks) entry.key: entry};
    state = state.copyWith(
      playlists: [
        playlist.copyWith(tracks: [for (final key in keys) byKey[key]!]),
      ],
    );
    return true;
  }

  @override
  Future<int> removeTracksFromPlaylist(String id, Iterable<String> keys) async {
    calls.add('remove-playlist:${(keys.toList()..sort()).join(',')}');
    final playlist = state.playlistById(id)!;
    final selected = keys.toSet();
    state = state.copyWith(
      playlists: [
        playlist.copyWith(
          tracks: [
            for (final entry in playlist.tracks)
              if (!selected.contains(entry.key)) entry,
          ],
        ),
      ],
    );
    return selected.length;
  }

  @override
  Future<int> removeLovedTracks(Iterable<String> keys) async {
    calls.add('remove-loved:${(keys.toList()..sort()).join(',')}');
    final selected = keys.toSet();
    state = state.copyWith(
      loved: [
        for (final entry in state.loved)
          if (!selected.contains(entry.key)) entry,
      ],
    );
    return selected.length;
  }
}

class _Library extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

UserPlaylistCollection _playlist({
  List<CollectionTrackEntry> tracks = const [],
  String? coverImagePath,
}) => UserPlaylistCollection(
  id: 'mix',
  name: 'My mix',
  createdAt: DateTime(2026),
  updatedAt: DateTime(2026),
  tracks: tracks,
  coverImagePath: coverImagePath,
);

Future<ProviderContainer> _pump(
  WidgetTester tester,
  LibraryCollectionsState initial,
  Widget page,
) async {
  tester.view.physicalSize = const Size(430, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final container = ProviderContainer(
    overrides: [
      settingsProvider.overrideWith(FixedSettingsNotifier.new),
      libraryCollectionsProvider.overrideWith(() => _Collections(initial)),
      libraryPlaylistEntriesProvider.overrideWith(
        (ref, id) async =>
            ref.watch(libraryCollectionsProvider).playlistById(id)?.tracks ??
            const [],
      ),
      libraryLovedEntriesProvider.overrideWith(
        (ref) async => ref.watch(libraryCollectionsProvider).loved,
      ),
      localLibraryProvider.overrideWith(_Library.new),
      downloadHistoryProvider.overrideWith(_History.new),
      downloadHistoryVisibleBatchExistsProvider.overrideWith(
        (ref, request) => const {},
      ),
      localLibraryCoverProvider.overrideWith((ref, request) async => null),
      localLibraryFirstCoverProvider.overrideWith((ref, request) async => null),
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
        home: page,
      ),
    ),
  );
  await tester.pumpAndSettle();
  return container;
}

_Collections _collections(ProviderContainer container) =>
    container.read(libraryCollectionsProvider.notifier) as _Collections;

List<String> _rowTitles(WidgetTester tester) => [
  for (final tile in tester.widgetList<AlbumTrackTile>(
    find.byType(AlbumTrackTile),
  ))
    tile.trackName,
];

Future<void> _moveFirstToLast(WidgetTester tester) async {
  final handle = find.byKey(const ValueKey('collection-order-handle-a'));
  await tester.ensureVisible(handle);
  await tester.pumpAndSettle();
  final origin = tester.getCenter(handle);
  final lastBottom = tester.getBottomRight(
    find.byKey(const ValueKey('collection-order-handle-c')),
  );
  final gesture = await tester.startGesture(
    origin,
    kind: PointerDeviceKind.touch,
  );
  await gesture.moveBy(const Offset(0, 4));
  await tester.pump(const Duration(milliseconds: 100));
  await gesture.moveTo(Offset(origin.dx, lastBottom.dy + 80));
  await tester.pump(const Duration(milliseconds: 500));
  await gesture.up();
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('a library playlist uses the album design: compact Play and '
      'Shuffle, no Download, rows with title and artist', (tester) async {
    await _pump(
      tester,
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [_playlist(tracks: _entries)],
      ),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    final l10n = AppLocalizations.of(
      tester.element(find.byType(Scaffold).first),
    );

    expect(find.text('My mix'), findsWidgets);
    expect(find.text(l10n.tooltipPlay), findsOneWidget);
    expect(find.byTooltip(l10n.actionShuffle), findsOneWidget);
    expect(find.byKey(const ValueKey('collection-download-all')), findsNothing);
    expect(find.byIcon(Icons.download_rounded), findsNothing);
    expect(_rowTitles(tester), ['Alpha', 'Beta', 'Gamma']);
    expect(find.text('Artist'), findsNWidgets(3));
    expect(find.byTooltip(l10n.collectionRenamePlaylist), findsOneWidget);
    expect(find.byTooltip(l10n.collectionPlaylistChangeCover), findsOneWidget);
    expect(find.byTooltip(l10n.collectionExportM3u), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Loved songs open the same design without playlist-only '
      'actions, and an empty Loved shows its empty state', (tester) async {
    await _pump(
      tester,
      LibraryCollectionsState(isLoaded: true, loved: _entries),
      const PlaylistScreen.libraryLoved(),
    );
    final l10n = AppLocalizations.of(
      tester.element(find.byType(Scaffold).first),
    );
    expect(_rowTitles(tester), ['Alpha', 'Beta', 'Gamma']);
    expect(find.byTooltip(l10n.collectionRenamePlaylist), findsNothing);
    expect(find.byTooltip(l10n.collectionPlaylistChangeCover), findsNothing);
    expect(find.byTooltip(l10n.collectionExportM3u), findsOneWidget);
    expect(find.byTooltip(l10n.actionShuffle), findsOneWidget);
  });

  testWidgets('an empty playlist shows its empty state', (tester) async {
    await _pump(
      tester,
      LibraryCollectionsState(isLoaded: true, playlists: [_playlist()]),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    expect(find.text('Playlist is empty'), findsOneWidget);
    expect(find.byType(AlbumTrackTile), findsNothing);
  });

  testWidgets('an empty Loved shows its empty state', (tester) async {
    await _pump(
      tester,
      LibraryCollectionsState(isLoaded: true),
      const PlaylistScreen.libraryLoved(),
    );
    expect(find.text('Loved folder is empty'), findsOneWidget);
    expect(find.byType(AlbumTrackTile), findsNothing);
  });

  testWidgets('an edit made elsewhere, such as adding a song, refreshes the '
      'rows', (tester) async {
    final container = await _pump(
      tester,
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [_playlist(tracks: _entries)],
      ),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    final playlist = container
        .read(libraryCollectionsProvider)
        .playlistById('mix')!;
    _collections(container).state = container
        .read(libraryCollectionsProvider)
        .copyWith(
          playlists: [
            playlist.copyWith(
              tracks: [
                ...playlist.tracks,
                CollectionTrackEntry(
                  key: 'd',
                  addedAt: DateTime(2026),
                  track: const Track(
                    id: 'd',
                    name: 'Delta',
                    artistName: 'Artist',
                    albumName: 'Album',
                    duration: 180,
                  ),
                ),
              ],
            ),
          ],
        );
    await tester.pumpAndSettle();

    expect(_rowTitles(tester), ['Alpha', 'Beta', 'Gamma', 'Delta']);
  });

  testWidgets('rename saves through the collections and shows the new name', (
    tester,
  ) async {
    final container = await _pump(
      tester,
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [_playlist(tracks: _entries)],
      ),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    final l10n = AppLocalizations.of(
      tester.element(find.byType(Scaffold).first),
    );
    await tester.tap(find.byTooltip(l10n.collectionRenamePlaylist));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Renamed mix');
    await tester.tap(find.text(l10n.dialogSave));
    await tester.pumpAndSettle();

    expect(_collections(container).calls, ['rename:Renamed mix']);
    expect(find.text('Renamed mix'), findsWidgets);
    expect(find.text(l10n.collectionPlaylistRenamed), findsOneWidget);
  });

  testWidgets('the cover sheet offers removal only for a custom cover', (
    tester,
  ) async {
    final playlist = _playlist(
      tracks: _entries,
      coverImagePath: '/covers/x.jpg',
    );
    await _pump(
      tester,
      LibraryCollectionsState(isLoaded: true, playlists: [playlist]),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    final l10n = AppLocalizations.of(
      tester.element(find.byType(Scaffold).first),
    );
    await tester.tap(find.byTooltip(l10n.collectionPlaylistChangeCover));
    await tester.pumpAndSettle();
    expect(find.text(l10n.collectionPlaylistRemoveCover), findsOneWidget);
  });

  testWidgets('reorder saves the new order and the rows follow it', (
    tester,
  ) async {
    final container = await _pump(
      tester,
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [_playlist(tracks: _entries)],
      ),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    await tester.tap(find.byKey(const ValueKey('collection-edit-order')));
    await tester.pumpAndSettle();
    expect(find.byType(CollectionReorderSliver), findsOneWidget);
    await _moveFirstToLast(tester);
    await tester.tap(find.byKey(const ValueKey('collection-order-save')));
    await tester.pumpAndSettle();

    expect(_collections(container).calls, ['reorder:b,c,a']);
    expect(find.byType(CollectionReorderSliver), findsNothing);
    expect(_rowTitles(tester), ['Beta', 'Gamma', 'Alpha']);
  });

  for (final (name, page, state, removed) in [
    (
      'playlist',
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [
          UserPlaylistCollection(
            id: 'mix',
            name: 'My mix',
            createdAt: DateTime(2026),
            updatedAt: DateTime(2026),
            tracks: _entries,
          ),
        ],
      ),
      'remove-playlist:a,b',
    ),
    (
      'Loved',
      const PlaylistScreen.libraryLoved(),
      LibraryCollectionsState(isLoaded: true, loved: _entries),
      'remove-loved:a,b',
    ),
  ]) {
    testWidgets('$name removal of the selection refreshes the rows', (
      tester,
    ) async {
      final container = await _pump(tester, state, page);
      await tester.longPress(find.text('Alpha'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Beta'));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.remove_circle_outline));
      await tester.pumpAndSettle();

      expect(_collections(container).calls, [removed]);
      expect(_rowTitles(tester), ['Gamma']);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('the Wishlist stays a download list with its own actions', (
    tester,
  ) async {
    await _pump(
      tester,
      LibraryCollectionsState(isLoaded: true, wishlist: _entries),
      const LibraryTracksFolderScreen(),
    );
    expect(find.byType(LibraryTracksFolderScreen), findsOneWidget);
    expect(
      find.byKey(const ValueKey('collection-download-all')),
      findsOneWidget,
    );
    expect(find.text('Alpha'), findsOneWidget);
    expect(find.byType(PlaylistScreen), findsNothing);
  });
  testWidgets('back during Edit order with a changed order asks to discard '
      'it and stays on the page', (tester) async {
    final container = await _pump(
      tester,
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [_playlist(tracks: _entries)],
      ),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    await tester.tap(find.byKey(const ValueKey('collection-edit-order')));
    await tester.pumpAndSettle();
    await _moveFirstToLast(tester);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.text('Discard Changes?'), findsOneWidget);
    expect(find.byType(CollectionReorderSliver), findsOneWidget);
    expect(find.byType(PlaylistScreen), findsOneWidget);
    expect(_collections(container).calls, isEmpty);
  });

  testWidgets('back during Edit order with an unchanged order leaves edit '
      'mode and stays on the page', (tester) async {
    await _pump(
      tester,
      LibraryCollectionsState(
        isLoaded: true,
        playlists: [_playlist(tracks: _entries)],
      ),
      const PlaylistScreen.libraryPlaylist(libraryPlaylistId: 'mix'),
    );
    await tester.tap(find.byKey(const ValueKey('collection-edit-order')));
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.text('Discard Changes?'), findsNothing);
    expect(find.byType(CollectionReorderSliver), findsNothing);
    expect(find.byType(PlaylistScreen), findsOneWidget);
  });
}
