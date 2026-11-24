part of 'track_metadata_screen.dart';

extension _TrackMetadataLyricsAndSaving on _TrackMetadataScreenState {
  Widget _buildLyricsCard(BuildContext context, ColorScheme colorScheme) {
    final hasDisplayLyrics = _lyrics?.trim().isNotEmpty == true;
    final hasDisplaySource =
        (hasDisplayLyrics || _isInstrumental) &&
        _lyricsSource?.trim().isNotEmpty == true;
    return _metadataSectionSurface(
      context,
      child: Padding(
        padding: const EdgeInsets.all(_trackMetadataSectionPadding),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: _buildSectionTitle(context, context.l10n.trackLyrics),
                ),
                if (hasDisplayLyrics)
                  AppIconButton(
                    icon: Icon(CupertinoIcons.doc_on_doc, size: 20),
                    onPressed: () => _copyToClipboard(context, _lyrics!),
                    tooltip: context.l10n.trackCopyLyrics,
                  ),
              ],
            ),
            if (hasDisplaySource)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  context.l10n.trackLyricsSource(_lyricsSource!),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            const SizedBox(height: 12),

            if (_lyricsLoading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(20),
                  child: AppLoadingIndicator(),
                ),
              )
            // Status rows sit flat on the lyrics card instead of a second box.
            else if (_isInstrumental)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.music_note,
                      color: colorScheme.onSurfaceVariant,
                      size: 20,
                    ),
                    const SizedBox(width: 12),
                    Text(
                      context.l10n.trackInstrumental,
                      style: TextStyle(
                        color: colorScheme.onSurfaceVariant,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ],
                ),
              )
            else if (hasDisplayLyrics)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // A visible scrollbar and a fade at the cut show that the
                  // box scrolls, instead of a line clipped in half. Full
                  // width keeps the scrollbar at the card's edge rather than
                  // after the longest line.
                  Container(
                    width: double.infinity,
                    constraints: const BoxConstraints(maxHeight: 300),
                    child: ScrollEdgeFade(
                      size: 48,
                      fadeContent: true,
                      child: Scrollbar(
                        controller: _lyricsScrollController,
                        thumbVisibility: isDesktopPlatform,
                        child: SingleChildScrollView(
                          controller: _lyricsScrollController,
                          child: Text(
                            _lyrics!,
                            style: Theme.of(context).textTheme.bodyMedium
                                ?.copyWith(
                                  color: colorScheme.onSurface,
                                  height: 1.6,
                                ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  if (!_lyricsEmbedded && _canEditMetadataFile) ...[
                    const SizedBox(height: 16),
                    Center(
                      child: FilledButton.tonalIcon(
                        onPressed: _isEmbedding ? null : _embedLyrics,
                        icon: _isEmbedding
                            ? const AppLoadingIndicator(
                                size: 18,
                                strokeWidth: 2,
                              )
                            : const Icon(Icons.save_alt),
                        label: Text(context.l10n.trackEmbedLyrics),
                      ),
                    ),
                  ],
                ],
              )
            else if (_embeddedLyricsChecked && _fileExists)
              Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      children: [
                        Icon(
                          Icons.lyrics_outlined,
                          color: colorScheme.onSurfaceVariant,
                          size: 20,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            context.l10n.trackLyricsNotInFile,
                            style: TextStyle(
                              color: colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }

  /// The lyrics reader can miss files outside the extension file grants.
  /// Fall back to the same local tag reader used by Now Playing before
  /// reporting that a file has no lyrics. Neither call fetches online lyrics.
  Future<Map<String, dynamic>> _readLocalLyrics(String sourcePath) async {
    if (sourcePath.startsWith('network://')) {
      final metadata = await readPlaybackFileMetadataWithRetry(sourcePath);
      return {'lyrics': metadata['lyrics']?.toString() ?? '', 'source': ''};
    }
    try {
      final result = await PlatformBridge.getLyricsLRCWithSource(
        sourcePath,
      ).timeout(const Duration(seconds: 5));
      if (hasUsableLyricsContent(result['lyrics']?.toString() ?? '')) {
        return result;
      }
    } catch (e) {
      _log.d('Local lyrics reader failed; trying playback metadata: $e');
    }
    final metadata = await readPlaybackFileMetadataWithRetry(
      sourcePath,
    ).timeout(const Duration(seconds: 5));
    final lyrics = metadata['lyrics']?.toString() ?? '';
    if (hasUsableLyricsContent(lyrics)) return {'lyrics': lyrics, 'source': ''};
    // An Android document's .lrc file is out of the lyrics reader's reach.
    String? lrc;
    try {
      lrc = await const LyricsSidecarStore(
        extension: lrcExtension,
      ).read(sourcePath).timeout(const Duration(seconds: 5));
    } catch (e) {
      _log.d('Could not read the .lrc lyrics: $e');
    }
    return {
      'lyrics': lrc != null && hasUsableLyricsContent(lrc) ? lrc : lyrics,
      'source': '',
    };
  }

  /// Check for lyrics embedded in the audio file only (no network requests).
  /// Called automatically when the screen opens.
  Future<void> _checkEmbeddedLyrics() async {
    if (_lyricsLoading || !_fileExists) return;
    final generation = _metadataLoadGeneration;
    final sourcePath = cleanFilePath;
    if (!mounted) return;

    _setState(() {
      _lyricsLoading = true;
      _isInstrumental = false;
      _lyricsSource = null;
    });

    try {
      final embeddedResult = await _readLocalLyrics(sourcePath);

      final embeddedLyrics = embeddedResult['lyrics']?.toString() ?? '';
      final embeddedSource = embeddedResult['source']?.toString() ?? '';

      if (mounted &&
          generation == _metadataLoadGeneration &&
          sourcePath == cleanFilePath) {
        final instrumental = isInstrumentalLyricsMarker(embeddedLyrics);
        final cleanLyrics = cleanLyricsForDisplay(embeddedLyrics);
        if (instrumental || cleanLyrics.isNotEmpty) {
          _setState(() {
            _lyrics = instrumental ? null : cleanLyrics;
            _rawLyrics = instrumental ? null : embeddedLyrics;
            _lyricsSource = embeddedSource.isNotEmpty
                ? embeddedSource
                : context.l10n.trackLyricsEmbeddedSource;
            _lyricsEmbedded = true;
            _isInstrumental = instrumental;
            _lyricsLoading = false;
            _embeddedLyricsChecked = true;
          });
        } else {
          _setState(() {
            _lyrics = null;
            _rawLyrics = null;
            _lyricsSource = null;
            _lyricsEmbedded = false;
            _lyricsLoading = false;
            _embeddedLyricsChecked = true;
          });
        }
      }
    } catch (e) {
      if (mounted &&
          generation == _metadataLoadGeneration &&
          sourcePath == cleanFilePath) {
        _setState(() {
          _lyricsLoading = false;
          _embeddedLyricsChecked = true;
        });
      }
    }
  }

  Future<void> _embedLyrics() async {
    if (_isEmbedding || _rawLyrics == null || !_fileExists) return;
    _setState(() => _isEmbedding = true);

    final l10nFailedToWriteStorage = context.l10n.snackbarFailedToWriteStorage;
    final l10nFailedToEmbedLyrics = context.l10n.snackbarFailedToEmbedLyrics;
    final l10nUnsupportedFormat = context.l10n.snackbarUnsupportedAudioFormat;

    String? safTempPath;
    String? coverPath;

    try {
      final rawLyrics = _rawLyrics!;
      var workingPath = cleanFilePath;

      if (_isSafFile) {
        safTempPath = await PlatformBridge.copyContentUriToTemp(cleanFilePath);
        if (safTempPath == null || safTempPath.isEmpty) {
          throw Exception('Failed to access SAF file');
        }
        workingPath = safTempPath;
      }

      final lower = workingPath.toLowerCase();
      final isFlac = lower.endsWith('.flac');
      final isMp3 = lower.endsWith('.mp3');
      final isOpus = lower.endsWith('.opus') || lower.endsWith('.ogg');
      final isM4A = lower.endsWith('.m4a') || lower.endsWith('.aac');

      bool success = false;
      String? error;

      if (isFlac) {
        final result = await PlatformBridge.embedLyricsToFile(
          workingPath,
          rawLyrics,
        );
        if (result['success'] == true) {
          if (_isSafFile) {
            final ok = await PlatformBridge.writeTempToSaf(
              workingPath,
              cleanFilePath,
            );
            success = ok;
            if (!ok) {
              error = l10nFailedToWriteStorage;
            }
          } else {
            success = true;
          }
        } else {
          error = userFacingErrorMessage(
            result['error'],
            fallback: l10nFailedToEmbedLyrics,
          );
        }
      } else if (isMp3 || isOpus || isM4A) {
        final metadata = _buildFallbackMetadata();
        try {
          final result = await PlatformBridge.readFileMetadata(workingPath);
          if (result['error'] == null) {
            // Map separately so a non-explicit file keeps the fallback advisory.
            final mapped = <String, String>{};
            mergePlatformMetadataForTagEmbed(target: mapped, source: result);
            metadata.addAll(mapped);
          }
        } catch (e) {
          _log.w('Failed reading file metadata before lyrics embed: $e');
        }

        metadata['LYRICS'] = rawLyrics;
        metadata['UNSYNCEDLYRICS'] = rawLyrics;

        try {
          final tempDir = await getTemporaryDirectory();
          final coverOutput =
              '${tempDir.path}${Platform.pathSeparator}lyrics_cover_${DateTime.now().millisecondsSinceEpoch}.jpg';
          final coverResult = await PlatformBridge.extractCoverToFile(
            workingPath,
            coverOutput,
          );
          if (coverResult['error'] == null) {
            coverPath = coverOutput;
          }
        } catch (_) {}

        // Embedding lyrics rewrites the tags read above; it must not drop
        // existing artist credits, so the primary mode keeps them joined.
        final artistTagMode = artistTagModeForExistingTags(
          ref.read(settingsProvider).artistTagMode,
        );
        String? ffmpegResult;
        if (isMp3) {
          ffmpegResult = await FFmpegMetadata.embedMetadataToMp3(
            mp3Path: workingPath,
            coverPath: coverPath,
            metadata: metadata,
          );
        } else if (isM4A) {
          ffmpegResult = await FFmpegMetadata.embedMetadataToM4a(
            m4aPath: workingPath,
            coverPath: coverPath,
            metadata: metadata,
          );
        } else {
          ffmpegResult = await FFmpegMetadata.embedMetadataToOpus(
            opusPath: workingPath,
            coverPath: coverPath,
            metadata: metadata,
            artistTagMode: artistTagMode,
          );
        }

        if (ffmpegResult == null) {
          error = l10nFailedToEmbedLyrics;
        } else if (_isSafFile) {
          final ok = await PlatformBridge.writeTempToSaf(
            ffmpegResult,
            cleanFilePath,
          );
          success = ok;
          if (!ok) {
            error = l10nFailedToWriteStorage;
          }
        } else {
          success = true;
        }
      } else {
        error = l10nUnsupportedFormat;
      }

      if (mounted) {
        if (success) {
          _setState(() {
            _lyricsEmbedded = true;
            _isEmbedding = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(context.l10n.trackLyricsEmbedded)),
          );
        } else {
          _setState(() => _isEmbedding = false);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(error ?? context.l10n.snackbarFailedToEmbedLyrics),
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        _setState(() => _isEmbedding = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.snackbarError(context.friendlyError(e))),
          ),
        );
      }
    } finally {
      if (coverPath != null) {
        try {
          await File(coverPath).delete();
        } catch (_) {}
      }
      if (safTempPath != null) {
        try {
          await File(safTempPath).delete();
        } catch (_) {}
      }
    }
  }

  String _sanitizeFileNameSegment(String value) {
    var sanitized = value
        .replaceAll(_TrackMetadataScreenState._invalidFileNameChars, '_')
        .trim();
    sanitized = sanitized.replaceAll(
      _TrackMetadataScreenState._leadingOrTrailingDots,
      '',
    );
    sanitized = sanitized.replaceAll(
      _TrackMetadataScreenState._multiUnderscore,
      '_',
    );
    if (sanitized.isEmpty) {
      return 'untitled';
    }
    return sanitized;
  }

  String _buildSaveBaseName() {
    final artist = _sanitizeFileNameSegment(artistName);
    final track = _sanitizeFileNameSegment(trackName);
    return '$artist - $track';
  }

  String _getFileDirectory() {
    if (isContentUri(cleanFilePath)) {
      // SAF URIs don't have a filesystem parent directory
      return '';
    }
    final file = File(cleanFilePath);
    return file.parent.path;
  }

  bool get _isSafFile => isContentUri(cleanFilePath);

  /// A folder the user chose, as a SAF tree on Android and otherwise a path,
  /// with a security-scoped bookmark on iOS. Null when they cancel.
  Future<({String? treeUri, String? path, String? bookmark})?>
  _pickSidecarFolder() async {
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        final picked = await PlatformBridge.pickSafTree();
        final treeUri = picked?['tree_uri'] as String? ?? '';
        if (treeUri.isEmpty) return null;
        return (treeUri: treeUri, path: null, bookmark: null);
      case TargetPlatform.iOS:
        final picked = await PlatformBridge.pickIosDirectory();
        if (picked == null) return null;
        return (treeUri: null, path: picked.path, bookmark: picked.bookmark);
      default:
        final path = await FilePicker.getDirectoryPath();
        if (path == null) return null;
        return (treeUri: null, path: path, bookmark: null);
    }
  }

  /// Writes `baseName.extension` as a sidecar of the current track.
  ///
  /// `writeTo` renders the file at the given path and returns the bridge
  /// result, or null when it has already told the user why nothing was
  /// written. SAF and network tracks are rendered into a temp directory and
  /// published to the download tree or the scanned audio document's parent,
  /// or uploaded to the song's NAS folder, replacing a file of that name as
  /// a local save does; filesystem tracks are written next to the audio
  /// file. Once the audio file is gone, its old folder says nothing about
  /// where the song went, so the user picks the folder instead.
  /// Exceptions propagate to the caller.
  Future<void> _publishSidecarFile({
    required String baseName,
    required String tempDirPrefix,
    required String extension,
    required String mimeType,
    required String Function() successMessage,
    required bool hideCurrentSnackBar,
    required Future<Map<String, dynamic>?> Function(String outputPath) writeTo,
  }) async {
    void showMessage(String message) {
      final messenger = ScaffoldMessenger.of(context);
      if (hideCurrentSnackBar) {
        messenger.hideCurrentSnackBar();
      }
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }

    final fileName = '$baseName.$extension';
    final hasFile = _fileExists;
    final folder = hasFile ? null : await _pickSidecarFolder();
    if (!mounted) return;
    if (!hasFile && folder == null) {
      if (hideCurrentSnackBar) {
        ScaffoldMessenger.of(context).hideCurrentSnackBar();
      }
      return;
    }

    // Renders the file in a temp directory for `publish`, which says whether
    // it reached its place.
    Future<void> publishFromTemp(
      Future<bool> Function(String tempOutput) publish,
    ) async {
      final tempDir = await Directory.systemTemp.createTemp(tempDirPrefix);
      final tempOutput = '${tempDir.path}${Platform.pathSeparator}$fileName';
      try {
        final result = await writeTo(tempOutput);
        if (result == null) return;
        if (result['error'] != null) {
          if (mounted) {
            showMessage(
              context.l10n.trackSaveFailed(
                context.friendlyError(result['error']),
              ),
            );
          }
          return;
        }
        final published = await publish(tempOutput);
        if (mounted) {
          showMessage(
            published
                ? successMessage()
                : context.l10n.trackSaveFailed('No storage access'),
          );
        }
      } finally {
        try {
          await tempDir.delete(recursive: true);
        } catch (_) {}
      }
    }

    if (folder == null ? _isSafFile : folder.treeUri != null) {
      final audioUri = cleanFilePath;
      // A song beside its file takes the file's own SAF folder, as HEAD did.
      final treeUri = folder?.treeUri;
      const relativeDir = '';
      await publishFromTemp((tempOutput) async {
        final safUri = treeUri != null && treeUri.isNotEmpty
            ? await PlatformBridge.createSafFileFromPath(
                treeUri: treeUri,
                relativeDir: relativeDir,
                fileName: fileName,
                mimeType: mimeType,
                srcPath: tempOutput,
              )
            : await PlatformBridge.writeSafSidecarFromPath(
                audioUri: audioUri,
                fileName: fileName,
                mimeType: mimeType,
                srcPath: tempOutput,
              );
        return safUri != null && safUri.isNotEmpty;
      });
      return;
    }

    final bookmark = folder?.bookmark;
    final access = bookmark == null
        ? null
        : await PlatformBridge.startAccessingIosBookmark(bookmark);
    if (bookmark != null && access == null) {
      if (mounted) {
        showMessage(context.l10n.trackSaveFailed('No storage access'));
      }
      return;
    }
    final dir = access?.path ?? folder?.path ?? _getFileDirectory();
    final outputPath = '$dir${Platform.pathSeparator}$fileName';

    final Map<String, dynamic>? result;
    try {
      result = await writeTo(outputPath);
    } finally {
      if (access != null) {
        await PlatformBridge.stopAccessingIosBookmark(access);
      }
    }
    if (result == null) {
      return;
    }

    if (mounted) {
      if (result['error'] != null) {
        showMessage(
          context.l10n.trackSaveFailed(context.friendlyError(result['error'])),
        );
      } else {
        showMessage(successMessage());
      }
    }
  }

  Future<void> _saveCoverArt() async {
    try {
      final baseName = _buildSaveBaseName();
      final audioPath = cleanFilePath;
      final cachedCover = _embeddedCoverPreviewPath ?? _localCoverPath;
      final hasFile = _fileExists;
      Future<bool> copyCover(String? path, String outputPath) async {
        if (path == null || path.isEmpty) return false;
        final uri = Uri.tryParse(path);
        final file = uri?.scheme == 'file' ? File.fromUri(uri!) : File(path);
        if (!await file.exists() || await file.length() == 0) return false;
        if (file.absolute.path != File(outputPath).absolute.path) {
          await file.copy(outputPath);
        }
        return true;
      }

      await _publishSidecarFile(
        baseName: baseName,
        tempDirPrefix: 'cover_',
        extension: 'jpg',
        mimeType: 'image/jpeg',
        successMessage: () => context.l10n.trackCoverSaved(baseName),
        hideCurrentSnackBar: false,
        writeTo: (outputPath) async {
          Map<String, dynamic>? result;
          if (hasFile) {
            // Imported files carry their own embedded artwork.
            result = await PlatformBridge.extractCoverToFile(
              audioPath,
              outputPath,
            );
          }
          if ((result == null || result['error'] != null) &&
              await copyCover(cachedCover, outputPath)) {
            return {'success': true};
          }
          if (result == null && mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(context.l10n.trackCoverNoSource)),
            );
          }
          return result;
        },
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              context.l10n.trackSaveFailed(context.friendlyError(e)),
            ),
          ),
        );
      }
    }
  }

  Future<void> _saveLyrics() async {
    try {
      final baseName = _buildSaveBaseName();
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(content: Text(context.l10n.trackSaveLyricsProgress)),
          );
      }

      await _publishSidecarFile(
        baseName: baseName,
        tempDirPrefix: 'lyrics_',
        extension: 'lrc',
        mimeType: 'application/octet-stream',
        successMessage: () => context.l10n.trackLyricsSaved(baseName),
        hideCurrentSnackBar: true,
        writeTo: (outputPath) async {
          var lyrics = _rawLyrics ?? _lyrics ?? '';
          if (!hasUsableLyricsContent(lyrics)) {
            // Lyrics not shown yet: read the song's own lyrics, as the screen does.
            lyrics =
                (await _readLocalLyrics(cleanFilePath))['lyrics']?.toString() ??
                '';
          }
          if (!hasUsableLyricsContent(lyrics)) return null;
          await File(outputPath).writeAsString(lyrics);
          return {'success': true};
        },
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text(
                context.l10n.trackSaveFailed(context.friendlyError(e)),
              ),
            ),
          );
      }
    }
  }
}
