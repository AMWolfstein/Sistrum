import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';

Map<String, TextStyle?> _roles(TextTheme t) => {
  'displayLarge': t.displayLarge,
  'displayMedium': t.displayMedium,
  'displaySmall': t.displaySmall,
  'headlineLarge': t.headlineLarge,
  'headlineMedium': t.headlineMedium,
  'headlineSmall': t.headlineSmall,
  'titleLarge': t.titleLarge,
  'titleMedium': t.titleMedium,
  'titleSmall': t.titleSmall,
  'bodyLarge': t.bodyLarge,
  'bodyMedium': t.bodyMedium,
  'bodySmall': t.bodySmall,
  'labelLarge': t.labelLarge,
  'labelMedium': t.labelMedium,
  'labelSmall': t.labelSmall,
};

void main() {
  test('Mornye type does not change', () {
    for (final brightness in Brightness.values) {
      for (final style in _roles(
        MornyeTheme.build(brightness).textTheme,
      ).values) {
        expect(style!.fontVariations, isNull);
        expect(style.fontFamily, isNot('Google Sans Flex'));
      }
    }
  });
}
