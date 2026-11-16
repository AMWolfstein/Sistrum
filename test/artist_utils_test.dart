import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/utils/artist_utils.dart';

void main() {
  test('artist tag values deduplicate names', () {
    expect(splitArtistTagValues('One, Two; one & Three feat. TWO'), [
      'One',
      'Two',
      'Three',
    ]);
    expect(splitArtistTagValues('One x Two'), ['One', 'Two']);
    expect(splitArtistTagValues('Malcolm X'), ['Malcolm']);
    expect(splitArtistTagValues('X'), ['X']);
    expect(splitArtistTagValues('  '), isEmpty);
  });

  test('primary tag value splits on provider feature variants', () {
    expect(primaryArtistTagValue('One featured Two'), 'One');
    expect(primaryArtistTagValue('One feature Two'), 'One');
  });
  test('primary artist prefers the first album artist', () {
    expect(
      primaryArtistName(
        'Track Artist, Guest Artist',
        albumArtist: 'Album Artist & Collaborator',
      ),
      'Album Artist',
    );
  });

  test('primary artist handles provider separator variants', () {
    expect(primaryArtistName('One; Two & Three'), 'One');
    expect(primaryArtistName('One feat. Two'), 'One');
    expect(primaryArtistName('AC/DC'), 'AC');
    expect(
      primaryArtistName(
        'AC/DC',
        rules: const TagCreditRules(keepTogether: ['AC/DC']),
      ),
      'AC/DC',
    );
    expect(
      primaryArtistName('Actual Artist, Guest', albumArtist: 'Various Artists'),
      'Actual Artist',
    );
  });
}
