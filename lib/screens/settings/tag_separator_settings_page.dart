import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/tag_credit_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_choice_chip.dart';
import 'package:sistrum/widgets/app_content_card.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/settings_group.dart';

/// Tag separators: one row per section (Artists, Genre) opens its chip
/// sheet; Keep together lists names that are never split.
class TagSeparatorSettingsPage extends ConsumerWidget {
  const TagSeparatorSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final credits = ref.watch(settingsProvider.select((s) => s.tagCredits));
    final l10n = context.l10n;
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          AppSliverHeader.page(title: l10n.tagSeparators),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
              child: Text(l10n.tagSeparatorsDescription),
            ),
          ),
          SliverToBoxAdapter(
            child: SettingsGroup(
              children: [
                SettingsItem(
                  title: l10n.tagSeparatorsArtists,
                  subtitle: _summary(credits.artistDelimiters),
                  onTap: () => _openSection(context, artists: true),
                ),
                SettingsItem(
                  title: l10n.tagSeparatorsGenre,
                  subtitle: _summary(credits.genreDelimiters),
                  onTap: () => _openSection(context, artists: false),
                ),
                SettingsItem(
                  title: l10n.tagKeepTogether,
                  subtitle: credits.keepTogether.isEmpty
                      ? null
                      : credits.keepTogether.join(' · '),
                  showDivider: false,
                  onTap: () => _openKeepTogether(context),
                ),
              ],
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 32)),
          const NavBarSliverSpacer(),
        ],
      ),
    );
  }

  static String _summary(List<String> delimiters) => delimiters.join('   ');

  Future<void> _openKeepTogether(BuildContext context) =>
      showAppBottomSheet<void>(
        context: context,
        useRootNavigator: true,
        title: context.l10n.tagKeepTogether,
        builder: (_) => const _KeepTogetherSheet(),
      );

  Future<void> _openSection(BuildContext context, {required bool artists}) {
    final l10n = context.l10n;
    return showAppBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      title: artists ? l10n.tagSeparatorsArtists : l10n.tagSeparatorsGenre,
      subtitle: artists ? l10n.tagSeparatorsArtistsFields : null,
      builder: (_) => _DelimiterSheet(artists: artists),
    );
  }
}

/// One section's sheet: the chip grid, applied as it is tapped, and Add
/// separator, which Save adds (selected) before closing the sheet.
class _DelimiterSheet extends ConsumerStatefulWidget {
  const _DelimiterSheet({required this.artists});

  /// The Artists section; otherwise Genre.
  final bool artists;

  @override
  ConsumerState<_DelimiterSheet> createState() => _DelimiterSheetState();
}

class _DelimiterSheetState extends ConsumerState<_DelimiterSheet> {
  final _field = TextEditingController();

  @override
  void dispose() {
    _field.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final credits = ref.watch(settingsProvider.select((s) => s.tagCredits));
    final notifier = ref.read(settingsProvider.notifier);
    final artists = widget.artists;
    final choices = artists ? kArtistDelimiterChoices : kGenreDelimiterChoices;
    final custom = artists
        ? credits.customArtistDelimiters
        : credits.customGenreDelimiters;
    final selected = artists
        ? credits.artistDelimiters
        : credits.genreDelimiters;
    final offered = [...choices, ...custom];
    final token = _field.text.trim();
    final valid = TagCreditSettings.isValidDelimiter(token, offered);
    final duplicate = token.isNotEmpty && !valid && isSeparatorToken(token);
    final error = token.isEmpty || valid
        ? null
        : duplicate
        ? l10n.tagSeparatorsDuplicate
        : l10n.metadataSeparatorInvalid;

    void save() {
      if (token.isNotEmpty) {
        if (!valid) return;
        artists
            ? notifier.addArtistDelimiter(token)
            : notifier.addGenreDelimiter(token);
      }
      Navigator.of(context).pop();
    }

    Widget chip(String token, {required bool custom}) {
      final chip = AppChoiceChip(
        label: _ChipLabel(token),
        selected: selected.contains(token),
        onSelected: (_) => artists
            ? notifier.toggleArtistDelimiter(token)
            : notifier.toggleGenreDelimiter(token),
      );
      if (!custom) return chip;
      // A custom chip is removed by long press, after confirming.
      return GestureDetector(
        onLongPress: () => _confirmRemove(token),
        child: chip,
      );
    }

    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(0, 8, 0, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _ChipGrid(
              children: [
                AppChoiceChip(
                  label: _ChipLabel(l10n.tagSeparatorsAll),
                  selected: offered.every(selected.contains),
                  onSelected: (_) => artists
                      ? notifier.toggleAllArtistDelimiters()
                      : notifier.toggleAllGenreDelimiters(),
                ),
                for (final token in choices) chip(token, custom: false),
                for (final token in custom) chip(token, custom: true),
              ],
            ),
            const SizedBox(height: 16),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: AppContentCard(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        l10n.tagSeparatorsAdd,
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _field,
                        maxLength: 16,
                        textInputAction: TextInputAction.done,
                        decoration: InputDecoration(
                          hintText: l10n.tagSeparatorsAddHint,
                          errorText: error,
                        ),
                        onChanged: (_) => setState(() {}),
                        onSubmitted: (_) => save(),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: FilledButton(
                onPressed: error == null ? save : null,
                child: Text(l10n.dialogSave),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmRemove(String token) async {
    final l10n = context.l10n;
    final remove = await showAppDialog<bool>(
      context: context,
      builder: (context) => AppAlertDialog(
        title: Text(l10n.tagSeparatorsRemoveTitle),
        content: Text(l10n.tagSeparatorsRemoveMessage),
        actions: [
          AppDialogAction(
            isDefault: true,
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.dialogCancel),
          ),
          AppDialogAction(
            filled: true,
            isDestructive: true,
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.dialogRemove),
          ),
        ],
      ),
    );
    if (remove != true || !mounted) return;
    final notifier = ref.read(settingsProvider.notifier);
    widget.artists
        ? notifier.removeCustomArtistDelimiter(token)
        : notifier.removeCustomGenreDelimiter(token);
  }
}

/// Equal-width cells, four to a row, in the Filters sheet's chip style.
class _ChipGrid extends StatelessWidget {
  const _ChipGrid({required this.children});

  final List<Widget> children;

  static const _gap = 8.0;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16),
    child: LayoutBuilder(
      builder: (context, constraints) {
        final cell = (constraints.maxWidth - 3 * _gap) / 4;
        return Wrap(
          spacing: _gap,
          runSpacing: _gap,
          children: [
            for (final child in children) SizedBox(width: cell, child: child),
          ],
        );
      },
    ),
  );
}

/// Shrinks a long delimiter like `featuring` to fit its cell.
class _ChipLabel extends StatelessWidget {
  const _ChipLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) =>
      FittedBox(fit: BoxFit.scaleDown, child: Text(text, maxLines: 1));
}

/// Names that are never split: a list with remove buttons, and an add field.
/// Changes apply as they are made, like the chips.
class _KeepTogetherSheet extends ConsumerStatefulWidget {
  const _KeepTogetherSheet();

  @override
  ConsumerState<_KeepTogetherSheet> createState() => _KeepTogetherSheetState();
}

class _KeepTogetherSheetState extends ConsumerState<_KeepTogetherSheet> {
  final _field = TextEditingController();

  @override
  void dispose() {
    _field.dispose();
    super.dispose();
  }

  bool _duplicate(List<String> names, String name) {
    final key = name.toLowerCase();
    return names.any((n) => n.toLowerCase() == key);
  }

  void _submit(List<String> names) {
    final name = _field.text.trim();
    if (name.isEmpty || _duplicate(names, name)) return;
    ref.read(settingsProvider.notifier).addKeepTogether(name);
    _field.clear();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final names = ref.watch(
      settingsProvider.select((s) => s.tagCredits.keepTogether),
    );
    final notifier = ref.read(settingsProvider.notifier);
    final typed = _field.text.trim();
    final duplicate = typed.isNotEmpty && _duplicate(names, typed);
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(0, 8, 0, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 12),
              child: Text(
                l10n.tagKeepTogetherDescription,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
            if (names.isNotEmpty)
              SettingsGroup(
                children: [
                  for (var i = 0; i < names.length; i++)
                    SettingsItem(
                      title: names[i],
                      trailing: IconButton(
                        icon: const Icon(Icons.close),
                        tooltip: l10n.tagKeepTogetherRemoveTooltip,
                        onPressed: () => notifier.removeKeepTogether(names[i]),
                      ),
                      showDivider: i != names.length - 1,
                    ),
                ],
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: TextField(
                controller: _field,
                textInputAction: TextInputAction.done,
                onChanged: (_) => setState(() {}),
                onSubmitted: (_) => _submit(names),
                decoration: InputDecoration(
                  hintText: l10n.tagKeepTogetherHint,
                  errorText: duplicate ? l10n.tagKeepTogetherDuplicate : null,
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.add),
                    tooltip: l10n.tagKeepTogetherAdd,
                    onPressed: duplicate || typed.isEmpty
                        ? null
                        : () => _submit(names),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
