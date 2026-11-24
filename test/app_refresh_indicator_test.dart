import 'package:cupertino_ui/cupertino_ui.dart'
    show CupertinoActivityIndicator, CupertinoSliverRefreshControl;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_refresh_indicator.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';

void main() {
  final macOS = TargetPlatformVariant.only(TargetPlatform.macOS);
  late int refreshes;
  late ScrollController scroll;

  Future<void> pumpPage(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    refreshes = 0;
    scroll = ScrollController();
    addTearDown(scroll.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        // The app's macOS scrolling: lists bounce past their edges.
        scrollBehavior: const MaterialScrollBehavior().copyWith(
          physics: const BouncingScrollPhysics(
            parent: AlwaysScrollableScrollPhysics(),
          ),
        ),
        home: Scaffold(
          body: AppRefreshIndicator(
            onRefresh: () async {
              refreshes++;
              await Future<void>.delayed(const Duration(milliseconds: 300));
            },
            child: CustomScrollView(
              controller: scroll,
              slivers: [
                const AppSliverHeader.tabRoot(title: 'Page'),
                SliverList.builder(
                  itemCount: 40,
                  itemBuilder: (_, i) =>
                      SizedBox(height: 80, child: Text('row $i')),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 120; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
  }

  testWidgets('refreshVisible refreshes the page on screen', (tester) async {
    await pumpPage(tester);
    expect(AppRefreshIndicator.refreshVisible(), isTrue);
    await settle(tester);
    expect(refreshes, 1);
  }, variant: macOS);

  group('Mornye', () {
    Future<void> pumpMornye(
      WidgetTester tester, {
      required bool bounces,
    }) async {
      refreshes = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          // Phones bounce unless the low-end profile turns overscroll off.
          scrollBehavior: const MaterialScrollBehavior().copyWith(
            physics: bounces
                ? const BouncingScrollPhysics(
                    parent: AlwaysScrollableScrollPhysics(),
                  )
                : const ClampingScrollPhysics(
                    parent: AlwaysScrollableScrollPhysics(),
                  ),
          ),
          home: Scaffold(
            body: AppRefreshIndicator(
              onRefresh: () async {
                refreshes++;
                await Future<void>.delayed(const Duration(milliseconds: 300));
              },
              child: CustomScrollView(
                slivers: [
                  const AppSliverHeader.page(title: 'Page'),
                  AppRefreshIndicator.sliver(),
                  SliverList.builder(
                    itemCount: 40,
                    itemBuilder: (_, i) =>
                        SizedBox(height: 80, child: Text('row $i')),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    }

    Future<void> pull(WidgetTester tester) async {
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('row 2')),
      );
      for (var i = 0; i < 8; i++) {
        await gesture.moveBy(const Offset(0, 40));
        await tester.pump(const Duration(milliseconds: 16));
      }
      expect(find.byType(CupertinoActivityIndicator), findsOneWidget);
      expect(find.byType(RefreshProgressIndicator), findsNothing);
      await gesture.up();
    }

    testWidgets('pulls with the iOS refresh control', (tester) async {
      await pumpMornye(tester, bounces: true);
      expect(find.byType(RefreshIndicator), findsNothing);
      await pull(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(refreshes, 1);
      await settle(tester);
      expect(find.byType(CupertinoActivityIndicator), findsNothing);
    });

    testWidgets('keeps the indicator when lists do not bounce', (tester) async {
      await pumpMornye(tester, bounces: false);
      expect(find.byType(RefreshIndicator), findsOneWidget);
      expect(find.byType(CupertinoSliverRefreshControl), findsNothing);
    });
  });
}
