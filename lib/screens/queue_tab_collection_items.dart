part of 'queue_tab.dart';

extension _QueueTabCollectionItemWidgets on _QueueTabState {
  /// The desktop menu of a folder that offers nothing beyond opening it.
  void _showOpenMenu(
    BuildContext context,
    Offset position,
    VoidCallback onOpen,
  ) => showContextMenu(
    context,
    position: position,
    groups: [
      [
        ContextMenuAction(
          icon: Icons.open_in_new,
          label: context.l10n.desktopMenuOpen,
          onSelected: onOpen,
        ),
      ],
    ],
  );

  /// The desktop menu of a playlist: open, rename, and delete. Deleting a
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
            label: l10n.desktopMenuOpen,
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

    return DesktopContextMenuRegion(
      onContextMenu:
          onContextMenu ??
          (position) => _showOpenMenu(context, position, onTap),
      child: _buildCollectionGridCell(
        context: context,
        colorScheme: colorScheme,
        cover: cover,
        title: title,
        count: count,
        onTap: onTap,
        onLongPress: onLongPress,
      ),
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

  /// A playlist entry that accepts dragged tracks and outlines itself while
  /// one hovers. [builder] gets the entry's tap and long-press actions.
  Widget _playlistDropTarget(
    BuildContext context,
    ColorScheme colorScheme,
    UserPlaylistCollection playlist,
    List<UnifiedLibraryItem> filteredUnifiedItems,
    Widget Function(
      BuildContext context,
      VoidCallback onTap,
      VoidCallback onLongPress,
    )
    builder,
  ) {
    return DragTarget<UnifiedLibraryItem>(
      onWillAcceptWithDetails: (_) => !_isPlaylistSelectionMode,
      onAcceptWithDetails: (details) {
        _onTrackDroppedOnPlaylist(
          context,
          details.data,
          playlist.id,
          playlist.name,
          allItems: filteredUnifiedItems,
        );
      },
      builder: (context, candidateData, rejectedData) {
        final isHovering = candidateData.isNotEmpty;
        // The drop highlight is a tint, on the fast effects spring.
        final tint = AppMotion.effectsFast(
          context,
          const AppMotionSpec(Duration(milliseconds: 150), Curves.linear),
        );
        return AnimatedContainer(
          duration: tint.duration,
          curve: tint.curve,
          decoration: isHovering
              ? BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: colorScheme.primary, width: 2),
                  color: colorScheme.primary.withValues(alpha: 0.1),
                )
              : null,
          child: builder(
            context,
            _isPlaylistSelectionMode
                ? () => _togglePlaylistSelection(playlist.id)
                : () => _openPlaylistById(playlist.id),
            _isPlaylistSelectionMode
                ? () => _togglePlaylistSelection(playlist.id)
                : () => _enterPlaylistSelectionMode(playlist.id),
          ),
        );
      },
    );
  }

  Widget _buildAllTabGridCollectionItem({
    required BuildContext context,
    required ColorScheme colorScheme,
    required _CollectionEntry entry,
    required LibraryCollectionsState collectionState,
    List<UnifiedLibraryItem> filteredUnifiedItems = const [],
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
    return _playlistDropTarget(
      context,
      colorScheme,
      playlist,
      filteredUnifiedItems,
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
}
