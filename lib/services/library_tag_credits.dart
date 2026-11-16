import 'package:sqflite/sqflite.dart';
import 'package:sistrum/models/tag_credit_settings.dart';
import 'package:sistrum/services/library_schema.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;

/// The tag a credit comes from. [dbName] is stored in the credits table.
enum TagCreditField {
  artist('artist'),
  albumArtist('album_artist'),
  genre('genre');

  const TagCreditField(this.dbName);

  final String dbName;
}

/// The delimiters and protected names that decide how one tag is split.
class TagCreditRules {
  const TagCreditRules({
    this.artistDelimiters = kArtistDelimiterDefaults,
    this.genreDelimiters = kGenreDelimiterDefaults,
    this.keepTogether = const [],
  });

  factory TagCreditRules.fromSettings(TagCreditSettings settings) =>
      TagCreditRules(
        artistDelimiters: settings.artistDelimiters,
        genreDelimiters: settings.genreDelimiters,
        keepTogether: settings.keepTogether,
      );

  final List<String> artistDelimiters;
  final List<String> genreDelimiters;
  final List<String> keepTogether;

  @override
  bool operator ==(Object other) =>
      other is TagCreditRules &&
      _sameList(artistDelimiters, other.artistDelimiters) &&
      _sameList(genreDelimiters, other.genreDelimiters) &&
      _sameList(keepTogether, other.keepTogether);

  @override
  int get hashCode => Object.hash(
    Object.hashAll(artistDelimiters),
    Object.hashAll(genreDelimiters),
    Object.hashAll(keepTogether),
  );
}

/// The credits a query reads: the rules they were split with and the index
/// version they were read from. A null version never matches a built table,
/// so the table is rebuilt for every such query.
class TagCreditScope {
  const TagCreditScope({this.rules = const TagCreditRules(), this.version});

  final TagCreditRules rules;
  final String? version;
}

const _letterOrDigit = r'\p{L}\p{N}';

/// Splits tag values by one set of [TagCreditRules]. The patterns are compiled
/// once here, so a caller splitting many rows keeps one splitter for them.
class TagCreditSplitter {
  TagCreditSplitter(TagCreditRules rules)
    : _rules = rules,
      _protections = [
        for (final name in _longestFirst(rules.keepTogether))
          if (name.trim().isNotEmpty) _protectionPattern(name.trim()),
      ];

  final TagCreditRules _rules;
  final List<RegExp> _protections;
  late final RegExp _artistPattern = _delimiterPattern(_rules.artistDelimiters);
  late final RegExp _genrePattern = _delimiterPattern(_rules.genreDelimiters);

  /// Splits one tag value into its names, in order, without duplicates.
  ///
  /// Keep-together names are protected longest first, so a name that contains
  /// a shorter kept name ("Tyler, The Creator" and "Tyler") keeps its own
  /// separators. Word delimiters such as `feat.` only match as whole words. A
  /// newline always separates, and genres never keep names together.
  List<String> split(String? raw, TagCreditField field) {
    final source = raw ?? '';
    if (source.trim().isEmpty) return const [];
    final kept = <String>[];
    var text = source;
    if (field != TagCreditField.genre) {
      for (final pattern in _protections) {
        text = text.replaceAllMapped(pattern, (match) {
          kept.add(match[0]!);
          return _marker(kept.length - 1);
        });
      }
    }
    final pattern = field == TagCreditField.genre
        ? _genrePattern
        : _artistPattern;
    final names = <String>[];
    final seen = <String>{};
    for (final part in text.split(pattern)) {
      final name = _restore(_stripStrayBrackets(part.trim()), kept).trim();
      if (name.isEmpty) continue;
      if (seen.add(sqlite.normalizeLookupText(name))) names.add(name);
    }
    return names;
  }
}

/// Splits one tag value with a fresh [TagCreditSplitter]; loops over many rows
/// should build one splitter instead.
List<String> splitTagCredits(
  String? raw,
  TagCreditField field,
  TagCreditRules rules,
) => TagCreditSplitter(rules).split(raw, field);

Iterable<String> _longestFirst(List<String> names) =>
    [...names]..sort((a, b) => b.trim().length.compareTo(a.trim().length));

String _escape(String value) => value.replaceAllMapped(
  RegExp(r'[.*+?^$(){}\[\]|\\/]'),
  (match) => '\\${match[0]}',
);

bool _isWord(String token) =>
    RegExp('[$_letterOrDigit]', unicode: true).hasMatch(token);

RegExp _delimiterPattern(List<String> delimiters) {
  final alternatives = <String>[r'\r?\n'];
  for (final token in delimiters) {
    final trimmed = token.trim();
    if (trimmed.isEmpty) continue;
    // A word delimiter must stand alone: no letter or digit on either side.
    alternatives.add(
      _isWord(trimmed)
          ? '(?<![$_letterOrDigit])${_escape(trimmed)}(?![$_letterOrDigit])'
          : _escape(trimmed),
    );
  }
  return RegExp(
    '\\s*(?:${alternatives.join('|')})\\s*',
    caseSensitive: false,
    unicode: true,
  );
}

/// A marker that cannot occur in a tag, standing in for a protected name.
String _marker(int index) => '\u0000$index\u0000';

RegExp _protectionPattern(String name) => RegExp(
  '(?<![$_letterOrDigit])${_escape(name)}(?![$_letterOrDigit])',
  caseSensitive: false,
  unicode: true,
);

String _restore(String part, List<String> kept) => part.replaceAllMapped(
  RegExp('\u0000(\\d+)\u0000'),
  (match) => kept[int.parse(match[1]!)],
);

/// Removes brackets left unpaired at the ends of a part, such as the `(` and
/// `)` around a feature. Balanced brackets, as in `Band (UK)`, stay.
String _stripStrayBrackets(String part) {
  final unbalanced =
      '('.allMatches(part).length != ')'.allMatches(part).length ||
      '['.allMatches(part).length != ']'.allMatches(part).length;
  if (!unbalanced) return part;
  return part.replaceAll(RegExp(r'^[()\[\]]+|[()\[\]]+$'), '').trim();
}

bool _sameList(List<String> a, List<String> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}

final _tagCreditCaches = Expando<LibraryTagCreditsCache>('tag credits');

/// The credits cache of one database connection.
LibraryTagCreditsCache tagCreditsCacheFor(Database db) =>
    _tagCreditCaches[db] ??= LibraryTagCreditsCache();

/// Names for every track of the visible library and of the downloads, kept in
/// a TEMP table so artist and genre queries never split a tag themselves. It is
/// rebuilt when the scope's version or rules change; no rescan is needed.
class LibraryTagCreditsCache {
  static const tempTable = 'library_tag_credits';
  static const _pageSize = 2000;

  String? _version;
  TagCreditRules? _rules;
  Future<void>? _pending;

  /// Serialises rebuilds so concurrent queries cannot race the table swap.
  Future<void> ensure(Database db, TagCreditScope scope) {
    final run = (_pending ?? Future<void>.value()).then(
      (_) => _ensure(db, scope),
    );
    _pending = run.then<void>((_) {}, onError: (_) {});
    return run;
  }

  Future<void> _ensure(Database db, TagCreditScope scope) async {
    final version = scope.version;
    if (version != null && _version == version && _rules == scope.rules) {
      return;
    }
    await db.execute('''
      CREATE TEMP TABLE IF NOT EXISTS $tempTable (
        source TEXT NOT NULL,
        row_id TEXT NOT NULL,
        field TEXT NOT NULL,
        name TEXT NOT NULL,
        name_key TEXT NOT NULL,
        position INTEGER NOT NULL
      )
    ''');
    final splitter = TagCreditSplitter(scope.rules);
    // One transaction keeps a concurrent query from reading a half-built table.
    await db.transaction((txn) async {
      await txn.execute('DELETE FROM $tempTable');
      await _copyRows(txn, 'l', LibrarySchema.visibleView, splitter);
      await _copyRows(txn, 'h', 'history_db.history', splitter);
      await txn.execute(
        'CREATE INDEX IF NOT EXISTS ${tempTable}_key '
        'ON $tempTable(field, name_key, source)',
      );
      await txn.execute(
        'CREATE INDEX IF NOT EXISTS ${tempTable}_row ON $tempTable(source, row_id)',
      );
    });
    _version = version;
    _rules = scope.rules;
  }

  /// [source] is `l` for the library and `h` for downloads: each table numbers
  /// its rows separately, so a row is identified by source and id.
  static Future<void> _copyRows(
    Transaction txn,
    String source,
    String table,
    TagCreditSplitter splitter,
  ) async {
    var after = '';
    while (true) {
      final rows = await txn.rawQuery(
        'SELECT id, artist_name, album_artist, genre FROM $table '
        'WHERE id > ? ORDER BY id LIMIT ?',
        [after, _pageSize],
      );
      if (rows.isEmpty) break;
      final batch = txn.batch();
      for (final row in rows) {
        final id = '${row['id']}';
        for (final field in TagCreditField.values) {
          final names = splitter.split(_rawValue(row, field), field);
          for (var i = 0; i < names.length; i++) {
            batch.insert(tempTable, {
              'source': source,
              'row_id': id,
              'field': field.dbName,
              'name': names[i],
              'name_key': sqlite.normalizeLookupText(names[i]),
              'position': i,
            });
          }
        }
      }
      await batch.commit(noResult: true);
      after = '${rows.last['id']}';
    }
  }

  /// An album artist left empty means the track artist, as the scanner stores
  /// it in album_artist_norm.
  static String? _rawValue(Map<String, Object?> row, TagCreditField field) {
    return switch (field) {
      TagCreditField.artist => row['artist_name'] as String?,
      TagCreditField.albumArtist =>
        _nonEmpty(row['album_artist'] as String?) ??
            row['artist_name'] as String?,
      TagCreditField.genre => row['genre'] as String?,
    };
  }

  static String? _nonEmpty(String? value) =>
      value == null || value.trim().isEmpty ? null : value;
}
