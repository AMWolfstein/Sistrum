import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/utils/isrc_utils.dart';
import 'package:sistrum/widgets/mornye_player_details_sheet.dart';

Future<void> showPlayerDetailsSheet(
  BuildContext context, {
  required PlayerMetadataController metadata,
  required MediaItem? mediaItem,
}) {
  final fallback = metadata.detailsSnapshot(mediaItem);
  final metadataFuture = metadata.readDetails(mediaItem);
  Widget details(ScrollController scrollController) =>
      FutureBuilder<Map<String, dynamic>>(
        future: metadataFuture,
        initialData: fallback,
        builder: (context, snapshot) => _MetadataList(
          meta: snapshot.data ?? fallback,
          scrollController: scrollController,
          mediaItem: mediaItem,
        ),
      );
  // The Mornye details draw their own glass, handle and header.
  return showModalBottomSheet<void>(
    context: context,
    sheetAnimationStyle: AppMotion.surfaceEntrance(context),
    isScrollControlled: true,
    useRootNavigator: true,
    backgroundColor: Colors.transparent,
    builder: (context) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      maxChildSize: 0.9,
      minChildSize: 0.4,
      builder: (context, scrollController) => details(scrollController),
    ),
  );
}

class _MetadataList extends StatelessWidget {
  final Map<String, dynamic> meta;
  final ScrollController scrollController;
  final MediaItem? mediaItem;

  const _MetadataList({
    required this.meta,
    required this.scrollController,
    this.mediaItem,
  });

  @override
  Widget build(BuildContext context) {
    String s(Object? v) => (v ?? '').toString();
    final l10n = context.l10n;
    final rows = <(String, String)>[
      (l10n.editMetadataFieldTitle, s(meta['title'])),
      (l10n.editMetadataFieldArtist, s(meta['artist'])),
      (l10n.editMetadataFieldAlbum, s(meta['album'])),
      (l10n.editMetadataFieldAlbumArtist, s(meta['album_artist'])),
      (l10n.editMetadataFieldGenre, s(meta['genre'])),
      (l10n.editMetadataFieldComposer, s(meta['composer'])),
      (l10n.editMetadataFieldDate, s(meta['date'])),
      (l10n.editMetadataFieldTrackNum, s(meta['track_number'])),
      (l10n.editMetadataFieldDiscNum, s(meta['disc_number'])),
      (l10n.editMetadataFieldIsrc, formatIsrcForDisplay(s(meta['isrc']))),
      (l10n.editMetadataFieldLabel, s(meta['label'])),
      (l10n.editMetadataFieldCopyright, s(meta['copyright'])),
      (l10n.libraryFilterFormat, s(meta['format']).toUpperCase()),
      (l10n.audioAnalysisCodec, s(meta['audio_codec'])),
      (
        l10n.audioAnalysisSampleRate,
        meta['sample_rate'] != null && (meta['sample_rate'] as num? ?? 0) > 0
            ? '${((meta['sample_rate'] as num) / 1000).toStringAsFixed(1)} kHz'
            : '',
      ),
      (
        l10n.audioAnalysisBitDepth,
        (meta['bit_depth'] as num? ?? 0) > 0 ? '${meta['bit_depth']}-bit' : '',
      ),
    ].where((r) => r.$2.trim().isNotEmpty && r.$2 != '0').toList();
    return MornyePlayerDetailsSheet(
      rows: rows,
      scrollController: scrollController,
      mediaItem: mediaItem,
    );
  }
}
