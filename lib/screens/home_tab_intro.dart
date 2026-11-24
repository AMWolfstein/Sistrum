part of 'home_tab.dart';

const _homeShelfLimit = 10;
const _homeShelfCoverSize = 120.0;
const _homeMornyeShelfGap = 16.0;

typedef HomeRecentlyAdded = ({List<UnifiedLibraryItem> items, int trackCount});

/// The newest downloaded and scanned tracks, with the Library's track count
/// for the shortcut at the end of the shelf. A reload that finds the same
/// songs keeps the same list, so Home does not rebuild for it.
final homeRecentlyAddedProvider = FutureProvider.autoDispose<HomeRecentlyAdded>(
  (ref) async {
    final includeLocal = ref.watch(
      settingsProvider.select((s) => s.localLibraryEnabled),
    );
    // Reload when a scan rebuilds the index.
    if (includeLocal) ref.watch(localLibraryIndexRevisionProvider);
    final memo = ref.read(_homeShelfMemoProvider);
    final (rows, counts) = await (
      LibraryDatabase.instance.getQueueTrackPage(
        QueueLibraryDbQuery(includeLocal: includeLocal, limit: _homeShelfLimit),
      ),
      // The Library tab's badges count the same Library; one count serves
      // both.
      ref.watch(
        libraryCountsProvider(
          LibraryCountsRequest.all(localLibraryEnabled: includeLocal),
        ).future,
      ),
    ).wait;
    final last = memo.recentlyAdded;
    if (last != null && _sameQueueRows(last.rows, rows)) {
      return (items: last.items, trackCount: counts.allTrackCount);
    }
    final items = <UnifiedLibraryItem>[];
    for (final row in rows) {
      final json = Map<String, dynamic>.from(row['item'] as Map);
      switch (row['source']) {
        case 'local':
          items.add(
            UnifiedLibraryItem.fromLocalLibrary(
              LocalLibraryItem.fromJson(json),
            ),
          );
      }
    }
    memo.recentlyAdded = (rows: rows, items: items);
    return (items: items, trackCount: counts.allTrackCount);
  },
);

bool _sameQueueRows(
  List<Map<String, dynamic>> first,
  List<Map<String, dynamic>> second,
) {
  if (first.length != second.length) return false;
  for (var index = 0; index < first.length; index++) {
    final (a, b) = (first[index], second[index]);
    if (a['source'] != b['source'] ||
        !mapEquals(a['item'] as Map?, b['item'] as Map?)) {
      return false;
    }
  }
  return true;
}

typedef HomeRepeatTrack = ({ListeningTrack track, UnifiedLibraryItem? item});

/// Candidates read from the statistics at a time; most shelves fill from
/// the first page.
const _homeRepeatPage = 40;

/// The most played files of the last four weeks, matched to their current
/// Library entry. Statistics keep the artwork seen at play time, which may
/// since have been evicted from a cache or rescanned away.
final homeOnRepeatProvider = FutureProvider.autoDispose<List<HomeRepeatTrack>>((
  ref,
) async {
  final memo = ref.read(_homeShelfMemoProvider);
  final tracks = await _homeReplayableTracks(ref, limit: _homeShelfLimit);
  final last = memo.onRepeat;
  if (last != null && _sameRepeatTracks(last, tracks)) return last;
  return memo.onRepeat = tracks;
});

/// One song per recently played album, ordered by the latest listening day.
final homeRecentlyPlayedProvider =
    FutureProvider.autoDispose<List<HomeRepeatTrack>>((ref) async {
      final memo = ref.read(_homeShelfMemoProvider);
      final played = await _homeReplayableTracks(
        ref,
        limit: _homeShelfLimit * 3,
        latestFirst: true,
      );
      final albums = <String>{};
      final tracks = [
        for (final entry in played)
          if (albums.add(
            entry.track.album.isEmpty
                ? entry.track.key
                : '${entry.track.artist}/${entry.track.album}',
          ))
            entry,
      ].take(_homeShelfLimit).toList();
      final last = memo.recentlyPlayed;
      if (last != null && _sameRepeatTracks(last, tracks)) return last;
      return memo.recentlyPlayed = tracks;
    });

/// Up to [limit] played songs of the last four weeks that can be played
/// again, matched to the Library. Reloads with what changes them.
Future<List<HomeRepeatTrack>> _homeReplayableTracks(
  Ref ref, {
  required int limit,
  bool latestFirst = false,
}) async {
  // A track that ends may have just earned a play. One that starts after
  // none, such as a restored session's, follows no play.
  ref.listen(currentMediaItemProvider.select((s) => s.value?.id), (
    previous,
    _,
  ) {
    if (previous != null) ref.invalidateSelf();
  });
  // A rescan can remove a track from the shelf.
  final localRevision = ref.watch(localLibraryIndexRevisionProvider);
  final includeLocal = ref.watch(
    settingsProvider.select((s) => s.localLibraryEnabled),
  );
  final memo = ref.read(_homeShelfMemoProvider);
  // Library matches hold until the Library changes, so a new track only
  // reads the statistics again.
  final library = (localRevision, includeLocal);
  if (memo.repeatLibrary != library) {
    // A new map, so a load still running for the old Library keeps its own.
    memo
      ..repeatLibrary = library
      ..repeatMatches = {};
  }
  final matches = memo.repeatMatches;
  await listeningRecorder.flush();
  final now = DateTime.now();
  final start = DateTime(now.year, now.month, now.day - 27);
  final end = DateTime(now.year, now.month, now.day + 1);
  final tracks = <HomeRepeatTrack>[];
  for (var offset = 0; ; offset += _homeRepeatPage) {
    // Played files and network songs only: remote sources cannot be
    // replayed from their recorded key.
    final totals = await ListeningStatisticsStore.instance.loadReplayableTracks(
      start,
      end,
      limit: _homeRepeatPage,
      offset: offset,
      latestFirst: latestFirst,
    );
    // Matched in rounds of the songs still missing, so no more songs are
    // looked up than one at a time would.
    var next = 0;
    while (tracks.length < limit && next < totals.length) {
      final round = totals.skip(next).take(limit - tracks.length).toList();
      next += round.length;
      tracks.addAll(
        await _homeRepeatTracks(
          round,
          includeLocal: includeLocal,
          matches: matches,
        ),
      );
    }
    if (tracks.length == limit || totals.length < _homeRepeatPage) break;
  }
  return tracks;
}

/// A played key's Library entry: a scanned file, or none.
/// [hidden] is a scanned file while Local Library is off.
typedef _HomeRepeatMatch = ({UnifiedLibraryItem? item, bool hidden});

/// [totals] that can still play, in order, matched to their scanned Library
/// entry. All lookups of a round run together; [matches] keeps them while
/// the Library stays the same.
Future<List<HomeRepeatTrack>> _homeRepeatTracks(
  List<ListeningTotal> totals, {
  required bool includeLocal,
  required Map<String, _HomeRepeatMatch> matches,
}) async {
  final missing = [
    for (final total in totals)
      if (!matches.containsKey(total.track.key)) total.track.key,
  ];
  if (missing.isNotEmpty) {
    final database = LibraryDatabase.instance;
    final scannedKeys = missing;
    final scannedIds = await Future.wait([
      for (final key in scannedKeys) database.getPhysicalFileIds([key]),
    ]);
    // With Local Library off its songs stay off Home as well.
    final rows = includeLocal
        ? {
            for (final row in await database.getByIds([
              for (final ids in scannedIds)
                if (ids.isNotEmpty) ids.first,
            ]))
              row['id']: row,
          }
        : const <Object?, Map<String, dynamic>>{};
    for (final (index, key) in scannedKeys.indexed) {
      final ids = scannedIds[index];
      final row = ids.isEmpty ? null : rows[ids.first];
      matches[key] = (
        item: row == null
            ? null
            : UnifiedLibraryItem.fromLocalLibrary(
                LocalLibraryItem.fromJson(row),
              ),
        hidden: ids.isNotEmpty && !includeLocal,
      );
    }
  }
  // Files played from outside the Library stay while they still exist,
  // checked each time since they can go at any moment.
  final outside = [
    for (final total in totals)
      if (matches[total.track.key] case (item: null, hidden: false))
        total.track.key,
  ];
  final present = await Future.wait(outside.map(fileExists));
  final exists = {
    for (final (index, key) in outside.indexed) key: present[index],
  };
  return [
    for (final total in totals)
      if (matches[total.track.key]! case (
        :final item,
        :final hidden,
      ) when !hidden && (item != null || exists[total.track.key]!))
        (track: total.track, item: item),
  ];
}

bool _sameRepeatTracks(
  List<HomeRepeatTrack> first,
  List<HomeRepeatTrack> second,
) {
  if (first.length != second.length) return false;
  for (var index = 0; index < first.length; index++) {
    final (a, b) = (first[index], second[index]);
    if (a.track.key != b.track.key ||
        a.track.title != b.track.title ||
        a.track.artist != b.track.artist ||
        a.track.album != b.track.album ||
        a.track.artwork != b.track.artwork ||
        !_sameLibraryItem(a.item, b.item)) {
      return false;
    }
  }
  return true;
}

bool _sameLibraryItem(UnifiedLibraryItem? first, UnifiedLibraryItem? second) =>
    identical(first, second) ||
    first != null &&
        second != null &&
        mapEquals(first.localItem?.toJson(), second.localItem?.toJson());

/// What the Home shelves read last, kept for the app's lifetime, so a reload
/// that finds the same songs returns the same value.
class _HomeShelfMemo {
  ({List<Map<String, dynamic>> rows, List<UnifiedLibraryItem> items})?
  recentlyAdded;
  (int, bool)? repeatLibrary;
  Map<String, _HomeRepeatMatch> repeatMatches = {};
  List<HomeRepeatTrack>? onRepeat;
  List<HomeRepeatTrack>? recentlyPlayed;
}

final _homeShelfMemoProvider = Provider<_HomeShelfMemo>(
  (ref) => _HomeShelfMemo(),
);

/// Everything under the search bar on the Material Home. Sections without
/// content leave no gap behind.
class _HomeSections extends ConsumerWidget {
  const _HomeSections({required this.inset});

  /// Horizontal page margin, wider on tablets.
  final double inset;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final showPlayer = ref.watch(playerUiEnabledProvider);
    final hasMediaItem =
        showPlayer &&
        ref.watch(currentMediaItemProvider.select((s) => s.value != null));
    // Only values: a reload's loading state shows the previous one.
    final hasResume =
        showPlayer &&
        ref.watch(playbackResumeProvider.select((s) => s.value != null));
    final recentlyAdded = ref.watch(
      homeRecentlyAddedProvider.select((s) => s.value),
    );
    final onRepeat =
        ref.watch(homeOnRepeatProvider.select((s) => s.value)) ?? const [];

    final showContinue =
        (hasMediaItem || hasResume) &&
        ref.watch(settingsProvider.select((s) => s.homeContinueListening));

    final recentlyPlayed =
        ref.watch(homeRecentlyPlayedProvider.select((s) => s.value)) ??
        const <HomeRepeatTrack>[];
    // The card only stands in once nothing was played: while the shelf
    // loads, it would show and then give way to Recently Played.
    final recentlyPlayedLoading = ref.watch(
      homeRecentlyPlayedProvider.select((s) => !s.hasValue && !s.hasError),
    );
    final hasMixes = ref.watch(
      libraryMixesProvider.select((s) => s.value?.isNotEmpty ?? false),
    );
    final sections = <Widget>[
      if (hasMixes) _HomeMixBannersSection(inset: inset),
      if (recentlyPlayed.isNotEmpty)
        _HomeSection(
          title: l10n.homeRecentlyPlayed,
          inset: inset,
          shelf: true,
          onShowAll: () =>
              pushPage<void>(context, (_) => const ListeningStatisticsScreen()),
          child: _HomeRecentlyPlayedShelf(
            tracks: recentlyPlayed,
            inset: inset + 8,
          ),
        )
      else if (showContinue && !recentlyPlayedLoading)
        _HomeSection(
          title: l10n.homeContinueListening,
          inset: inset,
          child: const _HomeContinueListeningCard(),
        ),
      if (recentlyAdded != null && recentlyAdded.items.isNotEmpty)
        _HomeSection(
          title: l10n.homeRecentlyAdded,
          inset: inset,
          shelf: true,
          child: _HomeRecentlyAddedShelf(
            items: recentlyAdded.items,
            trackCount: recentlyAdded.trackCount,
            inset: inset + 8,
          ),
        ),
      if (onRepeat.isNotEmpty)
        _HomeSection(
          title: l10n.homeOnRepeat,
          inset: inset,
          shelf: true,
          child: _HomeOnRepeatShelf(tracks: onRepeat, inset: inset + 8),
        ),
    ];

    // The Material Home always has its Shortcuts. The Sistrum Home has only
    // shelves, so an empty Library with nothing played would leave the page
    // blank: say so once the Library shelves have answered.
    if (sections.isEmpty) {
      final librarySettled = ref.watch(
        homeRecentlyAddedProvider.select((s) => s.hasValue || s.hasError),
      );
      if (librarySettled && !recentlyPlayedLoading) {
        return ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: MediaQuery.sizeOf(context).height * 0.5,
          ),
          child: EmptyState(
            icon: Icons.music_note,
            title: l10n.homeNothingYetTitle,
            message: l10n.homeNothingYetMessage,
          ),
        );
      }
    }

    return Padding(
      padding: const EdgeInsets.only(top: 8, bottom: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final (index, section) in sections.indexed) ...[
            if (index > 0) const SizedBox(height: 28),
            section,
          ],
        ],
      ),
    );
  }
}

class _HomeSection extends StatelessWidget {
  const _HomeSection({
    required this.title,
    required this.inset,
    required this.child,
    this.shelf = false,
    this.onShowAll,
  });

  final String title;
  final double inset;
  final Widget child;

  /// Shelves scroll edge to edge and pad their own items.
  final bool shelf;

  /// Makes the title a navigation action, marked with a chevron.
  final VoidCallback? onShowAll;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final heading = Text(
      title,
      style: Theme.of(context).textTheme.titleMedium?.copyWith(
        color: colorScheme.onSurface,
        fontSize: isDesktopPlatform ? 15 : null,
        fontWeight: FontWeight.w600,
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: EdgeInsets.fromLTRB(inset + 8, 0, inset + 8, 12),
          child: Semantics(
            header: true,
            child: onShowAll == null
                ? heading
                : Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: onShowAll,
                      child: MouseRegion(
                        cursor: SystemMouseCursors.click,
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Flexible(child: heading),
                            const SizedBox(width: 4),
                            Icon(
                              CupertinoIcons.chevron_forward,
                              size: 15,
                              color: colorScheme.onSurfaceVariant,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
          ),
        ),
        if (shelf)
          child
        else
          Padding(
            padding: EdgeInsets.symmetric(horizontal: inset),
            child: child,
          ),
      ],
    );
  }
}

/// The active track or the last dismissed session, with its saved progress.
class _HomeContinueListeningCard extends ConsumerStatefulWidget {
  const _HomeContinueListeningCard();

  @override
  ConsumerState<_HomeContinueListeningCard> createState() =>
      _HomeContinueListeningCardState();
}

class _HomeContinueListeningCardState
    extends ConsumerState<_HomeContinueListeningCard> {
  static const _coverSize = 96.0;
  bool _resuming = false;

  void _openPlayer() {
    if (isDesktopPlatform) {
      openDesktopNowPlaying();
    } else {
      Navigator.of(
        context,
        rootNavigator: true,
      ).push(NowPlayingRoute(child: const NowPlayingScreen()));
    }
  }

  Future<void> _resume({bool openPlayer = false}) async {
    if (_resuming) return;
    setState(() => _resuming = true);
    try {
      final resumed = await ref
          .read(musicPlayerControllerProvider)
          .resumeSavedSession();
      if (!mounted) return;
      ref.invalidate(playbackResumeProvider);
      if (resumed && openPlayer) _openPlayer();
    } finally {
      if (mounted) setState(() => _resuming = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final current = ref.watch(currentMediaItemProvider).value;
    final resume = ref.watch(playbackResumeProvider).value;
    final mediaItem = current ?? resume?.media.toMediaItem();
    if (mediaItem == null) return const SizedBox.shrink();

    final isPlaying = current != null && ref.watch(playbackPlayingProvider);
    final isLoading =
        _resuming || (current != null && ref.watch(playbackLoadingProvider));
    final controller = ref.read(musicPlayerControllerProvider);
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final artist = mediaItem.artist ?? '';
    final devicePixelRatio = MediaQuery.devicePixelRatioOf(context);
    // Mornye uses a neutral surface for the resume card.
    final onContainer = colorScheme.onSurface;
    final dimmed = colorScheme.onSurfaceVariant;
    final playColor = colorScheme.onSurface;
    final onPlayColor = colorScheme.surface;

    Widget artwork(double extent) => PlayerArtwork(
      // A network cover not stored yet shows once fetched.
      artUri:
          mediaItem.artUri?.toString() ??
          mediaItem.extras?['artworkRef']?.toString(),
      colorScheme: colorScheme,
      cacheWidth: (extent * devicePixelRatio).round(),
      iconSize: 32,
    );

    final title = Text(
      mediaItem.title,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: textTheme.titleLarge?.copyWith(color: onContainer),
    );
    final artistLine = Text(
      artist,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: textTheme.bodyMedium?.copyWith(color: dimmed),
    );
    final progress = _HomeContinueListeningProgress(
      duration: mediaItem.duration ?? Duration.zero,
      savedPosition: current == null ? resume?.position : null,
    );
    final playButton = Tooltip(
      message: isPlaying ? context.l10n.actionPause : context.l10n.tooltipPlay,
      child: Semantics(
        button: true,
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: isLoading
                ? null
                : current == null
                ? _resume
                : () => controller.togglePlayPause(isPlaying),
            // Round like the mini player's play button, so the same action
            // keeps the same shape across screens.
            child: Ink(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: playColor,
                shape: BoxShape.circle,
              ),
              child: Center(
                child: isLoading
                    ? AppLoadingIndicator(size: 24, color: onPlayColor)
                    : Icon(
                        isPlaying ? Icons.pause : Icons.play_arrow,
                        size: 28,
                        color: onPlayColor,
                      ),
              ),
            ),
          ),
        ),
      ),
    );

    final Widget content = Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(12, 12, 16, 12),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: SizedBox.square(
              dimension: _coverSize,
              child: artwork(_coverSize),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                title,
                if (artist.isNotEmpty) artistLine,
                const SizedBox(height: 12),
                progress,
              ],
            ),
          ),
          const SizedBox(width: 12),
          playButton,
        ],
      ),
    );

    return Material(
      color: colorScheme.surfaceContainerHigh,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: isLoading
            ? null
            : current == null
            ? () => _resume(openPlayer: true)
            : _openPlayer,
        child: content,
      ),
    );
  }
}

/// Follows position ticks on its own so the card above does not rebuild on
/// every one.
class _HomeContinueListeningProgress extends ConsumerWidget {
  const _HomeContinueListeningProgress({
    required this.duration,
    this.savedPosition,
  });

  final Duration duration;
  final Duration? savedPosition;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final durationMs = duration.inMilliseconds;
    final Duration position =
        savedPosition ?? ref.watch(playbackPositionProvider);
    final positionMs = position.inMilliseconds;
    final progress = durationMs > 0
        ? (positionMs / durationMs).clamp(0.0, 1.0)
        : 0.0;
    // Repaints on every position tick without repainting the sections.
    return RepaintBoundary(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(2),
        child: LinearProgressIndicator(
          value: progress,
          minHeight: 4,
          color: colorScheme.onSurface,
          backgroundColor: colorScheme.onSurface.withValues(alpha: 0.16),
        ),
      ),
    );
  }
}

/// A horizontal row of cover tiles that scrolls past the page margin.
class _HomeShelf extends StatelessWidget {
  const _HomeShelf({
    required this.inset,
    required this.itemCount,
    required this.itemBuilder,
    this.coverSize,
  });

  final double inset;
  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;

  /// Null for the platform's shelf cover size.
  final double? coverSize;

  @override
  Widget build(BuildContext context) {
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final coverSize = this.coverSize ?? _homeShelfCoverSize;
    return SizedBox(
      height: coverSize + 52 * textScale,
      child: DesktopScrollArrows(
        builder: (context, controller) => ListView.separated(
          controller: controller,
          scrollDirection: Axis.horizontal,
          padding: EdgeInsets.symmetric(horizontal: inset),
          itemCount: itemCount,
          separatorBuilder: (_, _) =>
              const SizedBox(width: _homeMornyeShelfGap),
          itemBuilder: itemBuilder,
        ),
      ),
    );
  }
}

/// Hands [builder] the shelf cover size, fitting about six covers across a
/// Mornye desktop pane.
class _HomeShelfSize extends StatelessWidget {
  const _HomeShelfSize({required this.inset, required this.builder});

  final double inset;
  final Widget Function(BuildContext context, double coverSize) builder;

  @override
  Widget build(BuildContext context) {
    if (!isDesktopPlatform) return builder(context, 160);
    return LayoutBuilder(
      builder: (context, constraints) {
        final content = constraints.maxWidth - 2 * inset;
        final perPane = (content + _homeMornyeShelfGap) / 6.1;
        return builder(
          context,
          (perPane - _homeMornyeShelfGap).clamp(130.0, 280.0),
        );
      },
    );
  }
}

class _HomeShelfTile extends StatelessWidget {
  const _HomeShelfTile({
    required this.cover,
    required this.title,
    required this.subtitle,
    required this.semanticLabel,
    required this.onTap,
    this.size,
  });

  final Widget cover;
  final String title;
  final String subtitle;
  final String semanticLabel;
  final VoidCallback onTap;

  /// Null for the platform's shelf cover size.
  final double? size;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final size = this.size ?? _homeShelfCoverSize;
    const radius = 10.0;
    final compactText = isDesktopPlatform;
    return Semantics(
      button: true,
      label: semanticLabel,
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(radius),
        child: SizedBox(
          width: size,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(radius),
                child: SizedBox.square(dimension: size, child: cover),
              ),
              const SizedBox(height: 8),
              Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: textTheme.labelLarge?.copyWith(
                  color: colorScheme.onSurface,
                  fontSize: compactText ? 13 : null,
                  fontWeight: compactText ? FontWeight.w400 : null,
                ),
              ),
              Text(
                subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: textTheme.bodySmall?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                  fontSize: compactText ? 13 : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HomeRecentlyAddedShelf extends ConsumerWidget {
  const _HomeRecentlyAddedShelf({
    required this.items,
    required this.trackCount,
    required this.inset,
  });

  final List<UnifiedLibraryItem> items;
  final int trackCount;
  final double inset;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    return _HomeShelfSize(
      inset: inset,
      builder: (context, coverSize) {
        final cacheWidth = (coverSize * MediaQuery.devicePixelRatioOf(context))
            .round();
        return _HomeShelf(
          inset: inset,
          coverSize: coverSize,
          itemCount: items.length + 1,
          itemBuilder: (context, index) {
            if (index == items.length) {
              return _HomeLibraryTile(trackCount: trackCount, size: coverSize);
            }
            final item = items[index];
            return _HomeShelfTile(
              size: coverSize,
              title: item.trackName,
              subtitle: item.artistName,
              semanticLabel: context.l10n.a11yOpenTrackByArtist(
                item.trackName,
                item.artistName,
              ),
              cover: _homeLibraryCover(
                item,
                colorScheme,
                coverSize,
                cacheWidth,
              ),
              onTap: () => _play(ref, index),
            );
          },
        );
      },
    );
  }

  void _play(WidgetRef ref, int index) {
    final media = <PlayableMedia>[];
    var initialIndex = 0;
    for (final (i, item) in items.indexed) {
      final playable = _homePlayableFromLibrary(item);
      if (playable == null || playable.source.trim().isEmpty) continue;
      if (i == index) initialIndex = media.length;
      media.add(playable);
    }
    if (media.isEmpty) return;
    ref
        .read(musicPlayerControllerProvider)
        .playAll(media, initialIndex: initialIndex);
  }
}

/// The favorite artists' latest albums and singles, newest first.
/// A Library track's own artwork: a download's embedded cover with its
/// online cover as fallback, or a scanned file's extracted thumbnail.
Widget _homeLibraryCover(
  UnifiedLibraryItem item,
  ColorScheme colorScheme,
  double size,
  int cacheWidth,
) {
  return PlayerArtwork(
    artUri: item.localCoverPath,
    colorScheme: colorScheme,
    cacheWidth: cacheWidth,
    iconSize: 32,
  );
}

PlayableMedia? _homePlayableFromLibrary(UnifiedLibraryItem item) =>
    switch (item) {
      UnifiedLibraryItem(:final localItem?) => playableFromLocal(localItem),
      _ => null,
    };

/// Ends the Recently added shelf with a way into the whole Library.
class _HomeLibraryTile extends StatelessWidget {
  const _HomeLibraryTile({required this.trackCount, required this.size});

  final int trackCount;
  final double size;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return Align(
      alignment: Alignment.topCenter,
      child: SizedBox.square(
        dimension: size,
        child: Material(
          color: colorScheme.surfaceContainerHigh,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: () => ShellNavigationService.requestTab(ShellTab.library),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _HomeIconBadge(
                    icon: Icons.library_music,
                    color: colorScheme.secondaryContainer,
                    onColor: colorScheme.onSecondaryContainer,
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        context.l10n.navLibrary,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: textTheme.labelLarge?.copyWith(
                          color: colorScheme.onSurface,
                        ),
                      ),
                      Text(
                        context.l10n.tracksCount(trackCount),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: textTheme.bodySmall?.copyWith(
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _HomeOnRepeatShelf extends ConsumerWidget {
  const _HomeOnRepeatShelf({required this.tracks, required this.inset});

  final List<HomeRepeatTrack> tracks;
  final double inset;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    return _HomeShelfSize(
      inset: inset,
      builder: (context, coverSize) {
        final cacheWidth = (coverSize * MediaQuery.devicePixelRatioOf(context))
            .round();
        return _HomeShelf(
          inset: inset,
          coverSize: coverSize,
          itemCount: tracks.length,
          itemBuilder: (context, index) {
            final (:track, :item) = tracks[index];
            return _HomeShelfTile(
              size: coverSize,
              title: track.title,
              subtitle: track.artist,
              semanticLabel: context.l10n.a11yOpenTrackByArtist(
                track.title,
                track.artist,
              ),
              cover: item != null
                  ? _homeLibraryCover(item, colorScheme, coverSize, cacheWidth)
                  : PlayerArtwork(
                      artUri: track.artwork,
                      colorScheme: colorScheme,
                      cacheWidth: cacheWidth,
                      iconSize: 32,
                    ),
              onTap: () => _homePlayRepeatTracks(ref, tracks, index),
            );
          },
        );
      },
    );
  }
}

/// Recently played tracks displayed as large covers named by album.
/// Choosing one plays from that song.
class _HomeRecentlyPlayedShelf extends ConsumerWidget {
  const _HomeRecentlyPlayedShelf({required this.tracks, required this.inset});

  final List<HomeRepeatTrack> tracks;
  final double inset;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    return _HomeShelfSize(
      inset: inset,
      builder: (context, coverSize) {
        final cacheWidth = (coverSize * MediaQuery.devicePixelRatioOf(context))
            .round();
        return _HomeShelf(
          inset: inset,
          coverSize: coverSize,
          itemCount: tracks.length,
          itemBuilder: (context, index) {
            final (:track, :item) = tracks[index];
            final title = track.album.isEmpty ? track.title : track.album;
            return _HomeShelfTile(
              size: coverSize,
              title: title,
              subtitle: track.artist,
              semanticLabel: context.l10n.a11yOpenTrackByArtist(
                title,
                track.artist,
              ),
              cover: item != null
                  ? _homeLibraryCover(item, colorScheme, coverSize, cacheWidth)
                  : PlayerArtwork(
                      artUri: track.artwork,
                      colorScheme: colorScheme,
                      cacheWidth: cacheWidth,
                      iconSize: 32,
                    ),
              onTap: () => _homePlayRepeatTracks(ref, tracks, index),
            );
          },
        );
      },
    );
  }
}

void _homePlayRepeatTracks(
  WidgetRef ref,
  List<HomeRepeatTrack> tracks,
  int index,
) => ref.read(musicPlayerControllerProvider).playAll([
  for (final (:track, :item) in tracks)
    (item == null ? null : _homePlayableFromLibrary(item)) ??
        PlayableMedia(
          id: track.key,
          source: track.key,
          title: track.title,
          artist: track.artist,
          album: track.album,
          artUri: track.artwork,
        ),
], initialIndex: index);

/// Circular icon container shared by the Home tiles. Interactive tiles keep
/// one plain shape; abstract shapes stay on artwork and decorative moments.
class _HomeIconBadge extends StatelessWidget {
  const _HomeIconBadge({
    required this.icon,
    required this.color,
    required this.onColor,
  });

  final IconData icon;
  final Color color;
  final Color onColor;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(color: color, shape: BoxShape.circle),
    child: SizedBox.square(
      dimension: 40,
      child: Icon(icon, size: 20, color: onColor),
    ),
  );
}

/// Mornye's first section: the day's Library mixes as banners.
class _HomeMixBannersSection extends ConsumerWidget {
  const _HomeMixBannersSection({required this.inset});

  final double inset;

  @override
  Widget build(BuildContext context, WidgetRef ref) => _HomeSection(
    title: '',
    inset: inset,
    shelf: true,
    child: _HomeMixBanners(
      mixes: ref.watch(libraryMixesProvider.select((s) => s.value)) ?? const [],
      inset: inset + 8,
    ),
  );
}

/// One banner per mix, in a horizontal row, as Mornye's own cards.
class _HomeMixBanners extends StatelessWidget {
  const _HomeMixBanners({required this.mixes, required this.inset});

  static const _gap = 16.0;

  final List<LibraryMixView> mixes;
  final double inset;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final seeds = mornyeBannerSeeds([
      for (final view in mixes) '${view.mix.kind.name}/${view.mix.key}',
    ]);
    return LayoutBuilder(
      builder: (context, constraints) {
        final content = constraints.maxWidth - 2 * inset;
        final width = isDesktopPlatform
            ? ((content + _gap) / 4.6 - _gap).clamp(180.0, 320.0)
            : math.min(content - 44, 280.0);
        return SizedBox(
          height: width / MornyeMixBanner.aspectRatio,
          child: DesktopScrollArrows(
            builder: (context, controller) => ListView.separated(
              controller: controller,
              scrollDirection: Axis.horizontal,
              padding: EdgeInsets.symmetric(horizontal: inset),
              itemCount: mixes.length,
              separatorBuilder: (_, _) => const SizedBox(width: _gap),
              itemBuilder: (context, index) {
                final view = mixes[index];
                return ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Stack(
                    children: [
                      MornyeMixBanner(
                        seed: seeds[index],
                        title: libraryMixTitle(l10n, view.mix),
                        eyebrow: '',
                        subtitle: libraryMixSubtitle(l10n, view.mix),
                        width: width,
                      ),
                      Positioned.fill(
                        child: Material(
                          color: Colors.transparent,
                          child: InkWell(
                            onTap: () => pushPage<void>(
                              context,
                              (_) => LibraryMixScreen(view: view),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        );
      },
    );
  }
}
