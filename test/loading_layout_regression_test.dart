import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/repo_provider.dart';
import 'package:sistrum/screens/repo_tab.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/extension_row.dart';

class _LoadingRepo extends RepoNotifier {
  @override
  RepoState build() => const RepoState(
    isLoading: true,
    isInitialized: true,
    registryUrl: 'https://example.invalid/registry.json',
  );

  @override
  Future<void> initialize(String cacheDir) async {}
}

void main() {
  for (final width in [320.0, 1024.0]) {
    for (final brightness in Brightness.values) {
      testWidgets('loading rows fit at $width in $brightness', (tester) async {
        tester.view.physicalSize = Size(width, 1200);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        for (final skeleton in const [
          TrackListSkeleton(itemCount: 3),
          HomeSearchSkeleton(),
        ]) {
          await tester.pumpWidget(
            MaterialApp(
              theme: MornyeTheme.build(brightness),
              home: Scaffold(body: skeleton),
            ),
          );
          await tester.pump(const Duration(milliseconds: 100));
          expect(tester.takeException(), isNull);
        }
        await tester.pumpWidget(const SizedBox.shrink());
      });
    }

    testWidgets('store skeleton fits at $width with Sistrum', (tester) async {
      tester.view.physicalSize = Size(width, 1200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      const paths = MethodChannel('plugins.flutter.io/path_provider');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        paths,
        (_) async => '/tmp/sistrum-loading-test',
      );
      addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
      await tester.pumpWidget(
        ProviderScope(
          overrides: [repoProvider.overrideWith(_LoadingRepo.new)],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const RepoTab(),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.byType(ExtensionRow), findsWidgets);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
