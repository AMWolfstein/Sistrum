import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_tag_credits_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/artist_screen.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/library_row_mapper.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    show databaseFactoryFfi, sqfliteFfiInit;

import 'support/fixed_settings.dart';

const _artist = 'Dana Vale';
const _album = 'Long Play';
const _path = '/music/long/1.flac';

/// Opens both databases and holds one scanned album by [_artist], so the
/// library holds that artist and album.
Future<void> _open() async {
  await HistoryDatabase.instance.database;
  final library = await LibraryDatabase.instance.database;
  await library.insert('library_sources', {
    'id': 'music',
    'path': '/Volumes/music',
    'display_name': 'music',
    'available': 1,
    'enabled': 1,
  });
  await library.insert(
    'library',
    LibraryRowMapper.encode(
      LocalLibraryItem(
        id: 'long-1',
        sourceId: 'music',
        trackName: 'Long One',
        artistName: _artist,
        albumName: _album,
        filePath: _path,
        scannedAt: DateTime.utc(2026, 9, 1),
        trackNumber: 1,
        discNumber: 1,
        format: 'flac',
      ).toJson(),
    ),
  );
  final batch = library.batch();
  sqlite.putPathKeysInBatch(batch, 'library_path_keys', 'long-1', _path);
  await batch.commit(noResult: true);
}

Future<void> _close() async {
  await LibraryDatabase.instance.close();
  await HistoryDatabase.instance.close();
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

/// Records the routes pushed onto the test app's navigator.
class _Pushes extends NavigatorObserver {
  final pushed = <Route<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (previousRoute != null) pushed.add(route);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  const backend = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Directory root;
  late List<String> backendCalls;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    root = Directory.systemTemp.createTempSync('library-navigation-');
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    messenger.setMockMethodCallHandler(paths, (_) async => root.path);
    // Any call to the metadata backend would mean an online page was asked for.
    backendCalls = [];
    messenger.setMockMethodCallHandler(backend, (call) async {
      backendCalls.add(call.method);
      return null;
    });
    addTearDown(() {
      messenger.setMockMethodCallHandler(paths, null);
      messenger.setMockMethodCallHandler(backend, null);
      root.deleteSync(recursive: true);
    });
  });

  test(
    'the library lookups find a credited artist and an album by name',
    () async {
      await _open();
      try {
        const scope = TagCreditScope();
        expect(
          await libraryHasArtist(_artist, credits: scope, includeLocal: true),
          isTrue,
        );
        expect(
          await libraryHasArtist(
            'Nobody Here',
            credits: scope,
            includeLocal: true,
          ),
          isFalse,
        );
        expect(
          await libraryAlbumKeyFor(
            'long play',
            artistName: _artist,
            includeLocal: true,
          ),
          LibraryDatabase.albumKeyFor(_album, null, _artist),
        );
        expect(
          await libraryAlbumKeyFor('Missing Album', includeLocal: true),
          isNull,
        );
      } finally {
        await _close();
      }
    },
  );

  /// Pumps a host whose [Builder] context starts the navigation under test.
  Future<BuildContext> pumpHost(WidgetTester tester, _Pushes pushes) async {
    late BuildContext context;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(
            () => FixedSettingsNotifier(
              const AppSettings(localLibraryEnabled: true),
            ),
          ),
          // Keeps the history index from loading in the background.
          tagCreditScopeProvider.overrideWithValue(const TagCreditScope()),
          downloadHistoryProvider.overrideWith(_History.new),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          navigatorObservers: [pushes],
          home: Scaffold(
            body: Builder(
              builder: (inner) {
                context = inner;
                return const SizedBox();
              },
            ),
          ),
        ),
      ),
    );
    return context;
  }

  // The route is recorded when it is pushed; pumping would build its page and
  // start its library reads inside the test.
  /// The page the last pushed route shows, read without building it.
  Widget pushedPage(_Pushes pushes, BuildContext context) {
    final route = pushes.pushed.last as MaterialPageRoute<void>;
    return route.builder(context);
  }

  testWidgets('Go to artist opens the library artist page', (tester) async {
    await tester.runAsync(_open);
    try {
      final pushes = _Pushes();
      final context = await pumpHost(tester, pushes);
      await tester.runAsync(
        () => navigateToArtist(context, artistName: _artist),
      );
      final page = pushedPage(pushes, context);
      expect(page, isA<ArtistScreen>());
      expect((page as ArtistScreen).artistName, _artist);
      expect(backendCalls, isEmpty);
    } finally {
      await tester.runAsync(_close);
    }
  });

  testWidgets('Go to album opens the library album page', (tester) async {
    await tester.runAsync(_open);
    try {
      final pushes = _Pushes();
      final context = await pumpHost(tester, pushes);
      await tester.runAsync(
        () => navigateToAlbum(context, albumName: _album, artistName: _artist),
      );
      final page = pushedPage(pushes, context);
      expect(page, isA<AlbumScreen>());
      expect(
        (page as AlbumScreen).albumKey,
        LibraryDatabase.albumKeyFor(_album, null, _artist),
      );
      expect(backendCalls, isEmpty);
    } finally {
      await tester.runAsync(_close);
    }
  });

  testWidgets(
    'an artist the library does not hold shows a message and opens nothing',
    (tester) async {
      await tester.runAsync(_open);
      try {
        final pushes = _Pushes();
        final context = await pumpHost(tester, pushes);
        await tester.runAsync(
          () => navigateToArtist(context, artistName: 'Nobody Here'),
        );
        await tester.pump();
        expect(
          find.text(
            context.l10n.clickableInformationUnavailable(
              context.l10n.trackArtist,
            ),
          ),
          findsOneWidget,
        );
        expect(pushes.pushed, isEmpty);
        expect(backendCalls, isEmpty);
      } finally {
        await tester.runAsync(_close);
      }
    },
  );

  testWidgets(
    'an album the library does not hold shows a message and opens nothing',
    (tester) async {
      await tester.runAsync(_open);
      try {
        final pushes = _Pushes();
        final context = await pumpHost(tester, pushes);
        await tester.runAsync(
          () => navigateToAlbum(context, albumName: 'Missing Album'),
        );
        await tester.pump();
        expect(
          find.text(
            context.l10n.clickableInformationUnavailable(
              context.l10n.trackAlbum,
            ),
          ),
          findsOneWidget,
        );
        expect(pushes.pushed, isEmpty);
        expect(backendCalls, isEmpty);
      } finally {
        await tester.runAsync(_close);
      }
    },
  );

  testWidgets('a track without an album name shows the album message', (
    tester,
  ) async {
    final pushes = _Pushes();
    final context = await pumpHost(tester, pushes);
    await navigateToTrackAlbum(
      context,
      const Track(
        id: 'song',
        name: 'Song',
        artistName: 'Artist',
        albumName: '',
        duration: 180,
      ),
    );
    await tester.pump();
    expect(
      find.text(
        context.l10n.clickableInformationUnavailable(context.l10n.trackAlbum),
      ),
      findsOneWidget,
    );
    expect(pushes.pushed, isEmpty);
    expect(backendCalls, isEmpty);
  });
}
