import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/services/m3u_playlist_service.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/logger.dart';

List<Track> _parsePlaylistInBackground(Map<String, dynamic> request) {
  final content = utf8.decode(request['bytes'] as Uint8List);
  final seed = request['id_seed'] as int;
  return request['format'] == 'csv'
      ? CsvImportService._parseCsv(content, idSeed: seed)
      : M3uPlaylistService.parseM3u(content, idSeed: seed);
}

List<Track> _hydratePlaylistInBackground(List<dynamic> rows) => [
  for (final row in rows) Track.fromJson(Map<String, dynamic>.from(row as Map)),
];

class CsvImportService {
  static final _log = AppLogger('CsvImportService');
  static final RegExp _lineSplitPattern = RegExp(r'\r\n|\r|\n');

  /// Native parsing consumes bytes directly. Old native binaries and test
  /// hosts retain the same parser in an isolate, never the Flutter frame.
  static Future<List<Track>> parsePlaylistInBackground(
    Uint8List bytes, {
    required String format,
    int? idSeed,
  }) async {
    final seed = idSeed ?? DateTime.now().millisecondsSinceEpoch;
    List<dynamic> rows;
    try {
      final result = await PlatformBridge.runNativeDataJob({
        'operation': 'parse_playlist',
        'format': format,
        'id_seed': seed,
      }, bytes: bytes);
      rows = result['tracks'] as List;
    } on MissingPluginException {
      return compute(_parsePlaylistInBackground, {
        'bytes': bytes,
        'format': format,
        'id_seed': seed,
      });
    }
    return compute(_hydratePlaylistInBackground, rows);
  }

  static List<Track> _parseCsv(String content, {int? idSeed}) {
    final List<Track> tracks = [];
    final lines = content.split(_lineSplitPattern);
    if (lines.isEmpty) return tracks;

    int startIdx = 0;
    while (startIdx < lines.length && lines[startIdx].trim().isEmpty) {
      startIdx++;
    }
    if (startIdx >= lines.length) return tracks;

    final headers = _parseLine(lines[startIdx]);
    final colMap = <String, int>{};
    for (int i = 0; i < headers.length; i++) {
      String h = _cleanValue(headers[i]).toLowerCase();
      colMap[h] = i;
    }

    _log.d('CSV Headers: ${colMap.keys.toList()}');

    for (int i = startIdx + 1; i < lines.length; i++) {
      final line = lines[i].trim();
      if (line.isEmpty) continue;

      final values = _parseLine(line);

      String? getVal(List<String> keys) {
        return _getValue(values, colMap, keys);
      }

      String? trackName = getVal(['track name', 'track', 'name', 'title']);
      String? artistName = getVal([
        'artist name(s)',
        'artist name',
        'artist',
        'artists',
      ]);
      String? albumName = getVal(['album name', 'album']);
      String? isrc = getVal(['isrc']);
      String? spotifyId = getVal([
        'track uri',
        'spotify - id',
        'spotify id',
        'spotify_id',
        'id',
        'uri',
      ]);

      if (spotifyId != null && spotifyId.startsWith('spotify:track:')) {
        spotifyId = spotifyId.replaceAll('spotify:track:', '');
      }

      if ((trackName != null && trackName.isNotEmpty && artistName != null) ||
          (spotifyId != null && spotifyId.isNotEmpty)) {
        tracks.add(
          Track(
            id:
                spotifyId ??
                'csv_${idSeed ?? DateTime.now().millisecondsSinceEpoch}_$i',
            name: trackName ?? 'Unknown Track',
            artistName: artistName ?? 'Unknown Artist',
            albumName: albumName ?? 'Unknown Album',
            isrc: isrc,
            duration: 0,
            coverUrl: null,
          ),
        );
      }
    }

    _log.i('Parsed ${tracks.length} tracks from CSV');
    return tracks;
  }

  static String? _getValue(
    List<String> values,
    Map<String, int> colMap,
    List<String> possibleKeys,
  ) {
    for (final key in possibleKeys) {
      if (colMap.containsKey(key)) {
        final index = colMap[key]!;
        if (index < values.length) {
          return _cleanValue(values[index]);
        }
      }
    }
    return null;
  }

  static String _cleanValue(String val) {
    val = val.trim();
    if (val.startsWith('"') && val.endsWith('"') && val.length >= 2) {
      val = val.substring(1, val.length - 1);
    }
    val = val.replaceAll('""', '"');
    return val;
  }

  static List<String> _parseLine(String line) {
    final List<String> result = [];
    bool inQuote = false;
    var buffer = StringBuffer();

    for (int i = 0; i < line.length; i++) {
      final char = line[i];
      if (char == '"') {
        if (inQuote && i + 1 < line.length && line[i + 1] == '"') {
          buffer.write('"');
          i++;
        } else {
          inQuote = !inQuote;
        }
        continue;
      }
      if (char == ',' && !inQuote) {
        result.add(buffer.toString());
        buffer = StringBuffer();
        continue;
      }
      buffer.write(char);
    }
    result.add(buffer.toString());
    return result;
  }
}
