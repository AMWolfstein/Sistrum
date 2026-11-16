import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/screens/artist_screen.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';

const _dana = 'Dana Vale';

final _overview = LibraryArtistOverview([
  ArtistAlbum(
    id: 'mix',
    name: 'Mixtape',
    releaseDate: '2022-03-03',
    totalTracks: 9,
    coverUrl: 'https://covers.example/mixtape.jpg',
    albumType: 'album',
    artists: _dana,
  ),
  ArtistAlbum(
    id: 'long',
    name: 'Long Play',
    releaseDate: '2020-05-01',
    totalTracks: 12,
    coverUrl: '/covers/long.jpg',
    albumType: 'album',
    artists: _dana,
  ),
  ArtistAlbum(
    id: 'single',
    name: 'Lone Single',
    releaseDate: '2021-01-01',
    totalTracks: 1,
    albumType: 'single',
    artists: _dana,
  ),
  ArtistAlbum(
    id: 'ep',
    name: 'Night Drive',
    releaseDate: '2015-01-01',
    totalTracks: 5,
    albumType: 'ep',
    artists: _dana,
  ),
]);

const _songs = [
  Track(
    id: 'song-1',
    name: 'Song One',
    artistName: _dana,
    albumName: 'Mixtape',
    duration: 180,
  ),
  Track(
    id: 'song-2',
    name: 'Song Two',
    artistName: _dana,
    albumName: 'Mixtape',
    duration: 200,
  ),
];

const _guests = [
  Track(
    id: 'guest-1',
    name: 'Guest One',
    artistName: 'Kit, Dana Vale',
    albumArtist: 'Kit',
    albumName: 'Kit Album',
    duration: 200,
  ),
  Track(
    id: 'guest-2',
    name: 'Guest Two',
    artistName: 'Kit, Dana Vale',
    albumArtist: 'Kit',
    albumName: 'Kit Album',
    duration: 210,
  ),
];

class _Player extends MusicPlayerController {
  @override
  Future<void> setShuffle(bool enabled) async {}
}

class _Playback extends PlaybackController {
  final played = <({List<String> ids, int start})>[];

  @override
  Future<void> playTrackList(List<Track> tracks, {int startIndex = 0}) async {
    played.add((
      ids: [for (final track in tracks) track.id],
      start: startIndex,
    ));
  }
}

Future<_Playback> _pumpLibraryArtist(
  WidgetTester tester, {
  LibraryArtistOverview? overview,
  List<Track> songs = _songs,
  List<Track> popular = const [],
  List<Track> featured = _guests,
}) async {
  SharedPreferences.setMockInitialValues({});
  await tester.binding.setSurfaceSize(const Size(430, 1800));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  final playback = _Playback();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        libraryArtistOverviewProvider.overrideWith(
          (ref, artist) async => overview ?? _overview,
        ),
        libraryArtistTracksProvider.overrideWith((ref, artist) async => songs),
        libraryArtistPopularTracksProvider.overrideWith(
          (ref, artist) async => popular,
        ),
        libraryArtistFeaturedTracksProvider.overrideWith(
          (ref, artist) async => featured,
        ),
        libraryAlbumTracksProvider.overrideWith(
          (ref, albumKey) async => [
            Track(
              id: '$albumKey-1',
              name: 'One',
              artistName: _dana,
              albumName: albumKey,
              duration: 180,
            ),
            Track(
              id: '$albumKey-2',
              name: 'Two',
              artistName: _dana,
              albumName: albumKey,
              duration: 180,
            ),
          ],
        ),
        musicPlayerControllerProvider.overrideWithValue(_Player()),
        playbackProvider.overrideWith(() => playback),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ArtistScreen(artistName: _dana),
      ),
    ),
  );
  await settle(tester);
  // The cover palette's sampling timer is 5 seconds; let it finish.
  await tester.pump(const Duration(seconds: 6));
  return playback;
}

/// Bounded pumps: the page keeps a looping shimmer, so it never settles.
Future<void> settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  testWidgets('the library artist shows its sections and no online actions', (
    tester,
  ) async {
    await _pumpLibraryArtist(tester, popular: _songs);

    expect(find.text('Latest Release'), findsOneWidget);
    expect(find.text('Popular'), findsOneWidget);
    expect(find.text('Albums (2)'), findsOneWidget);
    expect(find.text('Singles & EPs (2)'), findsOneWidget);
    expect(find.text('Compilations (2)'), findsOneWidget);
    expect(find.text('Releases'), findsNothing);
    expect(find.textContaining('monthly listeners'), findsNothing);
    expect(find.byTooltip('Open in Other Services'), findsNothing);
    expect(find.bySemanticsLabel('Download Discography'), findsNothing);
    expect(find.byIcon(CupertinoIcons.arrow_down), findsNothing);
  });

  testWidgets('the header image is the cover of the newest dated album', (
    tester,
  ) async {
    await _pumpLibraryArtist(tester);

    final surface = tester.widget<MornyeArtistSurface>(
      find.byType(MornyeArtistSurface),
    );
    expect(surface.imageSource, 'https://covers.example/mixtape.jpg');
  });

  testWidgets('the header button plays the artist and Shuffle stays', (
    tester,
  ) async {
    final playback = await _pumpLibraryArtist(tester);

    expect(find.byTooltip('Shuffle'), findsOneWidget);
    await tester.tap(find.byIcon(CupertinoIcons.play_fill).first);
    await settle(tester);
    expect(playback.played.single.ids, ['song-1', 'song-2']);
    expect(playback.played.single.start, 0);
  });

  testWidgets('Latest Release plays its album', (tester) async {
    final playback = await _pumpLibraryArtist(tester);

    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey('artist-latest')),
        matching: find.byIcon(CupertinoIcons.play_fill),
      ),
    );
    await settle(tester);
    expect(playback.played.single.ids, ['mix-1', 'mix-2']);
  });

  testWidgets('a Compilations card plays from its song', (tester) async {
    final playback = await _pumpLibraryArtist(tester);

    await tester.tap(find.text('Guest Two'));
    await settle(tester);
    expect(playback.played.single.ids, ['guest-1', 'guest-2']);
    expect(playback.played.single.start, 1);
  });

  testWidgets('sections with nothing in them are hidden', (tester) async {
    await _pumpLibraryArtist(
      tester,
      overview: LibraryArtistOverview([_overview.albums.first]),
      featured: const [],
    );

    expect(find.text('Albums (1)'), findsOneWidget);
    expect(find.textContaining('Singles & EPs'), findsNothing);
    expect(find.textContaining('Compilations'), findsNothing);
    expect(find.text('Popular'), findsNothing);
  });

  testWidgets('an artist without a dated album has no Latest Release', (
    tester,
  ) async {
    await _pumpLibraryArtist(
      tester,
      overview: LibraryArtistOverview([
        const ArtistAlbum(
          id: 'undated',
          name: 'Undated',
          releaseDate: '',
          totalTracks: 4,
          albumType: 'ep',
          artists: _dana,
        ),
      ]),
    );

    expect(find.text('Latest Release'), findsNothing);
    final surface = tester.widget<MornyeArtistSurface>(
      find.byType(MornyeArtistSurface),
    );
    expect(surface.imageSource, isNull);
  });
}
