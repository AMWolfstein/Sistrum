import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_choice_chip.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

Widget _app(ThemeData theme, Widget home) => ProviderScope(
  child: MaterialApp(
    theme: theme,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

void main() {
  for (final brightness in Brightness.values) {
    final theme = MornyeTheme.build(brightness);
    final variant = '$brightness';

    testWidgets('choice rows preserve adaptive appearance ($variant)', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _app(
            theme,
            Scaffold(
              body: Column(
                children: [
                  for (final selected in [false, true]) ...[
                    AppSheetOption(
                      title: Text('Legacy $selected'),
                      subtitle: const Text('Subtitle'),
                      leading: const AppIcon(Icons.speed_outlined),
                      selected: selected,
                      trailing: selected
                          ? AppIcon(
                              Icons.check,
                              color: theme.colorScheme.primary,
                            )
                          : null,
                      onTap: () {},
                    ),
                    AppSheetOption.choice(
                      title: Text('Choice $selected'),
                      subtitle: const Text('Subtitle'),
                      leading: const AppIcon(Icons.speed_outlined),
                      selected: selected,
                      onTap: () {},
                    ),
                  ],
                ],
              ),
            ),
          ),
        );
        for (final selected in [false, true]) {
          Finder row(String name) => find.ancestor(
            of: find.text('$name $selected'),
            matching: find.byType(AppSheetOption),
          );
          Finder icons(String name) =>
              find.descendant(of: row(name), matching: find.byType(Icon));
          expect(tester.getSize(row('Choice')), tester.getSize(row('Legacy')));
          final legacyIcons = tester.widgetList<Icon>(icons('Legacy')).toList();
          final choiceIcons = tester.widgetList<Icon>(icons('Choice')).toList();
          expect(choiceIcons.length, selected ? 2 : 1);
          for (var i = 0; i < choiceIcons.length; i++) {
            expect(choiceIcons[i].icon, legacyIcons[i].icon);
            expect(choiceIcons[i].color, legacyIcons[i].color);
            expect(
              tester.getSize(icons('Choice').at(i)),
              tester.getSize(icons('Legacy').at(i)),
            );
          }
          expect(
            tester.getSemantics(find.text('Choice $selected')),
            isSemantics(isSelected: selected, hasTapAction: true),
          );
          expect(
            tester.getSemantics(find.text('Choice $selected')),
            isSemantics(isInMutuallyExclusiveGroup: true),
          );
        }
        expect(tester.takeException(), isNull);
      } finally {
        semantics.dispose();
      }
    });

    testWidgets(
      'short sheets fit their content and resize with options ($variant)',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final count = ValueNotifier(2);
        addTearDown(count.dispose);
        await tester.pumpWidget(
          _app(
            theme,
            Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  child: const Text('Open options'),
                  onPressed: () => showAppScrollableBottomSheet<void>(
                    context: context,
                    title: 'Options',
                    builder: (context, controller) =>
                        ValueListenableBuilder<int>(
                          valueListenable: count,
                          builder: (context, count, _) => SingleChildScrollView(
                            controller: controller,
                            child: Column(
                              children: [
                                for (var i = 0; i < count; i++)
                                  AppSheetOption(
                                    title: Text('Option $i'),
                                    onTap: () {},
                                  ),
                              ],
                            ),
                          ),
                        ),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open options'));
        await tester.pumpAndSettle();
        final sheet = find.byType(AppBottomSheet);
        final shortHeight = tester.getSize(sheet).height;
        expect(shortHeight, lessThan(844 * 0.5));
        expect(
          tester.getRect(sheet).bottom -
              tester.getRect(find.text('Option 1')).bottom,
          lessThan(40),
        );
        final scroll = find.descendant(
          of: sheet,
          matching: find.byType(SingleChildScrollView),
        );
        await tester.drag(scroll, const Offset(0, -250));
        await tester.pumpAndSettle();
        expect(tester.getSize(sheet).height, closeTo(shortHeight, 1));
        count.value = 4;
        await tester.pumpAndSettle();
        expect(tester.getSize(sheet).height, greaterThan(shortHeight));
        expect(tester.getSize(sheet).height, lessThan(844 * 0.85));
        count.value = 40;
        await tester.pumpAndSettle();
        expect(tester.getSize(sheet).height, closeTo(844 * 0.85, 1));
        await tester.drag(scroll, const Offset(0, -400));
        await tester.pumpAndSettle();
        count.value = 1;
        await tester.pumpAndSettle();
        expect(tester.getSize(sheet).height, lessThan(shortHeight));
        expect(find.text('Option 0').hitTestable(), findsOneWidget);
        await tester.fling(scroll, const Offset(0, 200), 1200);
        await tester.pumpAndSettle();
        expect(sheet, findsNothing);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'scrollable sheet keeps its header and dismisses from body or handle ($variant)',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        int? selected;
        await tester.pumpWidget(
          _app(
            theme,
            Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  child: const Text('Open options'),
                  onPressed: () async {
                    selected = await showAppScrollableBottomSheet<int>(
                      context: context,
                      title: 'Options',
                      builder: (context, controller) => SingleChildScrollView(
                        controller: controller,
                        child: Column(
                          children: [
                            for (var i = 0; i < 40; i++)
                              AppSheetOption(
                                title: Text('Option $i'),
                                onTap: () => Navigator.pop(context, i),
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
          ),
        );
        Future<void> open() async {
          await tester.tap(find.text('Open options'));
          await tester.pumpAndSettle();
          expect(
            tester.getSize(find.byType(AppBottomSheet)).height,
            closeTo(844 * 0.85, 1),
          );
          expect(find.byType(AppSheetHandle).hitTestable(), findsOneWidget);
        }

        await open();
        final scroll = find.descendant(
          of: find.byType(AppBottomSheet),
          matching: find.byType(SingleChildScrollView),
        );
        await tester.drag(scroll, const Offset(0, -400));
        await tester.pumpAndSettle();
        expect(
          tester.getSize(find.byType(AppBottomSheet)).height,
          closeTo(844 * 0.95, 1),
        );
        expect(find.text('Options').hitTestable(), findsOneWidget);
        expect(find.byType(AppSheetHandle).hitTestable(), findsOneWidget);
        await tester.ensureVisible(find.text('Option 39'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Option 39'));
        await tester.pumpAndSettle();
        expect(selected, 39);
        await open();
        await tester.fling(scroll, const Offset(0, 400), 1200);
        await tester.pumpAndSettle();
        expect(find.byType(AppBottomSheet), findsNothing);
        await open();
        await tester.drag(scroll, const Offset(0, -400));
        await tester.pumpAndSettle();
        await tester.fling(
          find.byType(AppSheetHandle),
          const Offset(0, 300),
          1200,
        );
        await tester.pumpAndSettle();
        expect(find.byType(AppBottomSheet), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('sheet selects, disables and returns values ($variant)', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(360, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var selected = false;
      var disabledTaps = 0;
      bool? result;
      await tester.pumpWidget(
        _app(
          theme,
          Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                child: const Text('Open'),
                onPressed: () async {
                  result = await showAppBottomSheet<bool>(
                    context: context,
                    title: 'Library filter',
                    builder: (context) => StatefulBuilder(
                      builder: (context, setSheetState) => Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Wrap(
                            children: [
                              AppChoiceChip(
                                label: const Text('Downloaded tracks'),
                                selected: selected,
                                onSelected: (value) => setSheetState(() {
                                  selected = value;
                                }),
                              ),
                            ],
                          ),
                          AppSheetOption(
                            enabled: false,
                            title: const Text('Unavailable'),
                            onTap: () => disabledTaps++,
                          ),
                          AppSheetOption(
                            title: const Text('Apply'),
                            onTap: () => Navigator.pop(context, selected),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(find.byType(MornyeGlassPanel), findsOneWidget);
      await tester.tap(find.text('Unavailable'));
      expect(disabledTaps, 0);
      await tester.tap(find.text('Downloaded tracks'));
      await tester.pumpAndSettle();
      expect(selected, isTrue);
      await tester.tap(find.text('Apply'));
      await tester.pumpAndSettle();
      expect(result, isTrue);
      expect(find.text('Library filter'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('nested dialog retains theme and navigation ($variant)', (
      tester,
    ) async {
      final nestedNavigator = GlobalKey<NavigatorState>();
      bool? result;
      await tester.pumpWidget(
        _app(
          ThemeData(),
          Navigator(
            key: nestedNavigator,
            onGenerateRoute: (_) => MaterialPageRoute<void>(
              builder: (_) => Theme(
                data: theme,
                child: Builder(
                  builder: (context) => Scaffold(
                    body: TextButton(
                      child: const Text('Open import'),
                      onPressed: () async {
                        result = await showAppDialog<bool>(
                          context: context,
                          useRootNavigator: false,
                          builder: (context) => AppAlertDialog(
                            title: const Text('Import playlist'),
                            actions: [
                              AppDialogAction(
                                onPressed: () => Navigator.pop(context, true),
                                child: const Text('Confirm'),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open import'));
      await tester.pumpAndSettle();
      expect(nestedNavigator.currentState!.canPop(), isTrue);
      expect(find.byType(MornyeGlassPanel), findsOneWidget);
      expect(
        Theme.of(tester.element(find.text('Import playlist'))).brightness,
        brightness,
      );
      await tester.tap(find.text('Confirm'));
      await tester.pumpAndSettle();
      expect(result, isTrue);
      expect(nestedNavigator.currentState!.canPop(), isFalse);
      expect(find.text('Open import'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}
