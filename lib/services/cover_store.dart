import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:sistrum/utils/cover_art_utils.dart';

/// Resolves local cover references to files.
///
/// A file reference (an absolute path or a `file://` URI) passes through
/// while its file exists. `https` covers stay with CoverCacheManager, and
/// other references are not stored.
class CoverStore {
  const CoverStore();

  static const instance = CoverStore();

  /// The local file [ref] names, or null when it names none or the file is
  /// gone.
  Future<String?> file(String? ref) async {
    if (ref == null || ref.isEmpty) return null;
    final path = localCoverPath(ref);
    return path != null && await File(path).exists() ? path : null;
  }
}

/// The local path an absolute path or `file://` reference names.
String? localCoverPath(String ref) {
  if (File(ref).isAbsolute) return ref;
  final uri = Uri.tryParse(ref);
  if (uri == null || !uri.isScheme('file')) return null;
  try {
    return uri.toFilePath();
  } on UnsupportedError {
    return null;
  }
}

/// [reference] when it names a cover that outlasts this run, else null: a
/// loopback URL ends with the app's process, and a file in a temporary
/// folder goes with the next purge. Other references pass unchanged.
String? durableCoverReference(String? reference) {
  final value = reference?.trim();
  if (value == null || value.isEmpty) return null;
  final uri = Uri.tryParse(value);
  if (uri != null && (uri.isScheme('http') || uri.isScheme('https'))) {
    return isLoopbackHost(uri.host) ? null : value;
  }
  final path = localCoverPath(value);
  if (path == null) return value;
  final normalized = p.normalize(path);
  // Download previews are written to these temporary folders.
  final temporary =
      p
          .split(normalized)
          .any((part) => part.startsWith('download_cover_preview_')) ||
      p.isWithin(p.normalize(Directory.systemTemp.path), normalized);
  return temporary ? null : value;
}
