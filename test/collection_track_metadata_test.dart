import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/download_history.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/unified_library_item.dart';

// Ported from the online album, playlist and artist pages: the library tracks
// that now fill those pages keep the file metadata they carry.
void main() {
  test('a downloaded library song keeps its file metadata in its track', () {
    final track = UnifiedLibraryItem.fromDownloadHistory(
      DownloadHistoryItem(
        id: 'example-download',
        trackName: 'Example Song',
        artistName: 'Example Artist',
        albumName: 'Example Album',
        albumArtist: 'Example Album Artist',
        filePath: '/music/example.flac',
        service: 'example-provider',
        downloadedAt: DateTime.utc(2026),
        isrc: 'USEXA0000001',
        trackNumber: 1,
        discNumber: 2,
        duration: 180,
        releaseDate: '2026-01-02',
      ),
    ).toTrack();

    expect(track.name, 'Example Song');
    expect(track.artistName, 'Example Artist');
    expect(track.albumArtist, 'Example Album Artist');
    expect(track.duration, 180);
    expect(track.trackNumber, 1);
    expect(track.discNumber, 2);
    expect(track.isrc, 'USEXA0000001');
    expect(track.releaseDate, '2026-01-02');
  });

  test('a scanned library song keeps its file metadata in its track', () {
    final track = UnifiedLibraryItem.fromLocalLibrary(
      LocalLibraryItem(
        id: 'example-local',
        trackName: 'Example Song',
        artistName: 'Example Artist',
        albumName: 'Example Album',
        albumArtist: 'Example Album Artist',
        filePath: '/music/example.flac',
        scannedAt: DateTime.utc(2026),
        isrc: 'USEXA0000002',
        trackNumber: 3,
        discNumber: 1,
        duration: 200,
        releaseDate: '2025-05-06',
      ),
    ).toTrack();

    expect(track.name, 'Example Song');
    expect(track.albumArtist, 'Example Album Artist');
    expect(track.duration, 200);
    expect(track.trackNumber, 3);
    expect(track.discNumber, 1);
    expect(track.isrc, 'USEXA0000002');
    expect(track.releaseDate, '2025-05-06');
    expect(track.source, 'local');
  });
}
