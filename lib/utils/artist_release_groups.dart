import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/services/library_database.dart' show LibraryDatabase;
import 'package:sistrum/services/sqlite_helpers.dart' show normalizeLookupText;
import 'package:sistrum/utils/artist_utils.dart';
import 'package:sistrum/utils/natural_compare.dart';

/// A release with one track is a single; one with more is an album.
enum ArtistReleaseKind { album, single }

/// One release in the library: an album and the songs of it that credit the
/// artist. [key] identifies it across sorts and play counts.
class ArtistRelease {
  const ArtistRelease({
    required this.key,
    required this.albumName,
    required this.albumKey,
    required this.primaryArtist,
    required this.kind,
    required this.totalTracks,
    required this.releasedOn,
    required this.coverPath,
    required this.tracks,
  });

  final String key;
  final String albumName;

  /// The Library key of the whole album, which opens every track of it.
  final String albumKey;
  final String primaryArtist;
  final ArtistReleaseKind kind;
  final int totalTracks;
  final DateTime? releasedOn;
  final String? coverPath;

  /// The songs of this release in the library that credit the artist.
  final List<LocalLibraryItem> tracks;
}

/// The artist's releases, each section already newest first. [latest] is the
/// newest of the artist's own releases, or of the compilations when the
/// artist has none.
class ArtistReleaseGroups {
  const ArtistReleaseGroups({
    this.latest,
    this.albums = const [],
    this.singles = const [],
    this.compilations = const [],
  });

  final ArtistRelease? latest;
  final List<ArtistRelease> albums;
  final List<ArtistRelease> singles;
  final List<ArtistRelease> compilations;

  bool get isEmpty =>
      latest == null &&
      albums.isEmpty &&
      singles.isEmpty &&
      compilations.isEmpty;
}

/// Groups the library [songs] that credit [artist] into their releases.
///
/// A song whose primary artist is [artist] is part of that artist's album or
/// single. A song that names [artist] as a second or later artist of a
/// release by someone else goes to [ArtistReleaseGroups.compilations] only.
ArtistReleaseGroups groupArtistReleases(
  String artist,
  Iterable<LocalLibraryItem> songs,
) {
  final ownerKey = normalizeLookupText(artist.trim());
  if (ownerKey.isEmpty) return const ArtistReleaseGroups();

  final own = <String, _ReleaseBuilder>{};
  final credited = <String, _ReleaseBuilder>{};
  for (final song in songs) {
    final primary = primaryArtistName(
      song.artistName,
      albumArtist: song.albumArtist,
    );
    final isPrimary = normalizeLookupText(primary) == ownerKey;
    final isCredited =
        isPrimary ||
        splitArtistNames(
          song.artistName,
        ).any((name) => normalizeLookupText(name) == ownerKey);
    if (!isCredited) continue;

    final key =
        '${normalizeLookupText(song.albumName)}|${normalizeLookupText(primary)}';
    (isPrimary ? own : credited)
        .putIfAbsent(key, () => _ReleaseBuilder(key, song, primary))
        .add(song);
  }

  final ownReleases = _newestFirst([for (final b in own.values) b.build()]);
  final compilations = _newestFirst([
    for (final b in credited.values) b.build(),
  ]);
  final candidates = ownReleases.isNotEmpty ? ownReleases : compilations;
  return ArtistReleaseGroups(
    latest: candidates.firstOrNull,
    albums: [
      for (final release in ownReleases)
        if (release.kind == ArtistReleaseKind.album) release,
    ],
    singles: [
      for (final release in ownReleases)
        if (release.kind == ArtistReleaseKind.single) release,
    ],
    compilations: compilations,
  );
}

/// The date a release's tag names, to the day when it has one. A year alone
/// counts as January 1st; null when there is no year.
DateTime? artistReleaseDate(String? raw) {
  final match = RegExp(
    r'^(\d{4})(?:-(\d{1,2})(?:-(\d{1,2}))?)?',
  ).firstMatch(raw?.trim() ?? '');
  if (match == null) return null;
  return DateTime(
    int.parse(match[1]!),
    int.tryParse(match[2] ?? '') ?? 1,
    int.tryParse(match[3] ?? '') ?? 1,
  );
}

/// Newest first; releases with no date go last, then by name.
int _compareNewest(ArtistRelease a, ArtistRelease b) {
  final dateA = a.releasedOn;
  final dateB = b.releasedOn;
  if (dateA != dateB) {
    if (dateA == null) return 1;
    if (dateB == null) return -1;
    return dateB.compareTo(dateA);
  }
  return compareNaturally(a.albumName, b.albumName);
}

List<ArtistRelease> _newestFirst(List<ArtistRelease> releases) =>
    releases..sort(_compareNewest);

class _ReleaseBuilder {
  _ReleaseBuilder(this.key, LocalLibraryItem first, this.primaryArtist)
    : albumName = first.albumName.trim(),
      albumKey = LibraryDatabase.albumKeyFor(
        first.albumName,
        first.albumArtist,
        first.artistName,
      );

  final String key;
  final String albumName;
  final String albumKey;
  final String primaryArtist;
  final List<LocalLibraryItem> _tracks = [];
  int _totalTracks = 0;
  String? _releaseDate;
  String? _coverPath;

  void add(LocalLibraryItem song) {
    _tracks.add(song);
    // The album's own count is on its tags, which a compilation track carries
    // as well as the album's songs.
    final total = song.totalTracks ?? 0;
    if (total > _totalTracks) _totalTracks = total;
    _releaseDate ??= _nonEmpty(song.releaseDate);
    _coverPath ??= _nonEmpty(song.coverPath);
  }

  ArtistRelease build() {
    // A release without a count in its tags is judged by the songs we hold.
    final total = _totalTracks > 0 ? _totalTracks : _tracks.length;
    return ArtistRelease(
      key: key,
      albumName: albumName,
      albumKey: albumKey,
      primaryArtist: primaryArtist,
      kind: total == 1 ? ArtistReleaseKind.single : ArtistReleaseKind.album,
      totalTracks: total,
      releasedOn: artistReleaseDate(_releaseDate),
      coverPath: _coverPath,
      tracks: List.unmodifiable(_tracks),
    );
  }

  static String? _nonEmpty(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }
}
