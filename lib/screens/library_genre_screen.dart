import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/track_card.dart';

/// The songs of one library genre: Play and Shuffle over them, then the
/// library track rows. Tapping a row plays from it.
class LibraryGenreScreen extends ConsumerWidget {
  const LibraryGenreScreen({
    super.key,
    required this.genreKey,
    required this.name,
  });

  final String genreKey;
  final String name;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final songs = ref.watch(libraryGenreItemsProvider(genreKey));
    final items = songs.value ?? const <UnifiedLibraryItem>[];
    final l10n = context.l10n;
    final colors = Theme.of(context).colorScheme;
    final inset = wideListInset(context);
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          AppSliverHeader.page(title: name),
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(24 + inset, 0, 24 + inset, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (songs.hasValue)
                    Text(
                      l10n.queueTrackCount(items.length),
                      style: TextStyle(color: colors.onSurfaceVariant),
                    ),
                  if (items.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: AlbumPlayActions(
                        compact: true,
                        playLabel: l10n.tooltipPlay,
                        shuffleTooltip: l10n.actionShuffle,
                        onPlay: () => _play(context, ref, items),
                        onShuffle: () =>
                            _play(context, ref, items, shuffle: true),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (songs.hasError)
            SliverToBoxAdapter(
              child: Center(
                child: TextButton.icon(
                  onPressed: () =>
                      ref.invalidate(libraryGenreItemsProvider(genreKey)),
                  icon: const Icon(Icons.refresh),
                  label: Text(l10n.dialogRetry),
                ),
              ),
            )
          else if (!songs.hasValue)
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator.adaptive()),
              ),
            )
          else if (items.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(child: Text(l10n.libraryEmptyCollection)),
            )
          else
            SliverPadding(
              padding: EdgeInsets.symmetric(horizontal: inset),
              sliver: SliverList.builder(
                itemCount: items.length,
                itemBuilder: (context, index) {
                  final item = items[index];
                  return TrackCard(
                    style: TrackCardStyle.flat,
                    leading: SizedBox.square(
                      dimension: 48,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: _SongCover(
                          cover: item.localCoverPath ?? item.coverUrl,
                          colors: colors,
                        ),
                      ),
                    ),
                    title: item.trackName,
                    subtitle: Text(
                      item.artistName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    onTap: () => _play(context, ref, items, start: item),
                  );
                },
              ),
            ),
          const NavBarSliverSpacer(),
        ],
      ),
    );
  }

  /// Plays the songs that have a file, from [start] or from the top; Shuffle
  /// starts at a random song with shuffle on.
  Future<void> _play(
    BuildContext context,
    WidgetRef ref,
    List<UnifiedLibraryItem> items, {
    UnifiedLibraryItem? start,
    bool shuffle = false,
  }) async {
    final playable = items
        .where(
          (item) =>
              item.filePath.trim().isNotEmpty &&
              !isCueVirtualPath(item.filePath),
        )
        .toList(growable: false);
    if (playable.isEmpty) return;
    final index = start != null
        ? max(0, playable.indexWhere((item) => item.id == start.id))
        : shuffle
        ? Random().nextInt(playable.length)
        : 0;
    try {
      await ref.read(musicPlayerControllerProvider).setShuffle(shuffle);
      await ref
          .read(playbackProvider.notifier)
          .playMediaQueue(
            playable.map(playableFromUnified),
            startIndex: index,
            externalPath: playable[index].filePath,
          );
    } catch (error) {
      if (context.mounted) showCannotOpenFileSnackBar(context, error);
    }
  }
}

/// A library cover is a file path or a URL; a song without one shows a note.
class _SongCover extends StatelessWidget {
  const _SongCover({required this.cover, required this.colors});

  final String? cover;
  final ColorScheme colors;

  @override
  Widget build(BuildContext context) {
    Widget fallback() => ColoredBox(
      color: colors.surfaceContainerHighest,
      child: Center(
        child: Icon(Icons.music_note, color: colors.onSurfaceVariant),
      ),
    );
    if (cover == null || cover!.isEmpty) return fallback();
    return CachedCoverImage(
      imageUrl: cover!,
      fit: BoxFit.cover,
      errorWidget: (context, url, error) => fallback(),
    );
  }
}
