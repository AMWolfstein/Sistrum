import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/app_icon_finder.dart';
import 'support/re_enrich_settings.dart';

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');

  testWidgets('downloaded album rows follow playback', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(430, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final cache = Directory.systemTemp.createTempSync('album_now_playing_');
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

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(ReEnrichTestSettings.new),
          downloadHistoryProvider.overrideWith(_History.new),
          libraryAlbumItemsProvider(albumKey).overrideWith(
            (ref) async => [
              for (final (index, id) in ['first', 'second'].indexed)
                UnifiedLibraryItem.fromDownloadHistory(
                  DownloadHistoryItem(
                    id: id,
                    trackName: id == 'first' ? 'First' : 'Second',
                    artistName: 'Artist',
                    albumName: 'Album',
                    filePath: '/music/$id.flac',
                    service: 'example-provider',
                    downloadedAt: DateTime(2026),
                    trackNumber: index + 1,
                    duration: 180,
                  ),
                ),
            ],
          ),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) =>
                Stream.value(const MediaItem(id: 'second', title: 'Second')),
          ),
          playbackPlayingProvider.overrideWith((ref) => true),
          playbackLoadingProvider.overrideWith((ref) => false),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AlbumScreen.library(albumKey: albumKey),
        ),
      ),
    );
    // The current row's glyph loops, so pump in bounded steps. Real file
    // IO from the cover lookups needs real time between the frames, or the
    // teardown waits for it forever.
    for (var i = 0; i < 20; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 5)),
      );
      await tester.pump(const Duration(milliseconds: 50));
    }

    Finder row(String title) => find.byWidgetPredicate(
      (widget) => widget is AlbumTrackTile && widget.trackName == title,
    );
    expect(row('Second'), findsOneWidget);
    expect(
      find.descendant(of: row('Second'), matching: find.byTooltip('Pause')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row('First'), matching: find.byTooltip('Play')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: row('Second'),
        matching: findAppIcon(CupertinoIcons.pause_fill),
      ),
      findsOneWidget,
    );
  });
}
