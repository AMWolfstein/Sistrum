import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/models/track.dart';
import 'package:mornye/providers/collection_suggestions_provider.dart';
import 'package:mornye/providers/library_collections_provider.dart';
import 'package:mornye/providers/music_player_provider.dart';
import 'package:mornye/providers/settings_provider.dart';
import 'package:mornye/services/collection_suggestions.dart';
import 'package:mornye/theme/app_theme.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/collection_suggestions_section.dart';

import 'support/fixed_settings.dart';

const _fresh = Track(
  id: 'fresh',
  name: 'Fresh song',
  artistName: 'Fresh artist',
  albumName: 'Album',
  duration: 180,
);

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);

  void publish(List<Track> tracks) {
    final date = DateTime.utc(2026);
    state = state.copyWith(
      playlists: [
        UserPlaylistCollection(
          id: 'example-playlist',
          name: 'Example playlist',
          createdAt: date,
          updatedAt: date,
          tracks: [
            for (final track in tracks)
              CollectionTrackEntry(key: track.id, track: track, addedAt: date),
          ],
        ),
      ],
    );
  }
}

Widget _app(
  ProviderContainer container,
  Widget child, {
  required bool mornye,
}) => UncontrolledProviderScope(
  container: container,
  child: MaterialApp(
    theme: mornye ? MornyeTheme.build(Brightness.light) : AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: child),
  ),
);

void main() {
  {
    {

    }
  }

  testWidgets('playlist membership updates filter already loaded suggestions', (
    tester,
  ) async {
    final collections = _Collections();
    final container = ProviderContainer(
      overrides: [
        settingsProvider.overrideWith(FixedSettingsNotifier.new),
        libraryCollectionsProvider.overrideWith(() => collections),
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => Stream.value(null),
        ),
        collectionSuggestionsProvider.overrideWith(
          (ref, request) async =>
              const CollectionSuggestions(tracks: [_fresh], related: true),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      _app(
        container,
        const CustomScrollView(
          slivers: [
            CollectionSuggestionsSliver(
              collectionKey: 'playlist:example-playlist',
              tracks: [],
              playlistId: 'example-playlist',
            ),
          ],
        ),
        mornye: false,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Fresh song'), findsOneWidget);
    collections.publish([_fresh]);
    await tester.pumpAndSettle();
    expect(find.text('Fresh song'), findsNothing);
    collections.publish([]);
    await tester.pumpAndSettle();
    expect(find.text('Fresh song'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
}
