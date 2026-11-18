import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/track_list_tile.dart';

void main() {
  const track = Track(
    id: 'track-1',
    name: 'Selected song',
    artistName: 'Artist',
    albumName: 'Album',
    duration: 180,
    trackNumber: 1,
  );

  Widget buildTile({
    bool selectionMode = false,
    bool selected = false,
    VoidCallback? onToggleSelection,
    VoidCallback? onEnterSelectionMode,
  }) {
    return ProviderScope(
      child: MaterialApp(
        localizationsDelegates: const [
          AppLocalizations.delegate,
          ...GlobalMaterialLocalizations.delegates,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: TrackListTile(
            track: track,
            leading: const SizedBox(width: 32, child: Text('1')),
            isSelectionMode: selectionMode,
            isSelected: selected,
            onToggleSelection: onToggleSelection,
            onEnterSelectionMode: onEnterSelectionMode,
          ),
        ),
      ),
    );
  }

  testWidgets('selection mode toggles the row', (tester) async {
    var toggled = false;
    await tester.pumpWidget(
      buildTile(
        selectionMode: true,
        selected: true,
        onToggleSelection: () => toggled = true,
      ),
    );

    expect(find.byType(AnimatedSelectionCheckbox), findsOneWidget);
    await tester.tap(find.text('Selected song'));

    expect(toggled, isTrue);
  });

  testWidgets('long press enters selection mode when the caller enables it', (
    tester,
  ) async {
    var enteredSelection = false;
    await tester.pumpWidget(
      buildTile(onEnterSelectionMode: () => enteredSelection = true),
    );

    await tester.longPress(find.text('Selected song'));

    expect(enteredSelection, isTrue);
  });
}
