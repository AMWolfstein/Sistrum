import 'package:material_ui/material_ui.dart';

/// Clips [child] to Mornye's geometry: [fallbackRadius], or an oval when null.
class AppShapeClip extends StatelessWidget {
  const AppShapeClip({super.key, required this.child, this.fallbackRadius});

  final BorderRadius? fallbackRadius;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final fallbackRadius = this.fallbackRadius;
    return fallbackRadius == null
        ? ClipOval(child: child)
        : ClipRRect(borderRadius: fallbackRadius, child: child);
  }
}

/// A filled circle behind a centered icon or logo.
class AppShapeBadge extends StatelessWidget {
  const AppShapeBadge({
    super.key,
    required this.size,
    required this.color,
    required this.child,
  });

  final double size;
  final Color color;
  final Widget child;

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: AppShapeClip(
      child: ColoredBox(
        color: color,
        child: Center(child: child),
      ),
    ),
  );
}
