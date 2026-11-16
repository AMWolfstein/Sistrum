import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/library_collections.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_tag_credits_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/library_collections_database.dart';
import 'package:sistrum/services/library_detail_data.dart';

/// The library artist's albums and singles, and the newest dated one.
final libraryArtistOverviewProvider = FutureProvider.autoDispose
    .family<LibraryArtistOverview, String>(
      (ref, artist) => loadLibraryArtistOverview(
        artist,
        credits: ref.watch(tagCreditScopeProvider),
        includeLocal: ref.watch(
          settingsProvider.select((s) => s.localLibraryEnabled),
        ),
      ),
    );

/// Every song that credits the library artist, in library order.
final libraryArtistTracksProvider = FutureProvider.autoDispose
    .family<List<Track>, String>(
      (ref, artist) => loadLibraryArtistTracks(
        artist,
        credits: ref.watch(tagCreditScopeProvider),
        includeLocal: ref.watch(
          settingsProvider.select((s) => s.localLibraryEnabled),
        ),
      ),
    );

/// The songs where the library artist is featured, for Compilations.
final libraryArtistFeaturedTracksProvider = FutureProvider.autoDispose
    .family<List<Track>, String>(
      (ref, artist) => loadLibraryArtistFeaturedTracks(
        artist,
        credits: ref.watch(tagCreditScopeProvider),
        includeLocal: ref.watch(
          settingsProvider.select((s) => s.localLibraryEnabled),
        ),
      ),
    );

/// The library artist's Popular songs: most played, filled up at random.
final libraryArtistPopularTracksProvider = FutureProvider.autoDispose
    .family<List<Track>, String>((ref, artist) {
      final settings = ref.watch(
        settingsProvider.select(
          (s) => (
            includeLocal: s.localLibraryEnabled,
            useStatistics: s.listeningStatisticsEnabled,
          ),
        ),
      );
      return loadLibraryArtistPopularTracks(
        artist,
        credits: ref.watch(tagCreditScopeProvider),
        includeLocal: settings.includeLocal,
        useStatistics: settings.useStatistics,
      );
    });

/// Every genre of the library, sorted by name. The credits version covers the
/// index changes that the songs of each genre depend on.
final libraryGenresProvider =
    FutureProvider.autoDispose<List<LibraryGenreEntry>>(
      (ref) => loadLibraryGenres(
        credits: ref.watch(tagCreditScopeProvider),
        includeLocal: ref.watch(
          settingsProvider.select((s) => s.localLibraryEnabled),
        ),
      ),
    );

/// The songs of one library genre, downloaded and scanned alike.
final libraryGenreItemsProvider = FutureProvider.autoDispose
    .family<List<UnifiedLibraryItem>, String>(
      (ref, genreKey) => loadLibraryGenreItems(
        genreKey,
        credits: ref.watch(tagCreditScopeProvider),
        includeLocal: ref.watch(
          settingsProvider.select((s) => s.localLibraryEnabled),
        ),
      ),
    );

/// The songs of a library album by album key, downloaded and scanned alike.
final libraryAlbumItemsProvider = FutureProvider.autoDispose
    .family<List<UnifiedLibraryItem>, String>((ref, albumKey) {
      // Reloads when the library index changes, as the credits do.
      ref.watch(tagCreditScopeProvider.select((scope) => scope.version));
      ref.watch(
        downloadHistoryProvider.select((state) => state.loadedIndexVersion),
      );
      return loadLibraryAlbumItems(
        albumKey,
        includeLocal: ref.watch(
          settingsProvider.select((s) => s.localLibraryEnabled),
        ),
      );
    });

final libraryAlbumTracksProvider = FutureProvider.autoDispose
    .family<List<Track>, String>(
      (ref, albumKey) async => [
        for (final item in await ref.watch(
          libraryAlbumItemsProvider(albumKey).future,
        ))
          item.toTrack(),
      ],
    );

/// Every song of a playlist in playlist order, with the keys that edits need.
/// shortcut: -1 is SQLite's no limit, so long playlists are not cut short.
/// Edits refresh it through the page, which watches the collections.
final libraryPlaylistEntriesProvider = FutureProvider.autoDispose
    .family<List<CollectionTrackEntry>, String>(
      (ref, playlistId) => LibraryCollectionsDatabase.instance
          .loadPlaylistEntries(playlistId, limit: -1),
    );

/// Every loved song, most recently loved first, with its keys.
final libraryLovedEntriesProvider =
    FutureProvider.autoDispose<List<CollectionTrackEntry>>(
      (ref) => LibraryCollectionsDatabase.instance.loadLovedEntries(limit: -1),
    );
