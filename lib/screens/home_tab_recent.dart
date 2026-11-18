part of 'home_tab.dart';

extension _HomeTabRecentUI on _HomeTabState {
  Widget _buildRecentAccess(_RecentAccessView view, ColorScheme colorScheme) {
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(child: _buildRecentAccessHeader(view, colorScheme)),
        if (view.uniqueItems.isNotEmpty)
          SliverPadding(
            padding:
                const EdgeInsets.fromLTRB(16, 0, 16, 8) +
                EdgeInsets.symmetric(horizontal: homeContentInset(context)),
            sliver: SliverList.builder(
              itemCount: view.uniqueItems.length,
              itemBuilder: (context, index) {
                final item = view.uniqueItems[index];
                return KeyedSubtree(
                  key: ValueKey(item.uniqueKey),
                  child: _buildRecentAccessItem(item, colorScheme),
                );
              },
            ),
          ),
      ],
    );
  }

  Widget _buildRecentAccessHeader(
    _RecentAccessView view,
    ColorScheme colorScheme,
  ) {
    return Padding(
      padding:
          EdgeInsets.fromLTRB(16, 8, 16, view.uniqueItems.isEmpty ? 8 : 0) +
          EdgeInsets.symmetric(horizontal: homeContentInset(context)),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Semantics(
            header: true,
            child: Text(
              context.l10n.searchRecentlySearched,
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          if (view.uniqueItems.isNotEmpty)
            TextButton(
              onPressed: () =>
                  ref.read(recentAccessProvider.notifier).clearHistory(),
              child: Text(context.l10n.dialogClearAll),
            ),
        ],
      ),
    );
  }

  Widget _buildRecentAccessItem(
    RecentAccessItem item,
    ColorScheme colorScheme,
  ) {
    final size = context.tokens.coverList;
    final isArtist = item.type == RecentAccessType.artist;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: _DownloadedOrRemoteCover(
        downloadedFilePath: null,
        imageUrl: item.imageUrl,
        width: size,
        height: size,
        borderRadius: BorderRadius.circular(isArtist ? size / 2 : 8),
        fallbackIcon: isArtist ? Icons.person : Icons.album,
        colorScheme: colorScheme,
      ),
      title: Text(item.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: item.subtitle == null
          ? null
          : Text(item.subtitle!, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: AppIconButton(
        icon: const Icon(Icons.close),
        tooltip: context.l10n.dialogRemove,
        onPressed: () =>
            ref.read(recentAccessProvider.notifier).removeItem(item),
      ),
      onTap: () => _navigateToRecentItem(item),
    );
  }

  /// Artists and albums open their local Library pages; other recents have
  /// no local page to open.
  void _navigateToRecentItem(RecentAccessItem item) {
    _searchFocusNode.unfocus();
    switch (item.type) {
      case RecentAccessType.artist:
        pushPage<void>(context, (_) => LibraryArtistScreen(artist: item.name));
      case RecentAccessType.album:
        pushPage<void>(
          context,
          (_) => LocalAlbumScreen(
            albumName: item.name,
            artistName: item.subtitle ?? '',
            coverPath: item.imageUrl,
          ),
        );
      case RecentAccessType.track:
      case RecentAccessType.playlist:
        break;
    }
  }
}
