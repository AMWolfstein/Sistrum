import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// A live sample of the selected glass mode over a sample backdrop, for
/// Settings > Appearance. It is rendered through [MornyeGlass], so it follows
/// the same policy and cost as the app: Liquid draws a lens in a batch, and
/// None a plain fill with no sampler. It reads the effective renderer, which is
/// the selected mode after device fallback.
///
/// It is hidden while the Performance hub compares modes, so a comparison
/// never draws this sample.
class MornyeGlassModePreview extends ConsumerWidget {
  const MornyeGlassModePreview({super.key, required this.modeLabel});

  /// The selected mode's name, shown in the panel.
  final String modeLabel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (ref.watch(mornyeGlassCompareModeProvider) != null) {
      return const SizedBox.shrink();
    }
    final theme = Theme.of(context);
    return ExcludeSemantics(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                for (final color in [
                  theme.colorScheme.primary,
                  const Color(0xff277f94),
                  const Color(0xffc69756),
                ])
                  // Light glass reads over a light backdrop, as in the app.
                  // Dense sample colours make white glass look gray.
                  theme.brightness == Brightness.light
                      ? Color.lerp(const Color(0xffffffff), color, 0.4)!
                      : color,
              ],
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: MornyeGlass.navigation(
              key: const ValueKey('mornye-glass-mode-preview'),
              blurEnabled: true,
              radius: 22,
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                  vertical: 14,
                ),
                child: Row(
                  children: [
                    const AppIcon(Icons.music_note_rounded),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text('Mornye', style: theme.textTheme.titleMedium),
                    ),
                    Text(modeLabel, style: theme.textTheme.bodySmall),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
