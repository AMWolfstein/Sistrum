import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/tooltip_finder.dart';

class _Settings extends SettingsNotifier {
  _Settings([this.initial = const AppSettings(localLibraryEnabled: false)]);

  final AppSettings initial;

  @override
  AppSettings build() => initial;
}

LibraryBrowseEntry _album(int index) => LibraryBrowseEntry(
  source: 'downloaded',
  key: 'album-$index',
  name: 'Album $index',
  artist: 'Artist $index',
  samplePath: '',
  trackCount: 1,
);

void main() {
  for (final desktop in [false, true]) {
    for (final newest in [false, true]) {
      testWidgets('album year sort applies and survives reopening '
          '(desktop: $desktop, newest: $newest)', (tester) async {
        SharedPreferences.setMockInitialValues({});
        final preferences = await SharedPreferences.getInstance();
        debugDesktopPlatformOverride = desktop;
        addTearDown(() => debugDesktopPlatformOverride = null);
        tester.view.physicalSize = desktop
            ? const Size(1200, 900)
            : const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final requests = <LibraryBrowseRequest>[];
        Future<void> pumpAlbums() async {
          await tester.pumpWidget(
            ProviderScope(
              overrides: [
                settingsProvider.overrideWith(
                  () => _Settings(loadBootstrapSettings(preferences)),
                ),
                libraryBrowsePageLoaderProvider.overrideWithValue((page) async {
                  requests.add(page.browse);
                  return LibraryBrowsePage([_album(0)]);
                }),
              ],
              child: MaterialApp(
                theme: MornyeTheme.build(Brightness.light),
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                home: Scaffold(
                  body: MornyeLibraryScreen(
                    page: MornyeLibraryPage.albums,
                    onOpenSection: (_) {},
                  ),
                ),
              ),
            ),
          );
          await tester.pumpAndSettle();
        }

        await pumpAlbums();
        final l10n = AppLocalizations.of(
          tester.element(find.byType(MornyeLibraryScreen)),
        );
        await tester.tap(findByTooltip(l10n.searchSortTitle));
        await tester.pumpAndSettle();
        await tester.tap(
          find.text(
            newest
                ? l10n.libraryFilterSortYearDesc
                : l10n.libraryFilterSortYearAsc,
          ),
        );
        await tester.pumpAndSettle();
        final mode = newest ? 'year-desc' : 'year-asc';
        expect(requests.last.sort, mode);
        expect(loadBootstrapSettings(preferences).libraryAlbumSortMode, mode);
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pumpAndSettle();
        await pumpAlbums();
        expect(requests.last.sort, mode);
        expect(tester.takeException(), isNull);
      });
    }
  }

  testWidgets(
    'Mornye albums header has a sort button and no completeness filter',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final requests = <LibraryBrowseRequest>[];
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            settingsProvider.overrideWith(_Settings.new),
            libraryBrowsePageLoaderProvider.overrideWithValue((page) async {
              requests.add(page.browse);
              return LibraryBrowsePage([
                LibraryBrowseEntry.fromRow({
                  'queue_source': 'downloaded',
                  'album_key': 'album',
                  'album_name': 'Album',
                  'artist_name': 'Artist',
                  'track_count': 8,
                }, isArtist: false),
              ]);
            }),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: MornyeLibraryScreen(
                page: MornyeLibraryPage.albums,
                onOpenSection: (_) {},
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final l10n = AppLocalizations.of(
        tester.element(find.byType(MornyeLibraryScreen)),
      );
      expect(findByTooltip(l10n.searchSortTitle), findsOneWidget);
      expect(findByTooltip('Filters'), findsNothing);
      expect(find.text('Album'), findsOneWidget);
      expect(requests.last.completeness, isNull);
      expect(tester.takeException(), isNull);
    },
  );
}
