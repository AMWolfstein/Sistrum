import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoColors;
import 'package:flutter/physics.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/app_motion.dart';

/// One switch style for settings, extension controls and editing sheets.
///
/// In Liquid it is the package's [LiquidGlassSwitch]: its thumb is a solid
/// pill at rest, and it draws the glass lens only while it is touched or
/// carried (the package hides the lens at rest, so an idle switch reads no
/// backdrop). In None, and with reduced motion, it is the plain
/// Mornye switch below. Both keep the same size, semantics and tap target.
class AppSwitch extends ConsumerWidget {
  const AppSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    this.semanticLabel,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final glass =
        ref.watch(mornyeLiquidGlassProvider) &&
        !MediaQuery.disableAnimationsOf(context);
    if (glass) {
      return _LiquidAppSwitch(
        value: value,
        onChanged: onChanged,
        semanticLabel: semanticLabel,
      );
    }
    return _MornyeSwitch(
      value: value,
      onChanged: onChanged,
      semanticLabel: semanticLabel,
    );
  }
}

/// The package switch, styled with the Mornye accent for "on". Disabled is a
/// half-opaque switch that takes no input (the package needs a callback, so
/// it gets a no-op one). RTL mirrors it, as the plain switch does.
class _LiquidAppSwitch extends StatelessWidget {
  const _LiquidAppSwitch({
    required this.value,
    required this.onChanged,
    required this.semanticLabel,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = onChanged != null;
    final inactive = scheme.brightness == Brightness.dark
        ? const Color(0xff39393d)
        : const Color(0xff8e8e93);
    Widget control = Opacity(
      opacity: enabled ? 1 : 0.5,
      child: IgnorePointer(
        ignoring: !enabled,
        child: LiquidGlassSwitch(
          value: value,
          onChanged: onChanged ?? (_) {},
          activeColor: scheme.primary,
          inactiveColor: inactive,
          thumbColor: Colors.white,
        ),
      ),
    );
    if (Directionality.of(context) == TextDirection.rtl) {
      control = Transform.flip(flipX: true, child: control);
    }
    return Semantics(
      label: semanticLabel,
      toggled: value,
      enabled: enabled,
      onTap: enabled ? () => onChanged!(!value) : null,
      child: ExcludeSemantics(child: control),
    );
  }
}

class _MornyeSwitch extends StatefulWidget {
  const _MornyeSwitch({
    required this.value,
    required this.onChanged,
    required this.semanticLabel,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final String? semanticLabel;

  @override
  State<_MornyeSwitch> createState() => _MornyeSwitchState();
}

class _MornyeSwitchState extends State<_MornyeSwitch>
    with SingleTickerProviderStateMixin {
  /// How far the thumb travels, from off to on.
  static const double _travel = 22;

  double? _dragPosition;
  bool _dragCancelled = false;

  /// The thumb, from off (0) to on (1). A drag places it under the finger;
  /// otherwise the snappy spring carries it to rest, starting from the
  /// finger's velocity on release and from its own velocity when a new
  /// value interrupts it.
  late final AnimationController _thumb = AnimationController.unbounded(
    vsync: this,
    value: widget.value ? 1 : 0,
  );

  /// The side the thumb is moving to, or null after a drag placed it.
  late double? _thumbTarget = _thumb.value;

  /// The finger's velocity, in thumb travels a second, for the next move.
  double? _releaseVelocity;

  @override
  void dispose() {
    _thumb.dispose();
    super.dispose();
  }

  /// Sends the thumb to the current value's side unless a drag holds it.
  /// Runs during build, after the parent has applied a new value, so a
  /// released thumb never heads for the old side first.
  void _followValue() {
    if (_dragPosition != null) return;
    final target = widget.value ? 1.0 : 0.0;
    final velocity = _releaseVelocity;
    _releaseVelocity = null;
    if (MediaQuery.disableAnimationsOf(context)) {
      _thumbTarget = target;
      if (_thumb.value != target) _thumb.value = target;
      return;
    }
    if (target == _thumbTarget && velocity == null) return;
    _thumbTarget = target;
    _thumb.animateWith(
      SpringSimulation(
        MornyeMotion.snappySpring,
        _thumb.value,
        target,
        velocity ?? _thumb.velocity,
        snapToEnd: true,
      ),
    );
  }

  void _place(double position) {
    _dragPosition = position;
    _thumbTarget = null;
    _thumb.value = position;
  }

  @override
  Widget build(BuildContext context) {
    final value = widget.value;
    final onChanged = widget.onChanged;
    final scheme = Theme.of(context).colorScheme;
    final active = CupertinoColors.systemGreen.resolveFrom(context);
    final enabled = onChanged != null;
    final fade = MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : MornyeMotion.effectsFast.duration;
    final inactive = scheme.brightness == Brightness.dark
        ? const Color(0xff39393d)
        : const Color(0xff8e8e93);
    _followValue();

    final position = _dragPosition ?? (value ? 1.0 : 0.0);
    final control = AnimatedContainer(
      duration: fade,
      curve: MornyeMotion.effectsFast.curve,
      width: 63,
      height: 28,
      padding: const EdgeInsets.all(2),
      decoration: BoxDecoration(
        color: position >= 0.5 ? active : inactive,
        borderRadius: BorderRadius.circular(14),
      ),
      child: AnimatedBuilder(
        animation: _thumb,
        // The thumb stays inside the track when the spring runs past a side.
        builder: (context, child) => Align(
          alignment: AlignmentDirectional(
            -1 + _thumb.value.clamp(0.0, 1.0) * 2,
            0,
          ),
          child: child,
        ),
        child: Container(
          width: 37,
          height: 24,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
    );

    return Listener(
      onPointerDown: (_) => _dragCancelled = false,
      onPointerCancel: (_) => _dragCancelled = true,
      child: Semantics(
        label: widget.semanticLabel,
        toggled: value,
        enabled: enabled,
        child: Opacity(
          opacity: enabled ? 1 : 0.5,
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: enabled ? () => onChanged(!value) : null,
              borderRadius: BorderRadius.circular(22),
              // InkWell supplies keyboard and accessible activation. The
              // thumb uses only paint/layout, even on a page of switches.
              child: ExcludeSemantics(
                child: GestureDetector(
                  // A drag picks the thumb up where it is, also mid-move.
                  onHorizontalDragStart: enabled
                      ? (_) =>
                            setState(() => _place(_thumb.value.clamp(0.0, 1.0)))
                      : null,
                  onHorizontalDragUpdate: enabled
                      ? (event) {
                          final direction =
                              Directionality.of(context) == TextDirection.rtl
                              ? -1.0
                              : 1.0;
                          setState(
                            () => _place(
                              ((_dragPosition ?? (value ? 1 : 0)) +
                                      event.delta.dx * direction / _travel)
                                  .clamp(0.0, 1.0),
                            ),
                          );
                        }
                      : null,
                  onHorizontalDragEnd: enabled
                      ? (details) {
                          final next =
                              (_dragPosition ?? (value ? 1 : 0)) >= 0.5;
                          final direction =
                              Directionality.of(context) == TextDirection.rtl
                              ? -1.0
                              : 1.0;
                          setState(() {
                            _dragPosition = null;
                            // The thumb leaves the finger at its speed.
                            _releaseVelocity =
                                (details.primaryVelocity ?? 0) *
                                direction /
                                _travel;
                          });
                          if (!_dragCancelled && next != value) onChanged(next);
                        }
                      : null,
                  onHorizontalDragCancel: enabled
                      ? () => setState(() => _dragPosition = null)
                      : null,
                  child: SizedBox(
                    width: 71,
                    height: 44,
                    child: Center(child: control),
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

class AppSwitchListTile extends StatelessWidget {
  const AppSwitchListTile({
    super.key,
    required this.value,
    required this.onChanged,
    required this.title,
    this.subtitle,
    this.contentPadding,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final Widget title;
  final Widget? subtitle;
  final EdgeInsetsGeometry? contentPadding;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: ListTile(
        title: title,
        subtitle: subtitle,
        contentPadding: contentPadding,
        enabled: onChanged != null,
        onTap: onChanged == null ? null : () => onChanged!(!value),
        trailing: AppSwitch(value: value, onChanged: onChanged),
      ),
    );
  }
}
