import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/track_lists/context_menu.dart';
import 'package:sistrum/widgets/track_lists/library_track_menu.dart';

/// A network-storage track that the old menu could save offline or upload.
UnifiedLibraryItem _networkItem() => UnifiedLibraryItem(
  id: 'network-track',
  trackName: 'Track',
  artistName: 'Artist',
  albumName: 'Album',
  filePath: 'network://nas.local/music/track.flac',
  addedAt: DateTime(2026),
  source: LibraryItemSource.local,
);

Widget _app(List<UnifiedLibraryItem> items) => ProviderScope(
  overrides: [playerUiEnabledProvider.overrideWithValue(true)],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.dark),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(
      body: Consumer(
        builder: (context, ref, _) => Center(
          child: TextButton(
            onPressed: () => showContextMenu(
              context,
              position: const Offset(160, 80),
              groups: libraryTrackMenuGroups(
                context,
                ref,
                items: items,
                onPlay: () {},
                onOpenDetails: () {},
                onConvert: () {},
                onDelete: () {},
              ),
            ),
            child: const Text('Open menu'),
          ),
        ),
      ),
    ),
  ),
);

void main() {
  testWidgets('library track menu offers only offline actions', (tester) async {
    tester.view.physicalSize = const Size(400, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app([_networkItem()]));
    await tester.tap(find.text('Open menu'));
    await tester.pumpAndSettle();

    for (final offline in [
      'Play',
      'Play next',
      'Add to queue',
      'Add to playlist',
      'Go to Album',
      'Go to Artist',
      'Show details',
      'Convert 1 track',
      'Delete 1 track',
    ]) {
      expect(find.text(offline), findsOneWidget, reason: offline);
    }
    for (final online in [
      'Download',
      'Save offline',
      'Upload to network storage',
      'Re-enrich',
      'Delete offline server copy',
      'Share',
      'Add to Wishlist',
      'Copy Deezer link',
      'Download another quality',
    ]) {
      expect(find.text(online), findsNothing, reason: online);
    }
    expect(tester.takeException(), isNull);
  });
}
