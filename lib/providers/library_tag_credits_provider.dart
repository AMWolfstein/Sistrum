import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/library_tag_credits.dart';

/// The credits rules from Tag separators in settings.
final tagCreditRulesProvider = Provider<TagCreditRules>(
  (ref) => TagCreditRules.fromSettings(
    ref.watch(settingsProvider.select((s) => s.tagCredits)),
  ),
);

/// The rules and index versions the library's credits table is built from.
final tagCreditScopeProvider = Provider<TagCreditScope>((ref) {
  final revision = ref.watch(localLibraryIndexRevisionProvider);
  final history = ref.watch(
    downloadHistoryProvider.select((state) => state.loadedIndexVersion),
  );
  return TagCreditScope(
    rules: ref.watch(tagCreditRulesProvider),
    version: '$revision:$history',
  );
});
