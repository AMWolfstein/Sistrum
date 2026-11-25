import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/services/audio_analysis_runner.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/audio_analysis_widget.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// Opens the audio quality analysis of the file at [filePath] and runs it at
/// once. Presented like the Now Playing details sheet: a sheet on phones and
/// tablets, a dialog on desktop.
Future<void> showSongAnalysisSheet(
  BuildContext context, {
  required String filePath,
  String? codecHint,
  String? exportName,
  AudioAnalysisRunner? runner,
}) {
  Widget sheet(ScrollController scrollController) => _SongAnalysisSheet(
    filePath: filePath,
    codecHint: codecHint,
    exportName: exportName,
    runner: runner,
    scrollController: scrollController,
  );
  if (isDesktopPlatform) {
    return showAppSheetDialog<void>(
      context: context,
      isScrollControlled: true,
      mornyeGlass: false,
      builder: (context) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 1,
        minChildSize: 1,
        builder: (context, scrollController) => sheet(scrollController),
      ),
    );
  }
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    backgroundColor: Colors.transparent,
    builder: (context) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.9,
      maxChildSize: 0.95,
      minChildSize: 0.5,
      builder: (context, scrollController) => sheet(scrollController),
    ),
  );
}

class _SongAnalysisSheet extends ConsumerWidget {
  const _SongAnalysisSheet({
    required this.filePath,
    required this.scrollController,
    this.codecHint,
    this.exportName,
    this.runner,
  });

  final String filePath;
  final String? codecHint;
  final String? exportName;
  final AudioAnalysisRunner? runner;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return MornyeGlass.navigation(
      blurEnabled: ref.watch(mornyeBlurEnabledProvider),
      child: SafeArea(
        top: false,
        child: Column(
          children: [
            const AppSheetHandle(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      context.l10n.trackAudioQualityAnalysis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  CupertinoButton(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    onPressed: () => Navigator.of(context).pop(),
                    child: Text(
                      context.l10n.dialogDone,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: theme.colorScheme.primary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                controller: scrollController,
                padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
                child: AudioAnalysisCard(
                  filePath: filePath,
                  codecHint: codecHint,
                  exportName: exportName,
                  analyzeOnOpen: true,
                  runner: runner,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
