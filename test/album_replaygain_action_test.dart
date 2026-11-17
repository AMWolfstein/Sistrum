import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/models/download_history.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  for (final downloaded in [false, true]) {
    for (final remove in [false, true]) {
      testWidgets(
        'ReplayGain survives hiding the album selection (downloaded: $downloaded, remove: $remove)',
        (tester) async {
          SharedPreferences.setMockInitialValues({});
          var attempts = 0;
          messenger.setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'safCopyToTemp') attempts++;
            // Simulate an inaccessible file. Reaching this call and showing
            // the result proves confirmation actually starts the operation.
            return null;
          });
          addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
          const path = 'content://library/document/track.flac';
          final item = DownloadHistoryItem(
            id: 'track',
            trackName: 'Track',
            artistName: 'Artist',
            albumName: 'Album',
            filePath: path,
            service: 'provider-a',
            downloadedAt: DateTime(2026),
          );
          final albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');
          final localItem = LocalLibraryItem(
            id: 'track',
            trackName: 'Track',
            artistName: 'Artist',
            albumName: 'Album',
            filePath: path,
            scannedAt: DateTime(2026),
          );
          final row = downloaded
              ? UnifiedLibraryItem.fromDownloadHistory(item)
              : UnifiedLibraryItem.fromLocalLibrary(localItem);
          await tester.pumpWidget(
            ProviderScope(
              overrides: [
                libraryAlbumItemsProvider(
                  albumKey,
                ).overrideWith((ref) async => [row]),
              ],
              child: MaterialApp(
                theme: MornyeTheme.build(Brightness.light),
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                home: SelectionOverlayHost(
                  child: AlbumScreen.library(albumKey: albumKey),
                ),
              ),
            ),
          );
          await tester.pumpAndSettle();
          await tester.scrollUntilVisible(find.text('Track'), 200);
          await tester.longPress(find.text('Track'));
          await tester.pumpAndSettle();
          final l10n = AppLocalizations.of(
            tester.element(find.byType(SelectionBottomBar)),
          );
          Future<void> openConfirmation() async {
            final action = find.text(
              remove
                  ? l10n.selectionRemoveReplayGainCount(1)
                  : l10n.selectionReplayGainCount(1),
            );
            await tester.ensureVisible(action);
            await tester.tap(action);
            await tester.pumpAndSettle();
            expect(find.byType(SelectionBottomBar), findsNothing);
            expect(find.byType(AppAlertDialog), findsOneWidget);
          }

          await openConfirmation();
          await tester.tap(find.text(l10n.dialogCancel));
          await tester.pumpAndSettle();
          expect(attempts, 0);
          expect(find.byType(SelectionBottomBar), findsOneWidget);

          await openConfirmation();
          await tester.tap(
            find.descendant(
              of: find.byType(AppDialogAction),
              matching: find.text(
                remove
                    ? l10n.trackRemoveReplayGain
                    : l10n.replayGainBatchConfirmTitle,
              ),
            ),
          );
          await tester.pumpAndSettle();
          expect(attempts, 1);
          expect(
            find.text(
              remove
                  ? l10n.replayGainRemoveBatchSuccess(0, 1)
                  : l10n.replayGainBatchSuccess(0, 1),
            ),
            findsOneWidget,
          );
          expect(find.byType(SelectionBottomBar), findsNothing);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }
}
