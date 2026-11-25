import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/controllers/song_cue_split.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/controllers/song_file_actions.dart';
import 'package:sistrum/controllers/song_sidecar_files.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/song_analysis_sheet.dart';

/// The per-song menu: the three-dots menu of a song row and the Now Playing
/// actions share it. It works from the song's [track] and its file, resolved
/// from the library when [filePath] and [localItem] are not given.
///
/// [file] is the song's file when the caller has already checked it, so the
/// menu opens at once; otherwise the file is resolved first.
///
/// [displayedLyrics] are lyrics a screen already shows, saved as they are.
/// [onMetadataSaved] runs after the metadata editor saves, [onReplayGainChanged]
/// after ReplayGain changes the file and [onDeleted] after the file is deleted.
Future<void> showSongMenu(
  BuildContext context,
  WidgetRef ref, {
  required Track track,
  String? filePath,
  LocalLibraryItem? localItem,
  SongFile? file,
  String? displayedLyrics,
  VoidCallback? onMetadataSaved,
  VoidCallback? onReplayGainChanged,
  VoidCallback? onDeleted,
  Rect? anchor,
}) async {
  final menuAnchor = anchor ?? mornyeMenuAnchor(context);
  final song =
      file ??
      await resolveSongFile(
        ref,
        track: track,
        path: filePath,
        localItem: localItem,
      );
  if (!context.mounted) return;
  await showMornyeContextMenu<void>(
    context: context,
    anchor: menuAnchor,
    builder: (sheetContext) {
      final l10n = sheetContext.l10n;
      final options = _songMenuOptions(
        context,
        ref,
        song,
        displayedLyrics: displayedLyrics,
        onMetadataSaved: onMetadataSaved,
        onReplayGainChanged: onReplayGainChanged,
        onDeleted: onDeleted,
      );

      void closeAndRun(VoidCallback action) {
        Navigator.pop(sheetContext);
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!context.mounted) return;
          action();
        });
      }

      final groups = <List<MornyeMenuAction>>[];
      for (final option in options) {
        if (groups.isEmpty || option.dividerAbove || option.destructive) {
          groups.add([]);
        }
        groups.last.add(
          MornyeMenuAction(
            icon: mornyeIconFor(option.icon),
            label: option.label,
            destructive: option.destructive,
            onPressed: () => closeAndRun(option.onTap),
          ),
        );
      }
      return MornyeContextMenu(
        quickActions: [
          if (song.fileExists)
            MornyeMenuAction(
              icon: CupertinoIcons.play_fill,
              label: l10n.trackMetadataPlay,
              onPressed: () =>
                  closeAndRun(() => playSongFile(context, ref, song)),
            ),
          if (song.path != null)
            MornyeMenuAction(
              icon: CupertinoIcons.share_solid,
              label: l10n.trackMetadataShare,
              onPressed: () => closeAndRun(() => shareSongFile(context, song)),
            ),
        ],
        groups: groups,
      );
    },
  );
}

class _SongMenuOption {
  const _SongMenuOption({
    required this.icon,
    required this.label,
    required this.onTap,
    this.destructive = false,
    this.dividerAbove = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool destructive;
  final bool dividerAbove;
}

/// The menu items in order: queue, collections, navigation, metadata tools,
/// analysis, then delete. Share and Play are the menu's quick actions above them.
List<_SongMenuOption> _songMenuOptions(
  BuildContext context,
  WidgetRef ref,
  SongFile song, {
  String? displayedLyrics,
  VoidCallback? onMetadataSaved,
  VoidCallback? onReplayGainChanged,
  VoidCallback? onDeleted,
}) {
  final l10n = context.l10n;
  final track = song.track;
  final item = song.localItem;
  final hasFile = song.fileExists;
  final cleanPath = song.cleanPath;
  final hasCover = (item?.coverPath ?? '').trim().isNotEmpty;
  final isCueFile = song.isCueFile;
  final isLoved = ref.read(libraryCollectionsProvider).isLoved(track);
  return [
    if (hasFile && item != null)
      _SongMenuOption(
        icon: Icons.playlist_play,
        label: l10n.trackPlayNext,
        onTap: () => enqueueSong(context, ref, song, playNext: true),
      ),
    if (hasFile && item != null)
      _SongMenuOption(
        icon: Icons.queue_music,
        label: l10n.trackAddToQueue,
        onTap: () => enqueueSong(context, ref, song, playNext: false),
      ),
    _SongMenuOption(
      icon: isLoved ? Icons.favorite : Icons.favorite_border,
      label: isLoved
          ? l10n.trackOptionRemoveFromLoved
          : l10n.trackOptionAddToLoved,
      onTap: () => toggleSongLoved(context, ref, track),
    ),
    _SongMenuOption(
      icon: Icons.playlist_add,
      label: l10n.collectionAddToPlaylist,
      onTap: () => showAddTrackToPlaylistSheet(context, ref, track),
    ),
    if (track.albumName.trim().isNotEmpty)
      _SongMenuOption(
        icon: Icons.album_outlined,
        label: l10n.homeGoToAlbum,
        onTap: () => openSongAlbum(context, track),
      ),
    _SongMenuOption(
      icon: Icons.person_outline,
      label: l10n.mornyeGoToArtist,
      onTap: () => openSongArtist(context, track),
    ),
    if (hasFile)
      _SongMenuOption(
        icon: Icons.edit_outlined,
        label: l10n.trackEditMetadata,
        dividerAbove: true,
        onTap: () => editSongMetadata(context, song, onSaved: onMetadataSaved),
      ),
    if (song.canSaveSidecars && (hasFile || hasCover))
      _SongMenuOption(
        icon: Icons.image_outlined,
        label: l10n.trackSaveCoverArt,
        onTap: () => saveSongCoverArt(context, song),
      ),
    if (song.canSaveSidecars)
      _SongMenuOption(
        icon: Icons.lyrics_outlined,
        label: l10n.trackSaveLyrics,
        onTap: () =>
            saveSongLyrics(context, song, displayedLyrics: displayedLyrics),
      ),
    if (hasFile && !isCueFile)
      _SongMenuOption(
        icon: Icons.graphic_eq,
        label: l10n.trackReplayGain,
        onTap: () =>
            updateSongReplayGain(context, song, onChanged: onReplayGainChanged),
      ),
    if (hasFile && !isCueFile)
      _SongMenuOption(
        icon: Icons.remove_circle_outline,
        label: l10n.trackRemoveReplayGain,
        onTap: () =>
            removeSongReplayGain(context, song, onChanged: onReplayGainChanged),
      ),
    if (hasFile && isCueFile && item != null)
      _SongMenuOption(
        icon: Icons.call_split,
        label: l10n.cueSplitTitle,
        onTap: () => showCueSplitSheet(context, ref, song),
      ),
    if (hasFile && !isCueFile && cleanPath != null)
      _SongMenuOption(
        icon: Icons.analytics_outlined,
        label: l10n.trackAudioQualityAnalysis,
        onTap: () => showSongAnalysisSheet(
          context,
          filePath: cleanPath,
          codecHint: item?.format,
          exportName: songSaveBaseName(track),
        ),
      ),
    if (song.canDelete)
      _SongMenuOption(
        icon: Icons.delete_outline,
        label: l10n.trackRemoveFromDevice,
        destructive: true,
        onTap: () => deleteSongFromDevice(context, song, onDeleted: onDeleted),
      ),
  ];
}

/// The three-dots button that opens [showSongMenu] for a song.
class SongMenuButton extends ConsumerWidget {
  const SongMenuButton({
    super.key,
    required this.track,
    this.filePath,
    this.localItem,
    this.dense = false,
  });

  final Track track;
  final String? filePath;
  final LocalLibraryItem? localItem;

  /// A 32dp pointer target for desktop table rows.
  final bool dense;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    return AppIconButton(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      icon: AppIcon(
        CupertinoIcons.ellipsis,
        color: colorScheme.onSurfaceVariant,
        size: 20,
      ),
      onPressed: () => showSongMenu(
        context,
        ref,
        track: track,
        filePath: filePath,
        localItem: localItem,
      ),
      padding: EdgeInsets.zero,
      constraints: dense
          ? const BoxConstraints(minWidth: 32, minHeight: 32)
          : const BoxConstraints(minWidth: 48, minHeight: 48),
      style: dense
          ? IconButton.styleFrom(
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            )
          : null,
    );
  }
}
