import 'dart:async';
import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/services/audio_analysis_runner.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/audio_analysis_widget.dart';
import 'package:sistrum/widgets/song_analysis_sheet.dart';

/// Stands in for the analysis engine: it records the file it was asked about
/// and keeps the analysis running.
class _StubRunner extends AudioAnalysisRunner {
  final requested = <String>[];

  @override
  Future<AudioAnalysisRunResult> runAnalysis(
    String filePath,
    bool Function() isCancelled,
  ) {
    requested.add(filePath);
    return Completer<AudioAnalysisRunResult>().future;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory tempDir;
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    debugDesktopPlatformOverride = false;
    tempDir = Directory.systemTemp.createTempSync('song_analysis_sheet_test');
  });
  tearDown(() {
    debugDesktopPlatformOverride = null;
    tempDir.deleteSync(recursive: true);
  });

  testWidgets('audio quality analysis opens a sheet and starts the analysis', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final path = (File(
      '${tempDir.path}/Artist - Song.flac',
    )..writeAsBytesSync(const [0])).path;
    final runner = _StubRunner();

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showSongAnalysisSheet(
                  context,
                  filePath: path,
                  codecHint: 'flac',
                  exportName: 'Artist - Song',
                  runner: runner,
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    for (var i = 0; i < 5; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 40)),
      );
      await tester.pump();
    }

    expect(find.text('Audio quality analysis'), findsWidgets);
    expect(find.byType(AudioAnalysisCard), findsOneWidget);
    expect(
      tester
          .widget<AudioAnalysisCard>(find.byType(AudioAnalysisCard))
          .analyzeOnOpen,
      isTrue,
    );
    expect(runner.requested, [path]);
  });
}
