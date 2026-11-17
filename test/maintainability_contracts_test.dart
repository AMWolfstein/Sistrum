import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/audio_metadata_mapper.dart';
import 'package:sistrum/services/ffmpeg_models.dart';
import 'package:sistrum/models/tag_credit_settings.dart';
import 'package:sistrum/services/id3v23_lyrics.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/utils/artist_utils.dart';

void main() {
  group('FFmpeg value models', () {
    test('normalizes structured and legacy decryption descriptors', () {
      final structured = DownloadDecryptionDescriptor.fromDownloadResult({
        'output_extension': 'M4A',
        'decryption': {
          'strategy': 'ffmpeg_mov_key',
          'key': ' secret ',
          'options': {'repair_ac4': true},
        },
      });
      final legacy = DownloadDecryptionDescriptor.fromDownloadResult({
        'decryption_key': 'legacy',
        'output_extension': '.flac',
      });

      expect(structured, isNotNull);
      expect(structured!.normalizedStrategy, 'ffmpeg.mov_key');
      expect(structured.normalizedOutputExtension, '.m4a');
      expect(structured.key, 'secret');
      expect(legacy!.inputFormat, 'mov');
      expect(legacy.normalizedOutputExtension, '.flac');
    });

    test('rejects empty or unsupported structured decryption requests', () {
      expect(
        DownloadDecryptionDescriptor.fromDownloadResult({
          'decryption': {'strategy': 'custom', 'key': 'secret'},
        }),
        isNull,
      );
      expect(
        DownloadDecryptionDescriptor.fromDownloadResult({
          'decryption': {'strategy': 'ffmpeg.mov_key', 'key': ' '},
        }),
        isNull,
      );
    });
  });

  group('audio metadata modules', () {
    test('maps shared tags consistently for native, M4A, and ID3 writers', () {
      final metadata = {
        'TITLE': 'Track',
        'ARTIST': 'Artist',
        'ALBUM': 'Album',
        'ALBUM_ARTIST': 'Album Artist',
        'TRACKNUMBER': '3/12',
        'DISCNUMBER': '1/2',
        'DATE': '2026-08-01',
        'GENRE': 'Pop',
        'ISRC': 'TEST12345678',
        'UNSYNCEDLYRICS': 'Lyrics',
        'ORGANIZATION': 'Label',
        'COPYRIGHT': 'Copyright',
        'COMPOSER': 'Composer',
        'COMMENT': 'Comment',
        'ITUNESADVISORY': '1',
        'REPLAYGAIN_TRACK_GAIN': '-5.00 dB',
        'BIT_DEPTH': '24',
      };

      final native = AudioMetadataMapper.vorbisToNativeChunkFields(metadata);
      final m4a = AudioMetadataMapper.convertToM4aTags(metadata);
      final m4aIdentity = AudioMetadataMapper.m4aReleaseIdentityFields(
        metadata,
      );
      final id3 = AudioMetadataMapper.convertToId3Tags(metadata);

      expect(native['title'], 'Track');
      expect(native['artist'], 'Artist');
      expect(native['album'], 'Album');
      expect(native['album_artist'], 'Album Artist');
      expect(native['track_number'], '3');
      expect(native['track_total'], '12');
      expect(native['disc_number'], '1');
      expect(native['disc_total'], '2');
      expect(native['replaygain_track_gain'], '-5.00 dB');
      expect(native['date'], '2026-08-01');
      expect(native['genre'], 'Pop');
      expect(native['label'], 'Label');
      expect(native['copyright'], 'Copyright');
      expect(native['composer'], 'Composer');
      expect(native['comment'], 'Comment');
      expect(native['explicit'], '1');
      expect(m4a['isrc'], 'TEST12345678');
      expect(m4a['lyrics'], 'Lyrics');
      expect(m4aIdentity['explicit'], '1');
      expect(id3['TSRC'], 'TEST12345678');
      expect(id3['ITUNESADVISORY'], '1');
      expect(id3['REPLAYGAIN_TRACK_GAIN'], '-5.00 dB');
      expect(id3['title'], 'Track');
      expect(id3['artist'], 'Artist');
      expect(id3['album'], 'Album');
      expect(id3['track'], '3/12');
      expect(id3['disc'], '1/2');
      expect(native, isNot(contains('bit_depth')));
    });

    test('builds split Vorbis artist entries without losing other tags', () {
      final entries = AudioMetadataMapper.buildVorbisMetadataEntries(
        {'ARTIST': 'First & Second', 'TITLE': 'Track'},
        artistTagMode: artistTagModeSplitVorbis,
        tagRules: const TagCreditRules(),
      );

      expect(
        entries.where((entry) => entry.key == 'ARTIST').map((e) => e.value),
        ['First', 'Second'],
      );
      expect(
        entries.where((entry) => entry.key == 'TITLE').single.value,
        'Track',
      );
      final primary = AudioMetadataMapper.buildVorbisMetadataEntries(
        {
          'ARTIST': 'First & Second',
          'ALBUMARTIST': 'First, Second',
          'TITLE': 'Track',
        },
        artistTagMode: artistTagModePrimary,
        tagRules: const TagCreditRules(),
      );
      expect(
        primary.where((entry) => entry.key == 'ARTIST').map((e) => e.value),
        ['First'],
      );
      expect(
        primary
            .where((entry) => entry.key == 'ALBUMARTIST')
            .map((e) => e.value),
        ['First'],
      );
      final advisory = AudioMetadataMapper.buildVorbisMetadataEntries({
        'EXPLICIT': 'true',
      }, tagRules: const TagCreditRules()).single;
      expect(advisory.key, 'ITUNESADVISORY');
      expect(advisory.value, '1');
    });

    test(
      'split Vorbis artists follow the rules passed in, not the defaults',
      () {
        List<String> artists(String value, TagCreditRules rules) => [
          for (final entry in AudioMetadataMapper.buildVorbisMetadataEntries(
            {'ARTIST': value},
            artistTagMode: artistTagModeSplitVorbis,
            tagRules: rules,
          ))
            if (entry.key == 'ARTIST') entry.value,
        ];
        // The default delimiters include the / chip, so AC/DC splits.
        expect(artists('AC/DC', const TagCreditRules()), ['AC', 'DC']);
        // Settings without the / chip keep AC/DC whole in the written tags.
        final withoutSlash = TagCreditRules(
          artistDelimiters: [
            for (final token in kArtistDelimiterChoices)
              if (token != '/') token,
          ],
        );
        expect(artists('AC/DC', withoutSlash), ['AC/DC']);
        // A custom delimiter splits its own way; the default rules do not.
        const pipe = TagCreditRules(artistDelimiters: ['|']);
        expect(artists('Band | Guest', pipe), ['Band', 'Guest']);
        expect(artists('Band | Guest', const TagCreditRules()), [
          'Band | Guest',
        ]);
      },
    );

    test('writes one ID3v2.3 USLT frame and replaces it on update', () {
      final audio = Uint8List.fromList([0xff, 0xfb, 0x90, 0x64]);
      final first = Id3v23Lyrics.writeUnsyncedLyrics(audio, 'First lyrics');
      final second = Id3v23Lyrics.writeUnsyncedLyrics(
        first!,
        'Replacement lyrics',
      );

      expect(second, isNotNull);
      expect(second!.sublist(0, 4), [0x49, 0x44, 0x33, 0x03]);
      expect(_sequenceCount(second, ascii.encode('USLT')), 1);
      expect(second.sublist(second.length - audio.length), audio);
    });

    test('leaves unsupported ID3 versions unchanged', () {
      final id3v24 = Uint8List.fromList([
        0x49,
        0x44,
        0x33,
        0x04,
        0,
        0,
        0,
        0,
        0,
        0,
      ]);

      expect(Id3v23Lyrics.writeUnsyncedLyrics(id3v24, 'Lyrics'), isNull);
    });
  });

}

int _sequenceCount(Uint8List bytes, List<int> sequence) {
  var count = 0;
  for (var index = 0; index <= bytes.length - sequence.length; index++) {
    var matches = true;
    for (var offset = 0; offset < sequence.length; offset++) {
      if (bytes[index + offset] != sequence[offset]) {
        matches = false;
        break;
      }
    }
    if (matches) count++;
  }
  return count;
}
