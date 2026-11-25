import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/home_shortcut.dart';
import 'package:sistrum/models/settings.dart';

void main() {
  group('HomeShortcut.resolve', () {
    test('the stored defaults resolve to the defaults', () {
      expect(
        HomeShortcut.resolve(const AppSettings().homeShortcuts),
        HomeShortcut.defaults,
      );
    });

    test('keeps a saved choice in its order', () {
      expect(
        HomeShortcut.resolve([
          'loved',
          'listeningStats',
          'shuffle',
          'favoriteArtists',
        ]),
        [
          HomeShortcut.loved,
          HomeShortcut.listeningStats,
          HomeShortcut.shuffle,
          HomeShortcut.favoriteArtists,
        ],
      );
    });

    test('fills unknown names and repeats with unused defaults', () {
      expect(HomeShortcut.resolve(['equalizer', 'removedTool', 'equalizer']), [
        HomeShortcut.equalizer,
        HomeShortcut.shuffle,
        HomeShortcut.listeningStats,
        HomeShortcut.loved,
      ]);
    });

    test('keeps the first four of a longer list', () {
      expect(
        HomeShortcut.resolve([
          for (final shortcut in HomeShortcut.values.reversed) shortcut.name,
        ]),
        HomeShortcut.values.reversed.take(HomeShortcut.slotCount),
      );
    });
  });

  group('HomeShortcut.place', () {
    test('replaces the slot with a shortcut not on Home', () {
      expect(
        HomeShortcut.place(HomeShortcut.defaults, 1, HomeShortcut.equalizer),
        [
          HomeShortcut.shuffle,
          HomeShortcut.equalizer,
          HomeShortcut.listeningStats,
          HomeShortcut.loved,
        ],
      );
    });

    test('swaps with a shortcut already on Home', () {
      expect(
        HomeShortcut.place(HomeShortcut.defaults, 3, HomeShortcut.shuffle),
        [
          HomeShortcut.loved,
          HomeShortcut.equalizer,
          HomeShortcut.listeningStats,
          HomeShortcut.shuffle,
        ],
      );
    });

    test('leaves the list alone for the slot\'s own shortcut', () {
      expect(
        HomeShortcut.place(
          HomeShortcut.defaults,
          2,
          HomeShortcut.listeningStats,
        ),
        HomeShortcut.defaults,
      );
    });
  });
}
