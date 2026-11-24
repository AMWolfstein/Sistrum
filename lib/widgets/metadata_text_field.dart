import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';

Color metadataEditorFieldFill(BuildContext context, ColorScheme cs) {
  return Colors.transparent;
}

class MetadataTextField extends StatelessWidget {
  final ColorScheme colorScheme;
  final String label;
  final TextEditingController controller;
  final String? hint;
  final TextInputType? keyboard;
  final int maxLines;
  final List<TextInputFormatter>? inputFormatters;

  const MetadataTextField(
    this.colorScheme,
    this.label,
    this.controller, {
    super.key,
    this.hint,
    this.keyboard,
    this.maxLines = 1,
    this.inputFormatters,
  });

  @override
  Widget build(BuildContext context) {
    final cs = colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 4, bottom: 6),
            child: Text(
              label,
              style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: cs.onSurfaceVariant,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.1,
              ),
            ),
          ),
          CupertinoTextField(
            controller: controller,
            keyboardType: keyboard,
            inputFormatters: inputFormatters,
            maxLines: maxLines,
            placeholder: hint,
            cursorColor: cs.primary,
            style: Theme.of(context).textTheme.bodyLarge,
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 14),
            decoration: null,
          ),
          const Divider(height: 0.5),
        ],
      ),
    );
  }
}
