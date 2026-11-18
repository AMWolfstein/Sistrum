import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/system_volume_provider.dart';
import 'package:sistrum/screens/settings/settings_tab.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';
import 'package:sistrum/widgets/desktop/desktop_navigation.dart';
import 'package:sistrum/widgets/desktop/desktop_now_playing.dart';
import 'package:sistrum/widgets/playback_seek_slider.dart';
import 'package:sistrum/widgets/player_artwork.dart';
import 'package:sistrum/widgets/desktop/desktop_shortcuts.dart';
import 'package:sistrum/widgets/desktop/desktop_window.dart'
    show desktopWindowFullScreen;


const _track = MediaItem(id: 'track', title: 'A song', artist: 'An artist');

ThemeData _theme(bool mornye) =>
    mornye ? MornyeTheme.build(Brightness.light) : AppTheme.light();

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    debugDesktopPlatformOverride = true;
    desktopNowPlayingOpen.value = false;
    desktopInspectorPanel.value = null;
    desktopSidebarCollapsed.value = false;
  });

  tearDown(() {
    debugDesktopPlatformOverride = null;
    desktopWindowFullScreen.value = false;
    desktopNowPlayingOpen.value = false;
    desktopInspectorPanel.value = null;
    desktopSidebarCollapsed.value = false;
  });

  Future<void> setWindow(WidgetTester tester, double width) async {
    tester.view.physicalSize = Size(width, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  Widget app({
    required bool mornye,
    required Widget home,
    List<Override> overrides = const [],
    GlobalKey<NavigatorState>? navigatorKey,
  }) => ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      navigatorKey: navigatorKey,
      theme: _theme(mornye),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: navigatorKey == null
          ? null
          : (context, child) =>
                DesktopShortcuts(navigatorKey: navigatorKey, child: child!),
      home: home,
    ),
  );

  for (final mornye in [false, true]) {
    final style = mornye ? 'Mornye' : 'Material';

    group('$style settings', () {
      testWidgets('shows categories beside the selected page', (tester) async {
        final semantics = tester.ensureSemantics();
        await setWindow(tester, 1400);
        await tester.pumpWidget(
          app(
            mornye: mornye,
            home: Scaffold(
              body: SettingsTab(),
            ),
          ),
        );
        await tester.pumpAndSettle();

        final list = find.byKey(const ValueKey('settings-category-list'));
        expect(list, findsOneWidget);
        final appearance = find.descendant(
          of: list,
          matching: find.byKey(const ValueKey('settings-category:Appearance')),
        );
        expect(appearance, findsOneWidget);
        // The first category opens in the detail pane, not as a route.
        expect(find.byType(Navigator), findsNWidgets(2));

        await tester.tap(appearance);
        await tester.pumpAndSettle();
        final detail = find.descendant(
          of: find.byType(Navigator).last,
          matching: find.text('Appearance'),
        );
        expect(detail, findsWidgets);
        expect(
          tester.getSemantics(
            find.descendant(of: appearance, matching: find.byType(InkWell)),
          ),
          isSemantics(isSelected: true, hasTapAction: true),
        );
        semantics.dispose();
        expect(list, findsOneWidget);
        expect(tester.takeException(), isNull);
      });

      testWidgets('a new category ${mornye ? 'cuts in' : 'fades in'}', (
        tester,
      ) async {
        await setWindow(tester, 1400);
        await tester.pumpWidget(
          app(
            mornye: mornye,
            home: Scaffold(
              body: SettingsTab(),
            ),
          ),
        );
        await tester.pump();
        final fade = find.byKey(const ValueKey('settings-detail-fade'));
        // Opening Settings shows the first page without a fade.
        expect(tester.widget<Opacity>(fade).opacity, 1);
        await tester.pumpAndSettle();

        final other = find.byWidgetPredicate((widget) {
          final key = widget.key;
          return key is ValueKey<String> &&
              key.value.startsWith('settings-category:') &&
              key.value != 'settings-category:Appearance';
        }).first;
        await tester.tap(other);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 40));
        final opacity = tester.widget<Opacity>(fade).opacity;
        if (mornye) {
          expect(opacity, 1);
        } else {
          expect(opacity, greaterThan(0));
          expect(opacity, lessThan(1));
        }
        // The previous category's navigator is gone at once.
        expect(find.byType(Navigator), findsNWidgets(2));
        await tester.pumpAndSettle();
        expect(tester.widget<Opacity>(fade).opacity, 1);
        expect(tester.takeException(), isNull);
      });

      testWidgets('keeps the single list in narrow panes', (tester) async {
        await setWindow(tester, 600);
        await tester.pumpWidget(
          app(
            mornye: mornye,
            home: Scaffold(
              body: SettingsTab(),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('settings-category-list')),
          findsNothing,
        );
      });
    });

    group('$style inspector', () {
      List<Override> playerOverrides() => [
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => Stream.value(_track),
        ),
        playbackStateProvider.overrideWith(
          (ref) => Stream.value(PlaybackState()),
        ),
        playQueueProvider.overrideWith((ref) => Stream.value(const [_track])),
        systemVolumeProvider.overrideWith((ref) => Stream.value(0.5)),
      ];

      Future<void> pumpShell(
        WidgetTester tester,
        double width, {
        Widget nowPlaying = const Center(child: Text('now playing')),
      }) async {
        await setWindow(tester, width);
        final navigatorKey = GlobalKey<NavigatorState>();
        await tester.pumpWidget(
          app(
            mornye: mornye,
            navigatorKey: navigatorKey,
            overrides: playerOverrides(),
            home: Scaffold(
              body: DesktopShellLayout(
                destinations: const [
                  DesktopDestination(
                    tab: ShellTab.home,
                    icon: Icons.circle_outlined,
                    selectedIcon: Icons.circle,
                    label: 'home',
                  ),
                ],
                selected: ShellTab.home,
                onSelected: (_) {},
                // Destinations live in nested navigators, like the shell's.
                body: Navigator(
                  onGenerateRoute: (_) => PageRouteBuilder<void>(
                    pageBuilder: (_, _, _) =>
                        const Center(child: Text('content')),
                  ),
                ),
                nowPlaying: nowPlaying,
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
      }

      Future<void> pressShortcut(
        WidgetTester tester,
        LogicalKeyboardKey key,
      ) async {
        await tester.sendKeyDownEvent(LogicalKeyboardKey.control);
        await tester.sendKeyDownEvent(LogicalKeyboardKey.shift);
        await tester.sendKeyEvent(key);
        await tester.sendKeyUpEvent(LogicalKeyboardKey.shift);
        await tester.sendKeyUpEvent(LogicalKeyboardKey.control);
        await tester.pumpAndSettle();
      }

      testWidgets('sits beside the content at 1400, or floats in Mornye', (
        tester,
      ) async {
        await pumpShell(tester, 1400);
        final lyrics = find.byKey(const ValueKey('desktop-inspector-lyrics'));
        expect(lyrics, findsNothing);

        await tester.tap(find.byKey(const ValueKey('desktop-player-lyrics')));
        await tester.pumpAndSettle();
        expect(lyrics, findsOneWidget);
        expect(
          tester.getSize(lyrics).width,
          mornye ? mornyeGlassInspectorWidth : desktopInspectorWidth,
        );
        final content = tester.getRect(
          find.ancestor(
            of: find.text('content'),
            matching: find.byType(Center),
          ),
        );
        if (mornye) {
          // The glass runs the window's height over a pane that stays put.
          expect(content.right, 1400);
          expect(tester.getRect(lyrics).height, 800);
        } else {
          expect(content.right, tester.getRect(lyrics).left);
        }

        await pressShortcut(tester, LogicalKeyboardKey.keyU);
        expect(lyrics, findsNothing);
        final upNext = find.byKey(const ValueKey('desktop-inspector-upNext'));
        expect(upNext, findsOneWidget);
        // Escape closes a floating inspector and leaves one beside the
        // content open.
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(upNext, mornye ? findsNothing : findsOneWidget);

        if (!mornye) {
          await tester.tap(
            find.byKey(const ValueKey('desktop-player-up-next')),
          );
          await tester.pumpAndSettle();
        }
        expect(desktopInspectorPanel.value, isNull);
        expect(tester.takeException(), isNull);
      });

      if (mornye) {
        testWidgets('a full screen window puts the panel beside the content', (
          tester,
        ) async {
          desktopWindowFullScreen.value = true;
          addTearDown(() => desktopWindowFullScreen.value = false);
          await pumpShell(tester, 1400);
          await pressShortcut(tester, LogicalKeyboardKey.keyU);
          final upNext = find.byKey(const ValueKey('desktop-inspector-upNext'));
          expect(tester.getSize(upNext).width, desktopInspectorWidth);
          expect(tester.getRect(upNext).height, 800);
          expect(tester.takeException(), isNull);
        });
      }

      testWidgets('floats over the content at 1000', (tester) async {
        await pumpShell(tester, 1000);
        await pressShortcut(tester, LogicalKeyboardKey.keyL);
        final lyrics = find.byKey(const ValueKey('desktop-inspector-lyrics'));
        expect(lyrics, findsOneWidget);
        final content = tester.getRect(
          find.ancestor(
            of: find.text('content'),
            matching: find.byType(Center),
          ),
        );
        expect(content.right, 1000);
        expect(tester.getRect(lyrics).right, 1000);
        expect(tester.getRect(lyrics).left, lessThan(content.right));

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(lyrics, findsNothing);
        expect(tester.takeException(), isNull);
      });

      testWidgets('Now Playing covers the window and Escape returns', (
        tester,
      ) async {
        await pumpShell(tester, 1400);
        desktopNowPlayingOpen.value = true;
        await tester.pumpAndSettle();
        expect(find.text('now playing'), findsOneWidget);
        expect(find.text('content'), findsNothing);
        expect(find.text('home'), findsNothing);
        expect(
          tester.getSize(find.byKey(const ValueKey('desktop-now-playing'))),
          const Size(1400, 800),
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.text('now playing'), findsNothing);
        expect(find.text('content'), findsOneWidget);
      });

      testWidgets('Now Playing lines the timeline up with the cover', (
        tester,
      ) async {
        await pumpShell(tester, 1400, nowPlaying: const DesktopNowPlaying());
        desktopNowPlayingOpen.value = true;
        await tester.pumpAndSettle();
        // The Material backdrop blurs another copy of the artwork.
        final cover = tester.getRect(
          find.byWidgetPredicate(
            (widget) => widget is PlayerArtwork && widget.iconSize == 96,
          ),
        );
        final timeline = tester.getRect(find.byType(PlaybackSeekSlider));
        expect(timeline.left, cover.left);
        expect(timeline.right, cover.right);
      });

      testWidgets('full screen opens lyrics in Now Playing', (tester) async {
        bindDesktopFullScreenLyrics();
        await pumpShell(tester, 1400, nowPlaying: const DesktopNowPlaying());
        final player = find.byKey(const ValueKey('desktop-now-playing'));
        final inspectorLyrics = find.byKey(
          const ValueKey('desktop-inspector-lyrics'),
        );

        // A window that is not full screen shows lyrics in the panel.
        await tester.tap(find.byKey(const ValueKey('desktop-player-lyrics')));
        await tester.pumpAndSettle();
        expect(player, findsNothing);
        expect(inspectorLyrics, findsOneWidget);
        desktopInspectorPanel.value = null;

        desktopWindowFullScreen.value = true;
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('desktop-player-lyrics')));
        await tester.pumpAndSettle();
        expect(player, findsOneWidget);
        expect(
          find.byKey(const ValueKey('desktop-now-playing-lyrics')),
          findsOneWidget,
        );
        expect(find.text('content'), findsNothing);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(player, findsNothing);
        expect(desktopInspectorPanel.value, isNull);
        expect(find.text('content'), findsOneWidget);

        // Entering full screen with lyrics open moves them into the player.
        desktopWindowFullScreen.value = false;
        desktopInspectorPanel.value = DesktopInspectorPanel.lyrics;
        await tester.pumpAndSettle();
        expect(inspectorLyrics, findsOneWidget);
        desktopWindowFullScreen.value = true;
        await tester.pumpAndSettle();
        expect(player, findsOneWidget);

        await tester.tap(
          find.byKey(const ValueKey('desktop-now-playing-close')),
        );
        await tester.pumpAndSettle();
        expect(player, findsNothing);
        expect(tester.takeException(), isNull);
      });

      testWidgets('nested navigators leave the chrome readable', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        await pumpShell(tester, 1400);
        expect(find.bySemanticsLabel('content'), findsOneWidget);
        expect(find.bySemanticsLabel(RegExp('home')), findsWidgets);
        semantics.dispose();
      });
    });

    for (final desktop in [true, false]) {
      testWidgets(
        '$style sheet helper shows a ${desktop ? 'dialog' : 'sheet'}',
        (tester) async {
          debugDesktopPlatformOverride = desktop;
          await setWindow(tester, 1200);
          await tester.pumpWidget(
            app(
              mornye: mornye,
              home: Scaffold(
                body: Builder(
                  builder: (context) => Center(
                    child: TextButton(
                      onPressed: () => showAppBottomSheet<void>(
                        context: context,
                        title: 'Sheet title',
                        builder: (_) => const Text('Sheet body'),
                      ),
                      child: const Text('open'),
                    ),
                  ),
                ),
              ),
            ),
          );
          await tester.tap(find.text('open'));
          await tester.pumpAndSettle();
          expect(find.text('Sheet body'), findsOneWidget);
          final dialog = find.byKey(const ValueKey('app-sheet-dialog'));
          if (desktop) {
            expect(dialog, findsOneWidget);
            expect(find.byType(BottomSheet), findsNothing);
            final surface = find
                .descendant(of: dialog, matching: find.byType(Material))
                .first;
            expect(
              tester.getSize(surface).width,
              lessThanOrEqualTo(appSheetDialogMaxWidth),
            );
            final rect = tester.getRect(surface);
            expect(rect.center.dx, closeTo(600, 1));
          } else {
            expect(dialog, findsNothing);
            expect(find.byType(BottomSheet), findsOneWidget);
          }
        },
      );
    }
  }
}
