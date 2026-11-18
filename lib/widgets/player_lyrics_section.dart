import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart' show HapticFeedback;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/lyrics_display_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/song_lyrics_offset_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/utils/text_direction.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/expressive_button.dart';
import 'package:sistrum/widgets/lyrics_editor_sheet.dart';
import 'package:sistrum/widgets/lyrics_options_activity.dart';
import 'package:sistrum/widgets/player_lyrics.dart';
import 'package:sistrum/widgets/lyrics_text_scale.dart';

/// The directions of plain lyrics' lines, worked out once for each lyrics.
final _plainLyricDirections =
    Expando<({List<TextDirection?> text, List<TextDirection?> lines})>();

class PlayerLyricsSection extends ConsumerWidget {
  const PlayerLyricsSection({
    super.key,
    required this.metadata,
    required this.seekPreview,
    required this.colorScheme,
    required this.isActive,
    this.animateWords = true,
    this.options,
    this.onShareLyrics,
  });

  final PlayerMetadataController metadata;
  final PlaybackSeekPreview seekPreview;
  final ColorScheme colorScheme;
  final bool isActive;
  final bool animateWords;
  final Widget? options;

  /// Long-pressing lyrics opens sharing, preselecting a timed line.
  final ValueChanged<LyricLine?>? onShareLyrics;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final visibility = ref.watch(
      settingsProvider.select(
        (settings) =>
            (settings.playerShowPronunciation, settings.playerShowTranslation),
      ),
    );
    // The offline romanization is keyed by these; a change shows another one.
    ref.watch(
      settingsProvider.select(
        (settings) => (
          settings.locale,
          settings.lyricsRomanize,
          settings.lyricsRomanizedMainLine,
        ),
      ),
    );
    ref.watch(lyricsDisplayProvider);
    final display = ref.read(lyricsDisplayProvider.notifier);
    return ListenableBuilder(
      listenable: metadata,
      builder: (context, _) => LyricsOptionsActivity(
        child: _buildLyricsSection(
          context,
          visibility,
          display.shown(metadata.lyrics),
        ),
      ),
    );
  }

  Widget _buildLyricsSection(
    BuildContext context,
    (bool, bool) visibility,
    ParsedLyrics lyrics,
  ) {
    // Center in the part the player's controls leave visible. Only these
    // states depend on it, so a lyric list does not rebuild as they fade.
    if (metadata.loading) {
      return Builder(
        builder: (context) => Padding(
          padding: EdgeInsets.only(bottom: LyricsCoveredBottom.of(context)),
          child: const Center(child: AppLoadingIndicator()),
        ),
      );
    }
    if (metadata.lyrics.isEmpty) {
      // Scrolls when a short screen leaves too little room above the
      // controls, so the search button stays reachable.
      return LayoutBuilder(
        builder: (_, constraints) {
          final covered = LyricsCoveredBottom.of(context);
          return SingleChildScrollView(
            padding: EdgeInsets.only(bottom: covered),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minWidth: constraints.maxWidth,
                minHeight: constraints.hasBoundedHeight
                    ? (constraints.maxHeight - covered).clamp(
                        0.0,
                        double.infinity,
                      )
                    : 0,
              ),
              child: Center(child: _noLyrics(context)),
            ),
          );
        },
      );
    }
    return _lyricsContent(context, visibility, lyrics);
  }

  Widget _noLyrics(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 16),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        AppIcon(
          Icons.lyrics_outlined,
          size: 40,
          color: colorScheme.onSurfaceVariant,
        ),
        const SizedBox(height: 12),
        Text(
          context.l10n.nowPlayingNoLyrics,
          style: Theme.of(
            context,
          ).textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant),
        ),
        if (canEditPlayerLyrics(metadata)) ...[
          const SizedBox(height: 8),
          ExpressiveButton(
            key: const ValueKey('lyrics-write'),
            text: true,
            onPressed: () => openLyricsEditor(context, metadata),
            icon: const Icon(Icons.edit_note),
            child: Text(context.l10n.lyricsEditWrite),
          ),
        ],
      ],
    ),
  );

  Widget _lyricsContent(
    BuildContext context,
    (bool, bool) visibility,
    ParsedLyrics lyrics,
  ) {
    if (metadata.lyrics.synced) {
      return Stack(
        fit: StackFit.expand,
        children: [
          SyncedLyricsView(
            lyrics: lyrics,
            seekPreview: seekPreview,
            credits: _lyricsCredits(),
            colorScheme: colorScheme,
            isActive: isActive,
            animateWords: animateWords,
            showPronunciation: visibility.$1,
            showTranslation: visibility.$2,
            onLongPressLine: onShareLyrics,
            songKey: songLyricsOffsetKeyOf(metadata.item),
          ),
          if (options != null)
            PositionedDirectional(
              end: context.isMornye ? mornyeLyricsContentInset : 24,
              bottom: 8,
              child: options!,
            ),
        ],
      );
    }
    final plain = SingleChildScrollView(
      // Leaves room for the options button over the end of the text.
      padding: EdgeInsets.fromLTRB(24, 16, 24, options == null ? 32 : 80),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          GestureDetector(
            onLongPress: onShareLyrics == null
                ? null
                : () {
                    HapticFeedback.mediumImpact();
                    onShareLyrics!(null);
                  },
            child: LyricsTextScale(
              child: _plainLyrics(context, visibility, lyrics),
            ),
          ),
          ?_lyricsCredits(),
        ],
      ),
    );
    if (options == null) return plain;
    return Stack(
      fit: StackFit.expand,
      children: [
        plain,
        PositionedDirectional(
          end: context.isMornye ? mornyeLyricsContentInset : 24,
          bottom: 8,
          child: options!,
        ),
      ],
    );
  }

  /// Lyrics without timing, with each line's pronunciation and translation
  /// under it while they are shown.
  Widget _plainLyrics(
    BuildContext context,
    (bool, bool) visibility,
    ParsedLyrics lyrics,
  ) {
    final style = Theme.of(context).textTheme.titleMedium?.copyWith(
      height: 1.6,
      color: colorScheme.onSurface,
    );
    String? shown(String? value, bool visible) =>
        visible && value?.trim().isNotEmpty == true ? value!.trim() : null;
    final hasSupplements = lyrics.lines.any(
      (line) =>
          shown(line.romanization, visibility.$1) != null ||
          shown(line.translation, visibility.$2) != null,
    );
    final ambientDirection = Directionality.of(context);
    final directions = _plainLyricDirections[lyrics] ??= (
      text: lyricLineDirections(lyrics.plainText.split('\n')),
      lines: lyricLineDirections(lyrics.lines.map((line) => line.text)),
    );
    if (!hasSupplements) {
      // Each line reads in its own direction. One text has one, so lyrics
      // mixing both directions get a text a line, laid out the same.
      final first = directions.text.firstOrNull;
      if (directions.text.every((direction) => direction == first)) {
        return Text(
          lyrics.plainText,
          textDirection: first ?? ambientDirection,
          style: style,
          textAlign: TextAlign.center,
        );
      }
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final (index, line) in lyrics.plainText.split('\n').indexed)
            Text(
              line,
              textDirection: directions.text[index] ?? ambientDirection,
              style: style,
              textAlign: TextAlign.center,
            ),
        ],
      );
    }
    final supplementStyle = Theme.of(context).textTheme.bodyLarge?.copyWith(
      height: 1.4,
      color: colorScheme.onSurfaceVariant,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final (index, line) in lyrics.lines.indexed)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  line.text,
                  textDirection: directions.lines[index] ?? ambientDirection,
                  style: style,
                  textAlign: TextAlign.center,
                ),
                for (final supplement in [
                  shown(line.romanization, visibility.$1),
                  shown(line.translation, visibility.$2),
                ])
                  if (supplement != null)
                    Text(
                      supplement,
                      textDirection:
                          strongTextDirection(supplement) ??
                          directions.lines[index] ??
                          ambientDirection,
                      style: supplementStyle,
                      textAlign: TextAlign.center,
                    ),
              ],
            ),
          ),
      ],
    );
  }

  LyricsCredits? _lyricsCredits() {
    final (:writers, :provider) = metadata.credits;
    if (writers == null && provider == null) return null;
    return LyricsCredits(writers: writers, provider: provider);
  }
}
