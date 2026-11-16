import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/widgets/artist_featured_album_card.dart';

const _album = ArtistAlbum(
  id: 'album-1',
  name: 'Example album',
  releaseDate: '2026-02-03',
  totalTracks: 12,
  albumType: 'album',
  artists: 'Example artist',
);

Future<void> _pump(
  WidgetTester tester, {
  required VoidCallback onTap,
  required VoidCallback onPlay,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: ArtistFeaturedAlbumCard(
            album: _album,
            onTap: onTap,
            onPlay: onPlay,
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('the card describes the album by its type, year and tracks', (
    tester,
  ) async {
    await _pump(tester, onTap: () {}, onPlay: () {});
    expect(find.textContaining('2026'), findsOneWidget);
    expect(find.textContaining('12'), findsOneWidget);
  });

  testWidgets('the play action and card navigation stay separate', (
    tester,
  ) async {
    var played = 0;
    var opened = 0;
    await _pump(tester, onTap: () => opened++, onPlay: () => played++);

    await tester.tap(find.byTooltip('Play'));
    await tester.pumpAndSettle();
    expect((played, opened), (1, 0));

    await tester.tap(find.text('Example album'));
    await tester.pumpAndSettle();
    expect((played, opened), (1, 1));
  });
}
