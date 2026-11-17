import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show HapticFeedback;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/lyrics_editor_sheet.dart';
import 'package:sistrum/widgets/player_lyrics.dart';
import 'package:sistrum/widgets/lyrics_text_scale.dart';

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
    return ListenableBuilder(
      listenable: metadata,
      builder: (context, _) =>
          _buildLyricsSection(context, visibility, metadata.lyrics),
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
      // controls, so the edit button stays reachable.
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
          TextButton.icon(
            key: const ValueKey('lyrics-write'),
            onPressed: () => openLyricsEditor(context, metadata),
            icon: const Icon(Icons.edit_note),
            label: Text(context.l10n.lyricsEditWrite),
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
          ),
          if (options != null)
            PositionedDirectional(
              start: mornyeLyricsContentInset,
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
              // Each line takes its own direction, so a bilingual song's
              // Arabic lines are not laid out like its Latin ones.
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final line in metadata.lyrics.plainText.split('\n'))
                    Text(
                      line.isEmpty ? ' ' : line,
                      textDirection: lyricTextDirection(
                        line,
                        Directionality.of(context),
                      ),
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        height: 1.6,
                        color: colorScheme.onSurface,
                      ),
                      textAlign: TextAlign.center,
                    ),
                ],
              ),
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
          start: mornyeLyricsContentInset,
          bottom: 8,
          child: options!,
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
