import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/screens/network_storage_screen.dart';
import 'package:sistrum/services/network_storage_service.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/network_upload_actions.dart';

UnifiedLibraryItem song(String path) => UnifiedLibraryItem.fromLocalLibrary(
  LocalLibraryItem(
    id: path,
    trackName: 'Song',
    artistName: 'Artist',
    albumName: 'Album',
    filePath: path,
    scannedAt: DateTime(2026),
  ),
);

const nas = NetworkConnection(
  id: 'nas',
  name: 'NAS',
  protocol: NetworkProtocol.webdav,
  address: 'https://nas.example/music/',
);

void main() {
  testWidgets('the folder browser returns the chosen folder', (tester) async {
    final service = _Folders();
    String? picked;
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () async => picked = await browseNetworkUploadFolder(
                context,
                nas,
                service: service,
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    // Folders only, and none of the Library or download actions.
    expect(find.text('Rock'), findsOneWidget);
    expect(find.text('Song.flac'), findsNothing);
    expect(find.text('Add folder to Library'), findsNothing);
    expect(find.text('Download to this folder'), findsNothing);
    await tester.tap(find.text('Rock'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Upload to this folder'));
    await tester.pumpAndSettle();
    expect(service.checked, ['network://nas/Rock/']);
    expect(picked, 'network://nas/Rock/');
    expect(find.text('Open'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test('the offer waits for the connections to load', () {
    expect(networkUploadOffered(null, [song('/music/a.flac')]), isFalse);
    expect(networkUploadOffered([nas], [song('/music/a.flac')]), isTrue);
  });
}

class _Folders extends NetworkStorageService {
  _Folders() : super(read: () async => null, write: (_) async {});
  final checked = <String>[];

  @override
  Future<List<NetworkEntry>> list(
    NetworkConnection connection, [
    String path = '',
  ]) async => path.isEmpty
      ? const [
          NetworkEntry('Rock/', 'Rock', directory: true),
          NetworkEntry('Song.flac', 'Song.flac'),
        ]
      : const [];

  @override
  Future<void> checkUploadAccess(String folder) async => checked.add(folder);
}
