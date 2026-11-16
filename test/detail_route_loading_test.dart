import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';

LocalLibraryItem _track(int number) => LocalLibraryItem(
  id: 'track-$number',
  trackName: 'Song $number',
  artistName: 'Artist',
  albumName: 'Album',
  filePath: 'content://library/document/song-$number.flac',
  trackNumber: number,
  scannedAt: DateTime(2026),
);

void main() {
  Future<GlobalKey<NavigatorState>> mount(
    WidgetTester tester, {
    List<Override> overrides = const [],
  }) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(430, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      ProviderScope(
        overrides: overrides,
        child: MaterialApp(
          navigatorKey: navigator,
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: Text('Library')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return navigator;
  }

  PageRoute<void> route(Widget page) => PageRouteBuilder<void>(
    transitionDuration: const Duration(milliseconds: 400),
    reverseTransitionDuration: const Duration(milliseconds: 400),
    pageBuilder: (_, _, _) => page,
    transitionsBuilder: (_, animation, _, child) => SlideTransition(
      position: Tween(
        begin: const Offset(1, 0),
        end: Offset.zero,
      ).animate(animation),
      child: child,
    ),
  );

  for (final popEarly in [false, true]) {
    testWidgets(
      'metadata starts reading during entrance, early pop=$popEarly',
      (tester) async {
        final navigator = await mount(tester);
        var statCalls = 0;
        var followUpCalls = 0;
        final delayedStat = Completer<String>();
        const channel = MethodChannel('com.amwolfstein.sistrum/backend');
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'safStat') {
            statCalls++;
            if (popEarly) return delayedStat.future;
            return jsonEncode({'exists': false});
          }
          followUpCalls++;
          return null;
        });
        addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
        unawaited(
          navigator.currentState!.push(
            route(TrackMetadataScreen(localItem: _track(1))),
          ),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 150));
        expect(find.text('Song 1'), findsWidgets);
        expect(statCalls, 1);
        if (popEarly) navigator.currentState!.pop();
        await tester.pumpAndSettle();
        if (popEarly) {
          delayedStat.complete(jsonEncode({'exists': true, 'size': 100}));
          await tester.pump();
          expect(followUpCalls, 0);
        }
        expect(statCalls, 1);
        expect(tester.takeException(), isNull);
      },
    );
  }

  final albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');

  testWidgets('library album shows a loading state, then its rows', (
    tester,
  ) async {
    final result = Completer<List<UnifiedLibraryItem>>();
    final navigator = await mount(
      tester,
      overrides: [
        libraryAlbumItemsProvider(
          albumKey,
        ).overrideWith((ref) => result.future),
      ],
    );
    unawaited(
      navigator.currentState!.push(
        route(AlbumScreen.library(albumKey: albumKey)),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));
    expect(find.byType(AppLoadingIndicator), findsOneWidget);
    result.complete([
      for (final number in [2, 1])
        UnifiedLibraryItem.fromLocalLibrary(_track(number)),
    ]);
    await tester.pump(const Duration(milliseconds: 150));
    await tester.pump();
    expect(find.text('Song 1'), findsOneWidget);
    expect(find.text('Song 2'), findsOneWidget);
    expect(find.byType(AppLoadingIndicator), findsNothing);
    expect(
      ModalRoute.of(tester.element(find.byType(AlbumScreen)))!.animation!.value,
      lessThan(1),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getTopLeft(find.text('Song 1')).dy,
      lessThan(tester.getTopLeft(find.text('Song 2')).dy),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'library album retries failed loading and ignores completion after pop',
    (tester) async {
      final result = Completer<List<UnifiedLibraryItem>>();
      var attempts = 0;
      final navigator = await mount(
        tester,
        overrides: [
          libraryAlbumItemsProvider(albumKey).overrideWith((ref) async {
            if (++attempts == 1) throw StateError('Unavailable');
            return result.future;
          }),
        ],
      );
      unawaited(
        navigator.currentState!.push(
          route(AlbumScreen.library(albumKey: albumKey)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Retry'));
      await tester.pump();
      expect(attempts, 2);
      navigator.currentState!.pop();
      await tester.pumpAndSettle();
      result.complete([UnifiedLibraryItem.fromLocalLibrary(_track(1))]);
      await tester.pump();
      expect(find.byType(AlbumScreen), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
}
