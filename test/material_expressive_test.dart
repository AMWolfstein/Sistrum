import 'dart:ui' as ui;

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderRepaintBoundary;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:material_ui/material_ui.dart' as material_ui;
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/material_expressive.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_action_button.dart';
import 'package:sistrum/widgets/app_choice_chip.dart';
import 'package:sistrum/widgets/app_content_card.dart';
import 'package:sistrum/widgets/app_switch.dart';
import 'package:sistrum/widgets/expressive_navigation_bar.dart';
import 'package:sistrum/widgets/selection_action_button.dart';
import 'package:sistrum/widgets/settings_group.dart';

import 'support/app_icon_finder.dart';

Widget _host(
  Widget child, {
  ThemeData? theme,
  bool reduceMotion = false,
  double textScale = 1,
  Locale locale = const Locale('en'),
}) => ProviderScope(
  child: MaterialApp(
    theme: theme ?? AppTheme.light(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        disableAnimations: reduceMotion,
        textScaler: TextScaler.linear(textScale),
      ),
      child: child!,
    ),
    home: Scaffold(body: Center(child: child)),
  ),
);

void main() {
  testWidgets('bridge retains dynamic colors, AMOLED, font and locale', (
    tester,
  ) async {
    for (final systemFont in [false, true]) {
      final scheme = ColorScheme.fromSeed(
        seedColor: Colors.green,
        brightness: Brightness.dark,
      ).copyWith(primary: const Color(0xffc6f192));
      final theme = AppTheme.dark(
        dynamicScheme: scheme,
        isAmoled: true,
        useSystemFont: systemFont,
      );
      late material_ui.ThemeData adapted;
      late M3EThemeData expressive;
      late ThemeData host;
      late String adaptedCancel;
      late String hostCancel;
      await tester.pumpWidget(
        _host(
          MaterialExpressiveScope(
            child: Builder(
              builder: (context) {
                adapted = material_ui.Theme.of(context);
                expressive = M3ETheme.of(context);
                host = Theme.of(context);
                adaptedCancel = material_ui.MaterialLocalizations.of(
                  context,
                ).cancelButtonLabel;
                hostCancel = MaterialLocalizations.of(
                  context,
                ).cancelButtonLabel;
                return const SizedBox();
              },
            ),
          ),
          theme: theme,
          locale: const Locale('id'),
        ),
      );
      await tester.pumpAndSettle();
      expect(adapted.colorScheme.primary, scheme.primary);
      expect(adapted.colorScheme.primaryFixed, scheme.primaryFixed);
      expect(adapted.colorScheme.surface, scheme.surface);
      expect(adapted.scaffoldBackgroundColor, Colors.black);
      expect(
        expressive.typeScale.bodyMedium.fontFamily,
        theme.textTheme.bodyMedium!.fontFamily,
      );
      expect(host.colorScheme, scheme);
      expect(adaptedCancel, hostCancel);
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('chips preserve select and deselect callbacks', (tester) async {
    var selected = false;
    await tester.pumpWidget(
      _host(
        StatefulBuilder(
          builder: (context, setState) => AppChoiceChip(
            label: const Text('Albums'),
            icon: const Icon(Icons.album_outlined),
            count: 12,
            selected: selected,
            onSelected: (next) => setState(() => selected = next),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('12'), findsOneWidget);
    expect(findAppIcon(Icons.album_outlined), findsOneWidget);
    await tester.tap(find.text('Albums'));
    await tester.pumpAndSettle();
    expect(selected, isTrue);
    await tester.tap(find.text('Albums'));
    await tester.pumpAndSettle();
    expect(selected, isFalse);
  });

  testWidgets('large labels fit narrow settings and selection actions', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      _host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SettingsGroup(
              children: [
                AppSwitchListTile(
                  value: true,
                  onChanged: (_) {},
                  title: const Text('Continue playing music from your Library'),
                ),
              ],
            ),
            Row(
              children: [
                for (final label in [
                  'Remove ReplayGain (10)',
                  'Convert 10 tracks',
                ])
                  Expanded(
                    child: SelectionActionButton(
                      icon: Icons.music_note,
                      label: label,
                      onPressed: () {},
                    ),
                  ),
              ],
            ),
          ],
        ),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  for (final dark in [false, true]) {
    for (final reduceMotion in [false, true]) {
      testWidgets('navigation tint continues through system insets '
          '(dark: $dark, reduced motion: $reduceMotion)', (tester) async {
        final capture = GlobalKey();
        const backdrop = Color(0xff725ca8);
        final theme = dark ? AppTheme.dark() : AppTheme.light();
        final tint = theme.colorScheme.surfaceContainer.withValues(alpha: 0.72);
        for (final bottomInset in [24.0, 48.0]) {
          await tester.pumpWidget(
            _host(
              Builder(
                builder: (context) => MediaQuery(
                  data: MediaQuery.of(
                    context,
                  ).copyWith(padding: EdgeInsets.only(bottom: bottomInset)),
                  child: RepaintBoundary(
                    key: capture,
                    child: SizedBox(
                      width: 320,
                      child: ColoredBox(
                        color: backdrop,
                        child: ExpressiveNavigationBar(
                          selectedIndex: 0,
                          onDestinationSelected: (_) {},
                          backgroundColor: tint,
                          destinations: const [
                            NavigationDestination(
                              icon: Icon(Icons.home),
                              label: 'Home',
                            ),
                            NavigationDestination(
                              icon: Icon(Icons.settings),
                              label: 'Settings',
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              theme: theme,
              reduceMotion: reduceMotion,
            ),
          );
          await tester.pumpAndSettle();
          final colors = await tester.runAsync(() async {
            final boundary = tester.renderObject<RenderRepaintBoundary>(
              find.byKey(capture),
            );
            final image = await boundary.toImage();
            try {
              final bytes = (await image.toByteData(
                format: ui.ImageByteFormat.rawRgba,
              ))!;
              return [
                // Sample between destinations and at the physical bottom.
                for (final y in [32, image.height - 1])
                  Color.fromARGB(
                    bytes.getUint8((y * image.width + 160) * 4 + 3),
                    bytes.getUint8((y * image.width + 160) * 4),
                    bytes.getUint8((y * image.width + 160) * 4 + 1),
                    bytes.getUint8((y * image.width + 160) * 4 + 2),
                  ),
              ];
            } finally {
              image.dispose();
            }
          });
          expect(colors![0], colors[1]);
          final expected = Color.alphaBlend(tint, backdrop);
          expect(colors[0].r, closeTo(expected.r, 1 / 255));
          expect(colors[0].g, closeTo(expected.g, 1 / 255));
          expect(colors[0].b, closeTo(expected.b, 1 / 255));
          expect(
            tester.getSize(find.byType(ExpressiveNavigationBar)).height,
            64 + bottomInset,
          );
          expect(tester.takeException(), isNull);
        }
      });
    }
  }

  testWidgets('Sistrum keeps Cupertino actions and its own surfaces', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AppActionButton(
              onPressed: () {},
              icon: const Icon(Icons.play_arrow),
              label: const Text('Play'),
            ),
            AppChoiceChip(
              label: const Text('Albums'),
              selected: true,
              onSelected: (_) {},
            ),
            const AppContentCard(child: Text('Details')),
            AppSwitch(value: true, onChanged: (_) {}),
          ],
        ),
        theme: MornyeTheme.build(Brightness.dark),
        reduceMotion: true,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(MaterialExpressiveScope), findsNothing);
    expect(find.byType(CupertinoButton), findsAtLeast(2));
    expect(tester.takeException(), isNull);
  });
}
