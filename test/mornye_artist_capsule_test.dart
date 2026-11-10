import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/album_detail_header.dart';
import 'package:mornye/widgets/track_lists/context_menu.dart';
import 'package:mornye/widgets/mornye_artist_header.dart';
import 'package:mornye/widgets/mornye_chrome.dart';

void main() {
  Future<void> pumpHeader(
    WidgetTester tester, {
    required List<String> log,
    bool showTitle = false,
  }) async {
    await tester.binding.setSurfaceSize(const Size(900, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                MornyeArtistHeader(
                  name: 'Artist',
                  showTitle: showTitle,
                  artwork: const ColoredBox(color: Colors.grey),
                  actions: const [],
                  toolbarActions: [
                    HeaderCircleButton(
                      icon: CupertinoIcons.square_arrow_up,
                      tooltip: 'Share',
                      onPressed: () => log.add('share'),
                    ),
                    HeaderMoreButton(
                      groups: () => [
                        [
                          ContextMenuAction(
                            icon: CupertinoIcons.star,
                            label: 'Favorite',
                            onSelected: () => log.add('favorite'),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  {
    const platform = 'mobile';

    testWidgets('$platform toolbar title follows the platform', (tester) async {
      await pumpHeader(tester, log: [], showTitle: true);
      // The hero always names the artist; only the phone repeats it above.
      expect(find.text('Artist'), findsNWidgets(2));
    });

    testWidgets('$platform groups Share and More at the trailing edge', (
      tester,
    ) async {
      final log = <String>[];
      await pumpHeader(tester, log: log);

      final capsule = find.byType(MornyeActionCapsule);
      final bounds = tester.getRect(
        find.descendant(of: capsule, matching: find.byType(MornyeGlass)),
      );
      expect(bounds.right, 900 - 12);
      // Level with the round back button.
      final back = tester.getRect(find.byTooltip('Back'));
      expect(bounds.center.dy, back.center.dy);
      // Material keeps a 48 dp touch target inside the shared glass capsule.
      expect(bounds.height, kMinInteractiveDimension);
      final share = tester.getRect(find.byTooltip('Share'));
      final more = tester.getRect(
        find.byTooltip(
          MaterialLocalizations.of(tester.element(capsule)).showMenuTooltip,
        ),
      );
      for (final button in tester.widgetList<IconButton>(
        find.descendant(of: capsule, matching: find.byType(IconButton)),
      )) {
        final target = tester.getRect(find.byWidget(button));
        expect(target.width, greaterThanOrEqualTo(kMinInteractiveDimension));
        expect(target.height, greaterThanOrEqualTo(kMinInteractiveDimension));
      }
      expect(share.right, lessThanOrEqualTo(more.left));

      await tester.tap(find.byTooltip('Share'));
      await tester.pumpAndSettle();
      expect(log, ['share']);

      await tester.tapAt(more.center);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Favorite'));
      await tester.pumpAndSettle();
      expect(log, ['share', 'favorite']);
    });
  }

  testWidgets('selection mode leaves the toolbar empty', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: const Scaffold(
            body: CustomScrollView(
              slivers: [
                MornyeArtistHeader(
                  name: 'Artist',
                  showTitle: false,
                  artwork: ColoredBox(color: Colors.grey),
                  actions: [],
                ),
              ],
            ),
          ),
        ),
      ),
    );
    expect(find.byType(MornyeActionCapsule), findsNothing);
  });
}
