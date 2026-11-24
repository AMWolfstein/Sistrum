import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/mornye_player_details_sheet.dart';
import 'package:sistrum/widgets/player_details_sheet.dart';

const _item = MediaItem(
  id: 'song',
  title: 'A song',
  artist: 'An artist',
  album: 'An album',
  extras: {'source': 'https://example.com/song.flac'},
);

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  tearDown(() => debugDesktopPlatformOverride = null);

  Future<void> open(WidgetTester tester, {MediaItem item = _item}) async {
    final metadata = PlayerMetadataController(
      readMetadata: (_) async => const {},
    );
    addTearDown(metadata.dispose);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
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

  testWidgets('Mornye phones show the details under their handle', (
    tester,
  ) async {
    await open(tester);
    expect(find.byType(AppSheetHandle), findsOneWidget);
    expect(find.text('Details'), findsOneWidget);
    // The header and the metadata rows both name the song and artist.
    expect(find.text('A song'), findsWidgets);
    expect(find.text('An artist'), findsWidgets);
    expect(find.text('An album'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Mornye desktop shows the details as a dialog', (tester) async {
    debugDesktopPlatformOverride = true;
    await open(tester);
    final dialog = find.byKey(const ValueKey('app-sheet-dialog'));
    expect(dialog, findsOneWidget);
    expect(find.byType(BottomSheet), findsNothing);
    expect(find.text('Details'), findsOneWidget);
    // The always-visible bar accepts the draggable sheet's controller.
    expect(
      find.descendant(of: dialog, matching: find.byType(Scrollbar)),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
    // Mornye's dialog closes with its Done button.
    final done = AppLocalizations.of(
      tester.element(find.byType(MornyePlayerDetailsSheet)),
    ).dialogDone;
    await tester.tap(find.text(done));
    await tester.pumpAndSettle();
    expect(find.text('Details'), findsNothing);
  }, variant: TargetPlatformVariant.only(TargetPlatform.macOS));

  for (final desktop in [false, true]) {
    testWidgets(
      'Mornye keeps its glass details on ${desktop ? 'desktop' : 'phones'}',
      (tester) async {
        debugDesktopPlatformOverride = desktop;
        await open(tester);
        expect(find.byType(MornyePlayerDetailsSheet), findsOneWidget);
        expect(find.byType(AppBottomSheet), findsNothing);
        expect(find.byType(AppSheetHandle), findsOneWidget);
        expect(
          find.byKey(const ValueKey('app-sheet-dialog')),
          desktop ? findsOneWidget : findsNothing,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('Mornye decodes the cover at its thumbnail size', (tester) async {
    await open(
      tester,
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
