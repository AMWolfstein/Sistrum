import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/local_track_batch_actions.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';

import 'support/re_enrich_settings.dart';

class _History extends DownloadHistoryNotifier {
  final _updates = <String, String?>{};

  @override
  DownloadHistoryState build() => DownloadHistoryState();

  @override
  Future<void> updateMetadataForItem({
    required String id,
    required String trackName,
    required String artistName,
    required String albumName,
    String? albumArtist,
    String? coverUrl,
    String? isrc,
    int? trackNumber,
    int? totalTracks,
    int? discNumber,
    int? totalDiscs,
    String? releaseDate,
    String? genre,
    String? composer,
    String? label,
    String? copyright,
    bool? explicit,
    bool? hasLyrics,
    int? lyricsMetadataScanVersion,
  }) async {
    _updates[id] = isrc;
  }
}

class _Library extends LocalLibraryNotifier {
  int _refreshes = 0;

  @override
  LocalLibraryState build() => LocalLibraryState();

  @override
  Future<void> scanAllSources({
    bool forceFullScan = false,
    bool notify = true,
  }) async {
    _refreshes++;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  for (final includeLocal in [false, true]) {
    testWidgets(
      'Library re-enrich includes downloaded tracks and refreshes each source (mixed: $includeLocal)',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        final cache = Directory.systemTemp.createTempSync('library_reenrich_');
        DownloadedEmbeddedCoverResolver.setPersistentCacheDirectoryForTesting(
          cache,
        );
        addTearDown(() async {
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.runAsync(() async {
            await DownloadedEmbeddedCoverResolver.resetMemoryStateForTesting();
            DownloadedEmbeddedCoverResolver.setPersistentCacheDirectoryForTesting(
              null,
            );
            await cache.delete(recursive: true);
          });
        });
        final history = _History();
        final library = _Library();
        final requests = <Map<String, dynamic>>[];
        final paths = [
          'content://library/document/download.flac',
          if (includeLocal) '/music/local.flac',
        ];
        var completed = false;
        messenger.setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'reEnrichFile') {
            final request = Map<String, dynamic>.from(
              jsonDecode((call.arguments as Map)['request_json'] as String)
                  as Map,
            );
            requests.add(request);
            return request['preview_only'] == true
                ? {
                    'method': 'preview',
                    'enriched_metadata': {'isrc': 'USABC2600001'},
                  }
                : {'method': 'native'};
          }
          if (call.method == 'readAudioMetadata') {
            return {
              'trackName': 'Downloaded',
              'artistName': 'Artist',
              'albumName': 'Album',
              'isrc': 'USABC2600001',
            };
          }
          return null;
        });
        addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
        tester.view.physicalSize = const Size(800, 1600);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              settingsProvider.overrideWith(ReEnrichTestSettings.new),
              downloadHistoryProvider.overrideWith(() => history),
              localLibraryProvider.overrideWith(() => library),
            ],
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.light),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(
                body: Consumer(
                  builder: (context, ref, _) => TextButton(
                    onPressed: () => reEnrichLibraryTracks(
                      context,
                      ref,
                      [
                        UnifiedLibraryItem.fromDownloadHistory(
                          DownloadHistoryItem(
                            id: 'download',
                            spotifyId: 'provider-a:track-1',
                            trackName: 'Downloaded',
                            artistName: 'Artist',
                            albumName: 'Album',
                            filePath: paths.first,
                            service: 'example-provider',
                            downloadedAt: DateTime(2026),
                          ),
                        ),
                        if (includeLocal)
                          UnifiedLibraryItem.fromLocalLibrary(
                            LocalLibraryItem(
                              id: 'local',
                              trackName: 'Local',
                              artistName: 'Artist',
                              albumName: 'Album',
                              filePath: paths.last,
                              scannedAt: DateTime(2026),
                            ),
                          ),
                      ],
                      isActive: () => context.mounted,
                      onSelectionHide: () async {},
                      onSelectionRestore: () {},
                      onComplete: () => completed = true,
                    ),
                    child: const Text('Start'),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Start'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Review changes'));
        await tester.pumpAndSettle();
        expect(requests.map((request) => request['file_path']), paths);
        expect(requests.first['spotify_id'], 'provider-a:track-1');
        await tester.tap(find.text('Apply changes'));
        for (var attempt = 0; attempt < 50 && !completed; attempt++) {
          await tester.pump(const Duration(milliseconds: 100));
          await tester.runAsync(() async {
            await Future<void>.delayed(const Duration(milliseconds: 10));
          });
        }
        await tester.pumpAndSettle();
        expect(requests.map((request) => request['file_path']), [
          ...paths,
          ...paths,
        ]);
        expect(requests[paths.length]['spotify_id'], 'provider-a:track-1');
        expect(history._updates, {'download': 'USABC2600001'});
        expect(library._refreshes, includeLocal ? 1 : 0);
        expect(completed, isTrue);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
