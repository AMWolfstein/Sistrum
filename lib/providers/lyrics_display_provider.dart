import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart' show setEquals;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/lyrics_romanization.dart';
import 'package:sistrum/services/romanization/pinyin.dart';
import 'package:sistrum/services/romanization/text_romanizer.dart';
import 'package:sistrum/utils/lyrics_parser.dart';

/// Parses the app's `locale` setting (`system`, `en`, `pt_PT`, `zh_CN`).
Locale? localeFromAppSetting(String value) {
  if (value.isEmpty || value == 'system') return null;
  final parts = value.split(RegExp('[-_]'));
  return Locale(parts.first, parts.length > 1 ? parts[1] : null);
}

/// The scripts of the app's language, or of the system's when the app
/// follows it: the user reads them, so they are never romanized.
Set<LyricsScript> lyricsReaderScriptsFor(AppSettings settings) =>
    lyricsReaderScripts(
      localeFromAppSetting(settings.locale) ??
          PlatformDispatcher.instance.locale,
    );

final pinyinDataLoaderProvider = Provider<PinyinDataLoader>(
  (ref) => PinyinDataLoader(),
);

/// Local lyric display: the offline romanization and main-line settings
/// choose how lyrics show. Nothing here is saved or sent anywhere. The state
/// counts loaded pinyin data, so watching views rebuild once it arrives.
final lyricsDisplayProvider =
    NotifierProvider<LyricsDisplayController, int>(
      LyricsDisplayController.new,
    );

class LyricsDisplayController extends Notifier<int> {
  late PinyinDataLoader _pinyin;
  final _shown = Expando<(bool, bool, PinyinData?, Set<LyricsScript>, ParsedLyrics)>();
  bool _pinyinRequested = false;

  @override
  int build() {
    _pinyin = ref.watch(pinyinDataLoaderProvider);
    return 0;
  }

  /// [lyrics] as the player shows them, with the offline romanization and
  /// the main line the settings choose.
  ParsedLyrics shown(ParsedLyrics lyrics) {
    if (lyrics.isEmpty) return lyrics;
    final settings = ref.read(settingsProvider);
    final romanize = settings.lyricsRomanize;
    final mainLine = settings.lyricsRomanizedMainLine;
    if (!romanize && !mainLine) return lyrics;
    final pinyin = _pinyin.data;
    final readers = lyricsReaderScriptsFor(settings);
    final memo = _shown[lyrics];
    if (memo != null &&
        memo.$1 == romanize &&
        memo.$2 == mainLine &&
        identical(memo.$3, pinyin) &&
        setEquals(memo.$4, readers)) {
      return memo.$5;
    }
    var result = lyrics;
    if (romanize) {
      if (pinyin == null && lyricsNeedPinyin(lyrics, readerScripts: readers)) {
        _loadPinyin();
      }
      result = lyricsWithOfflineRomanization(
        result,
        pinyin: pinyin,
        readerScripts: readers,
      );
    }
    if (mainLine) result = lyricsWithRomanizedMainLines(result);
    _shown[lyrics] = (romanize, mainLine, pinyin, readers, result);
    return result;
  }

  /// Loads the Chinese readings, then has the views show them.
  void _loadPinyin() {
    if (_pinyinRequested) return;
    _pinyinRequested = true;
    unawaited(
      _pinyin.load().then((data) {
        if (data == null || !ref.mounted) return;
        state = state + 1;
      }),
    );
  }
}

/// Whether [lyrics] carries translation lines of its own, from the lyrics
/// file rather than from any online service.
bool lyricsHaveTranslation(ParsedLyrics lyrics) => lyrics.lines.any(
  (line) => line.translation?.trim().isNotEmpty == true,
);
