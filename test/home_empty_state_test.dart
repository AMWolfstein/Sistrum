import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/explore_provider.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/providers/home_top_picks_provider.dart';
import 'package:sistrum/providers/library_mixes_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/recent_access_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/weekly_releases_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/services/weekly_releases.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/error_card.dart';

import 'support/fixed_settings.dart';

const _title = 'Nothing here yet';

/// Home without extensions, a feed or a song in the player, with [added] as
/// its Recently added shelf.
Widget _home({
  required bool mornye,
  List<UnifiedLibraryItem> added = const [],
}) => ProviderScope(
  overrides: [
    homeNewReleasesProvider.overrideWith(
      (ref) => Stream.value(const <WeeklyRelease>[]),
    ),
    settingsProvider.overrideWith(
      () => FixedSettingsNotifier(
        const AppSettings(homeFeedProvider: AppSettings.homeFeedProviderOff),
      ),
    ),
    extensionProvider.overrideWith(_Extensions.new),
    exploreProvider.overrideWith(_Explore.new),
    downloadHistoryProvider.overrideWith(_History.new),
    recentAccessProvider.overrideWith(_Recent.new),
    libraryMixesProvider.overrideWith((ref) async => const []),
    homeTopPicksProvider.overrideWith((ref) async => const <ExploreItem>[]),
    homeRecentlyAddedProvider.overrideWith(
      (ref) async => (items: added, trackCount: added.length),
    ),
    homeRecentlyPlayedProvider.overrideWith((ref) async => const []),
    homeOnRepeatProvider.overrideWith((ref) async => const []),
    playerUiEnabledProvider.overrideWithValue(true),
    currentMediaItemProvider.overrideWithBuild(
      (ref, notifier) => Stream<MediaItem?>.value(null),
    ),
    playbackResumeProvider.overrideWith((ref) async => null),
  ],
  child: MaterialApp(
    theme: mornye ? MornyeTheme.build(Brightness.dark) : AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: const HomeTab(mode: HomeTabMode.browse),
  ),
);

Future<void> _open(WidgetTester tester, Widget home) async {
  tester.view.physicalSize = const Size(400, 900);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(home);
  for (var frame = 0; frame < 6; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  testWidgets('the Sistrum Home with nothing to show says so', (tester) async {
    await _open(tester, _home(mornye: true));
    expect(tester.takeException(), isNull);

    expect(find.byType(EmptyState), findsOneWidget);
    expect(find.text(_title), findsOneWidget);
    expect(
      find.text('Songs you add to your Library or play show up here.'),
      findsOneWidget,
    );
  });

  testWidgets('a Sistrum Home with a shelf has no empty state', (tester) async {
    await _open(
      tester,
      _home(
        mornye: true,
        added: [
          UnifiedLibraryItem.fromLocalLibrary(
            LocalLibraryItem(
              id: 'song',
              trackName: 'Example song',
              artistName: 'Artist',
              albumName: 'Album',
              filePath: '/music/example.flac',
              scannedAt: DateTime.utc(2026, 10, 1),
            ),
          ),
        ],
      ),
    );
    expect(tester.takeException(), isNull);

    expect(find.text('Recently added'), findsOneWidget);
    expect(find.text(_title), findsNothing);
  });

  testWidgets('the Material Home keeps its Shortcuts instead', (tester) async {
    await _open(tester, _home(mornye: false));
    expect(tester.takeException(), isNull);

    expect(find.text('Shortcuts'), findsOneWidget);
    expect(find.text(_title), findsNothing);
  });
}

class _Extensions extends ExtensionNotifier {
  @override
  ExtensionState build() => const ExtensionState();
}

class _Explore extends ExploreNotifier {
  @override
  ExploreState build() => const ExploreState();

  @override
  Future<void> fetchHomeFeed({bool forceRefresh = false}) async {}
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

class _Recent extends RecentAccessNotifier {
  @override
  RecentAccessState build() => const RecentAccessState(isLoaded: true);
}
