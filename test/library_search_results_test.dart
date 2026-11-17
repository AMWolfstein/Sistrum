import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/services/music_player_service.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/library_search_results.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/app_icon_finder.dart';

class _PlaybackRecorder extends PlaybackController {
  final paths = <String>[];

  @override
  Future<void> playMediaQueue(
    Iterable<PlayableMedia> queue, {
    required int startIndex,
    required String externalPath,
  }) async {
    paths.add(externalPath);
  }
}

LibrarySearchHit _hit(
  LibrarySearchKind kind,
  int index, {
  String query = 'Found',
}) => LibrarySearchHit(
  kind: kind,
  id: '$index',
  title: '$query ${kind.name} $index',
);

void main() {
  {
    {}
  }

  for (final local in [false, true]) {
    testWidgets('search opens metadata; only Play starts audio ($local)', (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues({});
      FlutterSecureStorage.setMockInitialValues({});
      const channel = MethodChannel('com.amwolfstein.sistrum/backend');
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        channel,
        (call) async => switch (call.method) {
          'safStat' => jsonEncode({'exists': true, 'size': 100}),
          'readAudioMetadata' || 'readFileMetadata' => '{}',
          'getLyricsLRCWithSource' => jsonEncode({'lyrics': '', 'source': ''}),
          'getSafFileModTimes' => '{}',
          _ => null,
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          channel,
          null,
        ),
      );
      const path = 'content://library/document/song.flac';
      final item = local
          ? UnifiedLibraryItem.fromLocalLibrary(
              LocalLibraryItem(
                id: 'chosen',
                trackName: 'Song',
                artistName: 'Artist',
                albumName: 'Album',
                filePath: path,
                scannedAt: DateTime(2026),
              ),
            )
          : UnifiedLibraryItem.fromDownloadHistory(
              DownloadHistoryItem(
                id: 'chosen',
                trackName: 'Song',
                artistName: 'Artist',
                albumName: 'Album',
                filePath: path,
                service: 'provider-a',
                downloadedAt: DateTime(2026),
              ),
            );
      final player = _PlaybackRecorder();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            playbackProvider.overrideWith(() => player),
            librarySearchProvider.overrideWith(
              (ref, request) async => request.kind == LibrarySearchKind.songs
                  ? [
                      LibrarySearchHit(
                        kind: LibrarySearchKind.songs,
                        id: 'chosen',
                        title: 'Song',
                        source: local ? 'local' : 'history',
                      ),
                    ]
                  : [],
            ),
            librarySearchTrackProvider.overrideWith((ref, key) async {
              expect(key, (source: local ? 'local' : 'history', id: 'chosen'));
              return item;
            }),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: CustomScrollView(
                slivers: [
                  LibrarySearchResults(query: 'Song', onOpenArtist: (_) {}),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester.widget<TrackCard>(find.byType(TrackCard)).style,
        TrackCardStyle.flat,
      );
      await tester.tap(find.text('Song'));
      await tester.pumpAndSettle();
      final details = tester.widget<TrackMetadataScreen>(
        find.byType(TrackMetadataScreen),
      );
      expect(details.item, item.historyItem);
      expect(details.localItem, item.localItem);
      expect(player.paths, isEmpty);
      tester.state<NavigatorState>(find.byType(Navigator).first).pop();
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Play'));
      await tester.pumpAndSettle();
      expect(player.paths, [path]);
      expect(find.byType(TrackMetadataScreen), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Library shows every result type and paginates', (tester) async {
    final requests = <LibrarySearchRequest>[];
    final artists = <String>[];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          librarySearchProvider.overrideWith((ref, request) async {
            requests.add(request);
            return List.generate(
              request.limit,
              (i) => _hit(request.kind, request.offset + i),
            );
          }),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                LibrarySearchResults(query: 'Found', onOpenArtist: artists.add),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      requests.map((r) => r.kind).toSet(),
      LibrarySearchKind.values.toSet(),
    );
    expect(requests.every((r) => r.limit == 6 && r.offset == 0), isTrue);
    await tester.tap(find.text('Artists').first);
    await tester.pumpAndSettle();
    expect(requests.last.limit, 41);
    await tester.tap(find.text('Found artists 0'));
    expect(artists, ['Found artists 0']);
    await tester.scrollUntilVisible(
      findAppIcon(Icons.expand_more),
      600,
      scrollable: find.byType(Scrollable).first,
    );
    // The list's extent is estimated until its rows are laid out: scroll on.
    await tester.drag(find.byType(Scrollable).first, const Offset(0, -300));
    await tester.pumpAndSettle();
    await tester.tap(findAppIcon(Icons.expand_more));
    await tester.pumpAndSettle();
    expect(requests.last.offset, 40);
    expect(tester.takeException(), isNull);
  });

  testWidgets('results read as the online search did: icon chips, Artists '
      'first, grouped rows with the Artist label, explicit songs badged', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          librarySearchProvider.overrideWith(
            (ref, request) async => [
              LibrarySearchHit(
                kind: request.kind,
                id: '1',
                title: 'Found ${request.kind.name}',
                artist: 'Someone',
                explicit: request.kind == LibrarySearchKind.songs,
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                LibrarySearchResults(query: 'Found', onOpenArtist: (_) {}),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    // Chips carry their kind's icon.
    expect(find.byIcon(mornyeIconFor(Icons.music_note)), findsWidgets);
    expect(find.byIcon(mornyeIconFor(Icons.person)), findsWidgets);
    // Artists come before Albums, then Playlists, then Songs.
    double top(String text) => tester
        .getTopLeft(find.textContaining(text, findRichText: true).last)
        .dy;
    expect(top('Found artists') < top('Found albums'), isTrue);
    expect(top('Found albums') < top('Found songs'), isTrue);
    // An artist row carries the Artist label, not a track count.
    final l10n = AppLocalizations.of(tester.element(find.byType(Scaffold)));
    expect(find.text(l10n.trackArtist), findsOneWidget);
    // Only the explicit song carries the E badge.
    expect(find.text('E'), findsOneWidget);
  });

  testWidgets('an older query cannot replace newer results', (tester) async {
    final old = Completer<List<LibrarySearchHit>>();
    late StateSetter update;
    var query = 'old';
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          librarySearchProvider.overrideWith((ref, request) {
            if (request.kind != LibrarySearchKind.songs) {
              return Future.value([]);
            }
            return request.query == 'old'
                ? old.future
                : Future.value([_hit(request.kind, 0, query: request.query)]);
          }),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: StatefulBuilder(
            builder: (context, setState) {
              update = setState;
              return Scaffold(
                body: CustomScrollView(
                  slivers: [
                    LibrarySearchResults(query: query, onOpenArtist: (_) {}),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
    await tester.pump();
    update(() => query = 'new');
    await tester.pumpAndSettle();
    old.complete([_hit(LibrarySearchKind.songs, 0, query: 'old')]);
    await tester.pumpAndSettle();
    expect(find.text('new songs 0'), findsOneWidget);
    expect(find.text('old songs 0'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('long-press selects songs for Library batch actions', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    // Tall enough that the selection bar never covers a result row.
    tester.view.physicalSize = const Size(800, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    UnifiedLibraryItem local(String id) => UnifiedLibraryItem.fromLocalLibrary(
      LocalLibraryItem(
        id: id,
        trackName: 'Song $id',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/music/$id.mp3',
        scannedAt: DateTime(2026),
      ),
    );
    final query = ValueNotifier('Song');
    addTearDown(query.dispose);
    final player = _PlaybackRecorder();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          playbackProvider.overrideWith(() => player),
          librarySearchProvider.overrideWith(
            (ref, request) async => switch (request.kind) {
              LibrarySearchKind.songs => [
                for (final id in ['a', 'b'])
                  LibrarySearchHit(
                    kind: LibrarySearchKind.songs,
                    id: id,
                    title: 'Song $id',
                    source: 'local',
                  ),
              ],
              LibrarySearchKind.albums => [
                _hit(LibrarySearchKind.albums, 0, query: 'Song'),
              ],
              _ => [],
            },
          ),
          librarySearchTrackProvider.overrideWith(
            (ref, key) async => local(key.id),
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: ValueListenableBuilder(
              valueListenable: query,
              builder: (context, value, _) => CustomScrollView(
                slivers: [
                  LibrarySearchResults(query: value, onOpenArtist: (_) {}),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Play'), findsNWidgets(2));

    await tester.longPress(find.text('Song a'));
    await tester.pumpAndSettle();
    expect(find.text('1 selected'), findsOneWidget);
    expect(find.text('Re-enrich (1)'), findsNothing);
    expect(find.text('Delete 1 track'), findsOneWidget);
    // Selecting hides row actions and keeps taps on the selection.
    expect(find.byTooltip('Play'), findsNothing);
    await tester.tap(find.text('Song b'));
    await tester.pumpAndSettle();
    expect(find.text('2 selected'), findsOneWidget);
    expect(find.text('All tracks selected'), findsOneWidget);
    await tester.tap(find.text('Song albums 0'));
    await tester.pumpAndSettle();
    expect(find.byType(LibrarySearchResults), findsOneWidget);
    expect(find.text('2 selected'), findsOneWidget);
    expect(player.paths, isEmpty);

    await tester.tap(find.text('Song b'));
    await tester.pumpAndSettle();
    expect(find.text('1 selected'), findsOneWidget);
    // Back leaves selection mode instead of the screen.
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.textContaining('selected'), findsNothing);
    expect(find.byTooltip('Play'), findsNWidgets(2));

    await tester.longPress(find.text('Song a'));
    await tester.pumpAndSettle();
    expect(find.text('1 selected'), findsOneWidget);
    query.value = 'Song ';
    await tester.pumpAndSettle();
    expect(find.textContaining('selected'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('wide Library search offers covers, songs included', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    tester.view.physicalSize = const Size(1280, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final requests = <LibrarySearchRequest>[];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          librarySearchProvider.overrideWith((ref, request) async {
            requests.add(request);
            return List.generate(request.limit, (i) {
              final hit = _hit(request.kind, request.offset + i);
              return request.kind == LibrarySearchKind.songs
                  ? LibrarySearchHit(
                      kind: hit.kind,
                      id: hit.id,
                      title: hit.title,
                      source: 'local',
                    )
                  : hit;
            });
          }),
          librarySearchTrackProvider.overrideWith(
            (ref, key) async => UnifiedLibraryItem.fromLocalLibrary(
              LocalLibraryItem(
                id: key.id,
                trackName: 'Song ${key.id}',
                artistName: 'Artist',
                albumName: 'Album',
                filePath: '/music/${key.id}.mp3',
                scannedAt: DateTime(2026),
              ),
            ),
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                LibrarySearchResults(query: 'Found', onOpenArtist: (_) {}),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    // Rows until the shared view setting picks covers.
    expect(find.byType(TrackCard), findsWidgets);
    expect(find.byTooltip('Grid'), findsOneWidget);

    await tester.tap(find.byTooltip('Grid'));
    await tester.pumpAndSettle();
    expect(find.byType(TrackCard), findsNothing);
    expect(find.byTooltip('List'), findsOneWidget);
    // The overview: one shelf per kind, songs as covers the albums' size.
    expect(find.byType(SliverGrid), findsNothing);
    final song = find.byKey(const ValueKey('songs:local:0'));
    expect(song, findsOneWidget);
    expect(
      tester.getSize(song).width,
      tester.getSize(find.byKey(const ValueKey('albums::0'))).width,
    );

    // One kind fills a grid of covers and still pages.
    await tester.tap(find.text('Songs').first);
    await tester.pumpAndSettle();
    expect(requests.last.kind, LibrarySearchKind.songs);
    expect(requests.last.limit, 41);
    expect(find.byType(SliverGrid), findsOneWidget);
    expect(find.byType(TrackCard), findsNothing);

    // A long press selects, as on the rows.
    await tester.longPress(find.text('Found songs 0'));
    await tester.pumpAndSettle();
    expect(find.text('1 selected'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('songs:local:0')),
        matching: find.byIcon(Icons.check),
      ),
      findsOneWidget,
    );
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.textContaining('selected'), findsNothing);

    // A narrow pane keeps the rows and offers no switch.
    tester.view.physicalSize = const Size(700, 1600);
    await tester.pumpAndSettle();
    expect(find.byType(SliverGrid), findsNothing);
    expect(find.byType(TrackCard), findsWidgets);
    expect(find.byTooltip('List'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
