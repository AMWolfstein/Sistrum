import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/selection_action_button.dart';

Widget _app(Widget child) => ProviderScope(
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.dark),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(
      body: Align(alignment: Alignment.topLeft, child: child),
    ),
  ),
);

void main() {
  for (final (width, columns, itemWidth) in [
    (360.0, 2, 176.0),
    (960.0, 3, 944 / 3),
    (1280.0, 4, 314.0),
  ]) {
    testWidgets('selection grid geometry ($columns columns)', (tester) async {
      tester.view.physicalSize = Size(width, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      var tapped = -1;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => SelectionActionGrid(
              children: [
                for (var index = 0; index < 5; index++)
                  SelectionActionButton(
                    key: ValueKey(index),
                    icon: Icons.edit,
                    label: 'Action $index',
                    onPressed: () => tapped = index,
                  ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final first = tester.getRect(find.byKey(const ValueKey(0)));
      for (var index = 0; index < 5; index++) {
        final rect = tester.getRect(find.byKey(ValueKey(index)));
        expect(rect.width, closeTo(itemWidth, 0.001));
        expect(rect.left, closeTo((index % columns) * (itemWidth + 8), 0.001));
        expect(
          rect.top,
          closeTo(first.top + (index ~/ columns) * (first.height + 8), 0.001),
        );
      }
      await tester.tap(find.text('Action 4'));
      expect(tapped, 4);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('selection bar keeps only offline actions', (tester) async {
    for (final selectedCount in [0, 1]) {
      await tester.pumpWidget(
        _app(
          LibraryTrackSelectionBar(
            selectedCount: selectedCount,
            allSelected: false,
            onClose: () {},
            onToggleSelectAll: () {},
            bottomPadding: 0,
            onReplayGain: ({required remove}) {},
            onDelete: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      // Re-enrich, FLAC upgrade and network uploads are online and absent.
      for (final icon in [
        Icons.auto_fix_high_outlined,
        Icons.download_for_offline_outlined,
        Icons.cloud_upload_outlined,
      ]) {
        expect(find.byIcon(icon), findsNothing);
      }
      expect(find.byType(SelectionActionButton), findsNWidgets(2));
      expect(tester.takeException(), isNull);
    }
  });
}
