import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';

// The cover-scheme tests of this file covered PlayerArtworkTheme, which the
// Mornye player does not use; only the artwork-source rules remain.
void main() {
  test('palette sources follow the artwork forms the player shows', () {
    expect(coverImageSource('https://example.test/a.jpg'), isNull);
    expect(
      coverImageSource(Uri.file('/covers/a.jpg').toString()),
      '/covers/a.jpg',
    );
    expect(coverImageSource('/covers/a.jpg'), '/covers/a.jpg');
    expect(coverImageSource('content://media/cover'), isNull);
    expect(coverImageSource('network://connection/cover.jpg'), isNull);
    expect(coverImageSource('file://host/covers/a.jpg'), isNull);
    expect(coverImageSource('covers/a.jpg'), isNull);
    expect(coverImageSource(null), isNull);
  });
}
