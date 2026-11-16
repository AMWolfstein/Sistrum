import 'package:sistrum/services/library_tag_credits.dart';

const artistTagModeJoined = 'joined';
const artistTagModeSplitVorbis = 'split_vorbis';
const artistTagModePrimary = 'primary';

const artistTagModes = {
  artistTagModeJoined,
  artistTagModeSplitVorbis,
  artistTagModePrimary,
};

// Same separator set as the Rust tag writer and the Android finalizer, so
// every write path stores the same primary artist. A standalone "x" needs
// spaces on both sides ("Malcolm X" stays).
final RegExp _primaryArtistSeparator = RegExp(
  r'\s*[,;&]\s*|\s+x\s+|\s+(?:feat(?:uring|ured|ure)?|ft|with)\.?(?:\s+|$)',
  caseSensitive: false,
);
final RegExp _metadataKeyPunctuation = RegExp(r'[^A-Z0-9]');

String primaryArtistName(
  String artists, {
  String? albumArtist,
  TagCreditRules rules = const TagCreditRules(),
}) {
  final preferred = albumArtist?.trim() ?? '';
  final normalizedPreferred = preferred.toLowerCase();
  final useAlbumArtist =
      preferred.isNotEmpty &&
      normalizedPreferred != 'various artists' &&
      normalizedPreferred != 'various' &&
      normalizedPreferred != 'va';
  final source = useAlbumArtist ? preferred : artists.trim();
  final split = splitTagCredits(source, TagCreditField.artist, rules);
  if (split.isNotEmpty) return split.first;

  final fallback = splitTagCredits(artists, TagCreditField.artist, rules);
  return fallback.isNotEmpty ? fallback.first : artists.trim();
}

bool shouldSplitVorbisArtistTags(String mode) {
  return mode == artistTagModeSplitVorbis;
}

bool isPrimaryArtistTagMode(String mode) =>
    mode.trim().toLowerCase() == artistTagModePrimary;

/// The first credited artist, or the trimmed value when nothing separates it.
String primaryArtistTagValue(String rawArtists) {
  final trimmed = rawArtists.trim();
  for (final part in trimmed.split(_primaryArtistSeparator)) {
    final artist = part.trim();
    if (artist.isNotEmpty) return artist;
  }
  return trimmed;
}

/// Mode for rewriting tags that already exist or that the user typed (editor,
/// lyrics embedding). Primary is meant for provider credits at download and
/// re-enrichment time and would silently discard names here.
String artistTagModeForExistingTags(String mode) =>
    isPrimaryArtistTagMode(mode) ? artistTagModeJoined : mode;

/// Artist tag value written for [mode]. Joined and split modes keep the full
/// credit; Vorbis splitting happens in the writers.
String artistTagValueForMode(String value, String mode) =>
    isPrimaryArtistTagMode(mode) ? primaryArtistTagValue(value) : value;

/// Rewrites artist and album-artist entries of an embed metadata map for
/// [mode], whichever key spelling the caller used (ARTIST, album_artist,
/// "ALBUM ARTIST", albumArtist). Other entries and empty values are kept.
Map<String, String> applyArtistTagModeToMetadata(
  Map<String, String> metadata,
  String mode,
) {
  if (!isPrimaryArtistTagMode(mode)) return metadata;
  return {
    for (final entry in metadata.entries)
      entry.key: switch (entry.key.toUpperCase().replaceAll(
        _metadataKeyPunctuation,
        '',
      )) {
        'ARTIST' || 'ALBUMARTIST' when entry.value.trim().isNotEmpty =>
          primaryArtistTagValue(entry.value),
        _ => entry.value,
      },
  };
}

/// Library writers (the split Vorbis writer) pass their settings [rules];
/// statistics, rewind and mixes keep the default delimiters.
List<String> splitArtistTagValues(
  String rawArtists, {
  TagCreditRules rules = const TagCreditRules(),
}) {
  final values = splitTagCredits(rawArtists, TagCreditField.artist, rules);
  if (values.isNotEmpty) {
    return values;
  }

  final trimmed = rawArtists.trim();
  return trimmed.isEmpty ? const [] : <String>[trimmed];
}
