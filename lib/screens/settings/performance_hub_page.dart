import 'dart:ui' as ui;

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/services/performance_auto_drive.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/performance_run.dart';
import 'package:sistrum/providers/performance_hub_provider.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/frame_timing_stats.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/performance_labels.dart';
import 'package:sistrum/widgets/app_action_button.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/settings_group.dart';

final _log = AppLogger('PerformanceHubPage');

/// Measures this app's frame timings on the device. Nothing leaves the phone.
class PerformanceHubPage extends ConsumerWidget {
  const PerformanceHubPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final hub = ref.watch(performanceHubProvider);
    final notifier = ref.read(performanceHubProvider.notifier);
    final live = hub.live;
    final latest = hub.runs.isEmpty ? null : hub.runs.first;
    final windowSeconds = ref.watch(performanceCompareWindowProvider).inSeconds;
    final reportedHz = validRefreshHz(
      ui.PlatformDispatcher.instance.implicitView?.display.refreshRate,
    );
    final budgetUs = frameBudgetUs(reportedHz);
    final na = l10n.perfHubNotAvailable;
    final recording = live != null && !live.compare;

    return PopScope(
      canPop: true,
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            AppSliverHeader.page(title: l10n.perfHubTitle),
            SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  16 + wideListInset(context),
                  8,
                  16 + wideListInset(context),
                  8,
                ),
                child: SettingsInfoCard(
                  icon: Icons.speed_outlined,
                  message: l10n.perfHubIntro,
                ),
              ),
            ),

            ...settingsSectionSlivers(
              title: l10n.perfHubDisplaySection,
              connected: true,
              children: [
                SettingsItem(
                  title: l10n.perfHubRefreshRate,
                  subtitle: l10n.perfHubRateNote,
                  trailing: Text(
                    reportedHz == null ? na : '${reportedHz.round()} Hz',
                  ),
                ),
                SettingsItem(
                  title: l10n.perfHubFrameBudget,
                  trailing: Text(
                    budgetUs == null
                        ? na
                        : '${(budgetUs / 1000).toStringAsFixed(2)} ms',
                  ),
                  showDivider: false,
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: l10n.perfHubRecordingSection,
              connected: true,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      AppActionButton(
                        onPressed: live == null
                            ? notifier.startRecording
                            : null,
                        icon: const AppIcon(Icons.fiber_manual_record_outlined),
                        label: Text(l10n.perfHubStart),
                      ),
                      AppActionButton(
                        outlined: true,
                        glass: false,
                        onPressed: recording ? notifier.stopRecording : null,
                        icon: const AppIcon(Icons.stop_rounded),
                        label: Text(l10n.perfHubStop),
                      ),
                    ],
                  ),
                ),
                if (recording) ...[
                  SettingsItem(
                    title: l10n.perfHubElapsed,
                    trailing: Text(performanceClock(live.elapsed)),
                  ),
                  SettingsItem(
                    title: l10n.perfHubRowFrames,
                    trailing: Text('${live.frames}'),
                  ),
                  SettingsItem(
                    title: l10n.perfHubLastFrame,
                    trailing: Text(
                      '${_ms(live.lastBuildUs / 1000)} / '
                      '${_ms(live.lastRasterUs / 1000)}',
                    ),
                  ),
                  SettingsItem(
                    title: l10n.perfHubOverBudgetSoFar,
                    trailing: Text(
                      live.overBudgetFrames == null || live.keptFrames == 0
                          ? na
                          : _percent(
                              live.overBudgetFrames! * 100 / live.keptFrames,
                            ),
                    ),
                    showDivider: false,
                  ),
                ] else
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                    child: Text(
                      l10n.perfHubRecordingHint,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
              ],
            ),

            ...settingsSectionSlivers(
              title: l10n.perfHubCompareSection,
              connected: true,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        l10n.perfHubCompareHint('$windowSeconds'),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l10n.perfHubCompareWarmup,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 12),
                      AppActionButton(
                        onPressed: live == null
                            ? () => _runComparison(notifier)
                            : null,
                        icon: const AppIcon(Icons.compare_arrows),
                        label: Text(l10n.perfHubCompareButton),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        l10n.perfHubAutoHint,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 8),
                      AppActionButton(
                        onPressed: live == null
                            ? () => _runAutomatic(context, ref, notifier)
                            : null,
                        icon: const AppIcon(Icons.play_circle_outline),
                        label: Text(l10n.perfHubAutoRunButton),
                      ),
                      if (live?.compare == true) ...[
                        const SizedBox(height: 8),
                        AppActionButton(
                          outlined: true,
                          glass: false,
                          onPressed: notifier.cancelComparison,
                          icon: const AppIcon(Icons.close_rounded),
                          label: Text(l10n.dialogCancel),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),

            ...settingsSectionSlivers(
              title: l10n.perfHubResultsSection,
              connected: true,
              children: [
                if (hub.tooFewFrames)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                    child: Text(
                      l10n.perfHubTooFewFrames,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                if (latest == null)
                  SettingsItem(title: l10n.perfHubNoResults, showDivider: false)
                else
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                    child: _ResultsTable(run: latest),
                  ),
              ],
            ),

            ...settingsSectionSlivers(
              title: l10n.perfHubExportSection,
              connected: true,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      AppActionButton(
                        outlined: true,
                        glass: false,
                        onPressed: latest == null
                            ? null
                            : () => _copy(context, performanceRunText(latest)),
                        icon: const AppIcon(Icons.copy),
                        label: Text(l10n.perfHubCopyText),
                      ),
                      AppActionButton(
                        outlined: true,
                        glass: false,
                        onPressed: latest == null
                            ? null
                            : () => _copy(context, performanceRunJson(latest)),
                        icon: const AppIcon(Icons.data_object),
                        label: Text(l10n.perfHubCopyJson),
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 32)),
            const NavBarSliverSpacer(),
          ],
        ),
      ),
    );
  }

  /// Drives the app through the automatic steps and measures each screen. A
  /// failure is logged; the notifier restores the mode and releases the hub.
  Future<void> _runAutomatic(
    BuildContext context,
    WidgetRef ref,
    PerformanceHubNotifier notifier,
  ) async {
    final l10n = context.l10n;
    try {
      // The loader is read now, before the shell changes tabs.
      final driver = buildAutoMeasureSteps(
        loader: ref.read(libraryBrowsePageLoaderProvider),
        noDataReason: l10n.perfHubAutoNoData,
        noScreenReason: l10n.perfHubAutoNoScreen,
      );
      await notifier.runAutomaticMeasurement(
        driver: driver,
        noScrollReason: l10n.perfHubAutoNoScroll,
        tooFewFramesReason: l10n.perfHubTooFewFrames,
      );
    } catch (e) {
      _log.w('Automatic measurement failed: $e');
    }
  }

  /// A comparison that throws is logged here, not left to the zone. The
  /// notifier releases the hub and restores the rendered mode in its finally.
  Future<void> _runComparison(PerformanceHubNotifier notifier) async {
    try {
      await notifier.compareModes();
    } catch (e) {
      _log.w('Comparison failed: $e');
    }
  }

  Future<void> _copy(BuildContext context, String text) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(context.l10n.perfHubCopied),
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 2),
      ),
    );
  }
}

typedef _Row = (String, String Function(PerformanceSession));

/// One column per session: the three modes of a comparison, or the single
/// session. Values the app cannot measure read "Not available".
class _ResultsTable extends StatelessWidget {
  const _ResultsTable({required this.run});

  final PerformanceRun run;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final na = l10n.perfHubNotAvailable;
    final rows = <_Row>[
      (
        l10n.perfHubRowRenderer,
        (s) => glassModeLabel(l10n, s.effectiveRenderer),
      ),
      (l10n.perfHubRowFrames, (s) => '${s.frames}'),
      ..._phaseRows(l10n.perfHubUi, (s) => s.ui, na),
      ..._phaseRows(l10n.perfHubRaster, (s) => s.raster, na),
      (
        l10n.perfHubRowJank,
        (s) => s.jankPercent == null ? na : _percent(s.jankPercent!),
      ),
      (
        l10n.perfHubFrameBudget,
        (s) => s.budgetMs == null ? na : '${s.budgetMs!.toStringAsFixed(2)} ms',
      ),
      (
        l10n.perfHubRowReported,
        (s) => s.reportedHz == null ? na : '${s.reportedHz!.round()} Hz',
      ),
      (
        l10n.perfHubRowMeasured,
        (s) => s.measuredHz == null ? na : '${s.measuredHz!.round()} Hz',
      ),
      (
        l10n.trackDuration,
        (s) => '${(s.durationMs / 1000).toStringAsFixed(1)} s',
      ),
      (l10n.audioInfoDevice, (s) => s.deviceModel ?? na),
      (l10n.perfHubRowAndroid, (s) => s.androidVersion ?? na),
      (l10n.perfHubRowApp, (s) => s.appVersion),
      (
        l10n.appearanceInterfaceSize,
        (s) => '${s.interfaceScale.toStringAsFixed(2)}x',
      ),
    ];

    // The budget fell back to the measured interval, so jank may be off.
    final jankUnreliable = run.sessions.any(
      (s) => s.reportedHz == null && s.jankPercent != null,
    );
    final modeChanged = run.sessions.any((s) => s.modeChangedDuringRecording);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (jankUnreliable) ...[
          Text(l10n.perfHubJankUnreliable, style: theme.textTheme.bodySmall),
          const SizedBox(height: 8),
        ],
        if (modeChanged) ...[
          Text(l10n.perfHubModeChanged, style: theme.textTheme.bodySmall),
          const SizedBox(height: 8),
        ],
        Row(
          children: [
            const Expanded(flex: 2, child: SizedBox.shrink()),
            for (final session in run.sessions)
              Expanded(
                child: Text(
                  glassModeLabel(l10n, session.glassMode),
                  textAlign: TextAlign.end,
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
          ],
        ),
        const Divider(height: 16),
        for (final (label, value) in rows)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 3),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 2,
                  child: Text(label, style: theme.textTheme.bodySmall),
                ),
                for (final session in run.sessions)
                  Expanded(
                    child: Text(
                      value(session),
                      textAlign: TextAlign.end,
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  List<_Row> _phaseRows(
    String prefix,
    FramePhaseStats? Function(PerformanceSession) phase,
    String na,
  ) {
    String value(PerformanceSession s, double Function(FramePhaseStats) pick) {
      final stats = phase(s);
      return stats == null ? na : _ms(pick(stats));
    }

    return [
      ('$prefix P50', (s) => value(s, (p) => p.p50)),
      ('$prefix P95', (s) => value(s, (p) => p.p95)),
      ('$prefix P99', (s) => value(s, (p) => p.p99)),
      ('$prefix max', (s) => value(s, (p) => p.max)),
    ];
  }
}

String _ms(double value) => '${value.toStringAsFixed(2)} ms';

String _percent(double value) => '${value.toStringAsFixed(1)} %';
