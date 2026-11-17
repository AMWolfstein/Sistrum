import 'dart:math';
import 'package:flutter/material.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/image_cache_utils.dart';
import 'package:sistrum/controllers/collapsing_header_scroll_mixin.dart';
import 'package:sistrum/controllers/selection_mode_mixin.dart';
import 'package:sistrum/widgets/error_card.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/collection_header_actions.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';
import 'package:sistrum/widgets/disc_separator_chip.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';
import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/controllers/confirm_and_delete_tracks.dart';
import 'package:sistrum/controllers/batch_track_actions.dart';
import 'package:sistrum/controllers/local_track_batch_actions.dart';
import 'package:sistrum/widgets/track_lists/context_menu.dart';
import 'package:sistrum/widgets/track_lists/library_track_menu.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/utils/audio_quality_badge_policy.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/library_selection_playback_actions.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/local_library_artwork_builder.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/models/download_history.dart';

part 'album_screen_library.dart';

/// A library album: the songs of [albumKey] from the library's own files.
class AlbumScreen extends ConsumerStatefulWidget {
  final String albumKey;

  const AlbumScreen.library({super.key, required this.albumKey});

  @override
  ConsumerState<AlbumScreen> createState() => _AlbumScreenState();
}

class _AlbumScreenState extends ConsumerState<AlbumScreen>
    with
        SelectionModeMixin<AlbumScreen>,
        CollapsingHeaderScrollMixin<AlbumScreen> {
  bool _embeddedCoverRefreshScheduled = false;

  void _onEmbeddedCoverChanged() {
    if (!mounted || _embeddedCoverRefreshScheduled) return;
    _embeddedCoverRefreshScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _embeddedCoverRefreshScheduled = false;
      if (mounted) setState(() {});
    });
  }

  static final _releaseYearPattern = RegExp(r'(\d{4})');

  String? _releaseYear(String? date) {
    if (date == null || date.isEmpty) return null;
    final match = _releaseYearPattern.firstMatch(date);
    return match?.group(1);
  }

  @override
  Widget build(BuildContext context) => _buildLibrary(context);
}
