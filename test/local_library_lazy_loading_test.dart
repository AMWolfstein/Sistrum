import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/providers/download_history_provider.dart';

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

  @override
  Future<void> reloadFromStorage() async {
  }
}

class _History extends DownloadHistoryNotifier {

  @override
  DownloadHistoryState build() => DownloadHistoryState();

  @override
  Future<void> reloadFromStorage() async {
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Local local;
  late _History history;
  late int queries;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    local = _Local();
    history = _History();
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

  test(
    'search and browse stop observing the local index when disabled',
    () async {
      final container = ProviderContainer(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          downloadHistoryProvider.overrideWith(() => history),
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
        search: '',
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
