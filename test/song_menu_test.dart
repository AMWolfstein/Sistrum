import 'dart:io';

import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/song_leftovers_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/song_menu.dart';

import 'support/app_icon_finder.dart';

const _track = Track(
  id: 'track-1',
  name: 'Song',
  artistName: 'Artist',
  albumName: 'Album',
  duration: 200,
);

/// A library with no other rows, that records what the deletion removed.
class _FakeLibrary extends LocalLibraryNotifier {
  final removed = <String>[];

  @override
  LocalLibraryState build() => LocalLibraryState(trackKeySet: <String>{});

  @override
  Future<LocalLibraryItem?> findExistingAsync({
    String? id,
    String? isrc,
    String? trackName,
    String? artistName,
  }) async => null;

  @override
  Future<void> removePhysicalFiles(Iterable<String> filePaths) async {
    removed.addAll(filePaths);
  }
}

/// An in-memory collections notifier: Loved toggles without the database.
class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);

  @override
  Future<bool> toggleLoved(Track track) async {
    final key = trackCollectionKey(track);
    final added = !state.containsLovedKey(key);
    state = state.copyWith(
      loved: added
          ? [
              CollectionTrackEntry(
                key: key,
                track: track,
                addedAt: DateTime.utc(2026),
              ),
              ...state.loved,
            ]
          : state.loved.where((entry) => entry.key != key).toList(),
    );
    return added;
  }
}

Future<void> _pump(
  WidgetTester tester, {
  required Widget child,
  List<Override> overrides = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(900, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        libraryCollectionsProvider.overrideWith(_Collections.new),
        libraryPlaylistPickerSummariesProvider.overrideWith(
          (ref, request) async => <PlaylistPickerSummary>[],
        ),
        ...overrides,
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: Center(child: child)),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// Lets the file checks finish on the real file system.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 5; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 40)),
    );
    await tester.pump();
  }
  await tester.pumpAndSettle();
}

Future<void> _openMenu(WidgetTester tester) async {
  await tester.tap(findAppIcon(CupertinoIcons.ellipsis));
  await _settle(tester);
}

/// The menu's labels in the order they are laid out.
List<String> _menuLabels(WidgetTester tester) {
  final menu = find.byType(MornyeContextMenu);
  return [
    for (final text in tester.widgetList<Text>(
      find.descendant(of: menu, matching: find.byType(Text)),
    ))
      if (text.data != null) text.data!,
  ];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory tempDir;
  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('song_menu_test');
  });
  tearDown(() {
    tempDir.deleteSync(recursive: true);
  });

  String songFile() => (File(
    '${tempDir.path}/Artist - Song.flac',
  )..writeAsBytesSync(const [0])).path;

  LocalLibraryItem libraryItem(String path) => LocalLibraryItem(
    id: 'song-1',
    trackName: 'Song',
    artistName: 'Artist',
    albumName: 'Album',
    filePath: path,
    scannedAt: DateTime(2026),
    format: 'flac',
  );

  testWidgets('menu lists the song actions in order, without Copy file path', (
    tester,
  ) async {
    final path = songFile();
    await _pump(
      tester,
      overrides: [localLibraryProvider.overrideWith(_FakeLibrary.new)],
      child: SongMenuButton(
        track: _track,
        filePath: path,
        localItem: libraryItem(path),
      ),
    );
    await _openMenu(tester);

    expect(_menuLabels(tester), [
      'Play',
      'Share',
      'Play next',
      'Add to queue',
      'Add to Loved',
      'Add to playlist',
      'Go to Album',
      'Go to Artist',
      'Edit Metadata',
      'Save Cover Art',
      'Save Lyrics (.lrc)',
      'Rescan ReplayGain',
      'Remove ReplayGain',
      'Audio quality analysis',
      'Remove from device',
    ]);
    expect(_menuLabels(tester), isNot(contains('Copy file path')));
  });

  testWidgets('a song without a file offers only its navigation', (
    tester,
  ) async {
    await _pump(
      tester,
      overrides: [localLibraryProvider.overrideWith(_FakeLibrary.new)],
      child: const SongMenuButton(track: _track),
    );
    await _openMenu(tester);

    expect(_menuLabels(tester), [
      'Add to Loved',
      'Add to playlist',
      'Go to Album',
      'Go to Artist',
    ]);
  });

  testWidgets('Add to Loved toggles the loved state and its label', (
    tester,
  ) async {
    final path = songFile();
    await _pump(
      tester,
      overrides: [localLibraryProvider.overrideWith(_FakeLibrary.new)],
      child: SongMenuButton(
        track: _track,
        filePath: path,
        localItem: libraryItem(path),
      ),
    );
    await _openMenu(tester);
    await tester.tap(find.text('Add to Loved'));
    await _settle(tester);
    expect(find.text('"Song" added to Loved'), findsOneWidget);
    // The first snackbar must leave, so the next one is shown.
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await _openMenu(tester);
    expect(_menuLabels(tester), contains('Remove from Loved'));
    expect(_menuLabels(tester), isNot(contains('Add to Loved')));
    await tester.tap(find.text('Remove from Loved'));
    await _settle(tester);
    expect(find.text('"Song" removed from Loved'), findsOneWidget);

    await _openMenu(tester);
    expect(_menuLabels(tester), contains('Add to Loved'));
  });

  testWidgets('Add to playlist opens the playlist picker', (tester) async {
    final path = songFile();
    await _pump(
      tester,
      overrides: [localLibraryProvider.overrideWith(_FakeLibrary.new)],
      child: SongMenuButton(
        track: _track,
        filePath: path,
        localItem: libraryItem(path),
      ),
    );
    await _openMenu(tester);
    await tester.tap(find.text('Add to playlist'));
    await _settle(tester);
    expect(find.text('No playlists yet'), findsOneWidget);
  });

  testWidgets('Remove from device asks first, then deletes the file', (
    tester,
  ) async {
    final path = songFile();
    final library = _FakeLibrary();
    final deleted = <String>[];
    await _pump(
      tester,
      overrides: [
        localLibraryProvider.overrideWith(() => library),
        songLeftoversProvider.overrideWith(
          (ref) => SongLeftovers(
            ref,
            deleteSong: (source) async {
              deleted.add(source!);
              return true;
            },
          ),
        ),
      ],
      child: SongMenuButton(
        track: _track,
        filePath: path,
        localItem: libraryItem(path),
      ),
    );

    await _openMenu(tester);
    await tester.tap(find.text('Remove from device'));
    await _settle(tester);
    expect(find.text('Remove from device?'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await _settle(tester);
    expect(deleted, isEmpty);
    expect(library.removed, isEmpty);

    await _openMenu(tester);
    await tester.tap(find.text('Remove from device'));
    await _settle(tester);
    await tester.tap(find.text('Delete'));
    await _settle(tester);
    expect(deleted, [path]);
    expect(library.removed, [path]);
  });
}
