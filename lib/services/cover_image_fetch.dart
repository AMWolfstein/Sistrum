import 'dart:io';

import 'package:http/http.dart' as http;

/// Fetches the cover image at [coverUrl] and writes its bytes to
/// [outputPath]. Returns the same `{success, file_path}` or `{success, error}`
/// shape the callers read from the platform bridge.
Future<Map<String, dynamic>> downloadCoverImageToFile(
  String coverUrl,
  String outputPath,
) async {
  final uri = Uri.tryParse(coverUrl);
  if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http')) {
    return {'success': false, 'error': 'Unsupported cover URL'};
  }
  try {
    final response = await http.get(uri).timeout(const Duration(seconds: 20));
    if (response.statusCode != 200 || response.bodyBytes.isEmpty) {
      return {
        'success': false,
        'error': 'Cover request failed (HTTP ${response.statusCode})',
      };
    }
    await File(outputPath).writeAsBytes(response.bodyBytes, flush: true);
    return {'success': true, 'file_path': outputPath};
  } catch (error) {
    return {'success': false, 'error': error.toString()};
  }
}
