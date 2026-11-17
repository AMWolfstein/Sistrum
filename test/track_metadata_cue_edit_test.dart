import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/models/download_history.dart';

void main() {
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  late Directory dir;
  late Directory cache;
  setUpAll(() async {
    dir = await Directory.systemTemp.createTemp('cue-edit-');
    cache = await Directory.systemTemp.createTemp('cue-edit-cache-');
  });
  tearDownAll(() async {
    await dir.delete(recursive: true);
    await cache.delete(recursive: true);
  });

  // A CUE track shares its .cue file; only the suffix makes it virtual.
  for (final cueTrack in [false, true]) {
    testWidgets(
      'the metadata edit option is ${cueTrack ? 'hidden for' : 'offered on'} '
      'a CUE virtual track (cueTrack=$cueTrack)',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        final cueFile = File('${dir.path}/album.cue')
          ..writeAsStringSync('FILE "album.flac" WAVE\n');
        final messenger = tester.binding.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(paths, (_) async => cache.path);
        addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
        final item = DownloadHistoryItem(
          id: 'cue-track',
          trackName: 'Track 1',
          artistName: 'Artist',
          albumName: 'Album',
          filePath: cueTrack ? '${cueFile.path}#track01' : cueFile.path,
          service: 'local',
          downloadedAt: DateTime(2026),
        );
        await tester.pumpWidget(
          ProviderScope(
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.dark),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: TrackMetadataScreen(item: item),
            ),
          ),
        );
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 200)),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('Show menu'));
        await tester.pumpAndSettle();

        expect(
          find.text('Edit Metadata'),
          cueTrack ? findsNothing : findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
}
