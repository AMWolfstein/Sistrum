import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mornye/controllers/player_metadata_controller.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/theme/app_theme.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/app_bottom_sheet.dart';
import 'package:mornye/widgets/mornye_player_details_sheet.dart';
import 'package:mornye/widgets/player_details_sheet.dart';

const _item = MediaItem(
  id: 'song',
  title: 'A song',
  artist: 'An artist',
  album: 'An album',
  extras: {'source': 'https://example.com/song.flac'},
);

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  Future<void> open(
    WidgetTester tester, {
    required bool mornye,
    MediaItem item = _item,
  }) async {
    final metadata = PlayerMetadataController(
      readMetadata: (_) async => const {},
    );
    addTearDown(metadata.dispose);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: mornye
              ? MornyeTheme.build(Brightness.light)
              : AppTheme.light(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showPlayerDetailsSheet(
                  context,
                  Theme.of(context).colorScheme,
                  metadata: metadata,
                  mediaItem: item,
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }

  testWidgets('Material phones open the shared sheet with its handle', (
    tester,
  ) async {
    await open(tester, mornye: false);
    final sheet = tester.widget<BottomSheet>(find.byType(BottomSheet));
    expect(sheet.showDragHandle, isNot(true));
    expect(find.byType(AppSheetHandle), findsOneWidget);
    expect(find.byType(AppBottomSheet), findsOneWidget);
    expect(find.text('Details'), findsOneWidget);
    expect(find.text('A song · An artist'), findsOneWidget);
    expect(find.text('An album'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });


  {

  }

  testWidgets('Mornye decodes the cover at its thumbnail size', (tester) async {
    await open(
      tester,
      mornye: true,
      item: _item.copyWith(artUri: Uri.file('/covers/song.jpg')),
    );
    final image = tester.widget<Image>(
      find.descendant(
        of: find.byType(MornyePlayerDetailsSheet),
        matching: find.byType(Image),
      ),
    );
    final provider = image.image as ResizeImage;
    expect(provider.width, (70 * tester.view.devicePixelRatio).ceil());
    expect(provider.height, isNull);
  });
}
