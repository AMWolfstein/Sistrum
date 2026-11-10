import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mornye/l10n/l10n.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/models/track.dart';
import 'package:mornye/providers/library_collections_provider.dart';
import 'package:mornye/providers/settings_provider.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/create_playlist_sheet.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();
}

class _Collections extends LibraryCollectionsNotifier {
  final orders = <List<String>>[];
  final createdNames = <String>[];

  @override
  LibraryCollectionsState build() {
    final added = DateTime(2026, 10, 1);
    return LibraryCollectionsState(
      isLoaded: true,
      playlists: [
        UserPlaylistCollection(
          id: 'mix',
          name: 'Mix',
          createdAt: added,
          updatedAt: added,
          tracks: [
            for (final name in ['First', 'Second', 'Third'])
              CollectionTrackEntry(
                key: name,
                track: Track(
                  id: name,
                  name: name,
                  artistName: 'Artist',
                  albumName: 'Album',
                  duration: 180,
                ),
                addedAt: added,
              ),
          ],
        ),
      ],
    );
  }

  @override
  Future<bool> reorderPlaylistTracks(
    String playlistId,
    List<String> orderedKeys,
  ) async {
    orders.add(orderedKeys);
    return true;
  }

  @override
  Future<String> createPlaylist(String name) async {
    createdNames.add(name);
    return name;
  }
}

Widget _app(Widget home, _Collections collections, {required bool mornye}) =>
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(_Settings.new),
        libraryCollectionsProvider.overrideWith(() => collections),
      ],
      child: MaterialApp(
        theme: mornye ? MornyeTheme.build(Brightness.light) : ThemeData(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home,
      ),
    );

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  setUpAll(() async {
    await (FontLoader('packages/cupertino_icons/CupertinoIcons')..addFont(
          rootBundle.load('packages/cupertino_icons/assets/CupertinoIcons.ttf'),
        ))
        .load();
  });

  for (final mornye in [false, true]) {
    testWidgets('create playlist offers three sources (Mornye: $mornye)', (
      tester,
    ) async {
      final collections = _Collections();
      await tester.pumpWidget(
        _app(
          Consumer(
            builder: (context, ref, _) => Scaffold(
              body: TextButton(
                onPressed: () => showCreatePlaylistSheet(context, ref),
                child: const Text('create'),
              ),
            ),
          ),
          collections,
          mornye: mornye,
        ),
      );
      await tester.tap(find.text('create'));
      await tester.pumpAndSettle();
      for (final title in [
        'Empty playlist',
        'From a folder',
        'Import playlist file',
      ]) {
        expect(find.text(title), findsOneWidget);
      }

      await tester.tap(find.text('Empty playlist'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(EditableText), 'Evening');
      await tester.tap(find.text('Create'));
      await tester.pumpAndSettle();
      expect(collections.createdNames, ['Evening']);
    });
  }
}
