import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/library_settings_page.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/settings_group.dart';

import 'support/fixed_settings.dart';

class _Library extends LocalLibraryNotifier {
  _Library(this._initial, this._cleanup);

  final LocalLibraryState _initial;
  final Future<int> Function() _cleanup;

  @override
  LocalLibraryState build() => _initial;

  @override
  Future<int> cleanupMissingFiles({String? iosBookmark}) => _cleanup();

  void _beginScan() => state = state.copyWith(isScanning: true);
}

class _RetryLibrary extends _Library {
  _RetryLibrary()
    : super(
        LocalLibraryState(
          loadFailed: true,
          sources: const [
            LocalLibrarySource(
              id: 'sd-card',
              path: 'content://storage/tree/sd-card',
              displayName: 'Saved music',
              available: false,
            ),
          ],
        ),
        () async => 0,
      );

  int _reloadCalls = 0;

  @override
  Future<void> reloadFromStorage() async {
    _reloadCalls++;
    state = state.copyWith(loadFailed: false, totalCount: 5);
  }
}

Finder get _cleanupRow => find.byWidgetPredicate(
  (widget) => widget is SettingsItem && widget.title == 'Cleanup Missing Files',
);

Future<void> _mount(WidgetTester tester, {required _Library library}) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(430, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(
          () => FixedSettingsNotifier(
            const AppSettings(localLibraryEnabled: true),
          ),
        ),
        localLibraryProvider.overrideWith(() => library),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const LibrarySettingsPage(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  await tester.scrollUntilVisible(find.text('Cleanup Missing Files'), 300);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('source shows the scan failure reason', (tester) async {
    const error =
        'The previous library scan was interrupted. Scan again to continue.';
    final library = _Library(
      LocalLibraryState(
        totalCount: 1877,
        sources: const [
          LocalLibrarySource(
            id: 'music',
            path: '/music',
            displayName: 'Saved music',
            trackCount: 1877,
            lastScanError: error,
          ),
        ],
      ),
      () async => 0,
    );
    await _mount(tester, library: library);
    // The folder rows sit above the actions the setup scrolled to.
    await tester.drag(find.byType(Scrollable).first, const Offset(0, 3000));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Saved music'), 200);
    await tester.pumpAndSettle();
    expect(find.textContaining(error), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed load shows saved folder and offers retry', (
    tester,
  ) async {
    final library = _RetryLibrary();
    await _mount(tester, library: library);
    await tester.scrollUntilVisible(find.text('Retry'), -300);
    await tester.pumpAndSettle();
    expect(find.textContaining('Library could not be loaded'), findsOneWidget);
    expect(find.text('Saved music'), findsOneWidget);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(library._reloadCalls, 1);
    expect(find.textContaining('Library could not be loaded'), findsNothing);
    expect(find.text('Saved music'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('cleanup reports errors and can retry', (tester) async {
    var calls = 0;
    await _mount(
      tester,
      library: _Library(LocalLibraryState(totalCount: 1), () async {
        calls++;
        if (calls == 1) throw StateError('cleanup failed');
        return 1;
      }),
    );
    await tester.tap(find.text('Cleanup Missing Files'));
    await tester.pumpAndSettle();
    expect(find.byType(SnackBar), findsOneWidget);
    expect(find.textContaining('Error:'), findsOneWidget);
    expect(tester.widget<SettingsItem>(_cleanupRow).onTap, isNotNull);
    await tester.tap(find.text('Cleanup Missing Files'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(find.text('Removed 1 missing files from library'), findsOneWidget);
    expect(calls, 2);
    expect(tester.takeException(), isNull);
  });

  testWidgets('cleanup stays available for hidden source rows', (tester) async {
    await _mount(
      tester,
      library: _Library(
        LocalLibraryState(
          sources: const [
            LocalLibrarySource(
              id: 'sd-card',
              path: 'content://storage/tree/sd-card',
              displayName: 'Music',
              available: false,
              trackCount: 5,
              isRemovable: true,
            ),
          ],
        ),
        () async => 0,
      ),
    );
    expect(tester.widget<SettingsItem>(_cleanupRow).onTap, isNotNull);
    await tester.tap(find.text('Cleanup Missing Files'));
    await tester.pumpAndSettle();
    expect(find.text('Removed 0 missing files from library'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('cleanup cannot race an active scan', (tester) async {
    var localCalls = 0;
    final library = _Library(LocalLibraryState(totalCount: 1), () async {
      localCalls++;
      return 0;
    });
    await _mount(tester, library: library);
    final originalTap = tester.widget<SettingsItem>(_cleanupRow).onTap!;
    library._beginScan();
    await tester.pump();
    expect(tester.widget<SettingsItem>(_cleanupRow).onTap, isNull);
    originalTap();
    await tester.pump();
    expect(localCalls, 0);
    expect(tester.takeException(), isNull);
  });
}
