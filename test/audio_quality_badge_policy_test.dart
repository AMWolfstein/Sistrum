import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/utils/audio_quality_badge_policy.dart';

void main() {
  test('Library local cards retain labels for legacy Opus rows', () {
    final local = UnifiedLibraryItem.fromLocalLibrary(
      LocalLibraryItem(
        id: 'local-song',
        trackName: 'Song',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/music/Song.opus',
        scannedAt: DateTime(2026),
      ),
    );
    expect(local.badgeQuality, 'OPUS');
  });

  group('Library audio quality badge color', () {
    test('keeps legacy 24-bit labels highlighted', () {
      expect(shouldHighlightAudioQualityBadge('24-bit/96kHz'), isTrue);
      expect(shouldHighlightAudioQualityBadge('FLAC 24bit-48kHz'), isTrue);
      expect(shouldHighlightAudioQualityBadge('24/192kHz'), isTrue);
    });

    test('highlights only measured bitrates above 900 kbps', () {
      expect(shouldHighlightAudioQualityBadge('900kbps'), isFalse);
      expect(shouldHighlightAudioQualityBadge('901kbps'), isTrue);
      expect(shouldHighlightAudioQualityBadge('FLAC 1760 kbps'), isTrue);
      expect(shouldHighlightAudioQualityBadge('1.76 Mbps'), isTrue);
    });

    test('leaves normal lossy bitrate labels neutral', () {
      expect(shouldHighlightAudioQualityBadge('AAC 320kbps'), isFalse);
      expect(shouldHighlightAudioQualityBadge('OPUS 256k'), isFalse);
      expect(shouldHighlightAudioQualityBadge('16-bit/44.1kHz'), isFalse);
    });
  });

  group('Library audio quality badge', () {
    test('keeps an Opus badge when bitrate has not been backfilled', () {
      expect(
        buildLibraryAudioQualityLabel(
          format: 'opus',
          bitDepth: 16,
          storedQuality: '16-bit/44.1kHz',
        ),
        'OPUS',
      );
    });

    test('uses stored Opus bitrate until measured bitrate is available', () {
      expect(
        buildLibraryAudioQualityLabel(
          format: 'opus',
          storedQuality: 'OPUS 320kbps',
        ),
        'OPUS 320kbps',
      );
      expect(
        buildLibraryAudioQualityLabel(
          format: 'opus',
          bitrateKbps: 256,
          storedQuality: 'OPUS 320kbps',
        ),
        'OPUS 256kbps',
      );
      expect(formatLibraryGridAudioQualityLabel('OPUS 320kbps'), '320k');
      expect(formatLibraryGridAudioQualityLabel('OPUS'), 'OPUS');
    });

    test('uses the path or SAF name when a legacy row has no format', () {
      expect(
        buildLibraryAudioQualityLabel(
          filePath: '/music/Song.opus',
          storedQuality: '16-bit/44.1kHz',
        ),
        'OPUS',
      );
      expect(
        buildLibraryAudioQualityLabel(
          filePath: 'content://music/document/42',
          fileName: 'Song.OPUS',
        ),
        'OPUS',
      );
    });

    test('shows only the bit depth of lossless audio', () {
      expect(
        buildLibraryAudioQualityLabel(
          format: 'flac',
          bitrateKbps: 1411,
          bitDepth: 16,
          storedQuality: '16-bit/44.1kHz',
        ),
        '16-bit',
      );
      expect(
        buildLibraryAudioQualityLabel(
          format: 'flac',
          storedQuality: '24-bit/96kHz',
        ),
        '24-bit',
      );
    });

    test('keeps bitrate meaningful for lossy formats', () {
      expect(
        buildLibraryAudioQualityLabel(
          format: 'mp3',
          bitrateKbps: 320,
          bitDepth: 16,
        ),
        'MP3 320kbps',
      );
      expect(
        buildLibraryAudioQualityLabel(
          format: 'aac',
          bitrateKbps: 256,
          bitDepth: 16,
        ),
        'AAC 256kbps',
      );
    });

    test('falls back to the measured bitrate when bit depth is unknown', () {
      expect(
        buildLibraryAudioQualityLabel(
          format: 'flac',
          bitrateKbps: 950,
          storedQuality: 'LOSSLESS',
        ),
        'FLAC 950kbps',
      );
      expect(
        buildLibraryAudioQualityLabel(
          format: 'flac',
          storedQuality: 'FLAC 1411kbps',
        ),
        'FLAC 1411kbps',
      );
    });
  });

  group('Library grid audio quality label', () {
    test('keeps both parts of detailed bit-depth labels', () {
      expect(
        formatLibraryGridAudioQualityLabel('16-bit/44.1kHz'),
        '16-bit/44.1kHz',
      );
      expect(
        formatLibraryGridAudioQualityLabel('16-bit/1411kbps'),
        '16-bit/1411kbps',
      );
      expect(isDetailedLibraryAudioQualityLabel('16-bit/44.1kHz'), isTrue);
    });

    test('keeps bitrate-only grid labels compact', () {
      expect(formatLibraryGridAudioQualityLabel('FLAC 1760kbps'), '1760k');
      expect(formatLibraryGridAudioQualityLabel('Bitrate 1760kbps'), '1760k');
      expect(isDetailedLibraryAudioQualityLabel('FLAC 1760kbps'), isFalse);
    });
  });
}
