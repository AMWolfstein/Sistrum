import 'package:flutter/material.dart';

/// Clips [child] to a circle, or to [radius] when given.
class AppShapeClip extends StatelessWidget {
  const AppShapeClip({super.key, required this.child, this.radius});

  final BorderRadius? radius;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final radius = this.radius;
    return radius == null
        ? ClipOval(child: child)
        : ClipRRect(borderRadius: radius, child: child);
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
