import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/selection_action_button.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';

void main() {
  setUp(() => debugDesktopPlatformOverride = true);
  tearDown(() => debugDesktopPlatformOverride = null);

  Widget app({required Widget child}) => ProviderScope(
    child: MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: child),
    ),
  );

  testWidgets('desktop lists center past the reading width', (tester) async {
    tester.view.physicalSize = const Size(1920, 1080);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    late double inset;
    late double rowHeight;
    await tester.pumpWidget(
      app(
        child: Builder(
          builder: (context) {
            inset = wideListInset(context);
            rowHeight = TrackTableLayout.rowMinHeightFor(context);
            return const SizedBox();
          },
        ),
      ),
    );
    // MaterialApp animates a theme change; settle so the new theme applies.
    await tester.pumpAndSettle();
    expect(inset, (1920 - desktopContentMaxWidth) / 2);
    expect(rowHeight, 40);
  });

  testWidgets('desktop selection toolbar drops the handle and keeps each '
      'action its own width', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    Widget bar() => Align(
      alignment: Alignment.bottomCenter,
      child: SelectionBottomBar(
        selectedCount: 2,
        allSelected: false,
        onClose: () {},
        onToggleSelectAll: () {},
        bottomPadding: 0,
        children: [
          SelectionActionGrid(
            children: [
              for (final label in ['Convert', 'Apply ReplayGain to 2 songs'])
                Builder(
                  builder: (context) => SelectionActionButton(
                    icon: Icons.swap_horiz,
                    label: label,
                    onPressed: () {},
                    colorScheme: Theme.of(context).colorScheme,
                  ),
                ),
            ],
          ),
        ],
      ),
    );

    for (final desktop in [true, false]) {
      debugDesktopPlatformOverride = desktop;
      await tester.pumpWidget(app(child: bar()));
      await tester.pumpAndSettle();
      final buttons = find.byType(SelectionActionButton);
      final first = tester.getRect(buttons.at(0));
      final second = tester.getRect(buttons.at(1));
      if (desktop) {
        expect(find.byType(AppSheetHandle), findsNothing);
        expect(find.textContaining('Tap'), findsNothing);
        expect(second.width, isNot(closeTo(first.width, 0.01)));
      } else {
        expect(find.byType(AppSheetHandle), findsOneWidget);
        expect(second.width, closeTo(first.width, 0.01));
      }
    }
  });
}
