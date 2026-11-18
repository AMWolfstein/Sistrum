part of '../platform_bridge.dart';

Object? _decodeJsonInBackground(String json) => jsonDecode(json);
String _encodeJsonInBackground(Object? value) => jsonEncode(value);

Future<String> _encodeNativeDataJobRequest(Map<String, dynamic> request) async {
  var nodes = 0;
  var characters = 0;
  bool small(Object? value, int depth) {
    if (++nodes > 256 || depth > 8) return false;
    if (value == null || value is bool || value is num) return true;
    if (value is String) {
      characters += value.length;
      return characters <= 16 * 1024;
    }
    if (value is List<Object?>) {
      if (value.length > 64) return false;
      return value.every((item) => small(item, depth + 1));
    }
    if (value is Map<Object?, Object?>) {
      if (value.length > 64) return false;
      return value.entries.every(
        (entry) =>
            entry.key is String &&
            small(entry.key, depth + 1) &&
            small(entry.value, depth + 1),
      );
    }
    return false;
  }

  if (small(request, 0)) return jsonEncode(request);
  return compute(_encodeJsonInBackground, request);
}

Object? _decodeJsonFileInBackground(String path) {
  final contents = File(path).readAsStringSync();
  return contents.isEmpty ? null : jsonDecode(contents);
}
