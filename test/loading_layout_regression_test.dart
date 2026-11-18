import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/widgets/animation_utils.dart';

void main() {
  for (final width in [320.0, 1024.0]) {
    for (final brightness in Brightness.values) {
      testWidgets('Material loading rows fit at $width in $brightness', (
        tester,
      ) async {
        tester.view.physicalSize = Size(width, 1200);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        for (final skeleton in const [
          TrackListSkeleton(itemCount: 3),
          AlbumTrackListSkeleton(itemCount: 3),
          HomeSearchSkeleton(),
        ]) {
          await tester.pumpWidget(
            MaterialApp(
              theme: brightness == Brightness.light
                  ? AppTheme.light()
                  : AppTheme.dark(),
              home: Scaffold(body: skeleton),
            ),
          );
          await tester.pump(const Duration(milliseconds: 100));
          expect(tester.takeException(), isNull);
        }
        await tester.pumpWidget(const SizedBox.shrink());
      });
    }
  }
}
