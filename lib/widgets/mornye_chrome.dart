import 'dart:ui' show ImageFilter, lerpDouble;

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/theme/mornye_glass_presets.dart';
export 'package:sistrum/theme/mornye_glass_presets.dart' show MornyeGlassKind;
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/mornye_glass_press.dart';
import 'package:sistrum/widgets/mornye_liquid_backdrop.dart';
import 'package:sistrum/widgets/mornye_squircle_border.dart';
import 'package:sistrum/widgets/animation_utils.dart';

class MornyeSegmentedControl extends ConsumerWidget {
  const MornyeSegmentedControl({
    super.key,
    required this.labels,
    required this.selectedIndex,
    required this.onChanged,
    this.samplesBackdrop = true,
    this.compact = false,
  });

  final List<String> labels;
  final int selectedIndex;
  final ValueChanged<int> onChanged;

  /// See [MornyeGlass.samplesBackdrop].
  final bool samplesBackdrop;

  /// A pointer-sized control for a desktop toolbar, 36 tall.
  final bool compact;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    // At least 56 so each segment keeps a 48 dp target inside the pill inset.
    final height = compact
        ? scaler.scale(36)
        : (scaler.scale(15) + 36).clamp(56.0, double.infinity);
    final contrastBlocked = ref.watch(
      mornyeGlassContrastBlockedProvider(MediaQuery.highContrastOf(context)),
    );
    return Padding(
      padding: EdgeInsets.symmetric(vertical: compact ? 0 : 8),
      child: SizedBox(
        height: height,
        child: MornyeGlassPress(
          radius: height / 2,
          child: MornyeGlass.navigation(
            blurEnabled: ref.watch(mornyeBlurEnabledProvider),
            radius: height / 2,
            samplesBackdrop: samplesBackdrop,
            child: _SelectionRow(
              labels: labels,
              selectedIndex: selectedIndex,
              onChanged: onChanged,
              // A desktop click never holds the glass, and over an opaque
              // toolbar a native thumb would only refract a flat fill.
              glass:
                  !isDesktopPlatform &&
                  ref.watch(mornyeLiquidGlassProvider) &&
                  !contrastBlocked,
              padding: EdgeInsets.all(compact ? 3 : 4),
              itemBuilder: (context, index, selected) => Center(
                child: Text(
                  labels[index],
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurface,
                    fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Shared material for panels, menus, dialogs and floating sheets.
class MornyeGlassPanel extends ConsumerWidget {
  const MornyeGlassPanel({
    super.key,
    required this.child,
    this.radius = 32,
    this.firstInGroup = true,
    this.lastInGroup = true,
    this.strongTint = false,
    this.tintOpacity,
    this.tintColor,
    this.blurEnabled = true,
    this.samplesBackdrop = true,
    this.smoothCorners = false,
    this.highlightIntensity = 1,
    this.glassProgress = 1,
    this.kind = MornyeGlassKind.chrome,
  });

  const MornyeGlassPanel.overlay({
    super.key,
    required this.child,
    this.radius = 32,
    this.firstInGroup = true,
    this.lastInGroup = true,
    this.tintOpacity = 0.78,
    this.tintColor,
    this.blurEnabled = true,
    this.samplesBackdrop = true,
    this.smoothCorners = false,
    this.highlightIntensity = 1,
    this.glassProgress = 1,
    this.kind = MornyeGlassKind.chrome,
  }) : strongTint = true;

  final Widget child;
  final double radius;
  final bool firstInGroup;
  final bool lastInGroup;
  final bool strongTint;
  final double? tintOpacity;
  final Color? tintColor;
  final bool blurEnabled;

  /// See [MornyeGlass.samplesBackdrop].
  final bool samplesBackdrop;
  final bool smoothCorners;
  final double highlightIntensity;
  final double glassProgress;

  /// What the surface is: [MornyeGlassKind.content] panels are plain fills.
  final MornyeGlassKind kind;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MornyeGlass.navigation(
      radius: radius,
      kind: kind,
      firstInGroup: firstInGroup,
      lastInGroup: lastInGroup,
      strongTint: strongTint,
      tintOpacity: tintOpacity,
      tintColor: tintColor,
      blurEnabled: blurEnabled && ref.watch(mornyeBlurEnabledProvider),
      samplesBackdrop: samplesBackdrop,
      smoothCorners: smoothCorners,
      highlightIntensity: highlightIntensity,
      glassProgress: glassProgress,
      // Tile ink must paint above the glass tint rather than beneath it.
      child: Material(type: MaterialType.transparency, child: child),
    );
  }
}

/// One glass surface for every mode, chosen by [mornyeGlassLevelProvider]:
/// Liquid is a native lens in a batch, and None is a plain fill with no
/// backdrop read. Foreground content stays above.
class MornyeGlass extends ConsumerWidget {
  const MornyeGlass.navigation({
    super.key,
    required this.child,
    required this.blurEnabled,
    this.radius = 32,
    this.firstInGroup = true,
    this.lastInGroup = true,
    this.strongTint = false,
    this.tintOpacity,
    this.tintColor,
    this.samplesBackdrop = true,
    this.refractionDepth = 0.30,
    this.smoothCorners = false,
    this.highlightIntensity = 1,
    this.glassProgress = 1,
    this.kind = MornyeGlassKind.chrome,
  });

  /// Host tests cannot run shader filters; this lets them build the lens.
  @visibleForTesting
  static bool? debugShaderFilterSupport;

  /// Whether the device can run lens shaders, or the test override says so.
  static bool get shaderFilterSupported =>
      debugShaderFilterSupport ?? ImageFilter.isShaderFilterSupported;

  final Widget child;
  final bool blurEnabled;
  final double radius;
  final bool firstInGroup;
  final bool lastInGroup;
  final bool strongTint;
  final double? tintOpacity;
  final Color? tintColor;
  final double refractionDepth;
  final bool smoothCorners;
  final double highlightIntensity;

  /// Reveal glass through its tint, without isolating the backdrop in opacity.
  final double glassProgress;

  /// Plain-page surfaces keep their tint without filtering a uniform backdrop.
  final bool samplesBackdrop;

  /// What the surface is. [MornyeGlassKind.content] is always a plain fill;
  /// [MornyeGlassKind.control] samples only where its preset allows it.
  final MornyeGlassKind kind;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final level = ref.watch(mornyeGlassLevelProvider);
    final preset = MornyeGlassPreset.of(level);
    final reveal = glassProgress.clamp(0.0, 1.0);
    final translucent =
        reveal > 0 &&
        blurEnabled &&
        level != MornyeGlassLevel.flat &&
        kind != MornyeGlassKind.content &&
        !ref.watch(
          mornyeGlassContrastBlockedProvider(
            MediaQuery.highContrastOf(context),
          ),
        );
    final dark = scheme.brightness == Brightness.dark;
    final shape = BorderRadius.vertical(
      top: firstInGroup ? Radius.circular(radius) : Radius.zero,
      bottom: lastInGroup ? Radius.circular(radius) : Radius.zero,
    );
    var tint = scheme.surfaceContainerHigh;
    if (translucent) {
      final artworkTint = Theme.of(
        context,
      ).extension<MornyeTheme>()?.chromeSurface;
      var tintSource =
          tintColor ?? (dark ? scheme.surfaceContainerHigh : Colors.white);
      var opacity = tintOpacity ?? (strongTint ? 0.80 : 0.60);
      if (tintColor == null && artworkTint != null) {
        // The artwork color is already known; retain its hue with a richer
        // tint instead of a saturation filter over changing page content.
        final hsl = HSLColor.fromColor(scheme.surfaceContainerHigh);
        final coverTint = hsl
            .withSaturation((hsl.saturation * 1.6).clamp(0, 1))
            .toColor();
        tintSource = dark
            ? coverTint
            : Color.lerp(Colors.white, coverTint, 0.15)!;
        opacity = opacity.clamp(0.60, 1.0);
      }
      // Light glass needs a white veil over saturated or dark artwork.
      // Keep this in the existing fill so whitening adds no backdrop pass.
      var baseTint = tintSource.withValues(
        alpha: dark ? opacity * 0.82 : (opacity * 0.88).clamp(0.50, 1.0),
      );
      if (dark) {
        // Bound bright artwork with paint instead of filtering backdrop luma.
        baseTint = Color.alphaBlend(
          baseTint,
          Colors.black.withValues(alpha: 0.35),
        );
      }
      // Keep the tinted endpoint opaque and the single-pass blur budget unchanged.
      tint = Color.alphaBlend(
        scheme.surfaceContainerHigh.withValues(alpha: preset.tintStrength),
        baseTint,
      );
    }
    final insideGlass =
        context.dependOnInheritedWidgetOfExactType<_GlassBackdropScope>() !=
        null;
    final sampleBackdrop =
        translucent &&
        samplesBackdrop &&
        !insideGlass &&
        preset.samplesFor(kind);
    final useLens =
        sampleBackdrop &&
        firstInGroup &&
        lastInGroup &&
        level == MornyeGlassLevel.liquid &&
        shaderFilterSupported;
    final clearFill = (dark ? const Color(0xff999999) : Colors.white)
        .withValues(alpha: dark ? 0.17 : 0.46);
    final fill = Color.lerp(
      scheme.surfaceContainerHigh,
      useLens && !strongTint
          ? Color.lerp(tint, clearFill, preset.clearMix)
          : tint,
      reveal,
    );
    final squircle = smoothCorners && firstInGroup && lastInGroup;
    final surface = DecoratedBox(
      decoration: squircle
          ? ShapeDecoration(color: fill, shape: MornyeSquircleBorder(radius))
          : BoxDecoration(color: fill, borderRadius: shape),
      // Child controls paint on this surface; never re-blur their parent.
      child: _GlassBackdropScope(child: child),
    );
    final shadows = firstInGroup && lastInGroup
        ? [
            BoxShadow(
              color: Colors.black.withValues(alpha: dark ? 0.14 : 0.08),
              blurRadius: 8,
              blurStyle: BlurStyle.outer,
              offset: const Offset(0, 2),
            ),
          ]
        : null;
    final Widget backdrop;
    if (useLens) {
      final lens = MornyeLiquidBackdrop(
        borderRadius: shape,
        dispersion: preset.dispersion,
        blurSigma: preset.blurSigma,
        refractionDepth: refractionDepth * reveal,
        smoothCorners: squircle,
        highlightIntensity: highlightIntensity * reveal,
        child: surface,
      );
      // A batched lens composes blur and refraction into one backdrop read.
      // Outside a batch it takes a key of its own: sharing one with the page or
      // an overlapping menu would lose their glass.
      backdrop = LiquidGlassBatch.backdropIdOf(context) == null
          ? LiquidGlassBatch(blurMethod: preset.batchBlurMethod, child: lens)
          : lens;
    } else {
      backdrop = surface;
    }
    return DecoratedBox(
      decoration: squircle
          ? ShapeDecoration(
              shape: MornyeSquircleBorder(radius),
              shadows: shadows,
            )
          : BoxDecoration(borderRadius: shape, boxShadow: shadows),
      child: squircle
          ? ClipPath.shape(shape: MornyeSquircleBorder(radius), child: backdrop)
          : ClipRRect(borderRadius: shape, child: backdrop),
    );
  }
}

class _GlassBackdropScope extends InheritedWidget {
  const _GlassBackdropScope({required super.child});

  @override
  bool updateShouldNotify(_GlassBackdropScope oldWidget) => false;
}

/// A searchable category chip; callers supply any glass around it.
class MornyeFilterChip extends StatelessWidget {
  const MornyeFilterChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.icon,
    this.tonal = true,
  });

  final String label;
  final bool selected;
  final VoidCallback? onTap;
  final IconData? icon;
  final bool tonal;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final content = Semantics(
      button: true,
      selected: selected,
      enabled: onTap != null,
      child: Material(
        color: !tonal
            ? Colors.transparent
            : selected
            ? scheme.primary.withValues(alpha: 0.12)
            : MornyeTheme.controlFill(context, enabled: onTap != null),
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (icon != null) ...[
                  Icon(icon, size: 18, color: scheme.primary),
                  const SizedBox(width: 8),
                ],
                Text(
                  label,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: selected ? scheme.primary : scheme.onSurface,
                    fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    return MornyeGlassPress(
      radius: 24,
      enabled: onTap != null,
      child: ClipRRect(borderRadius: BorderRadius.circular(24), child: content),
    );
  }
}

class MornyeNavigationIcon extends StatelessWidget {
  const MornyeNavigationIcon({
    super.key,
    required this.icon,
    required this.badgeCount,
  });

  final IconData icon;
  final int badgeCount;

  @override
  Widget build(BuildContext context) => AnimatedBadge(
    count: badgeCount,
    child: Badge(
      isLabelVisible: badgeCount > 0,
      label: Text('$badgeCount'),
      child: Icon(icon),
    ),
  );
}

class MornyeTabBar extends ConsumerWidget {
  const MornyeTabBar({
    super.key,
    required this.destinations,
    required this.selectedIndex,
    required this.onSelected,
    required this.blurEnabled,
    this.liquidGlass = true,
    this.hiddenIconIndices = const {},
    this.contentOpacity = const AlwaysStoppedAnimation(1),
    this.fold = const AlwaysStoppedAnimation(0),
  });

  /// Side of the circle the glass folds into.
  static const double foldedSize = 52;

  final List<NavigationDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelected;
  final bool blurEnabled;
  final bool liquidGlass;
  final Set<int> hiddenIconIndices;

  /// Fade only the foreground so the backdrop can keep sampling the page.
  final Animation<double> contentOpacity;

  /// From 0, the full bar, to 1, the glass narrowed into a leading circle of
  /// [foldedSize]. It may overshoot, which makes the shape wobble as it lands.
  final Animation<double> fold;

  /// The floating layout also supplies the coordinates for collapsing icons.
  static bool usesLiquidGlass(
    BuildContext context, {
    required bool blurEnabled,
    required bool liquidGlass,
    required bool contrastBlocked,
  }) =>
      blurEnabled &&
      liquidGlass &&
      !MediaQuery.disableAnimationsOf(context) &&
      !contrastBlocked;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final contrastBlocked = ref.watch(
      mornyeGlassContrastBlockedProvider(MediaQuery.highContrastOf(context)),
    );
    final floating = usesLiquidGlass(
      context,
      blurEnabled: blurEnabled,
      liquidGlass: liquidGlass,
      contrastBlocked: contrastBlocked,
    );
    return Padding(
      padding: EdgeInsets.symmetric(vertical: floating ? 8 : 0),
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          // The bar's own glass. It folds into the leading circle while its
          // icons stay where they are, and the bar's fold drives its size.
          Positioned.fill(
            child: LayoutBuilder(
              builder: (context, constraints) => AnimatedBuilder(
                animation: fold,
                builder: (context, _) {
                  final amount = fold.value;
                  return Align(
                    alignment: AlignmentDirectional.bottomStart,
                    child: SizedBox(
                      width: lerpDouble(
                        constraints.maxWidth,
                        foldedSize,
                        amount,
                      ),
                      height: lerpDouble(
                        constraints.maxHeight,
                        foldedSize,
                        amount,
                      ),
                      child: MornyeGlass.navigation(
                        blurEnabled: blurEnabled,
                        strongTint: true,
                        tintOpacity: MornyeTheme.navigationOpacity(context),
                        refractionDepth: 0.45,
                        radius: lerpDouble(32, foldedSize / 2, amount)!,
                        child: const SizedBox.expand(),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          // The foreground fades with the fold; the glass keeps sampling.
          FadeTransition(
            opacity: contentOpacity,
            child: _SelectionRow(
              labels: [
                for (final destination in destinations) destination.label,
              ],
              selectedIndex: selectedIndex,
              onChanged: onSelected,
              glass: floating,
              padding: EdgeInsets.symmetric(
                horizontal: floating ? 6 : 5,
                vertical: 5,
              ),
              itemBuilder: (context, index, selected) => ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 54),
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: floating ? 0 : 6),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconTheme(
                        data: IconThemeData(
                          size: 25,
                          color: selected ? scheme.primary : scheme.onSurface,
                        ),
                        child: Opacity(
                          opacity: hiddenIconIndices.contains(index) ? 0 : 1,
                          child: destinations[index].icon,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        destinations[index].label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                          fontSize: floating ? 11 : null,
                          fontWeight: FontWeight.w600,
                          color: selected ? scheme.primary : scheme.onSurface,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The selected item's thumb over a row of equal items, with taps selecting.
/// Glass mode lifts the package's motion pill over the items; other modes use
/// a plain tinted capsule under them. The thumb slides between slots.
class _SelectionRow extends StatefulWidget {
  const _SelectionRow({
    required this.labels,
    required this.selectedIndex,
    required this.onChanged,
    required this.itemBuilder,
    required this.glass,
    required this.padding,
  });

  final List<String> labels;
  final int selectedIndex;
  final ValueChanged<int> onChanged;
  final Widget Function(BuildContext context, int index, bool selected)
  itemBuilder;

  /// Requests the native motion pill; it still needs the device shader and
  /// motion allowance, checked at build.
  final bool glass;
  final EdgeInsets padding;

  @override
  State<_SelectionRow> createState() => _SelectionRowState();
}

class _SelectionRowState extends State<_SelectionRow> {
  /// A finger on the row lifts the glass thumb, as the native press does.
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final count = widget.labels.length;
    if (count == 0) return const SizedBox.shrink();
    final glass =
        widget.glass &&
        MornyeGlass.shaderFilterSupported &&
        !MediaQuery.disableAnimationsOf(context);
    // Items lay out from the start edge, so the thumb's slot mirrors in RTL.
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final slot = rtl ? count - 1 - widget.selectedIndex : widget.selectedIndex;
    final motion = AppMotion.surface(context);
    final items = Row(
      children: [
        for (var index = 0; index < count; index++)
          Expanded(
            child: Semantics(
              button: true,
              selected: index == widget.selectedIndex,
              label: widget.labels[index],
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  customBorder: const StadiumBorder(),
                  onTap: () => widget.onChanged(index),
                  child: ExcludeSemantics(
                    child: widget.itemBuilder(
                      context,
                      index,
                      index == widget.selectedIndex,
                    ),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
    final thumb = Positioned.fill(
      child: IgnorePointer(
        child: LayoutBuilder(
          builder: (context, constraints) => TweenAnimationBuilder<double>(
            // A new selection slides from wherever the thumb is now.
            tween: Tween<double>(end: slot.toDouble()),
            duration: motion.duration,
            curve: motion.curve,
            builder: (context, at, _) =>
                glass ? _glassThumb(constraints, at) : _flatThumb(context, at),
          ),
        ),
      ),
    );
    return Padding(
      padding: widget.padding,
      child: Listener(
        // An interrupted press (a scroll or the system) releases the lift.
        onPointerDown: (_) => setState(() => _pressed = true),
        onPointerUp: (_) => setState(() => _pressed = false),
        onPointerCancel: (_) => setState(() => _pressed = false),
        // The glass thumb sits above the icons so its lens refracts them; a
        // flat thumb stays beneath them. The lifted lens may overflow the slot.
        child: Stack(
          fit: StackFit.passthrough,
          clipBehavior: Clip.none,
          children: glass ? [items, thumb] : [thumb, items],
        ),
      ),
    );
  }

  /// [at] is the thumb's visual slot, counted from the start edge.
  Widget _glassThumb(BoxConstraints constraints, double at) {
    final width = constraints.maxWidth / widget.labels.length;
    final height = constraints.maxHeight;
    return LiquidGlassBatch.exclude(
      child: Consumer(
        builder: (context, ref, _) {
          final preset = MornyeGlassPreset.of(
            ref.watch(mornyeGlassLevelProvider),
          );
          return LiquidGlassMotionPill(
            center: Offset((at + 0.5) * width, height / 2),
            active: _pressed,
            restSize: Size(width, height),
            activeSize: Size(width * 1.28, height * 1.24),
            // The package's tab pill: a clear lens, refraction only, no blur.
            style: LiquidGlassStyle(
              blur: preset.pillBlurSigma,
              refractionIndex: preset.pillRefractionIndex,
              refractionWidth: preset.pillRefractionWidth,
              color: const Color(0x1CFFFFFF),
            ),
          );
        },
      ),
    );
  }

  Widget _flatThumb(BuildContext context, double at) {
    final count = widget.labels.length;
    return Align(
      alignment: Alignment(count <= 1 ? 0 : -1 + 2 * at / (count - 1), 0),
      child: FractionallySizedBox(
        widthFactor: 1 / count,
        heightFactor: 1,
        child: DecoratedBox(
          decoration: ShapeDecoration(
            color: Theme.of(context).colorScheme.onSurface.withValues(
              alpha: Theme.of(context).brightness == Brightness.dark
                  ? 0.12
                  : 0.08,
            ),
            shape: const StadiumBorder(),
          ),
        ),
      ),
    );
  }
}
