// ignore_for_file: invalid_use_of_protected_member, invalid_use_of_visible_for_testing_member
part of 'track_metadata_screen.dart';

// Embedded-cover preview: screen-local validation/dimension metadata keyed by
// track, while DownloadedEmbeddedCoverResolver owns extraction and temp files.
extension _TrackMetadataCover on _TrackMetadataScreenState {
  bool _isCacheTrackedPath(String? path) {
    if (!_hasPath(path)) return false;
    return _TrackMetadataScreenState._embeddedCoverPreviewCache.values.any(
          (entry) => entry.previewPath == path,
        ) ||
        DownloadedEmbeddedCoverResolver.isManagedPreviewPath(path);
  }

  bool _isVolatileSafTempPath(String path) {
    if (path.isEmpty) return false;
    return path.contains(
      '${Platform.pathSeparator}cache${Platform.pathSeparator}saf_',
    );
  }

  Future<String?> _readLocalFileValidationToken(String path) async {
    if (path.isEmpty || isContentUri(path) || _isVolatileSafTempPath(path)) {
      return null;
    }
    try {
      final stat = await fileStat(path);
      if (stat == null) return null;
      return '${stat.modified?.millisecondsSinceEpoch ?? 0}:${stat.size ?? 0}';
    } catch (_) {
      return null;
    }
  }

  Future<void> _cacheEmbeddedCoverPreview(
    String cacheKey,
    String sourcePath,
    String previewPath,
    ({int width, int height})? dimensions,
  ) async {
    final sourceValidationToken = await _readLocalFileValidationToken(
      sourcePath,
    );
    final existing =
        _TrackMetadataScreenState._embeddedCoverPreviewCache[cacheKey];
    _TrackMetadataScreenState._embeddedCoverPreviewCache[cacheKey] =
        _EmbeddedCoverPreviewCacheEntry(
          previewPath: previewPath,
          sourceValidationToken: sourceValidationToken,
          dimensions: dimensions,
        );
    if (existing != null && existing.previewPath != previewPath) {
      await _cleanupTempFileAndParentIfNotCached(existing.previewPath);
    }

    while (_TrackMetadataScreenState._embeddedCoverPreviewCache.length >
        _TrackMetadataScreenState._maxCoverPreviewCacheEntries) {
      final oldestKey =
          _TrackMetadataScreenState._embeddedCoverPreviewCache.keys.first;
      final removed = _TrackMetadataScreenState._embeddedCoverPreviewCache
          .remove(oldestKey);
      if (removed != null) {
        await _cleanupTempFileAndParentIfNotCached(removed.previewPath);
      }
    }
  }

  Future<void> _invalidateEmbeddedCoverPreviewCacheForPath(
    String cacheKey,
  ) async {
    if (cacheKey.isEmpty) return;
    final removed = _TrackMetadataScreenState._embeddedCoverPreviewCache.remove(
      cacheKey,
    );
    if (removed != null) {
      await _cleanupTempFileAndParentIfNotCached(removed.previewPath);
    }
  }

  Future<_EmbeddedCoverPreviewCacheEntry?>
  _getCachedEmbeddedCoverPreviewIfValid(
    String cacheKey,
    String sourcePath,
  ) async {
    if (cacheKey.isEmpty) return null;
    final cached =
        _TrackMetadataScreenState._embeddedCoverPreviewCache[cacheKey];
    if (cached == null) return null;

    if (!await fileExists(cached.previewPath)) {
      _TrackMetadataScreenState._embeddedCoverPreviewCache.remove(cacheKey);
      return null;
    }

    if (!isContentUri(sourcePath) && !_isVolatileSafTempPath(sourcePath)) {
      final currentToken = await _readLocalFileValidationToken(sourcePath);
      if (currentToken != null &&
          cached.sourceValidationToken != null &&
          currentToken != cached.sourceValidationToken) {
        _TrackMetadataScreenState._embeddedCoverPreviewCache.remove(cacheKey);
        await _cleanupTempFileAndParentIfNotCached(cached.previewPath);
        return null;
      }
    }

    return cached;
  }

  Future<void> _refreshEmbeddedCoverPreview({bool force = false}) async {
    final generation = _metadataLoadGeneration;
    final cacheKey = _coverCacheKey;
    final sourcePath = cleanFilePath;
    final oldPreviewPath = _embeddedCoverPreviewPath;

    if (!_fileExists) {
      await _invalidateEmbeddedCoverPreviewCacheForPath(cacheKey);
      await DownloadedEmbeddedCoverResolver.invalidate(sourcePath);
      if (mounted &&
          generation == _metadataLoadGeneration &&
          sourcePath == cleanFilePath) {
        setState(() {
          _embeddedCoverPreviewPath = null;
          _embeddedCoverDimensions = null;
        });
      }
      return;
    }

    if (force) {
      await _invalidateEmbeddedCoverPreviewCacheForPath(cacheKey);
      await DownloadedEmbeddedCoverResolver.scheduleRefreshForPath(
        sourcePath,
        force: true,
      );
    }

    String? newPreviewPath;
    ({int width, int height})? newDimensions;
    try {
      newPreviewPath = await DownloadedEmbeddedCoverResolver.resolveOrExtract(
        sourcePath,
      );
      if (newPreviewPath != null) {
        final cachedCover = force
            ? null
            : await _getCachedEmbeddedCoverPreviewIfValid(cacheKey, sourcePath);
        if (cachedCover != null && cachedCover.previewPath == newPreviewPath) {
          newDimensions = cachedCover.dimensions;
        } else {
          newDimensions = await FFmpegProbe.imageDimensions(newPreviewPath);
          await _cacheEmbeddedCoverPreview(
            cacheKey,
            sourcePath,
            newPreviewPath,
            newDimensions,
          );
        }
      }
    } catch (_) {}

    if (!mounted ||
        generation != _metadataLoadGeneration ||
        sourcePath != cleanFilePath) {
      return;
    }
    // A song opened on its library cover changes to the file's own picture
    // here, often while the cover still flies in. Decoded first, the new
    // picture is there in the frame the cover lands or changes; otherwise
    // the cover blinks empty for a frame.
    if (newPreviewPath != null && newPreviewPath != oldPreviewPath) {
      final cover = coverImageProvider(
        newPreviewPath,
        cacheWidth: coverCacheWidthForViewport(context),
      );
      await Future.wait([
        if (cover != null)
          precacheImage(
            cover,
            context,
            onError: (_, _) {},
          ).timeout(const Duration(milliseconds: 400), onTimeout: () {}),
        precacheMetadataBackdrop(context, newPreviewPath),
      ]);
      if (!mounted ||
          generation != _metadataLoadGeneration ||
          sourcePath != cleanFilePath) {
        return;
      }
    }

    setState(() {
      _embeddedCoverPreviewPath = newPreviewPath;
      _embeddedCoverDimensions = newDimensions;
    });
    if (oldPreviewPath != null && oldPreviewPath != newPreviewPath) {
      await _cleanupTempFileAndParentIfNotCached(oldPreviewPath);
    }
  }

  LocalLibraryItem get _localLibraryItem => _currentLocalLibraryItem!;
  bool get _hasLocalNavigation =>
      widget.localNavigationItems != null && widget.navigationIndex != null;
  bool get _hasTrackSwipeNavigation => _hasLocalNavigation;
  int? get _navigationIndex => _currentNavigationIndex;
  int get _navigationLength => widget.localNavigationItems?.length ?? 0;

  String get _itemId => _localLibraryItem.id;
  String get trackName =>
      _editedMetadata?['title']?.toString() ?? _localLibraryItem.trackName;
  String get artistName =>
      _editedMetadata?['artist']?.toString() ?? _localLibraryItem.artistName;
  String get albumName =>
      _editedMetadata?['album']?.toString() ?? _localLibraryItem.albumName;
  String? get albumArtist {
    final edited = _editedMetadata?['album_artist']?.toString();
    if (edited != null && edited.isNotEmpty) return edited;
    return normalizeOptionalString(_localLibraryItem.albumArtist);
  }

  int? get trackNumber {
    final edited = _editedMetadata?['track_number'];
    if (edited != null) {
      final v = int.tryParse(edited.toString());
      if (v != null && v > 0) return v;
    }
    return _localLibraryItem.trackNumber;
  }

  int? get totalTracks =>
      readPositiveInt(_editedMetadata?['total_tracks']) ??
      _localLibraryItem.totalTracks;

  int? get discNumber {
    final edited = _editedMetadata?['disc_number'];
    if (edited != null) {
      final v = int.tryParse(edited.toString());
      if (v != null && v > 0) return v;
    }
    return _localLibraryItem.discNumber;
  }

  int? get totalDiscs =>
      readPositiveInt(_editedMetadata?['total_discs']) ??
      _localLibraryItem.totalDiscs;

  String? get releaseDate =>
      _editedMetadata?['date']?.toString() ?? _localLibraryItem.releaseDate;
  String? get isrc {
    final raw =
        _editedMetadata?['isrc']?.toString() ?? _localLibraryItem.isrc;
    if (raw == null || raw.trim().isEmpty) return null;
    final upper = raw.trim().toUpperCase();
    // Only accept valid ISRC codes (CC-XXX-YY-NNNNN, 12 alphanumeric chars).
    // Strip hyphens/spaces that some sources include.
    final stripped = upper.replaceAll(RegExp(r'[-\s]'), '');
    if (_isrcValidationPattern.hasMatch(stripped)) return stripped;
    return null;
  }

  static final RegExp _isrcValidationPattern = RegExp(
    r'^[A-Z]{2}[A-Z0-9]{3}\d{7}$',
  );
  String? get genre =>
      _editedMetadata?['genre']?.toString() ?? _localLibraryItem.genre;
  String? get label =>
      _editedMetadata?['label']?.toString() ?? _localLibraryItem.label;
  String? get copyright =>
      _editedMetadata?['copyright']?.toString() ?? _localLibraryItem.copyright;
  String? get composer =>
      _editedMetadata?['composer']?.toString() ?? _localLibraryItem.composer;
  String? get albumType =>
      normalizeOptionalString(_editedMetadata?['album_type']?.toString());
  String? get upc => normalizeOptionalString(
    (_editedMetadata?['upc'] ?? _editedMetadata?['barcode'])?.toString(),
  );
  String? get comment =>
      normalizeOptionalString(_editedMetadata?['comment']?.toString());
  bool get isExplicit =>
      parseExplicitFlag(_editedMetadata?['explicit']) ??
      _localLibraryItem.explicit;
  int? get duration =>
      readPositiveInt(_editedMetadata?['duration']) ??
      _localLibraryItem.duration;
  int? get bitDepth =>
      readPositiveInt(_editedMetadata?['bit_depth']) ??
      _localLibraryItem.bitDepth;
  int? get sampleRate =>
      readPositiveInt(_editedMetadata?['sample_rate']) ??
      _localLibraryItem.sampleRate;
  int? get _audioBitrate => _localLibraryItem.bitrate;
  String? get _storedAudioFormat =>
      _resolvedAudioFormat ?? _localLibraryItem.format;

  String get _filePath => _localLibraryItem.filePath;
  String get _coverHeroTag =>
      widget.coverHeroTag ?? 'cover_lib_$_itemId';

  String? get _localCoverPath =>
      normalizeOptionalString(_editedMetadata?['cover_path']?.toString()) ??
      _localLibraryItem.coverPath;
  String get _service => MusicServices.local;
  DateTime get _addedAt {
    final modTime = _localLibraryItem.fileModTime;
    if (modTime != null && modTime > 0) {
      return DateTime.fromMillisecondsSinceEpoch(modTime);
    }
    return _localLibraryItem.scannedAt;
  }

  int? _readPlausibleBitrateKbps(dynamic value) {
    final parsed = readPositiveInt(value);
    if (parsed == null) return null;
    final kbps = parsed >= 10000 ? (parsed / 1000).round() : parsed;
    return kbps >= 16 ? kbps : null;
  }
}
