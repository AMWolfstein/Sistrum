import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sistrum/providers/download_history_provider.dart';

class _Settings extends SettingsNotifier {
  _Settings(this.initial);

  final AppSettings? initial;

  // The Rewind banner animates for as long as it is shown; keep it off so
  // the page settles.
  @override
  AppSettings build() =>
      initial ?? const AppSettings(listeningStatisticsEnabled: false);
}

/// Downloaded songs added on [dates]. With a queue item present and the
/// database page empty, the Library renders this in-memory snapshot.
class _History extends DownloadHistoryNotifier {
  _History(this.dates);

  final List<DateTime> dates;

  @override
  DownloadHistoryState build() => DownloadHistoryState(
    items: [
      for (final (index, date) in dates.indexed)
        DownloadHistoryItem(
          id: 'song-$index',
          trackName: 'Song $index',
          artistName: 'Artist',
          albumName: 'Album $index',
          filePath: '/music/song-$index.flac',
          service: 'example-provider',
          downloadedAt: date,
        ),
    ],
    totalCount: dates.length,
  );
}

class _Local extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

class _Collections extends LibraryCollectionsNotifier {
  _Collections(this.playlists);

  final List<UserPlaylistCollection> playlists;

  @override
  LibraryCollectionsState build() =>
      LibraryCollectionsState(isLoaded: true, playlists: playlists);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final queries = <String>[];

  setUp(() {
    queries.clear();
    SharedPreferences.setMockInitialValues({});
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactorySqflitePlugin;
    addTearDown(() => databaseFactory = previousFactory);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const paths = MethodChannel('plugins.flutter.io/path_provider');
    messenger.setMockMethodCallHandler(paths, (_) async => '/test/documents');
    addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
    // An empty database: every page query returns no rows.
    const sqlite = MethodChannel('com.tekartik.sqflite');
    final versions = <int, int>{};
    messenger.setMockMethodCallHandler(sqlite, (call) async {
      final args = (call.arguments as Map?) ?? {};
      if (call.method == 'openDatabase') {
        final id = versions.length + 1;
        versions[id] = (args['path'] as String).endsWith('history.db')
            ? HistoryDatabase.schemaVersion
            : LibraryDatabase.schemaVersion;
        return {'id': id};
      }
      final sql = args['sql'] as String? ?? '';
      if (call.method == 'execute' && sql.contains('CREATE VIRTUAL TABLE')) {
        throw PlatformException(code: 'sqlite_error', message: 'no fts5');
      }
      if (call.method != 'query') return null;
      queries.add(sql);
      if (sql.toLowerCase().contains('pragma user_version')) {
        return {
          'columns': ['user_version'],
          'rows': [
            [versions[args['id']]],
          ],
        };
      }
      return {'columns': <String>[], 'rows': <List<Object?>>[]};
    });
    addTearDown(() => messenger.setMockMethodCallHandler(sqlite, null));
  });

  Future<void> pumpLibrary(
    WidgetTester tester, {
    List<DateTime>? dates,
    List<UserPlaylistCollection> playlists = const [],
    bool withQueueItem = true,
  }) async {
    final preferences = await SharedPreferences.getInstance();
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(
            () => _Settings(
              hasPersistedAppSettings(preferences)
                  ? loadBootstrapSettings(preferences)
                  : null,
            ),
          ),
          downloadHistoryProvider.overrideWith(
            () => _History(dates ?? [DateTime(2026, 1, 10)]),
          ),
          localLibraryProvider.overrideWith(_Local.new),
          libraryCollectionsProvider.overrideWith(
            () => _Collections(playlists),
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          // Mornye opens Songs as a section page from its Library root.
          home: Scaffold(body: QueueTab(librarySection: 'all')),
        ),
      ),
    );
    // A loading indicator spins while the database page is pending, so the
    // page never settles; pump for a bounded time instead.
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
  }

  AppLocalizations l10nOf(WidgetTester tester) =>
      AppLocalizations.of(tester.element(find.byType(QueueTab)));

  {
    testWidgets('title sort survives a fresh Library and settings scope', (
      tester,
    ) async {
      await pumpLibrary(tester);
      final l10n = l10nOf(tester);
      await tester.tap(find.text(l10n.libraryFilterTitle).hitTestable());
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      final choice = find.text(l10n.searchSortTitleAZ);
      await tester.ensureVisible(choice);
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(choice);
      await tester.ensureVisible(find.text(l10n.libraryFilterApply));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text(l10n.libraryFilterApply));

      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      final preferences = await SharedPreferences.getInstance();
      expect(loadBootstrapSettings(preferences).historySortMode, 'a-z');
      await tester.pumpWidget(const SizedBox.shrink());
      queries.clear();
      await pumpLibrary(tester);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(QueueTab)),
      );
      expect(container.read(settingsProvider).historySortMode, 'a-z');
      expect(
        queries.any((sql) => sql.contains('ORDER BY sort_track ASC')),
        isTrue,
      );

      expect(tester.takeException(), isNull);
    });

    testWidgets('size sort applies from the Library menu', (tester) async {
      await pumpLibrary(tester);
      final l10n = l10nOf(tester);
      // The search field scrolls with the page over its plain background.
      expect(
        find.descendant(
          of: find.byType(AppSearchField),
          matching: find.byType(BackdropFilter),
        ),
        findsNothing,
      );
      await tester.tap(find.text(l10n.libraryFilterTitle).hitTestable());
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      await tester.ensureVisible(find.text(l10n.libraryFilterSortSizeLargest));

      expect(find.text(l10n.libraryFilterSortSizeLargest), findsOneWidget);
      expect(find.text(l10n.libraryFilterSortSizeSmallest), findsOneWidget);
      await tester.tap(find.text(l10n.libraryFilterSortSizeLargest));
      await tester.ensureVisible(find.text(l10n.libraryFilterApply));
      await tester.tap(find.text(l10n.libraryFilterApply));

      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      expect(queries.any((sql) => sql.contains('sort_size DESC')), isTrue);
      expect(find.text(l10n.libraryFilterSortSizeSmallest), findsNothing);

      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Sistrum keeps its filter button', (tester) async {
    await pumpLibrary(tester);
    final l10n = l10nOf(tester);

    expect(find.text(l10n.libraryFilterTitle), findsOneWidget);
  });
}
