import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_icon.dart';

Widget _host(Widget child, {bool mornye = false, bool reduceMotion = false}) {
  return MaterialApp(
    theme: ThemeData(extensions: [if (mornye) const MornyeTheme()]),
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: reduceMotion),
      child: Scaffold(body: Center(child: child)),
    ),
  );
}

Icon _drawnIcon(WidgetTester tester) =>
    tester.widget<Icon>(find.byType(Icon).first);

void main() {
  testWidgets('Sistrum keeps the original glyph or its Cupertino mapping', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(const AppIcon(Icons.favorite_border), mornye: true),
    );
    expect(_drawnIcon(tester).icon, Icons.favorite_border);

    await tester.pumpWidget(
      _host(const AppIcon.adaptive(Icons.favorite), mornye: true),
    );
    await tester.pumpAndSettle();
    expect(_drawnIcon(tester).icon, CupertinoIcons.heart_fill);
  });
}
