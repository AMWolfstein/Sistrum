import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/providers/album_track_sort_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_sort_menu.dart';
import 'package:sistrum/widgets/sort_header_button.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _album = (album: 'Example Album', artist: 'Example Artist');
const _songs = ['First', 'Second', 'Third'];

Future<void> _flush() => Future<void>.delayed(Duration.zero);

/// The header's sort control, wired the way the Album screen wires it.
class _AlbumSortHost extends ConsumerWidget {
  const _AlbumSortHost();

  @override
  Widget build(BuildContext context, WidgetRef ref) =>
      SortHeaderButton<AlbumTrackSort>(
        tooltip: context.l10n.albumSortSongs,
        current: ref.watch(albumTrackSortProvider(_album)),
        groups: albumTrackSortGroups(context, key: _album, songs: _songs),
        onSelected: (sort) => ref
            .read(albumTrackSortsProvider.notifier)
            .setSort(albumTrackSortScope(_album), sort),
      );
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('each album keeps its own sort across a restart', () async {
    final first = ProviderContainer();
    addTearDown(first.dispose);
    first
        .read(albumTrackSortsProvider.notifier)
        .setSort(albumTrackSortScope(_album), AlbumTrackSort.mostPlayed);
    await _flush();
    await _flush();

    final restarted = ProviderContainer();
    addTearDown(restarted.dispose);
    final sorts = restarted.read(albumTrackSortsProvider.notifier);
    await _flush();
    await _flush();
    // Spelling differences between pages must not split an album.
    expect(
      sorts.of(
        albumTrackSortScope((album: 'EXAMPLE ALBUM', artist: 'example artist')),
      ),
      AlbumTrackSort.mostPlayed,
    );
    expect(
      sorts.of(
        albumTrackSortScope((album: 'Example Album', artist: 'Someone Else')),
      ),
      AlbumTrackSort.album,
    );
  });

  test('a custom order survives a switch to another sort', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final sorts = container.read(albumTrackSortsProvider.notifier);
    container.read(albumTrackCustomOrdersProvider.notifier).setCustom(_album, [
      'Third',
      'First',
    ]);
    sorts.setSort(albumTrackSortScope(_album), AlbumTrackSort.custom);
    expect(container.read(albumTrackRanksProvider(_album)), {
      'third': -2,
      'first': -1,
    });
    sorts.setSort(albumTrackSortScope(_album), AlbumTrackSort.album);
    expect(container.read(albumTrackRanksProvider(_album)), isNull);
    sorts.setSort(albumTrackSortScope(_album), AlbumTrackSort.custom);
    expect(container.read(albumTrackRanksProvider(_album)), {
      'third': -2,
      'first': -1,
    });
  });

  testWidgets('the header control offers the album sorts', (tester) async {
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: Center(child: _AlbumSortHost())),
        ),
      ),
    );
    Future<void> openMenu() async {
      await tester.tap(find.byType(SortHeaderButton<AlbumTrackSort>));
      await tester.pumpAndSettle();
    }

    AlbumTrackSort sort() => container
        .read(albumTrackSortsProvider.notifier)
        .of(albumTrackSortScope(_album));

    await openMenu();
    await tester.tap(find.text('Most Played'));
    await tester.pumpAndSettle();
    expect(sort(), AlbumTrackSort.mostPlayed);

    await openMenu();
    await tester.tap(find.text('Custom Order'));
    await tester.pumpAndSettle();
    // Opening the editor already selects the order it shows.
    expect(sort(), AlbumTrackSort.custom);
    for (final song in _songs) {
      expect(find.text(song), findsOneWidget);
    }
    final list = tester.widget<ReorderableListView>(
      find.byType(ReorderableListView),
    );
    list.onReorderItem?.call(0, 2);
    await tester.pump();
    expect(container.read(albumTrackCustomOrdersProvider.notifier).of(_album), [
      albumTrackRankKey('Second'),
      albumTrackRankKey('Third'),
      albumTrackRankKey('First'),
    ]);
    expect(tester.takeException(), isNull);
  });
}
