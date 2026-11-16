import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/utils/release_type_utils.dart';

void main() {
  test('the track total tag sets the kind: 1-3 Single, 4-6 EP, 7+ Album', () {
    for (final total in [1, 2, 3]) {
      expect(
        libraryReleaseKind(totalTracks: total),
        LibraryReleaseKind.single,
        reason: 'total $total',
      );
    }
    for (final total in [4, 5, 6]) {
      expect(
        libraryReleaseKind(totalTracks: total),
        LibraryReleaseKind.ep,
        reason: 'total $total',
      );
    }
    for (final total in [7, 12]) {
      expect(
        libraryReleaseKind(totalTracks: total),
        LibraryReleaseKind.album,
        reason: 'total $total',
      );
    }
  });

  test('the tag decides over the name', () {
    expect(
      libraryReleaseKind(totalTracks: 12, albumName: 'Night Drive - Single'),
      LibraryReleaseKind.album,
    );
  });

  test('without a total tag a name ending in Single or EP decides', () {
    expect(
      libraryReleaseKind(albumName: 'Night Drive - Single'),
      LibraryReleaseKind.single,
    );
    expect(
      libraryReleaseKind(albumName: 'Night Drive - EP'),
      LibraryReleaseKind.ep,
    );
    expect(
      libraryReleaseKind(albumName: ' night drive - ep ', totalTracks: 0),
      LibraryReleaseKind.ep,
    );
  });

  test('without a total tag a plain name is an Album', () {
    expect(
      libraryReleaseKind(albumName: 'Night Drive'),
      LibraryReleaseKind.album,
    );
    expect(libraryReleaseKind(), LibraryReleaseKind.album);
  });
}
