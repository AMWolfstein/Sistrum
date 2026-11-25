import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/utils/artist_release_groups.dart';

LocalLibraryItem _song(
  String id, {
  required String album,
  required String artist,
  String? albumArtist,
  int? totalTracks,
  String? releaseDate,
  String? coverPath,
}) => LocalLibraryItem(
  id: id,
  trackName: 'Track $id',
  artistName: artist,
  albumName: album,
  albumArtist: albumArtist,
  filePath: '/music/$id.flac',
  coverPath: coverPath,
  scannedAt: DateTime(2026, 11, 1),
  totalTracks: totalTracks,
  releaseDate: releaseDate,
);

List<String> _names(Iterable<ArtistRelease> releases) => [
  for (final release in releases) release.albumName,
];

void main() {
  group('release kind', () {
    test('an album with one track is a single, with more an album', () {
      final groups = groupArtistReleases('Ana', [
        _song('s1', album: 'Lone', artist: 'Ana', totalTracks: 1),
        _song('a1', album: 'Long', artist: 'Ana', totalTracks: 3),
        _song('a2', album: 'Long', artist: 'Ana', totalTracks: 3),
      ]);

      expect(_names(groups.singles), ['Lone']);
      expect(_names(groups.albums), ['Long']);
      expect(groups.compilations, isEmpty);
    });

    test('a release without a track count is judged by the songs held', () {
      final groups = groupArtistReleases('Ana', [
        _song('s1', album: 'Lone', artist: 'Ana'),
        _song('a1', album: 'Pair', artist: 'Ana'),
        _song('a2', album: 'Pair', artist: 'Ana'),
      ]);

      expect(_names(groups.singles), ['Lone']);
      expect(_names(groups.albums), ['Pair']);
    });

    test('the largest track count on the tags wins over a partial copy', () {
      final groups = groupArtistReleases('Ana', [
        _song('a1', album: 'Long', artist: 'Ana', totalTracks: 1),
        _song('a2', album: 'Long', artist: 'Ana', totalTracks: 9),
      ]);

      expect(_names(groups.albums), ['Long']);
      expect(groups.albums.single.tracks, hasLength(2));
    });
  });

  group('compilations', () {
    test('a featured song sits only under the non-primary artist', () {
      final songs = [
        _song(
          'f1',
          album: 'Bo Sessions',
          artist: 'Bo, Ana',
          albumArtist: 'Bo',
          totalTracks: 4,
        ),
      ];

      final ana = groupArtistReleases('Ana', songs);
      expect(ana.albums, isEmpty);
      expect(ana.singles, isEmpty);
      expect(_names(ana.compilations), ['Bo Sessions']);

      final bo = groupArtistReleases('Bo', songs);
      expect(_names(bo.albums), ['Bo Sessions']);
      expect(bo.compilations, isEmpty);
    });

    test('a single by someone else with the artist featured is a compilation '
        'entry, not a single of theirs', () {
      final groups = groupArtistReleases('Ana', [
        _song('f1', album: 'Hook', artist: 'Bo feat. Ana', totalTracks: 1),
      ]);

      expect(groups.singles, isEmpty);
      expect(_names(groups.compilations), ['Hook']);
      expect(groups.latest?.albumName, 'Hook');
    });

    test('the album artist names the release even when the tag lists the '
        'owner first', () {
      final groups = groupArtistReleases('Ana', [
        _song(
          'a1',
          album: 'Joint',
          artist: 'Ana, Bo',
          albumArtist: 'Ana',
          totalTracks: 2,
        ),
      ]);

      expect(_names(groups.albums), ['Joint']);
      expect(groups.compilations, isEmpty);
    });

    test('a song that does not credit the artist is left out', () {
      final groups = groupArtistReleases('Ana', [
        _song('x1', album: 'Other', artist: 'Bo', totalTracks: 2),
      ]);

      expect(groups.isEmpty, isTrue);
    });

    test('an empty name has no releases', () {
      final groups = groupArtistReleases('  ', [
        _song('a1', album: 'Long', artist: 'Ana', totalTracks: 3),
      ]);

      expect(groups.isEmpty, isTrue);
    });
  });

  group('latest release', () {
    test('is the newest album or single of the artist, by full date', () {
      final groups = groupArtistReleases('Ana', [
        _song(
          'a1',
          album: 'Old',
          artist: 'Ana',
          totalTracks: 5,
          releaseDate: '2019-04-01',
        ),
        _song(
          's1',
          album: 'New Single',
          artist: 'Ana',
          totalTracks: 1,
          releaseDate: '2025-06-10',
        ),
        _song(
          'a2',
          album: 'Mid',
          artist: 'Ana',
          totalTracks: 2,
          releaseDate: '2023',
        ),
      ]);

      expect(groups.latest?.albumName, 'New Single');
    });

    test('compares a year-only date as January the first', () {
      final groups = groupArtistReleases('Ana', [
        _song(
          'a1',
          album: 'Year Only',
          artist: 'Ana',
          totalTracks: 4,
          releaseDate: '2024',
        ),
        _song(
          'a2',
          album: 'Dated',
          artist: 'Ana',
          totalTracks: 4,
          releaseDate: '2024-02',
        ),
      ]);

      expect(groups.latest?.albumName, 'Dated');
    });

    test('ignores a compilation newer than the artist own releases', () {
      final groups = groupArtistReleases('Ana', [
        _song(
          'a1',
          album: 'Own',
          artist: 'Ana',
          totalTracks: 4,
          releaseDate: '2020',
        ),
        _song(
          'f1',
          album: 'Feature',
          artist: 'Bo, Ana',
          albumArtist: 'Bo',
          totalTracks: 4,
          releaseDate: '2026',
        ),
      ]);

      expect(groups.latest?.albumName, 'Own');
    });

    test('falls back to the newest compilation when the artist has none', () {
      final groups = groupArtistReleases('Ana', [
        _song(
          'f1',
          album: 'Older',
          artist: 'Bo, Ana',
          albumArtist: 'Bo',
          totalTracks: 4,
          releaseDate: '2018',
        ),
        _song(
          'f2',
          album: 'Newer',
          artist: 'Cy, Ana',
          albumArtist: 'Cy',
          totalTracks: 4,
          releaseDate: '2022',
        ),
      ]);

      expect(groups.albums, isEmpty);
      expect(groups.latest?.albumName, 'Newer');
    });

    test('a release without a date is never the latest', () {
      final groups = groupArtistReleases('Ana', [
        _song('a1', album: 'Undated', artist: 'Ana', totalTracks: 4),
        _song(
          'a2',
          album: 'Dated',
          artist: 'Ana',
          totalTracks: 4,
          releaseDate: '2001',
        ),
      ]);

      expect(groups.latest?.albumName, 'Dated');
    });

    test('sections list newest first', () {
      final groups = groupArtistReleases('Ana', [
        _song(
          'a1',
          album: 'B',
          artist: 'Ana',
          totalTracks: 3,
          releaseDate: '2020',
        ),
        _song(
          'a2',
          album: 'A',
          artist: 'Ana',
          totalTracks: 3,
          releaseDate: '2024',
        ),
        _song('a3', album: 'C', artist: 'Ana', totalTracks: 3),
      ]);

      expect(_names(groups.albums), ['A', 'B', 'C']);
    });
  });

  test('a release keeps the Library album key of its first song', () {
    final groups = groupArtistReleases('Ana', [
      _song('a1', album: 'Night Tapes', artist: 'Ana', totalTracks: 3),
    ]);

    expect(groups.albums.single.albumKey, 'night tapes|ana');
    expect(groups.albums.single.primaryArtist, 'Ana');
  });

  group('artistReleaseDate', () {
    test('reads years, months and days, and nothing else', () {
      expect(artistReleaseDate('2024-03-01'), DateTime(2024, 3, 1));
      expect(artistReleaseDate('2024-03'), DateTime(2024, 3));
      expect(artistReleaseDate('2024'), DateTime(2024));
      expect(artistReleaseDate(' 1999-12-31 '), DateTime(1999, 12, 31));
      expect(artistReleaseDate('spring'), isNull);
      expect(artistReleaseDate(''), isNull);
      expect(artistReleaseDate(null), isNull);
    });
  });
}
