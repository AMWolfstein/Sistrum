import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('SortChoices');

/// Where one family of screens keeps its sorts: the SharedPreferences key,
/// the enum's values and the sort that is not stored.
class SortChoiceStore<T extends Enum> {
  const SortChoiceStore({
    required this.prefsKey,
    required this.values,
    required this.fallback,
  });

  final String prefsKey;
  final List<T> values;
  final T fallback;
}

/// The sort each screen chose, by the screen's scope key, saved in [store].
/// A scope that chose the fallback is not stored. Create one with
/// `NotifierProvider(() => SortChoices(store))`; read a choice with [of] or
/// with [sortChoiceIn] inside a `select`.
class SortChoices<T extends Enum> extends Notifier<Map<String, T>> {
  SortChoices(this._store);

  final SortChoiceStore<T> _store;
  Future<void>? _loading;

  @override
  Map<String, T> build() {
    _loading = _load();
    return const {};
  }

  T of(String scope) => sortChoiceIn(state, scope, _store.fallback);

  void setSort(String scope, T sort) {
    if (of(scope) == sort) return;
    state = {
      for (final entry in state.entries)
        if (entry.key != scope) entry.key: entry.value,
      if (sort != _store.fallback) scope: sort,
    };
    unawaited(_save());
  }

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = jsonDecode(prefs.getString(_store.prefsKey) ?? '{}');
      if (json is! Map || json.isEmpty || !ref.mounted) return;
      final byName = _store.values.asNameMap();
      state = {
        for (final entry in json.entries)
          if (entry.key is String && byName[entry.value] != null)
            entry.key as String: byName[entry.value]!,
        ...state,
      };
    } catch (error) {
      _log.w('Ignoring saved sorts (${_store.prefsKey}): ${error.runtimeType}');
    }
  }

  Future<void> _save() async {
    try {
      await _loading;
      if (!ref.mounted) return;
      final saved = jsonEncode({
        for (final entry in state.entries) entry.key: entry.value.name,
      });
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_store.prefsKey, saved);
    } catch (error) {
      _log.w('Could not save sorts (${_store.prefsKey}): ${error.runtimeType}');
    }
  }
}

/// The choice of [scope] among [choices], or [fallback] when unset.
T sortChoiceIn<T>(Map<String, T> choices, String scope, T fallback) =>
    choices[scope] ?? fallback;
