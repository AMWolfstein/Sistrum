// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member
part of 'track_metadata_screen.dart';

extension _TrackMetadataMenu on _TrackMetadataScreenState {
  /// The overflow menu's items in order. Every surface starts a new group
  /// where an item asks for a divider or is destructive.
  List<_MetadataOption> _menuOptions(
    BuildContext screenContext,
    WidgetRef ref,
    ColorScheme colorScheme,
    AppLocalizations l10n,
  ) {
    final canEditFile = _fileExists;
    final showPlayer = ref.read(playerUiEnabledProvider);
    final downloadItem = _downloadItem;
    return <_MetadataOption>[
      if (_fileExists && showPlayer)
        _MetadataOption(
          icon: Icons.playlist_play,
          label: l10n.trackPlayNext,
          onTap: () => _enqueueThis(ref, playNext: true),
        ),
      if (_fileExists && showPlayer)
        _MetadataOption(
          icon: Icons.queue_music,
          label: l10n.trackAddToQueue,
          onTap: () => _enqueueThis(ref, playNext: false),
        ),
      if (albumName.trim().isNotEmpty)
        _MetadataOption(
          icon: Icons.album_outlined,
          label: l10n.homeGoToAlbum,
          onTap: () => navigateToAlbum(
            screenContext,
            albumName: albumName,
            artistName: albumArtist ?? artistName,
          ),
        ),
      _MetadataOption(
        icon: Icons.person_outline,
        label: l10n.sistrumGoToArtist,
        onTap: () =>
            navigateToArtistCredits(screenContext, artistNames: artistName),
      ),
      _MetadataOption(
        icon: Icons.copy_outlined,
        label: l10n.trackCopyFilePath,
        dividerAbove: _fileExists,
        onTap: () => _copyToClipboard(screenContext, cleanFilePath),
      ),
      if (_canEditMetadataFile)
        _MetadataOption(
          icon: Icons.edit_outlined,
          label: l10n.trackEditMetadata,
          onTap: () => _showEditMetadataSheet(screenContext, ref, colorScheme),
        ),
      if (_coverUrl != null || _fileExists)
        _MetadataOption(
          icon: Icons.image_outlined,
          label: l10n.trackSaveCoverArt,
          onTap: _saveCoverArt,
        ),
      if (_fileExists || !_isLocalItem)
        _MetadataOption(
          icon: Icons.lyrics_outlined,
          label: l10n.trackSaveLyrics,
          onTap: _saveLyrics,
        ),
      if (canEditFile && !_isCueFile)
        _MetadataOption(
          icon: Icons.graphic_eq,
          label: l10n.trackReplayGain,
          onTap: () => _updateReplayGain(),
        ),
      if (canEditFile && !_isCueFile)
        _MetadataOption(
          icon: Icons.remove_circle_outline,
          label: l10n.trackRemoveReplayGain,
          onTap: () => _removeReplayGain(),
        ),
      if (canEditFile && _isCueFile)
        _MetadataOption(
          icon: Icons.call_split,
          label: l10n.cueSplitTitle,
          onTap: () => _showCueSplitSheet(screenContext),
        ),
      _MetadataOption(
        icon: Icons.share_outlined,
        label: l10n.trackMetadataShare,
        dividerAbove: _spotifyId == null && !(isrc?.isNotEmpty ?? false),
        onTap: () => _shareFile(screenContext),
      ),
      if (_canDeleteFile)
        _MetadataOption(
          icon: Icons.delete_outline,
          label: downloadItem?.originFilePath != null
              ? l10n.offlineRemove
              : l10n.trackRemoveFromDevice,
          destructive: true,
          onTap: () => _confirmDelete(screenContext, ref),
        ),
    ];
  }

  void _showOptionsMenu(
    BuildContext screenContext,
    WidgetRef ref,
    ColorScheme colorScheme, {
    Rect? anchor,
  }) {
    Widget buildMenu(BuildContext sheetContext) {
      final l10n = sheetContext.l10n;
      final options = _menuOptions(screenContext, ref, colorScheme, l10n);
      {
        final groups = <List<MornyeMenuAction>>[];
        for (final option in options) {
          if (option.icon == Icons.share_outlined) {
            continue;
          }
          if (groups.isEmpty || option.dividerAbove || option.destructive) {
            groups.add([]);
          }
          groups.last.add(
            MornyeMenuAction(
              icon: mornyeIconFor(option.icon),
              label: option.label,
              destructive: option.destructive,
              onPressed: () =>
                  _closeOptionsMenuAndRun(sheetContext, option.onTap),
            ),
          );
        }
        return MornyeContextMenu(
          quickActions: [
            if (_fileExists) ...[
              MornyeMenuAction(
                icon: CupertinoIcons.play_fill,
                label: l10n.trackMetadataPlay,
                onPressed: () => _closeOptionsMenuAndRun(
                  sheetContext,
                  () => _openFile(screenContext, rawFilePath),
                ),
              ),
            ],
            MornyeMenuAction(
              icon: CupertinoIcons.share_solid,
              label: l10n.trackMetadataShare,
              onPressed: () => _closeOptionsMenuAndRun(
                sheetContext,
                () => _shareFile(screenContext),
              ),
            ),
          ],
          groups: groups,
        );
      }
    }

    showMornyeContextMenu<void>(
      context: screenContext,
      anchor: anchor,
      builder: buildMenu,
    );
    return;
  }
}
