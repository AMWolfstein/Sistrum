import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/confirm_and_delete_tracks.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/l10n/app_localizations.dart';

void main() {
  late Directory dir;
  late File file;
  late List<List<String>> removed;
  late bool? result;

  setUp(() async {
    dir = await Directory.systemTemp.createTemp('confirm-remove-');
    file = File('${dir.path}/song.flac')..writeAsBytesSync([1, 2, 3]);
    removed = [];
    result = null;
  });

  tearDown(() => dir.delete(recursive: true));

  Future<void> pumpButton(
    WidgetTester tester, {
    bool Function()? isActive,
  }) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          downloadHistoryProvider.overrideWith(() => _History(removed)),
          localLibraryProvider.overrideWith(() => _Library(removed)),
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Consumer(
            builder: (context, ref, _) => TextButton(
              onPressed: () async {
                result = await confirmRemoveTrackFromDevice(
                  context,
                  ref,
                  filePath: file.path,
                  offlineCopy: false,
                  isActive: isActive ?? () => true,
                );
              },
              child: const Text('Remove'),
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('Cancel keeps the file and its entries', (tester) async {
    await pumpButton(tester);
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    expect(find.text('Remove from device?'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(result, isFalse);
    expect(file.existsSync(), isTrue);
    expect(removed, isEmpty);
  });

  testWidgets('Delete removes the file, the history and the library entry', (
    tester,
  ) async {
    await pumpButton(tester);
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    for (var i = 0; i < 200 && result == null; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 10)),
      );
      await tester.pump(const Duration(milliseconds: 10));
    }
    expect(result, isTrue);
    expect(file.existsSync(), isFalse);
    expect(removed, [
      [file.path],
      [file.path],
    ]);
  });

  testWidgets('a track that stopped being current is not deleted', (
    tester,
  ) async {
    await pumpButton(tester, isActive: () => false);
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    expect(result, isFalse);
    expect(file.existsSync(), isTrue);
    expect(removed, isEmpty);
  });
}

class _History extends DownloadHistoryNotifier {
  _History(this.removed);

  final List<List<String>> removed;

  @override
  DownloadHistoryState build() => DownloadHistoryState();

  @override
  Future<void> removePhysicalFiles(Iterable<String> filePaths) async {
    removed.add(filePaths.toList());
  }
}

class _Library extends LocalLibraryNotifier {
  _Library(this.removed);

  final List<List<String>> removed;

  @override
  LocalLibraryState build() => LocalLibraryState();

  @override
  Future<void> removePhysicalFiles(Iterable<String> filePaths) async {
    removed.add(filePaths.toList());
  }
}
