import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/routes/now_playing_route.dart';
import 'package:sistrum/widgets/player_details_sheet.dart';
import 'package:sistrum/widgets/player_gesture_regions.dart';

MediaItem _item(String id, {String? source}) => MediaItem(
  id: id,
  title: id,
  artist: 'Artist',
  album: 'Album',
  duration: const Duration(minutes: 3),
  extras: {'source': source ?? '/music/$id.flac'},
);

class _FooterRoute extends NowPlayingRoute {
  _FooterRoute(this.onOpenQueue) : super(child: const SizedBox());

  final VoidCallback onOpenQueue;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) => PlayerQueueSwipeRegion(
    onOpenQueue: onOpenQueue,
    child: const ColoredBox(
      key: ValueKey('gesture-player'),
      color: Colors.black,
    ),
  );
}

void main() {
  testWidgets('details retain their captured item when playback changes', (
    tester,
  ) async {
    final read = Completer<Map<String, dynamic>>();
    final metadata = PlayerMetadataController(
      readMetadata: (path) => path.startsWith('content://')
          ? read.future
          : Future.value({'title': 'Second tags'}),
    );
    addTearDown(metadata.dispose);
    final first = _item('First', source: 'content://library/first.flac');
    await metadata.load(first);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showPlayerDetailsSheet(
                  context,
                  metadata: metadata,
                  mediaItem: first,
                ),
                child: const Text('Details'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Details'));
    await tester.pumpAndSettle();
    expect(find.text('First'), findsWidgets);
    await metadata.load(_item('Second'));
    read.complete({'title': 'First tags'});
    await tester.pumpAndSettle();
    expect(find.text('First tags'), findsOneWidget);
    expect(find.text('Second tags'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('footer opens the queue upwards and dismisses downwards', (
    tester,
  ) async {
    final navigator = GlobalKey<NavigatorState>();
    var queueOpens = 0;
    await tester.pumpWidget(
      MaterialApp(navigatorKey: navigator, home: const SizedBox()),
    );
    navigator.currentState!.push(_FooterRoute(() => queueOpens++));
    await tester.pumpAndSettle();
    final footer = find.byKey(const ValueKey('gesture-player'));
    await tester.fling(footer, const Offset(0, -80), 600);
    await tester.pumpAndSettle();
    expect(queueOpens, 1);
    expect(footer, findsOneWidget);
    await tester.fling(footer, const Offset(0, 240), 1000);
    await tester.pumpAndSettle();
    expect(queueOpens, 1);
    expect(footer, findsNothing);
    expect(tester.takeException(), isNull);
  });
}
