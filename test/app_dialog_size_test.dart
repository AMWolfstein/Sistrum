import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/theme/app_theme.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/app_alert_dialog.dart';


void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  Future<void> open(
    WidgetTester tester,
    void Function(BuildContext context) show, {
    bool mornye = false,
    double width = 1200,
  }) async {
    tester.view.physicalSize = Size(width, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: mornye
              ? MornyeTheme.build(Brightness.light)
              : AppTheme.light(),
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



  group('Material alert dialogs stay within M3 widths', () {
    Future<Rect> alert(WidgetTester tester, String message) async {
      await open(
        tester,
        (context) => showAppDialog<void>(
          context: context,
          builder: (context) => AppAlertDialog(
            title: const Text('Remove?'),
            content: Text(message),
            actions: [
              AppDialogAction(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancel'),
              ),
            ],
          ),
        ),
      );
      return surface(tester, find.byType(AlertDialog));
    }


    testWidgets('phones keep the 280dp minimum', (tester) async {
      final rect = await alert(tester, 'Remove it?');
      expect(rect.width, lessThan(440));
      expect(rect.width, greaterThanOrEqualTo(280));
    });
  });
}
