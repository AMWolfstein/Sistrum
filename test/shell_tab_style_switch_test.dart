import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/screens/main_shell.dart';
import 'package:mornye/services/shell_navigation_service.dart';

void main() {
  int? switched(
    int index, {
    required bool fromMornye,
    required bool showStore,
  }) => shellIndexOfTab(
    shellTabAt(index, mornye: fromMornye, showStore: showStore),
    mornye: !fromMornye,
    showStore: showStore,
  );

  for (final showStore in [false, true]) {
    test(
      'switching styles keeps the tab, not the index (store: $showStore)',
      () {
        final materialSettings = showStore ? 3 : 2;
        final mornyeSearch = showStore ? 3 : 2;

        // Mornye has no Settings destination (Settings opens as a page), so
        // Material's Settings has no Mornye destination to land on.
        expect(
          switched(materialSettings, fromMornye: false, showStore: showStore),
          isNull,
        );
        // Mornye's Search lands on Material's Home, where Material searches.
        expect(
          switched(mornyeSearch, fromMornye: true, showStore: showStore),
          0,
        );
        for (final index in [0, 1, if (showStore) 2]) {
          expect(
            switched(index, fromMornye: false, showStore: showStore),
            index,
          );
        }
        // Mornye phones have no Settings destination.
        expect(
          switched(materialSettings, fromMornye: false, showStore: showStore),
          isNull,
        );
      },
    );
  }

  test('Mornye Search maps to its own tab', () {
    expect(shellTabAt(2, mornye: true, showStore: false), ShellTab.search);
  });
}
