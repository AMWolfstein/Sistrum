import 'dart:typed_data';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:cupertino_ui/cupertino_ui.dart'
    show CupertinoButton, CupertinoIcons;
import 'package:flutter/rendering.dart' show RenderRepaintBoundary;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart' show FontLoader, rootBundle;
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_artist_songs_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_artist_screen.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';

import 'support/fixed_settings.dart';
import 'support/tooltip_finder.dart';

/// Set to a directory to also save the phone screenshot there as artist.png.
const _shotDirEnv = 'SISTRUM_SHOT_DIR';

final _coverDir = Directory.systemTemp.createTempSync('artist_covers_');

/// A real PNG cover, so the header and cards show artwork.
Future<String> _cover(String name, ui.Color color) async {
  final recorder = ui.PictureRecorder();
  ui.Canvas(
    recorder,
  ).drawRect(const ui.Rect.fromLTWH(0, 0, 64, 64), ui.Paint()..color = color);
  final image = await recorder.endRecording().toImage(64, 64);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  final path = '${_coverDir.path}/$name.png';
  File(path).writeAsBytesSync(bytes!.buffer.asUint8List());
  return path;
}

UnifiedLibraryItem _song(
  String id, {
  required String album,
  required String artist,
  String? albumArtist,
  required int totalTracks,
  String? releaseDate,
  String? coverPath,
}) => UnifiedLibraryItem.fromLocalLibrary(
  LocalLibraryItem(
    id: id,
    trackName: 'Song $id',
    artistName: artist,
    albumName: album,
    albumArtist: albumArtist,
    filePath: '${_coverDir.path}/$id.flac',
    coverPath: coverPath,
    scannedAt: DateTime(2026, 11, 17),
    duration: 200,
    totalTracks: totalTracks,
    releaseDate: releaseDate,
  ),
);

/// The library of "Ana": an album, a single (her latest release) and a
/// feature on Bo's album.
Future<List<UnifiedLibraryItem>> _anaLibrary() async {
  final night = await _cover('night', const ui.Color(0xFF2E7D7A));
  final glass = await _cover('glass', const ui.Color(0xFFE08A3C));
  final bo = await _cover('bo', const ui.Color(0xFF6A4C9C));
  return [
    _song(
      'n1',
      album: 'Night Tapes',
      artist: 'Ana',
      albumArtist: 'Ana',
      totalTracks: 3,
      releaseDate: '2024-03-01',
      coverPath: night,
    ),
    _song(
      'n2',
      album: 'Night Tapes',
      artist: 'Ana',
      albumArtist: 'Ana',
      totalTracks: 3,
      releaseDate: '2024-03-01',
      coverPath: night,
    ),
    _song(
      'n3',
      album: 'Night Tapes',
      artist: 'Ana',
      albumArtist: 'Ana',
      totalTracks: 3,
      releaseDate: '2024-03-01',
      coverPath: night,
    ),
    _song(
      'g1',
      album: 'Glass Rain',
      artist: 'Ana',
      albumArtist: 'Ana',
      totalTracks: 1,
      releaseDate: '2025-06-10',
      coverPath: glass,
    ),
    _song(
      'f1',
      album: 'Bo Sessions',
      artist: 'Bo, Ana',
      albumArtist: 'Bo',
      totalTracks: 4,
      releaseDate: '2025-01-05',
      coverPath: bo,
    ),
  ];
}

/// No collections are stored, so favourites start empty and stay in memory.
class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);
}

/// Sections below the first screen are built only once scrolled near, so
/// the checks of what is there use a [height] that shows the whole page.
ThemeData _theme() {
  final base = MornyeTheme.build(Brightness.dark);
  return base.copyWith(
    textTheme: base.textTheme.apply(fontFamily: 'Inter'),
    primaryTextTheme: base.primaryTextTheme.apply(fontFamily: 'Inter'),
  );
}

Future<void> _pump(
  WidgetTester tester, {
  required List<UnifiedLibraryItem> songs,
  String artist = 'Ana',
  Key? boundaryKey,
  double height = 915,
}) async {
  tester.view.physicalSize = Size(412, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(
          () => FixedSettingsNotifier(const AppSettings()),
        ),
        libraryArtistSongsProvider.overrideWith(
          (ref, name) async => name == artist ? songs : const [],
        ),
        libraryCollectionsProvider.overrideWith(() => _Collections()),
      ],
      child: MaterialApp(
        theme: _theme(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: RepaintBoundary(
          key: boundaryKey,
          child: LibraryArtistScreen(artist: artist),
        ),
      ),
    ),
  );
  await _settle(tester);
}

/// Image files decode outside the fake clock, so give them real time.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 30)),
    );
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    // The shot shows real text and icons, not the test font's blocks.
    await (FontLoader('Inter')..addFont(
          File(
            'test/support/fonts/InterVariable.ttf',
          ).readAsBytes().then((b) => ByteData.sublistView(b)),
        ))
        .load();
    await (FontLoader('packages/cupertino_icons/CupertinoIcons')..addFont(
          rootBundle.load('packages/cupertino_icons/assets/CupertinoIcons.ttf'),
        ))
        .load();
  });

  testWidgets('lays out the latest release, albums, singles and compilations '
      'with Play and no Download', (tester) async {
    await _pump(
      tester,
      height: 4000,
      songs: await tester.runAsync(_anaLibrary) ?? [],
    );

    // The newest own release is the latest one; the album and single follow.
    expect(find.text('Latest release'), findsOneWidget);
    expect(find.text('Albums (1)'), findsOneWidget);
    expect(find.text('Singles (1)'), findsOneWidget);
    expect(find.text('Compilations (1)'), findsOneWidget);
    expect(find.text('Night Tapes'), findsOneWidget);
    expect(find.text('Glass Rain'), findsWidgets);
    expect(find.text('Bo Sessions'), findsOneWidget);

    // Three round actions: Shuffle, the large Play and the favourite star.
    // The online Download and the Artist Page link are gone.
    expect(findByTooltip('Shuffle'), findsOneWidget);
    expect(find.bySemanticsLabel('Play'), findsOneWidget);
    expect(findByTooltip('Add to Favorite Artists'), findsOneWidget);
    expect(find.text('Play'), findsNothing);
    expect(find.text('Shuffle'), findsNothing);
    expect(find.byIcon(CupertinoIcons.arrow_down), findsNothing);
    expect(find.text('Artist Page'), findsNothing);
    expect(find.text('Songs'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('sections without releases are left out', (tester) async {
    final songs = await tester.runAsync(_anaLibrary) ?? [];
    // Only the single, so no albums and no compilations.
    await _pump(
      tester,
      height: 4000,
      songs: [
        for (final song in songs)
          if (song.albumName == 'Glass Rain') song,
      ],
    );

    expect(find.text('Latest release'), findsOneWidget);
    expect(find.text('Singles (1)'), findsOneWidget);
    expect(find.textContaining('Albums ('), findsNothing);
    expect(find.textContaining('Compilations ('), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('an artist with no songs has no sections and nothing to play', (
    tester,
  ) async {
    await _pump(tester, height: 4000, songs: const []);

    expect(find.text('Latest release'), findsNothing);
    expect(find.textContaining('Albums ('), findsNothing);
    expect(find.textContaining('Singles ('), findsNothing);
    expect(find.textContaining('Compilations ('), findsNothing);
    // Shuffle and Play do nothing without tracks; the star still works.
    final shuffle = tester.widget<HeaderCircleButton>(
      find.byWidgetPredicate(
        (w) => w is HeaderCircleButton && w.tooltip == 'Shuffle',
      ),
    );
    expect(shuffle.onPressed, isNull);
    final play = tester.widget<CupertinoButton>(
      find.ancestor(
        of: find.bySemanticsLabel('Play'),
        matching: find.byType(CupertinoButton),
      ),
    );
    expect(play.onPressed, isNull);
    final star = tester.widget<HeaderCircleButton>(
      find.byWidgetPredicate(
        (w) =>
            w is HeaderCircleButton && w.tooltip == 'Add to Favorite Artists',
      ),
    );
    expect(star.onPressed, isNotNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the artist page has no sort control', (tester) async {
    await _pump(tester, songs: await tester.runAsync(_anaLibrary) ?? []);

    expect(find.byIcon(Icons.sort), findsNothing);
    expect(find.text('Title (A-Z)'), findsNothing);
    expect(find.text('Year (Newest first)'), findsNothing);
    expect(find.text('Most Played'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a release card opens its album', (tester) async {
    await _pump(tester, songs: await tester.runAsync(_anaLibrary) ?? []);

    await tester.tap(find.text('Night Tapes').first);
    await _settle(tester);

    expect(find.byType(LocalAlbumScreen), findsOneWidget);
  });

  testWidgets('saves the phone screenshot when $_shotDirEnv is set', (
    tester,
  ) async {
    final shotDir = Platform.environment[_shotDirEnv];
    if (shotDir == null || shotDir.isEmpty) return;
    final key = GlobalKey();
    await _pump(
      tester,
      songs: await tester.runAsync(_anaLibrary) ?? [],
      boundaryKey: key,
    );

    final boundary = tester.renderObject<RenderRepaintBoundary>(
      find.byKey(key),
    );
    await tester.runAsync(() async {
      final image = await boundary.toImage();
      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
      image.dispose();
      Directory(shotDir).createSync(recursive: true);
      File('$shotDir/artist.png').writeAsBytesSync(bytes!.buffer.asUint8List());
    });
  });
}
