import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/metadata_separator_settings_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/settings_group.dart';

import 'support/fixed_settings.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  for (final size in [const Size(390, 844), const Size(844, 390)]) {
    testWidgets('separator preview, save, cancel and reset ($size)', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final container = ProviderContainer(
        overrides: [settingsProvider.overrideWith(FixedSettingsNotifier.new)],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const MetadataSeparatorSettingsPage(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      Future<void> tap(String text) async {
        final target = find.text(text).last;
        await tester.ensureVisible(target);
        await tester.pumpAndSettle();
        await tester.tap(target);
        await tester.pumpAndSettle();
      }

      await tap('Artist');
      expect(find.byType(AppSheetHandle).hitTestable(), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(AppBottomSheet),
          matching: find.byType(SettingsGroup),
        ),
        findsOneWidget,
      );
      await tap('Semicolon (;)');
      expect(find.text('Artist A; Artist B'), findsOneWidget);
      await tap('Save');
      expect(container.read(settingsProvider).metadataSeparators.artist, '; ');
      expect(container.read(settingsProvider).metadataSeparators.genre, '');
      await tap('Artist');
      await tap('Custom separator');
      await tester.enterText(find.byType(EditableText), ' | ');
      await tester.pumpAndSettle();
      tester.view.viewInsets = FakeViewPadding(
        bottom: size.height > size.width ? 300 : 150,
      );
      await tester.pumpAndSettle();
      expect(find.byType(AppSheetHandle).hitTestable(), findsOneWidget);
      expect(tester.takeException(), isNull);
      expect(find.text('Artist A | Artist B'), findsOneWidget);
      await tap('Save');
      tester.view.viewInsets = const FakeViewPadding();
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).metadataSeparators.artist, ' | ');
      await tap('Artist');
      await tap('Slash (/)');
      // Dismissing a draft must not overwrite the saved preference.
      await tester.fling(
        find.byType(AppSheetHandle),
        Offset(0, size.height * 0.6),
        1200,
      );
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).metadataSeparators.artist, ' | ');
      await tap('Artist');
      await tap('Keep source formatting');
      await tap('Save');
      expect(
        container.read(settingsProvider).metadataSeparators.isDefault,
        isTrue,
      );
      expect(tester.takeException(), isNull);
    });
  }
}
