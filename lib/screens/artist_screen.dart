import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/widgets/artist_featured_album_card.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/utils/artist_album_sort.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/utils/provider_resource_ids.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/widgets/error_card.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/widgets/track_lists/context_menu.dart';
import 'package:sistrum/widgets/track_lists/library_track_menu.dart';
import 'package:sistrum/widgets/artist_popular_carousel.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/controllers/batch_track_actions.dart';
import 'package:sistrum/controllers/local_track_batch_actions.dart';

part 'artist_screen_widgets.dart';
part 'artist_screen_library.dart';

/// A library artist: its albums and songs come from the library's own files.
class ArtistScreen extends ConsumerStatefulWidget {
  final String artistName;

  const ArtistScreen({super.key, required this.artistName});

  @override
  ConsumerState<ArtistScreen> createState() => _ArtistScreenState();
}

class _ArtistScreenState extends ConsumerState<ArtistScreen> {
  final ScrollController _scrollController = ScrollController();
  List<ArtistAlbum>? _albumBucketSource;
  List<ArtistAlbum> _albumsOnlyBucket = const [];
  List<ArtistAlbum> _singlesBucket = const [];

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _ensureAlbumBuckets(List<ArtistAlbum> albums) {
    if (identical(albums, _albumBucketSource)) return;
    _albumBucketSource = albums;
    _albumsOnlyBucket = albums
        .where((a) => a.albumType == 'album')
        .toList(growable: false);
    _singlesBucket = albums
        .where((a) => a.albumType == 'single' || a.albumType == 'ep')
        .toList(growable: false);
  }

  @override
  Widget build(BuildContext context) => _buildLibraryArtist(context);
}
