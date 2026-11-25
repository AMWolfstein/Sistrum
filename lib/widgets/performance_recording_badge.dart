import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/performance_hub_provider.dart';
import 'package:sistrum/utils/performance_labels.dart';
import 'package:sistrum/widgets/app_action_button.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';

/// Small badge above every page while the Performance hub records, so the
/// owner can leave the hub and keep using the app. It sits above the
/// navigator, so it has no Overlay ancestor: no tooltips here. It is an
/// opaque pill, not glass, because a backdrop read each frame would skew the
/// timings. Inactive, it is a zero-size positioned child, so it takes no touches.
class PerformanceRecordingBadge extends ConsumerWidget {
  const PerformanceRecordingBadge({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Watches only the flag, so the hub is not built until a recording starts.
    final active = ref.watch(performanceRecordingActiveProvider);
    final live = active
        ? ref.watch(performanceHubProvider.select((s) => s.live))
        : null;
    if (live == null) {
      return const Positioned(left: 0, top: 0, child: SizedBox.shrink());
    }
    final l10n = context.l10n;
    final label = live.compare
        ? l10n.perfHubCompareStep(
            glassModeLabel(l10n, live.mode),
            '${live.stepSecondsLeft}',
          )
        : '${l10n.perfHubRecording} ${performanceClock(live.elapsed)} · ${live.frames}';
    final notifier = ref.read(performanceHubProvider.notifier);
    return Positioned(
      top: MediaQuery.paddingOf(context).top + 8,
      right: 12,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Padding(
          padding: const EdgeInsets.only(left: 14, right: 4, top: 4, bottom: 4),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const DecoratedBox(
                decoration: BoxDecoration(
                  color: Colors.redAccent,
                  shape: BoxShape.circle,
                ),
                child: SizedBox(width: 8, height: 8),
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
              ),
              if (live.compare)
                AppActionButton(
                  outlined: true,
                  glass: false,
                  onPressed: notifier.cancelComparison,
                  icon: const AppIcon(Icons.close_rounded),
                  label: Text(l10n.dialogCancel),
                )
              else
                Semantics(
                  label: l10n.perfHubStop,
                  button: true,
                  child: AppIconButton(
                    icon: const AppIcon(Icons.stop_rounded),
                    onPressed: notifier.stopRecording,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
