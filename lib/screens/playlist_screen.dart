import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/collection_header_actions.dart';
import 'package:sistrum/controllers/selection_mode_mixin.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/utils/image_cache_utils.dart';
import 'package:sistrum/utils/cover_art_utils.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';
import 'package:sistrum/controllers/collapsing_header_scroll_mixin.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';
import 'package:sistrum/controllers/collection_order_editing.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/collection_reorder_sliver.dart';
import 'package:sistrum/widgets/collection_track_actions.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/network_offline_actions.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/mornye_icons.dart';

part 'playlist_screen_library.dart';

/// A library playlist or the Loved songs, edited in the library's collections.
class PlaylistScreen extends ConsumerStatefulWidget {
  /// The library playlist's id; null for the Loved songs.
  final String? libraryPlaylistId;

  /// Set for the library's Loved songs.
  final bool libraryLoved;

  const PlaylistScreen._library({
    super.key,
    this.libraryPlaylistId,
    required this.libraryLoved,
  });

  /// A library playlist, edited in the library's collections.
  const PlaylistScreen.libraryPlaylist({
    Key? key,
    required String libraryPlaylistId,
  }) : this._library(
         key: key,
         libraryPlaylistId: libraryPlaylistId,
         libraryLoved: false,
       );

  /// The library's Loved songs, edited in the library's collections.
  const PlaylistScreen.libraryLoved({Key? key})
    : this._library(key: key, libraryLoved: true);

  @override
  ConsumerState<PlaylistScreen> createState() => _PlaylistScreenState();
}

class _PlaylistScreenState extends ConsumerState<PlaylistScreen>
    with
        SelectionModeMixin<PlaylistScreen>,
        CollapsingHeaderScrollMixin<PlaylistScreen>,
        CollectionOrderEditing<PlaylistScreen>,
        CollectionTrackActions<PlaylistScreen> {
  @override
  void leaveSelectionAfterTrackAction() => exitSelectionMode();

  /// Library mode stores the order in the library's collections.
  @override
  Future<bool> saveOrderKeys(List<String> orderedKeys) {
    final notifier = ref.read(libraryCollectionsProvider.notifier);
    final playlistId = widget.libraryPlaylistId;
    return playlistId == null
        ? notifier.reorderSavedTracks(
            LibraryTracksFolderMode.loved,
            orderedKeys,
          )
        : notifier.reorderPlaylistTracks(playlistId, orderedKeys);
  }

  @override
  Widget build(BuildContext context) => _buildLibrary(context);
}
