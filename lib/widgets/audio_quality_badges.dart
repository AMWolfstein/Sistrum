import 'package:flutter/material.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_tokens.dart';

class AudioQualityBadge extends StatelessWidget {
  final String label;
  final ColorScheme colorScheme;

  const AudioQualityBadge({
    super.key,
    required this.label,
    required this.colorScheme,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
      decoration: BoxDecoration(
        color: colorScheme.primaryContainer.withValues(alpha: 0.6),
        borderRadius: context.tokens.borderRadiusBadge,
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: context.tokens.badgeFontSize,
          fontWeight: FontWeight.w600,
          color: colorScheme.onPrimaryContainer,
          height: 1.3,
        ),
      ),
    );
  }
}

class ExplicitBadge extends StatelessWidget {
  final ColorScheme colorScheme;

  const ExplicitBadge({super.key, required this.colorScheme});

  @override
  Widget build(BuildContext context) {
    final label = context.l10n.metadataExplicitValue;
    return Tooltip(
      message: label,
      excludeFromSemantics: true,
      child: Semantics(
        label: label,
        child: ExcludeSemantics(
          child: Container(
            width: 18,
            height: 18,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: colorScheme.onSurfaceVariant.withValues(alpha: 0.85),
              borderRadius: context.tokens.borderRadiusBadge,
            ),
            child: Text(
              'E',
              style: TextStyle(
                fontSize: context.tokens.badgeFontSize,
                fontWeight: FontWeight.w700,
                color: colorScheme.surface,
                height: 1.0,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class ExplicitTrackTitle extends StatelessWidget {
  final String title;
  final bool explicit;
  final TextStyle? style;
  final TextAlign? textAlign;
  final int? maxLines;
  final TextOverflow overflow;
  final ColorScheme? colorScheme;

  const ExplicitTrackTitle({
    super.key,
    required this.title,
    required this.explicit,
    this.style,
    this.textAlign,
    this.maxLines,
    this.overflow = TextOverflow.clip,
    this.colorScheme,
  });

  @override
  Widget build(BuildContext context) {
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(text: title),
          if (explicit)
            WidgetSpan(
              alignment: PlaceholderAlignment.baseline,
              baseline: TextBaseline.alphabetic,
              child: Transform.translate(
                offset: const Offset(0, -2),
                child: Padding(
                  padding: const EdgeInsetsDirectional.only(start: 6),
                  child: ExplicitBadge(
                    colorScheme: colorScheme ?? Theme.of(context).colorScheme,
                  ),
                ),
              ),
            ),
        ],
      ),
      style: style,
      textAlign: textAlign,
      maxLines: maxLines,
      overflow: overflow,
    );
  }
}
