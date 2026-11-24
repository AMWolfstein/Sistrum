import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';

Widget _host(Widget child, {bool reduceMotion = false}) {
  return MaterialApp(
    theme: MornyeTheme.build(Brightness.light),
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: reduceMotion),
      child: Scaffold(body: Center(child: child)),
    ),
  );
}

Icon _drawnIcon(WidgetTester tester) =>
    tester.widget<Icon>(find.byType(Icon).first);

void main() {
  testWidgets('Mornye keeps the original glyph or its Cupertino mapping', (
    tester,
  ) async {
    await tester.pumpWidget(_host(const AppIcon(Icons.favorite_border)));
    expect(_drawnIcon(tester).icon, Icons.favorite_border);

    await tester.pumpWidget(_host(const AppIcon.adaptive(Icons.favorite)));
    await tester.pumpAndSettle();
    expect(_drawnIcon(tester).icon, CupertinoIcons.heart_fill);
  });

  testWidgets('AppIconButton keeps IconButton for dense, Mornye and reduced '
      'motion layouts', (tester) async {
    Widget button({EdgeInsets? padding}) => AppIconButton(
      tooltip: 'Close',
      icon: const Icon(Icons.close),
      padding: padding,
      onPressed: () {},
    );

    await tester.pumpWidget(_host(button(padding: EdgeInsets.zero)));
    expect(find.byType(IconButton), findsOneWidget);

    // MaterialApp animates theme changes; settle before reading the theme.
    await tester.pumpWidget(_host(button()));
    await tester.pumpAndSettle();
    expect(find.byType(IconButton), findsOneWidget);
    expect(_drawnIcon(tester).icon, Icons.close);

    await tester.pumpWidget(_host(button(), reduceMotion: true));
    await tester.pumpAndSettle();
    expect(find.byType(IconButton), findsOneWidget);
  });
}
