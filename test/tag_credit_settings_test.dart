import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/tag_credit_settings.dart';

void main() {
  test('fresh settings use the default delimiters and keep none together', () {
    final fresh = AppSettings.fromJson({}).tagCredits;
    expect(fresh.customArtistDelimiters, isEmpty);
    expect(fresh.artistDelimiters, kArtistDelimiterDefaults);
    expect(fresh.genreDelimiters, kGenreDelimiterDefaults);
    expect(fresh.keepTogether, isEmpty);
  });

  test('selections, custom delimiters and keep together round trip', () {
    const changed = TagCreditSettings(
      artistDelimiters: [';', '|'],
      genreDelimiters: ['/'],
      customArtistDelimiters: ['|'],
      customGenreDelimiters: ['+'],
      keepTogether: ['Tyler, The Creator'],
    );
    final restored = TagCreditSettings.fromJson(
      AppSettings(tagCredits: changed).toJson()['tagCredits']
          as Map<String, dynamic>,
    );
    expect(restored.artistDelimiters, [';', '|']);
    expect(restored.genreDelimiters, ['/']);
    expect(restored.customArtistDelimiters, ['|']);
    expect(restored.keepTogether, ['Tyler, The Creator']);
    expect(restored.customGenreDelimiters, ['+']);
  });

  test('empty selections fall back to the defaults', () {
    final restored = TagCreditSettings.fromJson({
      'artist_delimiters': <String>[],
      'genre_delimiters': ['  ', ''],
    });
    expect(restored.artistDelimiters, kArtistDelimiterDefaults);
    expect(restored.genreDelimiters, kGenreDelimiterDefaults);
  });

  test('stored lists are cleaned: trimmed, de-duplicated, invalid dropped', () {
    final restored = TagCreditSettings.fromJson({
      'keep_together': [
        '  Earth, Wind & Fire ',
        'earth, wind & fire',
        '',
        'bad\nname',
        42,
      ],
      'custom_artist_delimiters': ['|', '|', 'way-too-long-delimiter'],
    });
    expect(restored.keepTogether, ['Earth, Wind & Fire']);
    expect(restored.customArtistDelimiters, ['|']);
  });

  test('a keep together name may be longer than a delimiter', () {
    final name = 'A' * 100;
    final restored = TagCreditSettings.fromJson({
      'keep_together': [name, 'A' * 101],
    });
    expect(restored.keepTogether, [name]);
  });

  test('a new delimiter is 1-16 characters and not already offered', () {
    const offered = [';', 'feat.'];
    expect(TagCreditSettings.isValidDelimiter('|', offered), isTrue);
    expect(TagCreditSettings.isValidDelimiter(' | ', offered), isTrue);
    expect(TagCreditSettings.isValidDelimiter('', offered), isFalse);
    expect(TagCreditSettings.isValidDelimiter('   ', offered), isFalse);
    expect(TagCreditSettings.isValidDelimiter('FEAT.', offered), isFalse);
    expect(TagCreditSettings.isValidDelimiter('a' * 17, offered), isFalse);
    expect(TagCreditSettings.isValidDelimiter('a\nb', offered), isFalse);
  });

  test('backups with the removed save-time separators still load', () {
    final restored = AppSettings.fromJson({
      'metadataSeparators': {'artist': ' / ', 'genre': '; '},
    });
    expect(restored.tagCredits.keepTogether, isEmpty);
  });
}
