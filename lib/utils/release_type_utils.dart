/// The release kind the library groups by, from the release's track total.
enum LibraryReleaseKind { single, ep, album }

/// Album, Single or EP as Apple Music reads it: a track total of 1-3 is a
/// Single, 4-6 an EP and 7 or more an Album. Without a total tag a name ending
/// in " - Single" or " - EP" decides, and anything else is an Album, so a
/// partly owned album stays an Album.
LibraryReleaseKind libraryReleaseKind({int? totalTracks, String? albumName}) {
  if (totalTracks != null && totalTracks > 0) {
    if (totalTracks <= 3) return LibraryReleaseKind.single;
    if (totalTracks <= 6) return LibraryReleaseKind.ep;
    return LibraryReleaseKind.album;
  }
  final name = (albumName ?? '').trim().toLowerCase();
  if (name.endsWith(' - single')) return LibraryReleaseKind.single;
  if (name.endsWith(' - ep')) return LibraryReleaseKind.ep;
  return LibraryReleaseKind.album;
}
