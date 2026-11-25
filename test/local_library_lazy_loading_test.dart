import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/theme/mornye_theme.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();

  @override
  void setLocalLibraryEnabled(bool enabled) {
    state = state.copyWith(localLibraryEnabled: enabled);
  }
}

class _Local extends LocalLibraryNotifier {
  int _builds = 0;

  @override
  LocalLibraryState build() {
    _builds++;
    return LocalLibraryState();
  }

  void publishIndexChange() {
    state = state.copyWith(loadedIndexVersion: state.loadedIndexVersion + 1);
  }
}

class _SelectionCollections extends LibraryCollectionsNotifier {
  _SelectionCollections(this._record, {bool withTracks = false})
    : _withTracks = withTracks;
  final void Function(List<String>) _record;
  final bool _withTracks;

  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    playlists: [
      for (final id in ['First', 'Second', 'Keep'])
        UserPlaylistCollection(
          id: id,
          name: id,
          createdAt: DateTime.utc(2026),
          updatedAt: DateTime.utc(2026),
          tracks: _withTracks
              ? [
                  for (var i = 0; i < (id == 'Second' ? 2 : 1); i++)
                    CollectionTrackEntry(
                      key: '$id-$i',
                      addedAt: DateTime.utc(2026),
                      track: Track(
                        id: '$id-$i',
                        name: '$id-$i',
                        artistName: 'Artist',
                        albumName: 'Album',
                        duration: 180,
                      ),
                    ),
                ]
              : const [],
        ),
    ],
  );

  @override
  Future<void> ensurePlaylistsLoaded(Iterable<String> ids) async {}

  @override
  Future<int> deletePlaylists(Iterable<String> ids) async {
    final selected = ids.toSet();
    _record(selected.toList());
    state = state.copyWith(
      playlists: state.playlists
          .where((p) => !selected.contains(p.id))
          .toList(),
    );
    return selected.length;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Local local;
  late int queries;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    local = _Local();
    queries = 0;
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactorySqflitePlugin;
    addTearDown(() => databaseFactory = previousFactory);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const paths = MethodChannel('plugins.flutter.io/path_provider');
    messenger.setMockMethodCallHandler(paths, (_) async => '/test/documents');
    addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
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
      if (sql.toLowerCase().contains('pragma user_version')) {
        return {
          'columns': ['user_version'],
          'rows': [
            [versions[args['id']]],
          ],
        };
      }
      queries++;
      return {'columns': <String>[], 'rows': <List<Object?>>[]};
    });
    addTearDown(() => messenger.setMockMethodCallHandler(sqlite, null));
  });

  testWidgets('playlist selection confirms one deletion', (tester) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final calls = <List<String>>[];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          localLibraryProvider.overrideWith(() => local),
          libraryCollectionsProvider.overrideWith(
            () => _SelectionCollections(calls.add),
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: QueueTab(librarySection: 'playlists')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final l10n = AppLocalizations.of(tester.element(find.byType(QueueTab)));
    await tester.ensureVisible(find.text('First'));
    await tester.longPress(find.text('First'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Second'));
    await tester.tap(find.text('Second'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(l10n.selectionDeletePlaylistsCount(2)));
    await tester.pumpAndSettle();
    expect(find.text(l10n.collectionDeletePlaylistsMessage(2)), findsOneWidget);
    await tester.tap(find.text(l10n.dialogCancel));
    await tester.pumpAndSettle();
    expect(calls, isEmpty);
    expect(find.text('First'), findsOneWidget);
    await tester.tap(find.text(l10n.selectionDeletePlaylistsCount(2)));
    await tester.pumpAndSettle();
    await tester.tap(find.text(l10n.dialogDelete));
    await tester.pumpAndSettle();
    expect(calls, [
      ['First', 'Second'],
    ]);
    expect(find.text('First'), findsNothing);
    expect(find.text('Second'), findsNothing);
    expect(find.text('Keep'), findsOneWidget);
    expect(find.text(l10n.selectionDeletePlaylistsCount(2)), findsNothing);
    expect(find.text(l10n.collectionPlaylistsDeleted(2)), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test(
    'search and browse stop observing the local index when disabled',
    () async {
      final container = ProviderContainer(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          localLibraryProvider.overrideWith(() => local),
        ],
      );
      addTearDown(container.dispose);
      final search = librarySearchProvider((
        query: 'Song',
        kind: LibrarySearchKind.songs,
        limit: 40,
        offset: 0,
      ));
      final browse = libraryBrowseProvider((
        artists: false,
        sort: 'latest',
        limit: 40,
        completeness: null,
      ));
      container.listen(search, (_, _) {});
      container.listen(browse, (_, _) {});
      Future<void> settle() async {
        await container.read(search.future);
        await container.read(browse.future);
      }

      await settle();
      expect(local._builds, 0);
      container.read(settingsProvider.notifier).setLocalLibraryEnabled(true);
      await settle();
      expect(local._builds, 1);
      final enabledQueries = queries;
      local.publishIndexChange();
      await settle();
      expect(queries, greaterThan(enabledQueries));

      container.read(settingsProvider.notifier).setLocalLibraryEnabled(false);
      await settle();
      final disabledQueries = queries;
      local.publishIndexChange();
      await container.pump();
      expect(queries, disabledQueries);
      container.read(settingsProvider.notifier).setLocalLibraryEnabled(true);
      await settle();
      expect(local._builds, 1);
      expect(queries, greaterThan(disabledQueries));
    },
  );
}
