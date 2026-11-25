import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/providers/persisted_sort_choices.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _log = AppLogger('AlbumTrackSort');

enum AlbumTrackSort { album, mostPlayed, custom }

/// One album of the Library, kept apart from a same-named album by another
/// artist.
typedef AlbumTrackSortKey = ({String album, String artist});

/// The scope of [key] in the album stores, spelled the same across pages so
/// that a difference in case or padding does not split one album.
String albumTrackSortScope(AlbumTrackSortKey key) =>
    '${key.album.trim().toLowerCase()}|${key.artist.trim().toLowerCase()}';

/// The sort each album chose. Album order is the fallback. Its key is new: the
/// sorts saved under the earlier combined album key are not migrated, while
/// their custom orders are still read by [AlbumTrackCustomOrders].
final albumTrackSortsProvider =
    NotifierProvider<SortChoices<AlbumTrackSort>, Map<String, AlbumTrackSort>>(
      () => SortChoices(
        const SortChoiceStore(
          prefsKey: 'album_track_sorts',
          values: AlbumTrackSort.values,
          fallback: AlbumTrackSort.album,
        ),
      ),
    );

/// The sort [key]'s album has chosen, album order when unset.
final albumTrackSortProvider = Provider.autoDispose
    .family<AlbumTrackSort, AlbumTrackSortKey>(
      (ref, key) => ref.watch(
        albumTrackSortsProvider.select(
          (sorts) => sortChoiceIn(
            sorts,
            albumTrackSortScope(key),
            AlbumTrackSort.album,
          ),
        ),
      ),
    );

/// The saved custom order of each album, as [albumTrackRankKey]s. It is kept
/// apart from the sort, so a switch to another sort keeps it.
class AlbumTrackCustomOrders extends Notifier<Map<String, List<String>>> {
  // Keeps the key the album orders were saved under, so custom orders survive.
  static const _prefsKey = 'album_track_choices';
  Future<void>? _loading;

  @override
  Map<String, List<String>> build() {
    _loading = _load();
    return const {};
  }

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = jsonDecode(prefs.getString(_prefsKey) ?? '{}');
      if (json is! Map || json.isEmpty || !ref.mounted) return;
      state = {
        for (final entry in json.entries)
          if (entry.key is String && entry.value is Map)
            entry.key as String: [
              for (final key
                  in (entry.value as Map)['custom'] as List? ?? const [])
                if (key is String) key,
            ],
        ...state,
      };
    } catch (error) {
      _log.w('Ignoring saved album orders: ${error.runtimeType}');
    }
  }

  /// [key]'s custom order, empty when unset.
  List<String> of(AlbumTrackSortKey key) =>
      state[albumTrackSortScope(key)] ?? const [];

  /// Applies [songs], in their new order, as the album's custom order.
  void setCustom(AlbumTrackSortKey key, List<String> songs) {
    state = {
      ...state,
      albumTrackSortScope(key): [
        for (final song in songs) albumTrackRankKey(song),
      ],
    };
    unawaited(_save());
  }

  Future<void> _save() async {
    try {
      await _loading;
      if (!ref.mounted) return;
      final saved = jsonEncode({
        for (final entry in state.entries) entry.key: {'custom': entry.value},
      });
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_prefsKey, saved);
    } catch (error) {
      _log.w('Could not save the album order: ${error.runtimeType}');
    }
  }
}

final albumTrackCustomOrdersProvider =
    NotifierProvider<AlbumTrackCustomOrders, Map<String, List<String>>>(
      AlbumTrackCustomOrders.new,
    );

/// Plays per song of an album, read once each time the album opens so that
/// a song ending does not reorder the page under the listener.
final albumTrackPlaysProvider = FutureProvider.autoDispose
    .family<Map<String, int>, String>((ref, album) async {
      await listeningRecorder.flush();
      return ListeningStatisticsStore.instance.loadAlbumPlays(album);
    });

/// The ranks for [AlbumTrackOrder], or null for album order.
final albumTrackRanksProvider = Provider.autoDispose
    .family<Map<String, int>?, AlbumTrackSortKey>((ref, key) {
      final scope = albumTrackSortScope(key);
      switch (ref.watch(albumTrackSortProvider(key))) {
        case AlbumTrackSort.album:
          return null;
        case AlbumTrackSort.mostPlayed:
          final plays = ref.watch(albumTrackPlaysProvider(key.album)).value;
          return plays == null ? null : albumTrackPlayRanks(plays);
        case AlbumTrackSort.custom:
          final custom = ref.watch(
            albumTrackCustomOrdersProvider.select(
              (orders) => orders[scope] ?? const <String>[],
            ),
          );
          return albumTrackCustomRanks(custom);
      }
    });
