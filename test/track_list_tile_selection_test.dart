import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';

import 'support/fixed_settings.dart';

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

void main() {
  test('explicit batch selection forces one provider and quality picker', () {
    expect(
      shouldShowBatchDownloadPicker(
        forceQualityPicker: true,
        askQualityBeforeDownload: false,
        allowQualityVariants: false,
      ),
      isTrue,
    );
  });

  // Ported from the online album page: the library album's selection bar sits
  // in front of the shell navbar, so taps on the bar never reach the navbar.
  testWidgets('album selection bar is mounted in front of the shell navbar', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    await tester.binding.setSurfaceSize(const Size(430, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var navbarTaps = 0;
    final albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(FixedSettingsNotifier.new),
          downloadHistoryProvider.overrideWith(_History.new),
          libraryAlbumItemsProvider(albumKey).overrideWith(
            (ref) async => [
              UnifiedLibraryItem.fromDownloadHistory(
                DownloadHistoryItem(
                  id: 'track-1',
                  trackName: 'Selected song',
                  artistName: 'Artist',
                  albumName: 'Album',
                  filePath: '/music/track-1.flac',
                  service: 'example-provider',
                  downloadedAt: DateTime.utc(2026),
                  trackNumber: 1,
                  duration: 180,
                ),
              ),
            ],
          ),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(null),
          ),
          playbackPlayingProvider.overrideWith((ref) => false),
          playbackLoadingProvider.overrideWith((ref) => false),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: SelectionOverlayHost(
            child: Scaffold(
              extendBody: true,
              body: AlbumScreen.library(albumKey: albumKey),
              bottomNavigationBar: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => navbarTaps++,
                child: const SizedBox(height: 80),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Selected song'));
    await tester.longPress(find.text('Selected song'));
    await tester.pumpAndSettle();

    expect(find.byType(SelectionBottomBar), findsOneWidget);
    await tester.tapAt(const Offset(2, 898));
    await tester.pump();

    expect(navbarTaps, 0);
  });
}
