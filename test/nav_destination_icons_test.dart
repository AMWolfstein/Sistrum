import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/nav_destination_icons.dart';

void main() {
  Future<void> pumpIcon(WidgetTester tester) => tester.pumpWidget(
    MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      home: const Center(child: BouncingIcon(child: Icon(Icons.home))),
    ),
  );

  double scale(WidgetTester tester) => tester
      .widget<ScaleTransition>(
        find.descendant(
          of: find.byType(BouncingIcon),
          matching: find.byType(ScaleTransition),
        ),
      )
      .scale
      .value;

  testWidgets('Mornye shows the selected icon at rest', (tester) async {
    await pumpIcon(tester);
    expect(scale(tester), 1);
    expect(tester.binding.transientCallbackCount, 0);
  });
}
