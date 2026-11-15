import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_metadata_row.dart';

void main() {
  for (final useFileUri in [false, true]) {
    testWidgets('Sistrum metadata renders local artwork (URI=$useFileUri)', (
      tester,
    ) async {
      final cover = await tester.runAsync(() async {
        final directory = await Directory.systemTemp.createTemp(
          'metadata-cover-',
        );
        addTearDown(() => directory.delete(recursive: true));
        final recorder = ui.PictureRecorder();
        Canvas(recorder).drawColor(Colors.green, BlendMode.src);
        final picture = recorder.endRecording();
        final image = await picture.toImage(32, 32);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        image.dispose();
        picture.dispose();
        return File('${directory.path}/cover with spaces #1.png')
          ..writeAsBytesSync(bytes!.buffer.asUint8List());
      });
      final item = LocalLibraryItem(
        id: 'local-cover-track',
        trackName: 'Track with artwork',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/missing/local-cover-track.flac',
        coverPath: useFileUri ? cover!.uri.toString() : cover!.path,
        scannedAt: DateTime(2026),
      );
      late ImageProvider<Object> artworkProvider;
      await tester.runAsync(() async {
        await tester.pumpWidget(
          ProviderScope(
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.dark),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: TrackMetadataScreen(localItem: item),
            ),
          ),
        );
        final artwork = find.descendant(
          of: find.byType(Hero),
          matching: find.byType(Image),
        );
        // Warm the actual resized decode; FileImage alone has a different key.
        artworkProvider = tester.widget<Image>(artwork).image;
        await precacheImage(artworkProvider, tester.element(artwork));
      });
      await tester.pumpAndSettle();

      final artwork = find.descendant(
        of: find.byType(Hero),
        matching: find.byType(RawImage),
      );
      expect(artwork, findsOneWidget);
      expect(tester.widget<RawImage>(artwork).image, isNotNull);
      expect(find.byIcon(Icons.music_note), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await artworkProvider.evict();
    });
  }

  for (final brightness in Brightness.values) {
    testWidgets(
      'Sistrum metadata has single-level sections and stacked values ($brightness)',
      (tester) async {
        await tester.binding.setSurfaceSize(const Size(320, 780));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final item = DownloadHistoryItem(
          id: 'metadata-track',
          trackName: 'A long track title for a narrow screen',
          artistName: 'Artist',
          albumName: 'Album',
          filePath: '/missing/track.flac',
          service: 'provider-a',
          downloadedAt: DateTime(2026),
          isrc: 'USAAA2600001',
        );
        await tester.pumpWidget(
          ProviderScope(
            child: MaterialApp(
              theme: MornyeTheme.build(brightness),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(1.6)),
                child: child!,
              ),
              home: TrackMetadataScreen(item: item),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.byIcon(CupertinoIcons.back), findsOneWidget);
        expect(find.byIcon(CupertinoIcons.ellipsis), findsOneWidget);
        expect(find.byType(Card), findsNothing);
        final row = find.widgetWithText(MornyeMetadataRow, 'Track name');
        expect(
          find.ancestor(of: row, matching: find.byType(MornyeGlass)),
          findsNothing,
        );
        expect(
          find.ancestor(
            of: row,
            matching: find.byWidgetPredicate(
              (widget) =>
                  widget is Material &&
                  widget.color == MornyeTheme.controlFill(tester.element(row)),
            ),
          ),
          findsOneWidget,
        );
        final value = find.descendant(
          of: row,
          matching: find.text(item.trackName),
        );
        expect(
          tester.getTopLeft(value).dy,
          greaterThan(tester.getBottomLeft(find.text('Track name')).dy),
        );
        expect(
          tester.widget<Text>(value).style?.color,
          MornyeTheme.build(brightness).colorScheme.onSurface,
        );
        await tester.tap(find.byIcon(CupertinoIcons.ellipsis));
        await tester.pumpAndSettle();
        expect(find.byIcon(CupertinoIcons.square_stack), findsOneWidget);
        expect(find.byIcon(CupertinoIcons.gear_alt), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
