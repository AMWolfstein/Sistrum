import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';

import 'support/tooltip_finder.dart';

const _dialogKey = ValueKey('app-sheet-dialog');

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    debugDesktopPlatformOverride = true;
  });
  tearDown(() => debugDesktopPlatformOverride = null);

  Future<void> open(
    WidgetTester tester,
    void Function(BuildContext context) show, {
    double width = 1200,
  }) async {
    tester.view.physicalSize = Size(width, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: TextButton(
                  onPressed: () => show(context),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }

  Rect surface(WidgetTester tester, Finder dialog) => tester.getRect(
    find.descendant(of: dialog, matching: find.byType(Material)).first,
  );

  for (final size in AppDialogSize.values) {
    testWidgets('a ${size.name} sheet dialog is ${size.width} wide', (
      tester,
    ) async {
      await open(
        tester,
        (context) => showAppBottomSheet<void>(
          context: context,
          title: 'Title',
          dialogSize: size,
          builder: (_) => const Text('Body'),
        ),
      );
      final rect = surface(tester, find.byKey(_dialogKey));
      expect(rect.width, size.width);
      expect(rect.center.dx, closeTo(600, 1));
    });
  }

  testWidgets('scrollable sheets and a narrow window keep the class cap', (
    tester,
  ) async {
    await open(
      tester,
      width: 600,
      (context) => showAppScrollableBottomSheet<void>(
        context: context,
        title: 'Title',
        dialogSize: AppDialogSize.wide,
        builder: (_, controller) =>
            ListView(controller: controller, children: const [Text('Body')]),
      ),
    );
    // The 24dp window margin on each side wins over the 720dp class.
    expect(surface(tester, find.byKey(_dialogKey)).width, 600 - 48);
  });

  testWidgets('dialog corners follow the sheet radius', (tester) async {
    await open(
      tester,
      (context) => showAppBottomSheet<void>(
        context: context,
        title: 'Title',
        builder: (_) => const Text('Body'),
      ),
    );
    final shape =
        tester.widget<Dialog>(find.byKey(_dialogKey)).shape!
            as RoundedRectangleBorder;
    expect(shape.borderRadius, BorderRadius.circular(28));
    expect(findByTooltip('Close'), findsNothing);
  });
}
