final RegExp _artistNameSplitPattern = RegExp(
  r'\s*(?:,|;|&)\s*|\s+x\s+|\s+\b(?:feat(?:uring|ured|ure)?|ft|with)\.?(?=\s|$)\s*',
  caseSensitive: false,
);

List<String> splitArtistNames(String rawArtists, {int? creditedArtistCount}) {
  final raw = rawArtists.trim();
  if (raw.isEmpty) return const [];

  final names = raw
      .split(_artistNameSplitPattern)
      .map((part) => part.trim())
      .where((part) => part.isNotEmpty)
      .toList(growable: false);
  // "and" can be part of a band's name. Only treat it as a separator when
  // the provider's separate artist IDs confirm that many credited artists.
  if (creditedArtistCount != null && creditedArtistCount > names.length) {
    final expanded = names
        .expand(
          (name) => name.split(RegExp(r'\s+and\s+', caseSensitive: false)),
        )
        .map((name) => name.trim())
        .where((name) => name.isNotEmpty)
        .toList(growable: false);
    if (expanded.length == creditedArtistCount) return expanded;
  }
  return names;
}

String primaryArtistName(String artists, {String? albumArtist}) {
  final preferred = albumArtist?.trim() ?? '';
  final normalizedPreferred = preferred.toLowerCase();
  final useAlbumArtist =
      preferred.isNotEmpty &&
      normalizedPreferred != 'various artists' &&
      normalizedPreferred != 'various' &&
      normalizedPreferred != 'va';
  final source = useAlbumArtist ? preferred : artists.trim();
  final split = splitArtistNames(source);
  if (split.isNotEmpty) return split.first;

  final fallback = splitArtistNames(artists);
  return fallback.isNotEmpty ? fallback.first : artists.trim();
}

List<String> splitArtistTagValues(String rawArtists) {
  final seen = <String>{};
  final values = <String>[];
  for (final part in splitArtistNames(rawArtists)) {
    final key = part.toLowerCase();
    if (seen.add(key)) {
      values.add(part);
    }
  }

  if (values.isNotEmpty) {
    return values;
  }

  final trimmed = rawArtists.trim();
  return trimmed.isEmpty ? const [] : <String>[trimmed];
}
