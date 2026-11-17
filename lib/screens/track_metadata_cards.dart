part of 'track_metadata_screen.dart';

/// Content width, inside the page padding, at which the cards split into two
/// columns of about 470 or more each.
const double _trackMetadataTwoColumnMinWidth = 960;

/// The content width for a window [width]: the reading width on phones and the
/// wider two-column width on large windows.
double _trackMetadataContentMaxWidth(double width) =>
    width >= _trackMetadataTwoColumnMinWidth + 32
    ? math.min(width, largeContentMaxWidth)
    : adaptiveContentMaxWidth(width);

extension _TrackMetadataCards on _TrackMetadataScreenState {
  List<Widget> _headerMetadataItems(BuildContext context) => [
    if (_displayAudioQuality != null && _displayAudioQuality!.isNotEmpty)
      HeaderMetaItem(_displayAudioQuality!),
    if (duration != null) HeaderMetaItem(formatClock(duration!)),
    if (_service == 'local')
      HeaderMetaItem(
        context.l10n.librarySourceLocal,
        icon: CupertinoIcons.folder,
      )
    else if (_service.isNotEmpty)
      HeaderMetaItem(_service[0].toUpperCase() + _service.substring(1)),
  ];

  Widget _buildAnimatedTrackContent(
    BuildContext context,
    WidgetRef ref,
    ColorScheme colorScheme,
  ) {
    final currentKey = ValueKey<String>('metadata_content_$_itemId');
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 240),
      reverseDuration: const Duration(milliseconds: 180),
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      layoutBuilder: (currentChild, previousChildren) {
        return Stack(
          alignment: Alignment.topCenter,
          children: <Widget>[...previousChildren, ?currentChild],
        );
      },
      transitionBuilder: (child, animation) {
        if (_metadataTransitionDirection == 0) {
          return child;
        }
        final isIncoming = child.key == currentKey;
        final direction = _metadataTransitionDirection.toDouble();
        final begin = Offset(
          isIncoming ? 0.18 * direction : -0.18 * direction,
          0,
        );
        final curved = CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutCubic,
          reverseCurve: Curves.easeInCubic,
        );
        return ClipRect(
          child: SlideTransition(
            position: Tween<Offset>(
              begin: begin,
              end: Offset.zero,
            ).animate(curved),
            child: FadeTransition(opacity: animation, child: child),
          ),
        );
      },
      child: Padding(
        key: currentKey,
        padding: const EdgeInsets.all(16),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final details = [
              _buildMetadataCard(context, colorScheme, _fileSize),
              const SizedBox(height: 16),
              _buildFileInfoCard(context, colorScheme, _fileExists, _fileSize),
            ];
            final media = [
              _buildLyricsCard(context, colorScheme),
              if (_fileExists) ...[
                const SizedBox(height: 16),
                AudioAnalysisCard(
                  filePath: _filePath,
                  codecHint: _storedAudioFormat,
                  exportName: _buildSaveBaseName(),
                ),
                if (HiResCheckCard.isCandidate(
                  _filePath,
                  _storedAudioFormat,
                )) ...[
                  const SizedBox(height: 16),
                  HiResCheckCard(
                    filePath: _filePath,
                    formatHint: _storedAudioFormat,
                  ),
                ],
              ],
            ];
            Widget column(List<Widget> children) => Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: children,
            );
            final twoColumns =
                constraints.maxWidth >= _trackMetadataTwoColumnMinWidth;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // analysis instead of one long column of stretched rows.
                if (twoColumns)
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: column(details)),
                      const SizedBox(width: 16),
                      Expanded(child: column(media)),
                    ],
                  )
                else
                  column([...details, const SizedBox(height: 16), ...media]),

                const SizedBox(height: 32),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildMetadataCard(
    BuildContext context,
    ColorScheme colorScheme,
    int? fileSize,
  ) {
    return _metadataSectionSurface(
      context,
      colorScheme,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              context.l10n.trackMetadata,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
                color: colorScheme.onSurface,
              ),
            ),
            const SizedBox(height: 16),

            _buildMetadataGrid(context, colorScheme),

            if (upc case final barcodeValue?) ...[
              const SizedBox(height: 16),
              MetadataBarcode(
                value: barcodeValue,
                label: context.l10n.editMetadataFieldUpc,
                onCopy: () => _copyToClipboard(context, barcodeValue),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildMetadataGrid(BuildContext context, ColorScheme colorScheme) {
    final audioQualityStr = _displayAudioQuality;
    final replayGainTrackGain = normalizeOptionalString(
      _editedMetadata?['replaygain_track_gain']?.toString(),
    );
    final replayGainTrackPeak = normalizeOptionalString(
      _editedMetadata?['replaygain_track_peak']?.toString(),
    );
    final replayGainAlbumGain = normalizeOptionalString(
      _editedMetadata?['replaygain_album_gain']?.toString(),
    );
    final replayGainAlbumPeak = normalizeOptionalString(
      _editedMetadata?['replaygain_album_peak']?.toString(),
    );

    final items = <_MetadataItem>[
      _MetadataItem(context.l10n.trackTrackName, trackName),
      _MetadataItem(context.l10n.trackArtist, artistName),
      if (albumArtist != null && albumArtist != artistName)
        _MetadataItem(context.l10n.trackAlbumArtist, albumArtist!),
      _MetadataItem(context.l10n.trackAlbum, albumName),
      if (trackNumber != null && trackNumber! > 0)
        _MetadataItem(context.l10n.trackTrackNumber, trackNumber.toString()),
      if (totalTracks != null && totalTracks! > 0)
        _MetadataItem(
          context.l10n.editMetadataFieldTrackTotal,
          totalTracks.toString(),
        ),
      if (discNumber != null && discNumber! > 0)
        _MetadataItem(context.l10n.trackDiscNumber, discNumber.toString()),
      if (totalDiscs != null && totalDiscs! > 0)
        _MetadataItem(
          context.l10n.editMetadataFieldDiscTotal,
          totalDiscs.toString(),
        ),
      if (duration != null)
        _MetadataItem(context.l10n.trackDuration, formatClock(duration!)),
      if (audioQualityStr != null)
        _MetadataItem(context.l10n.trackAudioQuality, audioQualityStr),
      if (_editedMetadata?['bpm'] case final int bpm when bpm > 0)
        _MetadataItem(context.l10n.trackBpm, bpm.toString()),
      if (normalizeOptionalString(_editedMetadata?['mood']?.toString())
          case final String mood)
        _MetadataItem(context.l10n.trackMood, mood),
      if (replayGainTrackGain != null)
        _MetadataItem(
          context.l10n.trackReplayGainTrackGain,
          replayGainTrackGain,
        ),
      if (replayGainTrackPeak != null)
        _MetadataItem(
          context.l10n.trackReplayGainTrackPeak,
          replayGainTrackPeak,
        ),
      if (replayGainAlbumGain != null)
        _MetadataItem(
          context.l10n.trackReplayGainAlbumGain,
          replayGainAlbumGain,
        ),
      if (replayGainAlbumPeak != null)
        _MetadataItem(
          context.l10n.trackReplayGainAlbumPeak,
          replayGainAlbumPeak,
        ),
      if (_embeddedCoverDimensions case final dimensions?)
        _MetadataItem(
          context.l10n.trackCoverResolution,
          '${dimensions.width} × ${dimensions.height} px',
        ),
      if (releaseDate != null && releaseDate!.isNotEmpty)
        _MetadataItem(context.l10n.trackReleaseDate, releaseDate!),
      if (genre != null && genre!.isNotEmpty)
        _MetadataItem(context.l10n.trackGenre, genre!),
      if (label != null && label!.isNotEmpty)
        _MetadataItem(context.l10n.trackLabel, label!),
      if (copyright != null && copyright!.isNotEmpty)
        _MetadataItem(context.l10n.trackCopyright, copyright!),
      if (composer != null && composer!.isNotEmpty)
        _MetadataItem(context.l10n.editMetadataFieldComposer, composer!),
      if (albumType != null)
        _MetadataItem(context.l10n.trackAlbumType, albumType!),
      if (comment != null)
        _MetadataItem(context.l10n.editMetadataFieldComment, comment!),
      if (isrc != null && isrc!.isNotEmpty)
        _MetadataItem(
          'ISRC',
          formatIsrcForDisplay(isrc),
          rawValue: canonicalIsrcForCopy(isrc),
        ),
    ];

    items.add(
      _MetadataItem(context.l10n.trackMetadataService, _service.toUpperCase()),
    );
    items.add(
      _MetadataItem(context.l10n.trackDownloaded, _formatFullDate(_addedAt)),
    );

    return Column(
      children: items.map((metadata) {
        final isCopyable =
            metadata.label == 'ISRC' ||
            metadata.label == 'Spotify ID' ||
            metadata.label == 'Deezer ID' ||
            metadata.label == 'Amazon ASIN' ||
            metadata.label == 'Tidal ID' ||
            metadata.label == 'Qobuz ID';
        return MornyeMetadataRow(
          label: metadata.label,
          value: metadata.value,
          showDivider: metadata != items.last,
          trailing: isCopyable
              ? Icon(
                  CupertinoIcons.doc_on_doc,
                  size: 16,
                  color: colorScheme.onSurfaceVariant,
                )
              : null,
          onTap: isCopyable
              ? () => _copyToClipboard(context, metadata.rawValue)
              : null,
          onLongPress: () => _showMetadataCopySheet(
            context,
            metadata: metadata,
            allMetadata: items,
          ),
        );
      }).toList(),
    );
  }

  Future<void> _showMetadataCopySheet(
    BuildContext context, {
    required _MetadataItem metadata,
    required List<_MetadataItem> allMetadata,
  }) async {
    final action = await showAppBottomSheet<String>(
      context: context,
      title: metadata.label,
      subtitle: metadata.value,
      maxHeightFactor: 0.58,
      builder: (sheetContext) => ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.only(bottom: 12),
        children: [
          SettingsGroup(
            children: [
              SettingsItem(
                icon: Icons.content_copy_rounded,
                title: sheetContext.l10n.metadataCopyValue,
                onTap: () => Navigator.pop(sheetContext, 'value'),
              ),
              SettingsItem(
                icon: Icons.label_outline_rounded,
                title: sheetContext.l10n.metadataCopyField,
                onTap: () => Navigator.pop(sheetContext, 'field'),
              ),
              SettingsItem(
                icon: Icons.copy_all_rounded,
                title: sheetContext.l10n.metadataCopyAll,
                showDivider: false,
                onTap: () => Navigator.pop(sheetContext, 'all'),
              ),
            ],
          ),
        ],
      ),
    );
    if (!context.mounted || action == null) return;
    final text = switch (action) {
      'field' => '${metadata.label}: ${metadata.rawValue}',
      'all' =>
        allMetadata.map((item) => '${item.label}: ${item.rawValue}').join('\n'),
      _ => metadata.rawValue,
    };
    _copyToClipboard(context, text);
  }

  String _formatLabelForRaw(String raw) {
    final normalized = raw.toLowerCase().replaceAll('-', '_');
    return switch (normalized) {
      'flac' => 'FLAC',
      'alac' => 'ALAC',
      'wav' || 'wave' => 'WAV',
      'aiff' || 'aif' || 'aifc' => 'AIFF',
      'eac3' || 'ec_3' => 'EAC3',
      'ac3' || 'ac_3' => 'AC3',
      'ac4' || 'ac_4' => 'AC4',
      'aac' || 'mp4a' => 'AAC',
      'm4a' || 'mp4' => 'M4A',
      'mp3' => 'MP3',
      'opus' => 'Opus',
      'ogg' => 'OGG',
      _ => raw.toUpperCase(),
    };
  }

  String _displayFormatLabelForFile(String fileName) {
    final storedFormat = normalizeOptionalString(_storedAudioFormat);
    final raw =
        storedFormat ??
        (fileName.contains('.') ? fileName.split('.').last : 'Unknown');
    return _formatLabelForRaw(raw);
  }

  bool _isBitrateFormatLabel(String label) {
    return const {
      'MP3',
      'OPUS',
      'OGG',
      'M4A',
      'AAC',
      'EAC3',
      'AC3',
      'AC4',
    }.contains(label.toUpperCase());
  }

  /// One file detail pill. Only the format is emphasized, so the group keeps
  /// a single strong container; the others stay neutral.
  Widget _fileInfoPill(
    ColorScheme colorScheme,
    String label, {
    bool emphasized = false,
    IconData? icon,
  }) {
    final foreground = emphasized
        ? colorScheme.onPrimaryContainer
        : colorScheme.onSurfaceVariant;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: emphasized
            ? colorScheme.primaryContainer
            : colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: foreground),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(
              color: foreground,
              fontWeight: FontWeight.w600,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFileInfoCard(
    BuildContext context,
    ColorScheme colorScheme,
    bool fileExists,
    int? fileSize,
  ) {
    final displayFilePath = _formatPathForDisplay(rawFilePath);
    final fileName = _extractFileNameFromPathOrUri(rawFilePath);
    final fileExtension = _displayFormatLabelForFile(fileName);
    final resolvedQuality = _displayAudioQuality;
    final lossyBitrateLabel = _extractLossyBitrateLabel(resolvedQuality);

    return _metadataSectionSurface(
      context,
      colorScheme,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              context.l10n.trackFileInfo,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
                color: colorScheme.onSurface,
              ),
            ),
            const SizedBox(height: 16),

            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _fileInfoPill(colorScheme, fileExtension, emphasized: true),
                if (fileSize != null)
                  _fileInfoPill(colorScheme, formatBytes(fileSize)),
                if (_isBitrateFormatLabel(fileExtension) &&
                    lossyBitrateLabel != null)
                  _fileInfoPill(colorScheme, lossyBitrateLabel)
                else if (_audioBitrate != null &&
                    _audioBitrate! > 0 &&
                    _isBitrateFormatLabel(fileExtension))
                  _fileInfoPill(colorScheme, '${_audioBitrate}kbps')
                else if (bitDepth != null &&
                    bitDepth! > 0 &&
                    sampleRate != null)
                  _fileInfoPill(
                    colorScheme,
                    buildDisplayAudioQuality(
                          bitDepth: bitDepth,
                          sampleRate: sampleRate,
                        ) ??
                        '',
                  ),
                if (!_isBitrateFormatLabel(fileExtension) &&
                    _audioBitrate != null &&
                    _audioBitrate! > 0)
                  _fileInfoPill(colorScheme, '${_audioBitrate}kbps'),
                _fileInfoPill(
                  colorScheme,
                  _service.toUpperCase(),
                  icon: _getServiceIcon(_service),
                ),
              ],
            ),

            const SizedBox(height: 16),

            InkWell(
              onTap: () => _copyToClipboard(context, cleanFilePath),
              borderRadius: BorderRadius.circular(12),
              child: Container(
                constraints: BoxConstraints(
                  minHeight: context.tokens.minTouchTarget,
                ),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        displayFilePath,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontFamily: 'monospace',
                          color: colorScheme.onSurfaceVariant,
                        ),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Icon(
                      CupertinoIcons.doc_on_doc,
                      size: 18,
                      color: colorScheme.onSurfaceVariant,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
