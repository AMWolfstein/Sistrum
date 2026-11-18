import 'dart:io';

import 'package:path/path.dart' as p;

/// The local file a cover reference names: an absolute path or a `file://`
/// URI, read as it is. Null for other references and for files not on disk.
String? localCoverPath(String? ref) {
  if (ref == null || ref.isEmpty) return null;
  if (File(ref).isAbsolute) return ref;
  final uri = Uri.tryParse(ref);
  if (uri == null || !uri.isScheme('file')) return null;
  try {
    return uri.toFilePath();
  } on UnsupportedError {
    return null;
  }
}

/// [reference] when it names a local cover that outlasts this run, else null:
/// a file in a temporary folder goes with the next purge, and a web URL is
/// not a local cover.
String? durableCoverReference(String? reference) {
  final value = reference?.trim();
  if (value == null || value.isEmpty) return null;
  final uri = Uri.tryParse(value);
  if (uri != null && (uri.isScheme('http') || uri.isScheme('https'))) {
    return null;
  }
  final path = localCoverPath(value);
  if (path == null) return value;
  final normalized = p.normalize(path);
  // Legacy network tags and download previews are read from these folders.
  final temporary =
      p
          .split(normalized)
          .any(
            (part) =>
                part == 'network_metadata' ||
                part.startsWith('download_cover_preview_'),
          ) ||
      p.isWithin(p.normalize(Directory.systemTemp.path), normalized);
  return temporary ? null : value;
}
