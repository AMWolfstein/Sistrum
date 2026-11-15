import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/screens/settings/backup_restore_page.dart';
import 'package:sistrum/widgets/settings_group.dart';

class _BackupFilePicker extends FilePickerPlatform {
  bool opened = false;

  @override
  Future<PlatformFile?> pickFile({
    String? dialogTitle,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    dynamic Function(FilePickerStatus)? onFileLoading,
    int compressionQuality = 0,
    AndroidOptions androidOptions = const AndroidOptions(),
    DarwinOptions darwinOptions = const DarwinOptions(),
    WindowsOptions windowsOptions = const WindowsOptions(),
    LinuxOptions linuxOptions = const LinuxOptions(),
    WebOptions webOptions = const WebOptions(),
  }) async {
    opened = true;
    // A custom JSON/SFLB filter becomes JSON-only on Android because SFLB
    // has no system MIME mapping.
    expect(type, FileType.any);
    expect(allowedExtensions, isNull);
    return null;
  }
}

void main() {
  testWidgets(
    'settings-only preset and empty selection update export controls',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: BackupRestorePage(),
          ),
        ),
      );
      await tester.tap(find.text('Settings only'));
      await tester.pumpAndSettle();
      final switches = tester
          .widgetList<SettingsSwitchItem>(find.byType(SettingsSwitchItem))
          .toList();
      expect(switches.map((item) => item.value), [
        true,
        false,
        false,
        false,
        false,
      ]);
      expect(switches.last.onChanged, isNull);
      switches.first.onChanged!(false);
      await tester.pumpAndSettle();
      final export = tester
          .widgetList<SettingsItem>(find.byType(SettingsItem))
          .firstWhere((item) => item.title == 'Create backup file');
      expect(export.onTap, isNull);
      expect(
        export.subtitle,
        'Select at least one category to create a backup.',
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('restore does not exclude backups with an unknown MIME type', (
    tester,
  ) async {
    final original = FilePickerPlatform.instance;
    final picker = _BackupFilePicker();
    FilePickerPlatform.instance = picker;
    addTearDown(() => FilePickerPlatform.instance = original);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: BackupRestorePage(),
        ),
      ),
    );
    final restore = find.text('Choose backup file');
    await tester.scrollUntilVisible(restore, 300);
    await tester.tap(restore);
    await tester.pumpAndSettle();
    expect(picker.opened, isTrue);
    expect(find.byType(AlertDialog), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
