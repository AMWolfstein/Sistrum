part of 'track_metadata_screen.dart';

extension _TrackMetadataHeader on _TrackMetadataScreenState {
  List<Widget> _headerMetadataItems(BuildContext context) => [
    if (_displayAudioQuality != null && _displayAudioQuality!.isNotEmpty)
      HeaderMetaItem(_displayAudioQuality!),
    if (duration != null) HeaderMetaItem(formatClock(duration!)),
    // The header's title is plain text, so the badge sits with the details.
    if (isExplicit)
      Builder(
        builder: (header) =>
            ExplicitBadge(colorScheme: HeaderPalette.of(header)),
      ),
    if (_service == 'local')
      HeaderMetaItem(
        context.l10n.librarySourceLocal,
        icon: CupertinoIcons.folder,
      )
    else if (_service.isNotEmpty)
      HeaderMetaItem(_service[0].toUpperCase() + _service.substring(1)),
  ];

  /// The cover on its way between a list row and the header: this page's
  /// cover, with corners that go from the row thumbnail's to the header's.
  /// Without it the cover would fly with square corners and get them back
  /// on landing. The flight's animation is at 1 on this page both ways.
  Widget _coverFlight(
    BuildContext flight,
    Animation<double> animation,
    HeroFlightDirection direction,
    BuildContext from,
    BuildContext to,
  ) {
    final header = direction == HeroFlightDirection.push ? to : from;
    // The desktop Mornye header clips with continuous corners instead.
    final headerCorners =
        header.findAncestorWidgetOfExactType<ClipRRect>()?.borderRadius ??
        header.findAncestorWidgetOfExactType<ClipRSuperellipse>()?.borderRadius;
    final corners = BorderRadiusTween(
      begin: flight.tokens.borderRadiusThumb,
      end: headerCorners?.resolve(Directionality.of(flight)),
    ).animate(animation);
    return AnimatedBuilder(
      animation: corners,
      builder: (context, cover) => ClipRRect(
        borderRadius: corners.value ?? BorderRadius.zero,
        child: cover,
      ),
      child: (header.widget as Hero).child,
    );
  }

  /// The header albums and playlists have, for one song: its cover, title,
  /// artist and album, the file's quality and the song's main actions. The
  /// rest of the actions stay in the More menu.
  ///
  /// The slivers go straight into the page's scroll view, so the toolbar
  /// stays pinned once the cover and details have scrolled away.
  List<Widget> _buildHeader(BuildContext context, ColorScheme colorScheme) {
    final coverCacheWidth = coverCacheWidthForViewport(context);
    final backdropCacheWidth = metadataBackdropCacheExtent(context);
    // Downloaded-item entry points must await resolveOrExtract before pushing
    // this route. That precondition keeps the Hero, foreground, and backdrop
    // on one source from the first frame; this synchronous lookup also covers
    // cached rebuilds and in-screen swipe navigation.
    final sharedEmbeddedCoverPath = _hasPath(_embeddedCoverPreviewPath)
        ? _embeddedCoverPreviewPath
        : DownloadedEmbeddedCoverResolver.resolve(_filePath);
    final artworkSource = resolveMetadataArtworkSource(
      embeddedCoverPath: sharedEmbeddedCoverPath,
      localCoverPath: _localCoverPath,
    );

    Widget placeholder(double iconSize) => ColoredBox(
      color: colorScheme.surfaceContainerHighest,
      child: Center(
        child: AppIcon.adaptive(
          Icons.music_note,
          size: iconSize,
          color: colorScheme.onSurfaceVariant,
        ),
      ),
    );

    Widget artworkImage({
      required int cacheWidth,
      int? cacheHeight,
      required double iconSize,
    }) {
      final source = artworkSource;
      if (source == null) return placeholder(iconSize);
      final filePath = coverFilePath(source);
      if (filePath == null) return placeholder(iconSize);
      return Image.file(
        File(filePath),
        fit: BoxFit.cover,
        cacheWidth: cacheWidth,
        cacheHeight: cacheHeight,
        gaplessPlayback: true,
        filterQuality: FilterQuality.low,
        errorBuilder: (_, _, _) => placeholder(iconSize),
      );
    }

    // Watched here: the header builds its actions outside this build.
    final showPlayer = ref.watch(playerUiEnabledProvider);
    final back = HeaderCircleButton(
      icon: CupertinoIcons.back,
      tooltip: MaterialLocalizations.of(context).backButtonTooltip,
      onPressed: _popWithMetadataResult,
    );
    return AlbumDetailHeader(
      title: trackName,
      expandedHeight: calculateExpandedHeight(context),
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: RepaintBoundary(
        key: ValueKey<String>('metadata_backdrop_$_itemId'),
        child: artworkImage(
          cacheWidth: backdropCacheWidth,
          cacheHeight: backdropCacheWidth,
          iconSize: 80,
        ),
      ),
      paletteSource: coverFilePath(artworkSource),
      blurAndScrimBackground: artworkSource != null,
      // The header rounds the cover outside the Hero, so the flight rounds
      // it itself.
      coverBuilder: (context, coverSize) => Hero(
        tag: _coverHeroTag,
        flightShuttleBuilder: _coverFlight,
        child: SizedBox.square(
          dimension: coverSize,
          child: artworkImage(cacheWidth: coverCacheWidth, iconSize: 48),
        ),
      ),
      // Built inside the header, so it reads the header's artwork scheme.
      subtitle: Builder(builder: _buildHeaderSubtitle),
      meta: HeaderMetaRow(items: _headerMetadataItems(context)),
      actions: Builder(
        builder: (header) => _buildHeaderActions(header, showPlayer),
      ),
      appBarActions: [
        Builder(
          builder: (buttonContext) => HeaderCircleButton(
            icon: CupertinoIcons.ellipsis,
            tooltip: MaterialLocalizations.of(context).showMenuTooltip,
            onPressed: () => _showOptionsMenu(
              context,
              ref,
              colorScheme,
              anchor: mornyeMenuAnchor(buttonContext),
            ),
          ),
        ),
      ],
      leading: back,
    ).buildSlivers(context);
  }

  /// The artist with the album on a line below it, each cut short on its
  /// own so a long artist does not push the album out.
  Widget _buildHeaderSubtitle(BuildContext header) {
    final scheme = HeaderPalette.of(header);
    final artistStyle = isDesktopPlatform
        ? desktopDetailSubtitleStyle(header)
        : Theme.of(header).textTheme.titleMedium?.copyWith(
            color: Theme.of(header).colorScheme.primary,
            fontWeight: FontWeight.w400,
          );
    final album = albumName.trim();
    Text line(String text, {TextStyle? style}) => Text(
      text,
      style: style,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: isDesktopPlatform ? TextAlign.start : TextAlign.center,
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: isDesktopPlatform
          ? CrossAxisAlignment.start
          : CrossAxisAlignment.center,
      children: [
        line(artistName, style: artistStyle),
        if (album.isNotEmpty)
          line(
            album,
            style: Theme.of(
              header,
            ).textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
          ),
      ],
    );
  }

  /// Play as the one wide filled button, between a round Edit and a round
  /// Add to queue. A round button is left out where the song cannot take
  /// its action; Play stays, disabled, while the file is missing.
  ///
  /// Until the file has been checked the song is taken to be there, so the
  /// row has its buttons from the first frame of the page's entrance instead
  /// of gaining them a moment later. Edit on a network song still waits to
  /// learn whether its connection accepts writes.
  Widget _buildHeaderActions(BuildContext header, bool showPlayer) {
    final tokens = header.tokens;
    final l10n = header.l10n;
    final unchecked = _fileAccess == null;
    final hasFile = _fileExists || unchecked;
    final play = HeaderFilledButton(
      icon: Icons.play_arrow,
      label: l10n.trackMetadataPlay,
      onPressed: hasFile ? () => _openFile(context, rawFilePath) : null,
    );
    final edit = (unchecked || _canEditMetadataFile)
        ? HeaderCircleButton(
            icon: Icons.edit_outlined,
            tooltip: l10n.trackEditMetadata,
            tonal: true,
            onPressed: () => _showEditMetadataSheet(
              context,
              ref,
              Theme.of(context).colorScheme,
            ),
          )
        : null;
    final queue = hasFile && showPlayer
        ? HeaderCircleButton(
            icon: Icons.playlist_add,
            tooltip: l10n.trackAddToQueue,
            tonal: true,
            onPressed: () => _enqueueThis(ref, playNext: false),
          )
        : null;
    if (isDesktopPlatform) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        spacing: tokens.gapSm,
        children: [play, ?edit, ?queue],
      );
    }
    return Row(
      spacing: tokens.gapMd,
      children: [
        ?edit,
        Expanded(child: play),
        ?queue,
      ],
    );
  }

  /// The surface of one section of the page.
  Widget _metadataSectionSurface(
    BuildContext context, {
    required Widget child,
  }) {
    return Material(
      color: MornyeTheme.controlFill(context),
      // The radius of Mornye's grouped lists (see SettingsGroup).
      borderRadius: BorderRadius.circular(28),
      clipBehavior: Clip.antiAlias,
      child: DividerTheme(
        data: DividerTheme.of(
          context,
        ).copyWith(color: MornyeTheme.metadataDividerColor(context)),
        child: child,
      ),
    );
  }
}
