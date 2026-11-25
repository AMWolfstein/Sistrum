import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/song_file_actions.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/mornye_player_actions_sheet.dart';

const _track = Track(
  id: 'track-1',
  name: 'Song',
  artistName: 'Artist',
  albumName: 'Album',
  duration: 200,
);

MediaItem _playing({String? source}) => MediaItem(
  id: 'track-1',
  title: 'Song',
  artist: 'Artist',
  album: 'Album',
  extras: {'source': ?source},
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('only a file on this device counts as a local playing song', () {
    expect(isLocalPlayingSong(_playing()), isFalse);
    expect(
      isLocalPlayingSong(_playing(source: 'network://host/song')),
      isFalse,
    );
    expect(isLocalPlayingSong(_playing(source: '/music/Song.flac')), isTrue);
  });

  for (final deletable in [true, false]) {
    testWidgets(
      'Now Playing actions ${deletable ? 'offer' : 'omit'} Remove from device '
      'for a file on this device ($deletable)',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        tester.view.physicalSize = const Size(900, 1800);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        String? picked;
        final item = _playing(source: '/music/Song.flac');

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              playerCollectionTrackProvider.overrideWith(
                (ref, MediaItem media) async => _track,
              ),
            ],
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.light),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Builder(
                builder: (context) => Scaffold(
                  body: TextButton(
                    onPressed: () async {
                      picked = await showMornyeContextMenu<String>(
                        context: context,
                        builder: (_) => MornyePlayerActionsSheet(
                          mediaItem: item,
                          deletable: deletable,
                        ),
                      );
                    },
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.text('open'));
        await tester.pumpAndSettle();

        final entry = find.text('Remove from device');
        if (!deletable) {
          expect(entry, findsNothing);
          return;
        }
        expect(entry, findsOneWidget);
        await tester.tap(entry);
        await tester.pumpAndSettle();
        expect(picked, 'delete');
      },
    );
  }
}
