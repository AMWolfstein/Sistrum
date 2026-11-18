import 'dart:convert';

import 'package:sistrum/utils/lyrics_draft.dart';
import 'package:sistrum/utils/lyrics_parser.dart';

/// Local lyric text helpers: romanization needs, plain lyrics as lines, and
/// the translation and pronunciation lines a lyrics file may already carry.
/// Nothing here calls an online service.

final _wordTimeTag = RegExp(r'<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>');
final _lineTimeTag = RegExp(r'\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\]');
final _hasWord = RegExp(r'[\p{L}\p{N}]', unicode: true);
final _instrumental = RegExp(
  r'^[(\[]?\s*(instrumental|music|intro|outro|interlude|solo)\s*[)\]]?$',
  caseSensitive: false,
);

/// The text of a lyric line as sent for translation: timing tags removed
/// and whitespace collapsed. Empty for lines with nothing to translate.
String translatableLyricText(String text) {
  final clean = text
      .replaceAll(_wordTimeTag, '')
      .replaceAll(_lineTimeTag, '')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
  if (!_hasWord.hasMatch(clean) || _instrumental.hasMatch(clean)) return '';
  return clean;
}

final _nonLatinLetter = RegExp(r'(?!\p{Script=Latin})\p{L}', unicode: true);

/// Whether [text] has letters outside the Latin alphabet, so reading it
/// along needs a romanization.
bool lyricNeedsRomanization(String text) => _nonLatinLetter.hasMatch(text);

/// Whether a line written outside the Latin alphabet has no provider
/// romanization, so a translation asks the model for one.
bool lyricsNeedRomanization(ParsedLyrics lyrics) => lyrics.lines.any(
  (line) =>
      line.romanization?.trim().isNotEmpty != true &&
      lyricNeedsRomanization(translatableLyricText(line.text)),
);

final _plainLines = Expando<ParsedLyrics>();

/// Lyrics without timing as one line per row of their text, so they carry
/// translations and pronunciations the way synced lines do. Synced lyrics,
/// and plain lyrics that already have lines, are returned as they are.
ParsedLyrics lyricsWithPlainLines(ParsedLyrics lyrics) {
  if (lyrics.synced || lyrics.lines.isNotEmpty) return lyrics;
  return _plainLines[lyrics] ??= ParsedLyrics(
    synced: false,
    wordSynced: false,
    lines: List.unmodifiable([
      for (final row in lyrics.plainText.split('\n'))
        if (row.trim().isNotEmpty)
          LyricLine(time: Duration.zero, text: row.trim()),
    ]),
    plainText: lyrics.plainText,
    writers: lyrics.writers,
    provider: lyrics.provider,
  );
}

String _supplementText(String? value) => value?.trim() ?? '';

/// `[x-romaji-words:...]` and `[x-translation-words:...]` JSON for [words],
/// as both lyrics parsers read it.
/// Empty when a word cannot be given a valid time range.
String lyricWordsSupplement(List<LyricWord> words) {
  final rows = <Map<String, Object>>[];
  for (var i = 0; i < words.length; i++) {
    final word = words[i];
    final start = word.time.inMilliseconds;
    final end =
        word.end?.inMilliseconds ??
        (i + 1 < words.length ? words[i + 1].time.inMilliseconds : start);
    if (word.text.trim().isEmpty ||
        end < start ||
        (rows.isNotEmpty && (rows.last['startTimeMs']! as int) > start)) {
      return '';
    }
    rows.add({'text': word.text, 'startTimeMs': start, 'endTimeMs': end});
  }
  return rows.isEmpty ? '' : jsonEncode(rows);
}

/// The `-words` JSON for [words] timing [text], or empty when they do not
/// spell it: both lyrics parsers drop such timing.
String _timedSupplement(String text, List<LyricWord> words) =>
    text.isEmpty || words.map((word) => word.text).join() != text
    ? ''
    : lyricWordsSupplement(words);

/// The supplement tags a lyric line written as LRC carries for [line]'s
/// translation and pronunciation, with their word timing, as
/// (kind, payload) in the order they are written.
List<(String, String)> lyricLineSupplements(LyricLine line) {
  String encode(String text) => base64.encode(utf8.encode(text));
  final romanization = _supplementText(line.romanization);
  final romanizationWords = _timedSupplement(
    romanization,
    line.romanizationWords,
  );
  final translation = _supplementText(line.translation);
  final translationWords = _timedSupplement(translation, line.translationWords);
  return [
    if (romanizationWords.isNotEmpty)
      ('romaji-words', encode(romanizationWords)),
    if (romanization.isNotEmpty) ('romaji', encode(romanization)),
    if (translationWords.isNotEmpty)
      ('translation-words', encode(translationWords)),
    if (translation.isNotEmpty) ('translation', encode(translation)),
  ];
}

/// [raw] LRC with each timed line carrying the translation and
/// pronunciation [shown] gives it, as the `[x-translation:<ms>:...]` and
/// `[x-romaji:<ms>:...]` tags both lyrics parsers read back, with their
/// `-words` timing, so they stay with the song's file alongside its own
/// lyrics. Other lines, tags and supplements stay as they are. Null when
/// [raw] is not synced LRC or already holds these.
String? lyricsWithTranslations(String raw, ParsedLyrics shown) {
  if (!LyricsDraft.canEdit(raw)) return null;
  final draft = LyricsDraft.parse(raw);
  if (!draft.synced) return null;
  final byTime = <int, Map<String, LyricLine>>{};
  for (final line in shown.lines) {
    final text = translatableLyricText(line.text);
    if (line.isBackground || text.isEmpty) continue;
    if (lyricLineSupplements(line).isEmpty) continue;
    (byTime[line.time.inMilliseconds] ??= {}).putIfAbsent(text, () => line);
  }
  if (byTime.isEmpty) return null;

  LyricLine? shownLineOf(LyricsDraftLine line) {
    final time = line.time;
    final text = translatableLyricText(line.text);
    if (time == null || text.isEmpty) return null;
    // Older files mix centisecond lines with millisecond keys; the parsers
    // match within 10ms.
    final ms = time.inMilliseconds;
    for (var delta = 0; delta <= 10; delta++) {
      final match = byTime[ms - delta]?[text] ?? byTime[ms + delta]?[text];
      if (match != null) return match;
    }
    return null;
  }

  LyricsDraftLine withTranslation(LyricsDraftLine line) {
    final shownLine = shownLineOf(line);
    if (shownLine == null) return line;
    final tags = {
      for (final (kind, payload) in lyricLineSupplements(shownLine))
        kind: payload,
    };
    // Text written anew drops word timing kept for the old text.
    for (final kind in const ['romaji', 'translation']) {
      if (!tags.containsKey(kind) || tags.containsKey('$kind-words')) continue;
      final old = line.supplements.where((tag) => tag.$1 == kind);
      if (old.isEmpty || old.first.$2 != tags[kind]) tags['$kind-words'] = '';
    }
    // The parsers keep a line's first tag of a kind, so a new one takes the
    // old one's place and any later copy goes.
    final supplements = <(String, String)>[];
    final placed = <String>{};
    for (final supplement in line.supplements) {
      final kind = supplement.$1;
      if (!tags.containsKey(kind)) {
        supplements.add(supplement);
      } else if (placed.add(kind) && tags[kind]!.isNotEmpty) {
        supplements.add((kind, tags[kind]!));
      }
    }
    for (final MapEntry(:key, :value) in tags.entries) {
      if (placed.add(key) && value.isNotEmpty) supplements.add((key, value));
    }
    return LyricsDraftLine(
      time: line.time,
      text: line.text,
      voice: line.voice,
      content: line.content,
      contentTime: line.contentTime,
      supplements: supplements,
      background: line.background,
    );
  }

  final updated = LyricsDraft(
    header: draft.header,
    lines: [for (final line in draft.lines) withTranslation(line)],
    footer: draft.footer,
  ).toLrc();
  return updated == draft.toLrc() ? null : updated;
}
