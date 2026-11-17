import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/download_history.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/widgets/audio_quality_badges.dart';
import 'package:sistrum/widgets/track_card.dart';

Widget _host(Widget child) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: child),
);

UnifiedLibraryItem _item({required bool explicit}) =>
    UnifiedLibraryItem.fromDownloadHistory(
      DownloadHistoryItem(
        id: 'a',
        trackName: 'Song',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/music/a.flac',
        service: 'example-provider',
        downloadedAt: DateTime(2026),
        explicit: explicit,
      ),
    );

void main() {
  testWidgets('a track row shows the E badge beside an explicit title', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        Column(
          children: [
            TrackCard(
              leading: const SizedBox.square(dimension: 48),
              title: 'Loud',
              explicit: true,
            ),
            TrackCard(
              leading: const SizedBox.square(dimension: 48),
              title: 'Calm',
            ),
            const SizedBox(
              width: 150,
              child: TrackGridCard(
                cover: SizedBox(),
                title: 'Grid',
                explicit: true,
              ),
            ),
          ],
        ),
      ),
    );
    expect(find.byType(ExplicitBadge), findsNWidgets(2));
    expect(find.text('E'), findsNWidgets(2));
  });

  test('a library item and its track carry the explicit flag', () {
    expect(_item(explicit: true).explicit, isTrue);
    expect(_item(explicit: true).toTrack().isExplicit, isTrue);
    expect(_item(explicit: false).explicit, isFalse);
    expect(_item(explicit: false).toTrack().isExplicit, isFalse);
  });

  test('a search hit reads the explicit column', () {
    final hit = LibrarySearchHit.fromRow(LibrarySearchKind.songs, {
      'id': '1',
      'title': 'Loud',
      'explicit': 1,
    });
    expect(hit.explicit, isTrue);
    expect(
      LibrarySearchHit.fromRow(LibrarySearchKind.songs, {
        'id': '2',
        'title': 'Calm',
        'explicit': 0,
      }).explicit,
      isFalse,
    );
  });
}
