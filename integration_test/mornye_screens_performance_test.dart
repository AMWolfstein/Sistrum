import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/recent_access_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/user_profile_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/album_completeness.dart';
import 'package:sistrum/services/user_profile_store.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/lazy_tab_view.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

import '../test/support/tooltip_finder.dart';
import 'performance_probe.dart';

final _tracks = List.generate(
  2000,
  (index) => Track(
    id: 'fixture-track-$index',
    name: 'Fixture song $index',
    artistName: 'Artist ${index ~/ 100}',
    albumName: 'Album ${(index ~/ 10).toString().padLeft(3, '0')}',
    duration: 240,
    source: 'example',
  ),
);

final _albums = List.generate(
  200,
  (index) => LibraryBrowseEntry(
    source: 'downloaded',
    key: 'fixture-album-$index',
    name: _tracks[index * 10].albumName,
    artist: _tracks[index * 10].artistName,
    samplePath: '',
    trackCount: 10,
    completeness: AlbumCompleteness(
      status: ['complete', 'incomplete', 'unknown'][index % 3],
      present: 10,
      expected: index % 3 == 2
          ? null
          : index % 3 == 1
          ? 12
          : 10,
    ),
  ),
);

void main() {
  final binding = PerformanceTestBinding.ensureInitialized();

  testWidgets('Mornye production screens with deterministic 2000-track data', (
    tester,
  ) async {
    final probe = PerformanceProbe(binding, tester, suite: 'mornye_screens');
    final preferences = (await tester.runAsync(SharedPreferences.getInstance))!;
    final settingsBefore = preferences.getString('app_settings');
    const databaseChannel = MethodChannel('com.tekartik.sqflite');
    final databaseCalls = <String>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(databaseChannel, (call) async {
      databaseCalls.add(call.method);
      fail('Unexpected database call: ${call.method}');
    });
    addTearDown(
      () => messenger.setMockMethodCallHandler(databaseChannel, null),
    );
    final index = ValueNotifier(0);
    addTearDown(index.dispose);
    final requests = <LibraryBrowseRequest>[];
    final pageOffsets = <int>[];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          libraryCollectionsProvider.overrideWith(_Collections.new),
          recentAccessProvider.overrideWith(_Recent.new),
          userProfileProvider.overrideWith(_Profile.new),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(null),
          ),
          libraryBrowsePageLoaderProvider.overrideWithValue((page) async {
            final request = page.browse;
            requests.add(request);
            pageOffsets.add(page.offset);
            final rows = _albums.where((album) {
              return request.completeness == null ||
                  album.completeness!.status ==
                      (request.completeness == incompleteAlbumFilter
                          ? 'incomplete'
                          : 'unknown');
            }).toList();
            if (request.sort == 'latest') {
              return LibraryBrowsePage(
                rows.reversed.skip(page.offset).take(request.limit).toList(),
              );
            }
            return LibraryBrowsePage(
              rows.skip(page.offset).take(request.limit).toList(),
            );
          }),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: _Tabs(index: index),
        ),
      ),
    );
    await tester.runAsync(() => probe.wait(const Duration(seconds: 1)));

    Future<void> select(int value) async {
      FocusManager.instance.primaryFocus?.unfocus();
      index.value = value;
      await probe.wait(const Duration(milliseconds: 350));
    }

    Future<void> scrollRoundTrip() async {
      final scrollable = find.byType(Scrollable).hitTestable().first;
      final position = tester.state<ScrollableState>(scrollable).position;
      position.jumpTo(0);
      await probe.wait(const Duration(milliseconds: 100));
      await position.animateTo(
        position.maxScrollExtent.clamp(0.0, 2400.0),
        duration: const Duration(milliseconds: 900),
        curve: Curves.linear,
      );
      await position.animateTo(
        0,
        duration: const Duration(milliseconds: 900),
        curve: Curves.linear,
      );
      await probe.wait(const Duration(milliseconds: 200));
    }

    await tester.runAsync(() async {
      await select(1);
      expect(find.byType(QueueTab), findsOneWidget);
      await tester.tap(find.text('Albums').hitTestable().first);
      await probe.wait(const Duration(milliseconds: 500));
      final position = tester
          .state<ScrollableState>(find.byType(Scrollable).hitTestable().first)
          .position;
      for (var page = 0; page < 4; page++) {
        position.jumpTo(position.maxScrollExtent);
        await probe.wait(const Duration(milliseconds: 250));
      }
      position.jumpTo(0);
      expect(requests.last.limit, 40);
      expect(pageOffsets.last, 160);
    });
    await probe.measure(
      'library_album_grid_scroll_2000_tracks',
      scrollRoundTrip,
      warmup: scrollRoundTrip,
    );

    Future<void> filterRoundTrip() async {
      for (final label in [
        'Incomplete albums',
        'Album completeness unknown',
        'All',
      ]) {
        await tester.tap(findByTooltip('Filters').hitTestable());
        await probe.wait(const Duration(milliseconds: 350));
        await tester.tap(find.text(label).hitTestable().last);
        await probe.wait(const Duration(milliseconds: 350));
      }
      expect(requests.last.completeness, isNull);
    }

    await probe.measure(
      'library_album_completeness_filters',
      filterRoundTrip,
      warmup: filterRoundTrip,
    );
    await tester.runAsync(() async {
      Navigator.of(
        tester.element(find.byType(Scrollable).hitTestable().first),
      ).pop();
      await probe.wait(const Duration(milliseconds: 500));
    });

    await tester.runAsync(() => select(2));
    Future<void> tabRoundTrip() async {
      for (final value in [0, 1, 2, 0]) {
        await tester.tap(
          find.descendant(
            of: find.byType(MornyeTabBar),
            matching: find.text(['Home', 'Library', 'Search'][value]),
          ),
        );
        await probe.wait(const Duration(milliseconds: 350));
        expect(index.value, value);
      }
    }

    await probe.measure(
      'lazy_tab_view_retained_production_tabs',
      tabRoundTrip,
      warmup: tabRoundTrip,
    );
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() async {
      await probe.wait(const Duration(milliseconds: 350));
      await preferences.reload();
    });
    expect(preferences.getString('app_settings'), settingsBefore);
    expect(databaseCalls, isEmpty);
    await probe.finish(
      metadata: {
        'scope':
            'Production QueueTab, MornyeLibraryScreen, HomeTab, LazyTabView and MornyeTabBar; synthetic provider data, no artwork/network/database/IME.',
        'trackCount': _tracks.length,
        'albumCount': _albums.length,
        'browseRequests': requests.length,
        'maximumBrowseLimit': requests
            .map((request) => request.limit)
            .reduce((a, b) => a > b ? a : b),
        'settingsUnchanged': true,
        'databaseCalls': databaseCalls.length,
      },
    );
  });
}

class _Tabs extends StatelessWidget {
  const _Tabs({required this.index});
  final ValueNotifier<int> index;

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<int>(
    valueListenable: index,
    builder: (context, value, _) => Scaffold(
      body: LazyTabView(
        index: value,
        preloadKeys: {const ValueKey('search')},
        children: const [
          HomeTab(key: ValueKey('home'), mode: HomeTabMode.browse),
          QueueTab(key: ValueKey('library')),
          HomeTab(key: ValueKey('search'), mode: HomeTabMode.search),
        ],
      ),
      bottomNavigationBar: MornyeTabBar(
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home), label: 'Home'),
          NavigationDestination(
            icon: Icon(Icons.library_music),
            label: 'Library',
          ),
          NavigationDestination(icon: Icon(Icons.search), label: 'Search'),
        ],
        selectedIndex: value,
        blurEnabled: true,
        onSelected: (value) {
          FocusManager.instance.primaryFocus?.unfocus();
          index.value = value;
        },
      ),
    ),
  );
}

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();
}

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);
}

class _Recent extends RecentAccessNotifier {
  @override
  RecentAccessState build() => const RecentAccessState(isLoaded: true);
}

class _Profile extends UserProfileNotifier {
  @override
  Future<UserProfile> build() async => const UserProfile();
}
