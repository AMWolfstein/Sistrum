part of 'queue_tab.dart';

extension _QueueTabCollectionItemWidgets on _QueueTabState {
  /// A completed download pinned above the Library until its row lands.
  Widget _buildLibraryQualityBadge(
    BuildContext context,
    ColorScheme colorScheme,
    String quality,
  ) {
    final tokens = context.tokens;
    final isHighlightedQuality = shouldHighlightAudioQualityBadge(quality);
    final displayQuality = formatLibraryGridAudioQualityLabel(quality);
    final isDetailed = isDetailedLibraryAudioQualityLabel(displayQuality);
    final label = Text(
      displayQuality,
      maxLines: 1,
      softWrap: false,
      style: Theme.of(context).textTheme.labelSmall?.copyWith(
        color: isHighlightedQuality
            ? colorScheme.onPrimary
            : colorScheme.onSurfaceVariant,
        fontSize: isDetailed ? tokens.badgeFontSize - 1 : tokens.badgeFontSize,
        fontWeight: FontWeight.w600,
      ),
    );
    return Container(
      padding: tokens.badgePadding,
      decoration: BoxDecoration(
        color: isHighlightedQuality
            ? colorScheme.primary
            : colorScheme.surfaceContainerHighest,
        borderRadius: tokens.borderRadiusBadge,
      ),
      child: isDetailed
          ? FittedBox(
              fit: BoxFit.scaleDown,
              alignment: AlignmentDirectional.centerStart,
              child: label,
            )
          : label,
    );
  }

  Widget _buildCollectionListItem({
    required BuildContext context,
    required ColorScheme colorScheme,
    IconData? icon,
    Color? iconBgColor,
    Widget? coverWidget,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
    ValueChanged<Offset>? onContextMenu,
  }) {
    final cover =
        coverWidget ??
        Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            color: iconBgColor ?? colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(8),
          ),
          child: AppIcon(icon ?? Icons.folder, color: Colors.white, size: 28),
        );

    final card = AppContentCard(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      mornyeSurface: false,
      onTap: onTap,
      onLongPress: onLongPress,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            SizedBox(width: 56, height: 56, child: cover),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            AppIcon(
              Icons.chevron_right,
              color: colorScheme.onSurfaceVariant,
              size: 20,
            ),
          ],
        ),
      ),
    );
    return card;
  }

  /// playlist outside the selection deletes only that playlist.
  void _showPlaylistMenu(
    BuildContext context,
    UserPlaylistCollection playlist,
    Offset position,
  ) {
    final l10n = context.l10n;
    final root = Navigator.of(context, rootNavigator: true).context;
    showContextMenu(
      context,
      position: position,
      groups: [
        [
          ContextMenuAction(
            icon: Icons.open_in_new,
            label: l10n.menuOpen,
            onSelected: () => _openPlaylistById(playlist.id),
          ),
          ContextMenuAction(
            icon: Icons.edit_outlined,
            label: l10n.collectionRenamePlaylist,
            onSelected: () async {
              final name = await promptPlaylistName(
                root,
                title: l10n.collectionRenamePlaylist,
                confirmLabel: l10n.dialogSave,
                initialName: playlist.name,
              );
              final trimmed = name?.trim() ?? '';
              if (trimmed.isEmpty || !mounted) return;
              await ref
                  .read(libraryCollectionsProvider.notifier)
                  .renamePlaylist(playlist.id, trimmed);
            },
          ),
        ],
        [
          ContextMenuAction(
            icon: Icons.delete_outline,
            label: l10n.collectionDeletePlaylist,
            destructive: true,
            onSelected: () {
              if (!_selectedPlaylistIds.contains(playlist.id)) {
                _exitPlaylistSelectionMode();
                _enterPlaylistSelectionMode(playlist.id);
              }
              _deleteSelectedPlaylists(this.context);
            },
          ),
        ],
      ],
    );
  }

  Widget _buildCollectionGridItem({
    required BuildContext context,
    required ColorScheme colorScheme,
    IconData? icon,
    Color? iconBgColor,
    Widget? coverWidget,
    required String title,
    required int count,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
    ValueChanged<Offset>? onContextMenu,
  }) {
    final cover =
        coverWidget ??
        Container(
          decoration: BoxDecoration(
            color: iconBgColor ?? colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(8),
          ),
          child: AppIcon(icon ?? Icons.folder, color: Colors.white, size: 40),
        );

    return _buildCollectionGridCell(
      context: context,
      colorScheme: colorScheme,
      cover: cover,
      title: title,
      count: count,
      onTap: onTap,
      onLongPress: onLongPress,
    );
  }

  Widget _buildCollectionGridCell({
    required BuildContext context,
    required ColorScheme colorScheme,
    required Widget cover,
    required String title,
    required int count,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
  }) {
    return Semantics(
      button: true,
      label: context.l10n.a11yOpenItemCount(title, count),
      child: GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: 1,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: cover,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w500),
            ),
            Text(
              context.l10n.itemCount(count),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<_CollectionEntry> _getVisibleCollectionEntries(
    LibraryCollectionsState collectionState,
  ) {
    final entries = <_CollectionEntry>[];
    if (collectionState.lovedCount > 0) {
      entries.add(_CollectionEntry.loved);
    }
    if (collectionState.favoriteArtistCount > 0) {
      entries.add(_CollectionEntry.favoriteArtists);
    }
    for (var i = 0; i < collectionState.playlists.length; i++) {
      entries.add(_CollectionEntry.playlist(i));
    }
    return entries;
  }

  /// Icon, tile color, title, count and folder of a non-playlist entry.
  (IconData, Color, String, int, VoidCallback) _specialCollection(
    BuildContext context,
    _CollectionEntryType type,
    LibraryCollectionsState collectionState,
  ) => switch (type) {
    _CollectionEntryType.loved => (
      Icons.favorite,
      const Color(0xFF8C67AC),
      context.l10n.collectionLoved,
      collectionState.lovedCount,
      _openLovedFolder,
    ),
    _ => (
      Icons.person,
      const Color(0xFFE91E63),
      context.l10n.collectionFavoriteArtists,
      collectionState.favoriteArtistCount,
      _openFavoriteArtistsFolder,
    ),
  };

  /// A playlist entry: a tap opens it, a long press starts selecting it.
  Widget _playlistEntry(
    BuildContext context,
    UserPlaylistCollection playlist,
    Widget Function(
      BuildContext context,
      VoidCallback onTap,
      VoidCallback onLongPress,
    )
    builder,
  ) {
    return builder(
      context,
      _isPlaylistSelectionMode
          ? () => _togglePlaylistSelection(playlist.id)
          : () => _openPlaylistById(playlist.id),
      _isPlaylistSelectionMode
          ? () => _togglePlaylistSelection(playlist.id)
          : () => _enterPlaylistSelectionMode(playlist.id),
    );
  }

  Widget _buildAllTabGridCollectionItem({
    required BuildContext context,
    required ColorScheme colorScheme,
    required _CollectionEntry entry,
    required LibraryCollectionsState collectionState,
  }) {
    if (entry.type != _CollectionEntryType.playlist) {
      final (icon, color, title, count, onTap) = _specialCollection(
        context,
        entry.type,
        collectionState,
      );
      return _buildCollectionGridItem(
        context: context,
        colorScheme: colorScheme,
        icon: icon,
        iconBgColor: color,
        title: title,
        count: count,
        onTap: onTap,
      );
    }
    final playlist = collectionState.playlists[entry.playlistIndex];
    final isSelected = _selectedPlaylistIds.contains(playlist.id);
    return _playlistEntry(
      context,
      playlist,
      (context, onTap, onLongPress) => Stack(
        children: [
          _buildCollectionGridItem(
            context: context,
            colorScheme: colorScheme,
            coverWidget: _buildPlaylistCover(context, playlist, colorScheme),
            title: playlist.name,
            count: playlist.trackCount,
            onTap: onTap,
            onLongPress: onLongPress,
            onContextMenu: (position) =>
                _showPlaylistMenu(context, playlist, position),
          ),
          if (_isPlaylistSelectionMode)
            Positioned(
              left: 0,
              top: 0,
              right: 0,
              child: IgnorePointer(
                child: AspectRatio(
                  aspectRatio: 1,
                  child: Container(
                    decoration: BoxDecoration(
                      color: isSelected
                          ? colorScheme.primary.withValues(alpha: 0.3)
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ),
            ),
          if (_isPlaylistSelectionMode)
            PositionedDirectional(
              top: 4,
              end: 4,
              child: IgnorePointer(
                child: AnimatedSelectionCheckbox(
                  selected: isSelected,
                  colorScheme: colorScheme,
                  size: 20,
                  unselectedColor: colorScheme.surface.withValues(alpha: 0.85),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildAllTabListCollectionItem({
    required BuildContext context,
    required ColorScheme colorScheme,
    required _CollectionEntry entry,
    required LibraryCollectionsState collectionState,
  }) {
    if (entry.type != _CollectionEntryType.playlist) {
      final (icon, color, title, count, onTap) = _specialCollection(
        context,
        entry.type,
        collectionState,
      );
      final countText = entry.type == _CollectionEntryType.favoriteArtists
          ? context.l10n.collectionArtistCount(count)
          : '$count ${count == 1 ? 'track' : 'tracks'}';
      return _buildCollectionListItem(
        context: context,
        colorScheme: colorScheme,
        icon: icon,
        iconBgColor: color,
        title: title,
        subtitle: '${context.l10n.collectionFoldersTitle} • $countText',
        onTap: onTap,
      );
    }
    final playlist = collectionState.playlists[entry.playlistIndex];
    final isSelected = _selectedPlaylistIds.contains(playlist.id);
    return _playlistEntry(
      context,
      playlist,
      (context, onTap, onLongPress) => Row(
        children: [
          if (_isPlaylistSelectionMode)
            Semantics(
              button: true,
              checked: isSelected,
              label: playlist.name,
              child: GestureDetector(
                onTap: () => _togglePlaylistSelection(playlist.id),
                behavior: HitTestBehavior.opaque,
                child: ConstrainedBox(
                  constraints: BoxConstraints.tightFor(
                    width: context.tokens.minTouchTarget,
                    height: context.tokens.minTouchTarget,
                  ),
                  child: Center(
                    child: AnimatedSelectionCheckbox(
                      selected: isSelected,
                      colorScheme: colorScheme,
                      size: 24,
                    ),
                  ),
                ),
              ),
            ),
          Expanded(
            child: _buildCollectionListItem(
              context: context,
              colorScheme: colorScheme,
              coverWidget: _buildPlaylistCover(
                context,
                playlist,
                colorScheme,
                56,
              ),
              title: playlist.name,
              subtitle:
                  '${playlist.trackCount} ${playlist.trackCount == 1 ? 'track' : 'tracks'}',
              onTap: onTap,
              onLongPress: onLongPress,
              onContextMenu: (position) =>
                  _showPlaylistMenu(context, playlist, position),
            ),
          ),
        ],
      ),
    );
  }
}
