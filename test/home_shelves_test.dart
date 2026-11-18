import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_mixes_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_row_mapper.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sistrum/services/library_mixes.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/music_player_runtime.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_riverpod/misc.dart' show Override, ProviderListenable;
import 'package:sqflite/sqflite.dart';
// The app reaches sqflite_common through sqflite and sqflite_common_ffi.
// ignore: depend_on_referenced_packages
import 'package:sqflite_common/sqflite_logger.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    show databaseFactoryFfi, sqfliteFfiInit;

import 'support/fixed_settings.dart';

/// Every read the databases ran, in order.
final _statements = <String>[];

int _ran(String fragment) =>
    _statements.where((sql) => sql.contains(fragment)).length;

/// The unfiltered Library count.
const _countRead = 'SUM(track_count)';

late Directory _root;

String _path(String name) => '${_root.path}/music/$name';

Map<String, dynamic> _download(String id, String path, {int number = 1}) => {
  'id': id,
  'trackName': 'Download $id',
  'artistName': 'Artist',
  'albumName': 'Album $id',
  'filePath': path,
  'service': 'example-provider',
  'downloadedAt': DateTime.utc(2026, 9, number + 1).toIso8601String(),
  'isrc': 'ISRC-$id',
};

Future<void> _addScanned(
  String id,
  String path, {
  String? source,
  String? name,
  DateTime? scannedAt,
  bool replace = false,
}) async {
  final db = await LibraryDatabase.instance.database;
  await db.insert(
    'library',
    LibraryRowMapper.encode(
      LocalLibraryItem(
        id: id,
        sourceId: source ?? LibraryDatabase.legacySourceId,
        trackName: name ?? 'Scanned $id',
        artistName: 'Artist',
        albumName: 'Album $id',
        filePath: path,
        scannedAt: scannedAt ?? DateTime.utc(2026, 10, 1),
      ).toJson(),
    ),
    conflictAlgorithm: replace ? ConflictAlgorithm.replace : null,
  );
  final batch = db.batch();
  sqlite.putPathKeysInBatch(batch, 'library_path_keys', id, path);
  await batch.commit(noResult: true);
}

/// [plays] plays of [key] for [milliseconds] in total, [daysAgo] days ago.
class _Revision extends LocalLibraryIndexRevision {
  void bumpForTest() => state++;
}

final _media = StreamController<MediaItem?>.broadcast();

ProviderContainer _container({
  required bool includeLocal,
  List<Override> overrides = const [],
}) {
  final container = ProviderContainer(
    overrides: [
      settingsProvider.overrideWith(
        () => FixedSettingsNotifier(
          AppSettings(localLibraryEnabled: includeLocal),
        ),
      ),
      localLibraryIndexRevisionProvider.overrideWith(_Revision.new),
      currentMediaItemProvider.overrideWithBuild(
        (ref, notifier) => _media.stream,
      ),
      ...overrides,
    ],
  );
  addTearDown(container.dispose);
  return container;
}

/// Lets a provider rerun after a change.
Future<void> _settle() =>
    Future<void>.delayed(const Duration(milliseconds: 50));

/// Reruns of [provider] after its first load.
ProviderSubscription<AsyncValue<T>> _countReloads<T>(
  ProviderContainer container,
  ProviderListenable<AsyncValue<T>> provider,
  void Function() onReload,
) => container.listen(provider, (previous, next) {
  if (next.isLoading) onReload();
});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUpAll(() async {
    _root = await Directory.systemTemp.createTemp('home-shelves-');
    await Directory('${_root.path}/music').create();
    sqfliteFfiInit();
    // ignore: experimental_member_use
    databaseFactory = SqfliteDatabaseFactoryLogger(
      databaseFactoryFfi,
      options: SqfliteLoggerOptions(
        type: SqfliteDatabaseFactoryLoggerType.all,
        log: (event) {
          if (event is SqfliteLoggerSqlEvent) _statements.add(event.sql);
        },
      ),
    );
    messenger.setMockMethodCallHandler(paths, (_) async => _root.path);
  });

  tearDownAll(() async {
    await LibraryDatabase.instance.close();
    await HistoryDatabase.instance.close();
    messenger.setMockMethodCallHandler(paths, null);
    await _root.delete(recursive: true);
  });

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await LibraryDatabase.instance.clearAll();
    await HistoryDatabase.instance.clearAll();
    await ListeningStatisticsStore.instance.clear();
    _statements.clear();
  });

  group('Recently added', () {
    // Downloads are gone, so only scanned songs show, with Local Library on.
    for (final includeLocal in [true]) {
      test('reloads per Library revision only '
          '(Local Library ${includeLocal ? 'on' : 'off'})', () async {
        for (var index = 0; index < 4; index++) {
          await _addScanned('d$index', _path('d$index.flac'));
        }
        await _addScanned('s0', _path('s0.flac'));
        final container = _container(includeLocal: includeLocal);
        var reloads = 0;
        _countReloads<HomeRecentlyAdded>(
          container,
          homeRecentlyAddedProvider,
          () => reloads++,
        );
        final first = await container.read(homeRecentlyAddedProvider.future);
        expect(first.items, hasLength(includeLocal ? 5 : 0));
        expect(first.trackCount, includeLocal ? 5 : 0);
        expect(reloads, 0);

        // Nothing changed in the Library: the same songs, so Home does not
        // rebuild.
        (container.read(localLibraryIndexRevisionProvider.notifier)
                as _Revision)
            .bumpForTest();
        await _settle();
        final same = await container.read(homeRecentlyAddedProvider.future);
        expect(reloads, includeLocal ? 1 : 0);
        expect(same, first);
        expect(identical(same.items, first.items), isTrue);

        // Added after the scanned songs.
        await _addScanned(
          'd9',
          _path('d9.flac'),
          scannedAt: DateTime.utc(2026, 10, 9),
        );
        (container.read(localLibraryIndexRevisionProvider.notifier)
                as _Revision)
            .bumpForTest();
        final added = await container.read(homeRecentlyAddedProvider.future);
        expect(added.items.first.localItem?.id, 'd9');
        expect(added.trackCount, includeLocal ? 6 : 0);
        // The Library index loads with its own startup warmup.
        expect(container.read(localLibraryProvider.exists), isFalse);
      });
    }

    test('shares one count with the Library badges per revision', () async {
      await HistoryDatabase.instance.upsertBatch([
        _download('d0', _path('d0.flac')),
      ]);
      final container = _container(includeLocal: true);
      container.listen(homeRecentlyAddedProvider, (_, _) {});
      final badges = libraryCountsProvider(
        const LibraryCountsRequest(
          searchQuery: '',
          filterSource: null,
          filterQuality: null,
          filterFormat: null,
          filterMetadata: null,
          localLibraryEnabled: true,
        ),
      );
      container.listen(badges, (_, _) {});
      await container.read(homeRecentlyAddedProvider.future);
      await container.read(badges.future);
      expect(_ran(_countRead), 1);

      (container.read(localLibraryIndexRevisionProvider.notifier) as _Revision).bumpForTest();
      await container.read(homeRecentlyAddedProvider.future);
      await container.read(badges.future);
      expect(_ran(_countRead), 2);
    });
  });

  group('Mixes', () {
    test(
      'keep their views while a Library change leaves their songs',
      () async {
        for (var index = 0; index < 30; index++) {
          await _addScanned('d$index', _path('d$index.flac'));
        }
        final mix = LibraryMix(
          kind: LibraryMixKind.artist,
          key: 'artist',
          label: 'Artist',
          artists: const ['Artist'],
          songIds: [for (var index = 0; index < 12; index++) 'local_d$index'],
        );
        SharedPreferences.setMockInitialValues({
          'home_library_mixes_v2': jsonEncode({
            'day': listeningDay(DateTime.now()),
            'includeLocal': true,
            'count': 30,
            'mixes': [mix.toJson()],
          }),
        });
        final runtime = MusicPlayerRuntime();
        addTearDown(runtime.dispose);
        final container = _container(
          includeLocal: true,
          overrides: [
            libraryMixesStartupProvider.overrideWithBuild(
              (ref, notifier) => true,
            ),
            musicPlayerRuntimeProvider.overrideWithValue(runtime),
          ],
        );
        var reloads = 0;
        _countReloads<List<LibraryMixView>>(
          container,
          libraryMixesProvider,
          () => reloads++,
        );
        final first = await container.read(libraryMixesProvider.future);
        expect(first.single.items, hasLength(12));
        // The Library's own startup warmup may still publish once after the
        // first load; the change under test comes after it.
        await _settle();
        reloads = 0;

        // A scanned song outside the mixes, added before its revision.
        await _addScanned('d30', _path('d30.flac'));
        await _settle();
        expect(reloads, 0);
        (container.read(localLibraryIndexRevisionProvider.notifier)
                as _Revision)
            .bumpForTest();
        await _settle();
        final kept = await container.read(libraryMixesProvider.future);
        expect(reloads, 1);
        expect(identical(kept, first), isTrue);

        // A mix song renamed.
        await _addScanned(
          'd3',
          _path('d3.flac'),
          name: 'Renamed',
          replace: true,
        );
        (container.read(localLibraryIndexRevisionProvider.notifier)
                as _Revision)
            .bumpForTest();
        await _settle();
        final renamed = await container.read(libraryMixesProvider.future);
        expect(identical(renamed, first), isFalse);
        expect(renamed.single.items[3].trackName, 'Renamed');
      },
    );
  });
}
