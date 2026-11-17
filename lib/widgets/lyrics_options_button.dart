import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/lyrics_editor_sheet.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';

/// Whether any line of [lyrics] carries a translation from its lyric file.
bool lyricsHaveTranslation(ParsedLyrics lyrics) =>
    lyrics.lines.any((line) => line.translation?.trim().isNotEmpty == true);

/// The player's lyrics menu: pronunciation and translation toggles for the
/// lines the lyric file already carries, plus editing. Hidden when it would
/// offer nothing.
class LyricsOptionsButton extends ConsumerStatefulWidget {
  const LyricsOptionsButton({
    super.key,
    required this.metadata,
    required this.colorScheme,
    this.onOpenChanged,
  });

  final PlayerMetadataController metadata;
  final ColorScheme colorScheme;

  /// Reports the menu opening and closing, so auto-hiding controls wait.
  final ValueChanged<bool>? onOpenChanged;

  @override
  ConsumerState<LyricsOptionsButton> createState() =>
      _LyricsOptionsButtonState();
}

class _LyricsOptionsButtonState extends ConsumerState<LyricsOptionsButton> {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    ref.watch(settingsProvider.select((s) => s.playerShowPronunciation));
    return ListenableBuilder(
      listenable: widget.metadata,
      builder: (context, _) => _buildButton(context) ?? const SizedBox.shrink(),
    );
  }

  Widget? _buildButton(BuildContext context) {
    final metadata = widget.metadata;
    if (metadata.loading || metadata.lyrics.isEmpty) return null;
    final canEdit = canEditPlayerLyrics(metadata);
    if (!metadata.lyrics.synced) return canEdit ? _editButton(context) : null;
    final lyrics = metadata.lyrics;
    final pronunciation = lyrics.lines.any(
      (line) => line.romanization?.trim().isNotEmpty == true,
    );
    final translation = lyricsHaveTranslation(lyrics);
    if (!pronunciation && !translation) {
      return canEdit ? _editButton(context) : null;
    }
    final colorScheme = widget.colorScheme;
    final foreground = _open ? colorScheme.surface : colorScheme.onSurface;
    return Builder(
      builder: (buttonContext) => IconButton.filledTonal(
        key: const ValueKey('lyrics-language-options'),
        tooltip: context.l10n.nowPlayingLyricsLanguageOptions,
        icon: const AppIcon(Icons.translate_rounded),
        style: IconButton.styleFrom(
          foregroundColor: foreground,
          backgroundColor: colorScheme.onSurface.withValues(
            alpha: _open ? 0.85 : 0.16,
          ),
        ),
        onPressed: () => _showMenu(
          buttonContext,
          pronunciation: pronunciation,
          translation: translation,
          canEdit: canEdit,
        ),
      ),
    );
  }

  /// The button alone when editing is the only option, as for plain lyrics.
  Widget _editButton(BuildContext context) {
    final colorScheme = widget.colorScheme;
    final foreground = _open ? colorScheme.surface : colorScheme.onSurface;
    return IconButton.filledTonal(
      key: const ValueKey('lyrics-edit'),
      tooltip: context.l10n.lyricsEditTitle,
      icon: const AppIcon.adaptive(Icons.edit_note),
      style: IconButton.styleFrom(
        foregroundColor: foreground,
        backgroundColor: colorScheme.onSurface.withValues(
          alpha: _open ? 0.85 : 0.16,
        ),
      ),
      onPressed: _edit,
    );
  }

  Future<void> _edit() async {
    _setOpen(true);
    try {
      await openLyricsEditor(context, widget.metadata);
    } finally {
      _setOpen(false);
    }
  }

  void _setOpen(bool open) {
    if (!mounted) return;
    setState(() => _open = open);
    widget.onOpenChanged?.call(open);
  }

  Future<void> _showMenu(
    BuildContext buttonContext, {
    required bool pronunciation,
    required bool translation,
    required bool canEdit,
  }) async {
    final settings = ref.read(settingsProvider);
    final l10n = context.l10n;
    final actions = <(String, String, IconData)>[
      if (pronunciation)
        (
          'pronunciation',
          settings.playerShowPronunciation
              ? l10n.nowPlayingHidePronunciation
              : l10n.nowPlayingShowPronunciation,
          settings.playerShowPronunciation
              ? Icons.voice_over_off_outlined
              : Icons.record_voice_over_outlined,
        ),
      if (translation)
        (
          'translation',
          settings.playerShowTranslation
              ? l10n.nowPlayingHideTranslation
              : l10n.nowPlayingShowTranslation,
          Icons.translate_rounded,
        ),
      if (canEdit) ('edit', l10n.lyricsEditTitle, Icons.edit_note),
    ];
    _setOpen(true);
    String? action;
    try {
      final anchor = mornyeMenuAnchor(buttonContext);
      var menuWidth = 240.0;
      for (final (_, label, _) in actions) {
        final painter = TextPainter(
          text: TextSpan(
            text: label,
            style: Theme.of(
              context,
            ).textTheme.bodyLarge?.copyWith(fontSize: 15),
          ),
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout();
        if (painter.width + 80 > menuWidth) menuWidth = painter.width + 80;
        painter.dispose();
      }
      action = await showMornyeContextMenu<String>(
        context: buttonContext,
        anchor: anchor,
        preferAbove: true,
        maxWidth: menuWidth,
        builder: (menuContext) => MornyeContextMenu(
          dense: true,
          groups: [
            [
              for (final (value, label, icon) in actions)
                MornyeMenuAction(
                  icon: icon,
                  label: label,
                  onPressed: () => Navigator.of(menuContext).pop(value),
                ),
            ],
          ],
        ),
      );
      if (!mounted) return;
      final notifier = ref.read(settingsProvider.notifier);
      switch (action) {
        case 'pronunciation':
          notifier.setPlayerShowPronunciation(
            !settings.playerShowPronunciation,
          );
        case 'translation':
          notifier.setPlayerShowTranslation(!settings.playerShowTranslation);
      }
    } finally {
      // The editor keeps controls visible until it closes.
      if (action != 'edit') _setOpen(false);
    }
    if (action == 'edit') {
      try {
        if (mounted) await openLyricsEditor(context, widget.metadata);
      } finally {
        _setOpen(false);
      }
    }
  }
}
