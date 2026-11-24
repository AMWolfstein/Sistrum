import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/music_player_service.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/tooltip_finder.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings(playerMode: 'internal');
}

class _Handler extends Fake implements MusicPlayerHandler {}

class _Player extends MusicPlayerController {
  final played = <PlayableMedia>[];

  @override
  Future<MusicPlayerHandler?> ensureInitialized() async => _Handler();

  @override
  Future<void> playSingle(PlayableMedia item) async => played.add(item);

  final queued = <PlayableMedia>[];

  @override
  Future<void> addToQueue(PlayableMedia item) async => queued.add(item);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    messenger.setMockMethodCallHandler(channel, (call) async {
      return switch (call.method) {
        'safStat' => jsonEncode({'exists': true, 'size': 100}),
        'readAudioMetadata' ||
        'readFileMetadata' ||
        'getSafFileModTimes' => '{}',
        'extractCoverToFile' => jsonEncode({'error': 'No cover'}),
        _ => null,
      };
    });
  });
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  for (final format in ['flac', 'opus']) {
    for (final menu in [false, true]) {
      testWidgets('metadata Play carries $format file information '
          '(menu=$menu)', (tester) async {
        await tester.binding.setSurfaceSize(const Size(393, 852));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final player = _Player();
        final bitDepth = format == 'flac' ? 24 : null;
        final sampleRate = format == 'flac' ? 96000 : 48000;
        final bitrate = format == 'flac' ? 2800 : 192;
        final path = 'content://library/document/song.$format';
        final screen = TrackMetadataScreen(
          localItem: LocalLibraryItem(
            id: 'local-song',
            trackName: 'Song',
            artistName: 'Artist',
            albumName: 'Album',
            filePath: path,
            scannedAt: DateTime(2026),
            duration: 210,
            bitDepth: bitDepth,
            sampleRate: sampleRate,
            bitrate: bitrate,
            format: format,
            explicit: true,
          ),
        );
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              settingsProvider.overrideWith(_Settings.new),
              musicPlayerControllerProvider.overrideWithValue(player),
            ],
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.dark),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: screen,
            ),
          ),
        );
        await tester.pumpAndSettle();
        if (menu) {
          await tester.tap(findByTooltip('Show menu'));
          await tester.pumpAndSettle();
          await tester.tap(
            find.descendant(
              of: find.byType(MornyeContextMenu),
              matching: find.text('Play'),
            ),
          );
        } else {
          // Play is in the header, on the first screen.
          await tester.tap(find.text('Play'));
        }
        await tester.pumpAndSettle();

        final media = player.played.single;
        expect(media.source, path);
        expect(media.title, 'Song');
        expect(media.artist, 'Artist');
        expect(media.album, 'Album');
        expect(media.duration, const Duration(seconds: 210));
        expect(media.bitDepth, bitDepth);
        expect(media.sampleRate, sampleRate);
        expect(media.bitrate, bitrate);
        expect(media.format, format);
        expect(media.explicit, isTrue);

        final reads = <String>[];
        final metadata = PlayerMetadataController(
          readMetadata: (path) async {
            reads.add(path);
            return {};
          },
        );
        addTearDown(metadata.dispose);
        // The player must show the known quality without opening Lyrics
        // or copying the SAF document for a full metadata probe.
        await metadata.load(media.toMediaItem());
        expect(
          metadata.qualityLabel,
          format == 'flac'
              ? 'FLAC  ·  24-bit  ·  96 kHz'
              : 'OPUS  ·  48 kHz  ·  192 kbps',
        );
        expect(reads, isEmpty);
        expect(tester.takeException(), isNull);
      });
    }
  }

  for (final exists in [true, false]) {
    testWidgets('metadata header holds the song actions '
        '(file exists=$exists)', (tester) async {
      await tester.binding.setSurfaceSize(const Size(393, 852));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      if (!exists) {
        messenger.setMockMethodCallHandler(channel, (call) async {
          return switch (call.method) {
            'safStat' => jsonEncode({'exists': false}),
            _ => null,
          };
        });
      }
      final player = _Player();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            settingsProvider.overrideWith(_Settings.new),
            musicPlayerControllerProvider.overrideWithValue(player),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: TrackMetadataScreen(
              localItem: LocalLibraryItem(
                id: 'local-song',
                trackName: 'Song',
                artistName: 'Artist',
                albumName: 'Album',
                filePath: 'content://library/document/song.flac',
                scannedAt: DateTime(2026),
                format: 'flac',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Play stays where it is; the round actions need the file.
      expect(find.text('Play'), findsOneWidget);
      final round = exists ? findsOneWidget : findsNothing;
      expect(findByTooltip('Edit Metadata'), round);
      expect(findByTooltip('Add to queue'), round);
      // Delete is kept to the More menu.
      expect(find.text('Delete'), findsNothing);

      await tester.tap(find.text('Play'));
      await tester.pumpAndSettle();
      expect(player.played, hasLength(exists ? 1 : 0));
      if (exists) {
        await tester.tap(findByTooltip('Add to queue'));
        await tester.pumpAndSettle();
        expect(player.queued.single.title, 'Song');
      }
      expect(tester.takeException(), isNull);
    });
  }
}
