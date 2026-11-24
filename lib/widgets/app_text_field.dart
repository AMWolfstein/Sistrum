import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';

/// Text input for dialogs, sheets and settings, with [decoration] or the
/// theme's input decoration.
class AppTextField extends StatelessWidget {
  const AppTextField({
    super.key,
    this.controller,
    this.label,
    this.hint,
    this.leading,
    this.errorText,
    this.decoration,
    this.keyboardType,
    this.textInputAction,
    this.inputFormatters,
    this.maxLines = 1,
    this.maxLength,
    this.autofocus = false,
    this.onChanged,
    this.onSubmitted,
    this.validator,
    this.scrollPadding = const EdgeInsets.all(20),
    this.autocorrect = true,
  });

  final TextEditingController? controller;
  final String? label;
  final String? hint;
  final Widget? leading;
  final String? errorText;

  /// Decoration of the field. Defaults to [label], [hint], [leading] and
  /// [errorText] on the theme's input decoration.
  final InputDecoration? decoration;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final List<TextInputFormatter>? inputFormatters;
  final int? maxLines;
  final int? maxLength;
  final bool autofocus;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  /// Validates inside an enclosing [Form], like [TextFormField].
  final FormFieldValidator<String>? validator;

  /// Keyboard reveal margin of the field.
  final EdgeInsets scrollPadding;

  /// False keeps keyboard correction disabled.
  final bool autocorrect;

  @override
  Widget build(BuildContext context) {
    final decoration =
        this.decoration ??
        InputDecoration(
          labelText: label,
          hintText: hint,
          prefixIcon: leading,
          errorText: errorText,
        );
    if (validator != null) {
      return TextFormField(
        controller: controller,
        decoration: decoration,
        keyboardType: keyboardType,
        textInputAction: textInputAction,
        inputFormatters: inputFormatters,
        maxLines: maxLines,
        maxLength: maxLength,
        autofocus: autofocus,
        onChanged: onChanged,
        onFieldSubmitted: onSubmitted,
        validator: validator,
        scrollPadding: scrollPadding,
        autocorrect: autocorrect,
      );
    }
    return TextField(
      controller: controller,
      decoration: decoration,
      keyboardType: keyboardType,
      textInputAction: textInputAction,
      inputFormatters: inputFormatters,
      maxLines: maxLines,
      maxLength: maxLength,
      autofocus: autofocus,
      onChanged: onChanged,
      onSubmitted: onSubmitted,
      scrollPadding: scrollPadding,
      autocorrect: autocorrect,
    );
  }
}
