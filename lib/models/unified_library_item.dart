import 'package:sistrum/models/library_collections.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/utils/audio_quality_badge_policy.dart';
import 'package:sistrum/utils/string_utils.dart';

enum LibraryItemSource { local }

/// A library track backed by a local-library scan record.
class UnifiedLibraryItem {
  final String id;
  final String trackName;
  final String artistName;
  final String albumName;
  final String? coverUrl;
  final String? localCoverPath;
  final String filePath;
  final String? quality;
  final DateTime addedAt;
  final LibraryItemSource source;

  final LocalLibraryItem? localItem;

  UnifiedLibraryItem({
    required this.id,
    required this.trackName,
    required this.artistName,
    required this.albumName,
    this.coverUrl,
    this.localCoverPath,
    required this.filePath,
    this.quality,
    required this.addedAt,
    required this.source,
    this.localItem,
  });

  factory UnifiedLibraryItem.fromLocalLibrary(LocalLibraryItem item) {
    return UnifiedLibraryItem(
      id: 'local_${item.id}',
      trackName: item.trackName,
      artistName: item.artistName,
      albumName: item.albumName,
      coverUrl: null,
      localCoverPath: item.coverPath,
      filePath: item.filePath,
      quality: buildDisplayAudioQuality(
        bitDepth: item.bitDepth,
        sampleRate: item.sampleRate,
        bitrateKbps: item.bitrate,
        format: item.format,
      ),
      addedAt: item.fileModTime != null
          ? DateTime.fromMillisecondsSinceEpoch(item.fileModTime!)
          : item.scannedAt,
      source: LibraryItemSource.local,
      localItem: item,
    );
  }

  String? qualityForMode(String mode) {
    final local = localItem;
    if (local != null) {
      return buildLibraryAudioQualityLabel(
        mode: mode,
        format: local.format,
        filePath: local.filePath,
        bitrateKbps: local.bitrate,
        bitDepth: local.bitDepth,
        sampleRate: local.sampleRate,
        storedQuality: quality,
      );
    }

    return quality;
  }

  /// Returns the collection key used to match this item against playlist
  /// entries: prefer ISRC, fall back to source:id.
  String get collectionKey {
    if (localItem != null) {
      return libraryTrackCollectionKey(
        id: localItem!.id,
        source: 'local',
        isrc: localItem!.isrc,
      );
    }
    return libraryTrackCollectionKey(id: id);
  }

  Track toTrack() {
    if (localItem != null) {
      final l = localItem!;
      return Track(
        id: l.id,
        name: l.trackName,
        artistName: l.artistName,
        albumName: l.albumName,
        albumArtist: l.albumArtist,
        coverUrl: l.coverPath,
        isrc: l.isrc,
        duration: l.duration ?? 0,
        trackNumber: l.trackNumber,
        discNumber: l.discNumber,
        releaseDate: l.releaseDate,
        source: 'local',
      );
    }
    return Track(
      id: id,
      name: trackName,
      artistName: artistName,
      albumName: albumName,
      coverUrl: coverUrl,
      duration: 0,
    );
  }
}
