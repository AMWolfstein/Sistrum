import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/utils/release_type_utils.dart';

void main() {
  test('a stored album type decides, with single and ep as singles', () {
    expect(
      isSingleRelease(albumType: 'Single', albumName: 'Album', totalTracks: 9),
      isTrue,
    );
    expect(isSingleRelease(albumType: 'ep', totalTracks: 4), isTrue);
    expect(isSingleRelease(albumType: 'album', totalTracks: 1), isFalse);
    // A padded stored type is not single, as the download rule has always read it.
    expect(isSingleRelease(albumType: ' single ', totalTracks: 1), isFalse);
  });

  test('without a stored type, one track is a single', () {
    expect(isSingleRelease(totalTracks: 1), isTrue);
    expect(isSingleRelease(totalTracks: 2), isFalse);
  });

  test('a release named Single or Singles is a single up to two tracks', () {
    expect(isSingleRelease(albumName: 'Singles', totalTracks: 2), isTrue);
    expect(isSingleRelease(albumName: 'single', totalTracks: 3), isFalse);
    expect(isSingleRelease(albumName: 'Singles'), isTrue);
  });

  test('a plain album without a track count is not a single', () {
    expect(isSingleRelease(albumName: 'Album'), isFalse);
    expect(isSingleRelease(), isFalse);
  });
}
