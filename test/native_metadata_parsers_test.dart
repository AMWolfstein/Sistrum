import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/csv_import_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final fixtures =
      jsonDecode(
            File(
              'test/fixtures/native_metadata_parsers.json',
            ).readAsStringSync(),
          )
          as Map<String, dynamic>;
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  for (final (index, raw) in (fixtures['playlists'] as List).indexed) {
    final fixture = Map<String, dynamic>.from(raw as Map);
    test('playlist worker matches shared native fixture $index', () async {
      final tracks = await CsvImportService.parsePlaylistInBackground(
        Uint8List.fromList(utf8.encode(fixture['body'] as String)),
        format: fixture['format'] as String,
        idSeed: fixture['id_seed'] as int,
      );
      expect(
        tracks
            .map(
              (track) => {
                'id': track.id,
                'name': track.name,
                'artistName': track.artistName,
                'albumName': track.albumName,
                'duration': track.duration,
                'isrc': track.isrc,
                'coverUrl': track.coverUrl,
              },
            )
            .toList(growable: false),
        fixture['tracks'],
      );
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'runNativeDataJob');
        final arguments = call.arguments as Map;
        expect(jsonDecode(arguments['request_json'] as String), {
          'operation': 'parse_playlist',
          'format': fixture['format'],
          'id_seed': fixture['id_seed'],
        });
        expect(
          arguments['bytes'] as Uint8List,
          utf8.encode(fixture['body'] as String),
        );
        return jsonEncode({'tracks': fixture['tracks']});
      });
      try {
        final nativeRows = await CsvImportService.parsePlaylistInBackground(
          Uint8List.fromList(utf8.encode(fixture['body'] as String)),
          format: fixture['format'] as String,
          idSeed: fixture['id_seed'] as int,
        );
        expect(
          nativeRows.map((track) => track.id),
          tracks.map((track) => track.id),
        );
        expect(
          nativeRows.map((track) => track.name),
          tracks.map((track) => track.name),
        );
      } finally {
        messenger.setMockMethodCallHandler(channel, null);
      }
    });
  }
}
