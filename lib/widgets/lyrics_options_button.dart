import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/lyrics_display_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/song_lyrics_offset_provider.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/lyrics_editor_sheet.dart';
import 'package:sistrum/widgets/lyrics_options_activity.dart';
import 'package:sistrum/widgets/lyrics_size_setting.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/player_lyrics_controls.dart';

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
  bool _hidden = false;
  bool _hovered = false;
  bool _focused = false;
  bool _canHide = false;
  Timer? _hideTimer;
  ValueListenable<(int, bool)>? _activity;
  ParsedLyrics? _lastLyrics;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final activity = LyricsOptionsActivity.maybeOf(context);
    if (identical(activity, _activity)) return;
    _activity?.removeListener(_reveal);
    _activity = activity;
    _activity?.addListener(_reveal);
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    _activity?.removeListener(_reveal);
    super.dispose();
  }

  void _scheduleHide() {
    _hideTimer?.cancel();
    if (!_canHide ||
        _hidden ||
        _open ||
        _hovered ||
        _focused ||
        _activity?.value.$2 == true) {
      return;
    }
    _hideTimer = Timer(const Duration(seconds: 5), () {
      if (!mounted || ModalRoute.of(context)?.isCurrent == false) return;
      setState(() => _hidden = true);
    });
  }

  void _reveal() {
    if (_hidden) setState(() => _hidden = false);
    _scheduleHide();
  }

  @override
  Widget build(BuildContext context) {
    // Rebuilds once offline romanization data has loaded.
    ref.watch(lyricsDisplayProvider);
    final settings = ref.watch(settingsProvider);
    // The button sits over lyric text, so it fades whether or not the
    // player's own controls stay. Accessible navigation keeps it unless
    // hiding was chosen there.
    final autoHide =
        !isAndroidTv &&
        (!MediaQuery.accessibleNavigationOf(context) ||
            settings.autoHideLyricsControlsWithAccessibility);
    return ListenableBuilder(
      listenable: widget.metadata,
      builder: (context, _) {
        final button = _buildButton(context);
        final canHide = autoHide && button != null && _activity != null;
        final lyrics = widget.metadata.lyrics;
        if (_canHide != canHide || !identical(_lastLyrics, lyrics)) {
          _canHide = canHide;
          _lastLyrics = lyrics;
          _hidden = false;
          _scheduleHide();
        }
        if (button == null) return const SizedBox.shrink();
        return Focus(
          onFocusChange: (focused) {
            _focused = focused;
            _reveal();
          },
          child: MouseRegion(
            onEnter: (_) {
              _hovered = true;
              _reveal();
            },
            onExit: (_) {
              _hovered = false;
              _scheduleHide();
            },
            child: PlayerLyricsControlsVisibility(
              hidden: _hidden,
              child: button,
            ),
          ),
        );
      },
    );
  }

  Widget? _buildButton(BuildContext context) {
    final metadata = widget.metadata;
    if (metadata.loading || metadata.lyrics.isEmpty) return null;
    final canEdit = canEditPlayerLyrics(metadata);
    final original = metadata.lyrics;
    // The offline romanization can be hidden too, though it is never saved.
    final pronunciation = ref
        .read(lyricsDisplayProvider.notifier)
        .shown(original)
        .lines
        .any((line) => line.romanization?.trim().isNotEmpty == true);
    final translation = lyricsHaveTranslation(original);
    // Synced lyrics can always be timed, for this song or every song.
    final timing = original.synced;
    final language = pronunciation || translation;
    if (!language && !timing) {
      return canEdit ? _editButton(context) : null;
    }
    final colorScheme = widget.colorScheme;
    final foreground = _open ? colorScheme.surface : colorScheme.onSurface;
    return Builder(
      builder: (buttonContext) => AppIconButton.filledTonal(
        // Without language choices the menu holds timing (and editing), so
        // a language glyph would mislead.
        key: ValueKey(language ? 'lyrics-language-options' : 'lyrics-options'),
        tooltip: language
            ? context.l10n.nowPlayingLyricsLanguageOptions
            : context.l10n.lyricsOffsetOptions,
        icon: language
            ? const AppIcon(Icons.translate_rounded)
            : const AppIcon.adaptive(Icons.tune_rounded),
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
          timing: timing,
        ),
      ),
    );
  }

  /// The button alone when editing is the only option, as for plain lyrics.
  Widget _editButton(BuildContext context) {
    final colorScheme = widget.colorScheme;
    final foreground = _open ? colorScheme.surface : colorScheme.onSurface;
    return AppIconButton.filledTonal(
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
    _reveal();
    widget.onOpenChanged?.call(open);
  }

  Future<void> _showMenu(
    BuildContext buttonContext, {
    required bool pronunciation,
    required bool translation,
    required bool canEdit,
    required bool timing,
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
      if (timing) ('timing', l10n.lyricsOffset, Icons.timer_outlined),
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
      // The timing sheet and the editor keep controls visible until they
      // close.
      if (action != 'edit' && action != 'timing') {
        _setOpen(false);
      }
    }
    if (action == 'timing') {
      try {
        final item = widget.metadata.item;
        if (mounted) {
          await showLyricsTimingSheet(
            context,
            songKey: songLyricsOffsetKeyOf(item),
            songTitle: item?.title,
          );
        }
      } finally {
        _setOpen(false);
      }
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
