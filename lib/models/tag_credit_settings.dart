/// Separators the library splits tag credits on, per section, and the names
/// that are never split. Selected chips are the active delimiters; custom ones
/// stay in their own list so a deselected custom chip is still offered.
library;

/// Chips offered in the Artists section, in display order.
const kArtistDelimiterChoices = <String>[
  ';',
  ',',
  '،',
  'feat.',
  'ft.',
  'featuring',
  '/',
  '&',
  'x',
  'with',
];

/// Artist delimiters selected on a fresh install: every chip, so All is on.
/// A name that one of them splits wrongly goes in Keep together.
const kArtistDelimiterDefaults = kArtistDelimiterChoices;

/// Chips offered in the Genre section, in display order.
const kGenreDelimiterChoices = <String>[';', '،', ',', '/', '&'];

/// Genre delimiters selected on a fresh install.
const kGenreDelimiterDefaults = <String>[';', '،'];

const _maxDelimiterLength = 16;

/// Whether [token] fits a separator: at most 16 characters, with no line
/// breaks or other control characters.
bool isSeparatorToken(String token) =>
    token.length <= _maxDelimiterLength &&
    !token.contains(RegExp(r'[\x00-\x1f\x7f]'));
const _maxKeepTogetherLength = 100;
const _maxKeepTogetherCount = 200;
const _maxCustomCount = 32;

class TagCreditSettings {
  const TagCreditSettings({
    this.artistDelimiters = kArtistDelimiterDefaults,
    this.genreDelimiters = kGenreDelimiterDefaults,
    this.customArtistDelimiters = const [],
    this.customGenreDelimiters = const [],
    this.keepTogether = const [],
  });

  final List<String> artistDelimiters;
  final List<String> genreDelimiters;
  final List<String> customArtistDelimiters;
  final List<String> customGenreDelimiters;
  final List<String> keepTogether;

  factory TagCreditSettings.fromJson(Map<String, dynamic> json) {
    final artist = _cleanList(
      json['artist_delimiters'],
      maxLength: _maxDelimiterLength,
      maxCount: _maxCustomCount + kArtistDelimiterChoices.length,
    );
    final genre = _cleanList(
      json['genre_delimiters'],
      maxLength: _maxDelimiterLength,
      maxCount: _maxCustomCount + kGenreDelimiterChoices.length,
    );
    return TagCreditSettings(
      artistDelimiters: artist.isEmpty ? kArtistDelimiterDefaults : artist,
      genreDelimiters: genre.isEmpty ? kGenreDelimiterDefaults : genre,
      customArtistDelimiters: _cleanList(
        json['custom_artist_delimiters'],
        maxLength: _maxDelimiterLength,
        maxCount: _maxCustomCount,
      ),
      customGenreDelimiters: _cleanList(
        json['custom_genre_delimiters'],
        maxLength: _maxDelimiterLength,
        maxCount: _maxCustomCount,
      ),
      keepTogether: _cleanList(
        json['keep_together'],
        maxLength: _maxKeepTogetherLength,
        maxCount: _maxKeepTogetherCount,
      ),
    );
  }

  Map<String, dynamic> toJson() => {
    'artist_delimiters': artistDelimiters,
    'genre_delimiters': genreDelimiters,
    'custom_artist_delimiters': customArtistDelimiters,
    'custom_genre_delimiters': customGenreDelimiters,
    'keep_together': keepTogether,
  };

  TagCreditSettings copyWith({
    List<String>? artistDelimiters,
    List<String>? genreDelimiters,
    List<String>? customArtistDelimiters,
    List<String>? customGenreDelimiters,
    List<String>? keepTogether,
  }) => TagCreditSettings(
    artistDelimiters: artistDelimiters ?? this.artistDelimiters,
    genreDelimiters: genreDelimiters ?? this.genreDelimiters,
    customArtistDelimiters:
        customArtistDelimiters ?? this.customArtistDelimiters,
    customGenreDelimiters: customGenreDelimiters ?? this.customGenreDelimiters,
    keepTogether: keepTogether ?? this.keepTogether,
  );

  /// Whether [token] is a valid new delimiter: 1-16 characters, no line breaks
  /// or control characters, and not already offered in [existing].
  static bool isValidDelimiter(String token, Iterable<String> existing) {
    final trimmed = token.trim();
    if (trimmed.isEmpty) return false;
    if (!isSeparatorToken(trimmed)) return false;
    final key = trimmed.toLowerCase();
    return !existing.any((other) => other.toLowerCase() == key);
  }
}

/// Trims, drops blanks and invalid values, and removes duplicates
/// case-insensitively, keeping the first spelling.
List<String> _cleanList(
  Object? value, {
  required int maxLength,
  required int maxCount,
}) {
  if (value is! List) return const [];
  final seen = <String>{};
  final out = <String>[];
  for (final item in value) {
    if (item is! String) continue;
    final trimmed = item.trim();
    if (trimmed.isEmpty || trimmed.length > maxLength) continue;
    if (_hasControlCharacter(trimmed)) continue;
    if (!seen.add(trimmed.toLowerCase())) continue;
    out.add(trimmed);
    if (out.length == maxCount) break;
  }
  return out;
}

bool _hasControlCharacter(String value) =>
    value.contains(RegExp(r'[\x00-\x1f\x7f]'));
