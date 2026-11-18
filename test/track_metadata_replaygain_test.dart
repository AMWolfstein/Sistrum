import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';

import 'support/app_icon_finder.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  for (final hasTags in [true, false]) {
    testWidgets('metadata displays only saved ReplayGain (tags=$hasTags)', (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues({});
      final metadata = hasTags
          ? {
              'bpm': '120',
              'mood': 'Calm',
              'replaygain_track_gain': ' -6.20 dB ',
              'replaygain_track_peak': '0.000000',
              'replaygain_album_gain': '0.00 dB',
              'replaygain_album_peak': '1.234567',
            }
          : {'replaygain_track_gain': ' ', 'replaygain_album_peak': null};
      var edits = 0;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'editFileMetadata') {
          edits++;
          final args = call.arguments as Map;
          final fields = Map<String, String>.from(
            jsonDecode(args['metadata_json'] as String) as Map,
          );
          metadata.addAll(fields);
          return jsonEncode({'success': true, 'method': 'native'});
        }
        return switch (call.method) {
          'safStat' => jsonEncode({'exists': true, 'size': 100}),
          'readAudioMetadata' => jsonEncode(metadata),
          'readFileMetadata' => jsonEncode({
            ...metadata,
            'audio_codec': 'flac',
          }),
          'safCopyToTemp' => 'temporary-song.flac',
          'writeTempToSaf' => jsonEncode({'success': true}),
          'getLyricsLRCWithSource' => jsonEncode({'lyrics': '', 'source': ''}),
          'getSafFileModTimes' => '{}',
          _ => null,
        };
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

      final item = LocalLibraryItem(
        id: 'track-$hasTags',
        trackName: 'Track',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: 'content://library/document/track-$hasTags.flac',
        scannedAt: DateTime(2026),
        bitDepth: 16,
        sampleRate: 44100,
        format: 'flac',
        hasReplayGain: hasTags,
      );
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: TrackMetadataScreen(localItem: item),
          ),
        ),
      );
      await tester.pumpAndSettle();
      for (final text in [
        'BPM',
        '120',
        'Mood',
        'Calm',
        'ReplayGain Track Gain',
        'ReplayGain Track Peak',
        'ReplayGain Album Gain',
        'ReplayGain Album Peak',
        '-6.20 dB',
        '0.000000',
        '0.00 dB',
        '1.234567',
      ]) {
        expect(find.text(text), hasTags ? findsOneWidget : findsNothing);
      }
      Future<void> openRemoval() async {
        await tester.tap(findAppIcon(Icons.more_vert));
        await tester.pumpAndSettle();
        expect(find.text('Edit audio file'), findsNothing);
        expect(find.text('Similar songs in Library'), findsNothing);
        expect(find.text('Remove ReplayGain'), findsOneWidget);
        await tester.ensureVisible(find.text('Remove ReplayGain'));
        await tester.tap(find.text('Remove ReplayGain'));
        await tester.pumpAndSettle();
        expect(find.byType(AppAlertDialog), findsOneWidget);
      }

      await openRemoval();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(edits, 0);
      if (hasTags) expect(find.text('-6.20 dB'), findsOneWidget);

      await openRemoval();
      await tester.tap(
        find.descendant(
          of: find.byType(AppDialogAction),
          matching: find.text('Remove ReplayGain'),
        ),
      );
      // The removal finishes its file I/O in real time, between frames.
      for (var attempt = 0; attempt < 20; attempt++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 50)),
        );
        await tester.pump(const Duration(milliseconds: 50));
      }
      await tester.pumpAndSettle();
      expect(edits, 1);
      expect(find.text('ReplayGain tags removed'), findsOneWidget);
      expect(find.text('ReplayGain Track Gain'), findsNothing);
      expect(find.text('ReplayGain Album Gain'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }
}
