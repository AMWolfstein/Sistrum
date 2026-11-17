import 'dart:async';

import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show HapticFeedback;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/lyrics_translation_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/lyrics_timeline.dart';
import 'package:sistrum/utils/synced_lyrics_scroll.dart';
import 'package:sistrum/widgets/player_lyrics.dart';

/// Single-line real-time lyric preview shown beneath track metadata on the
/// phone player. Tapping opens full synchronized lyrics.
class PlayerLyricPreview extends ConsumerStatefulWidget {
  const PlayerLyricPreview({
    super.key,
    required this.metadata,
    required this.seekPreview,
    required this.colorScheme,
    this.onTap,
  });

  final PlayerMetadataController metadata;
  final ValueListenable<Duration?> seekPreview;
  final ColorScheme colorScheme;
  final VoidCallback? onTap;

  @override
  ConsumerState<PlayerLyricPreview> createState() => _PlayerLyricPreviewState();
}

class _PlayerLyricPreviewState extends ConsumerState<PlayerLyricPreview> {
  ParsedLyrics? _lastLyrics;
  List<LyricLine> _lines = const [];
  Timer? _lineBoundaryTimer;
  final Stopwatch _elapsedClock = Stopwatch();
  Duration _anchorPosition = Duration.zero;
  bool _playing = false;
  ProviderSubscription<Duration>? _positionSub;
  ProviderSubscription<bool>? _playingSub;

  @override
  void initState() {
    super.initState();
    widget.seekPreview.addListener(_onSeekPreviewChanged);
    _positionSub = ref.listenManual<Duration>(playbackPositionProvider, (
      previous,
      next,
    ) {
      if (widget.seekPreview.value != null) return;
      _anchorPosition = next;
      _elapsedClock.reset();
      if (_playing) _elapsedClock.start();
      _scheduleNextLine();
      if (mounted) setState(() {});
    });
    _playingSub = ref.listenManual<bool>(playbackPlayingProvider, (
      previous,
      playing,
    ) {
      _playing = playing;
      if (playing) {
        _elapsedClock.start();
      } else {
        _elapsedClock.stop();
      }
      _scheduleNextLine();
      if (mounted) setState(() {});
    });
    _playing = _playingSub?.read() ?? false;
    _anchorPosition = _positionSub?.read() ?? Duration.zero;
    if (_playing) _elapsedClock.start();
  }

  @override
  void didUpdateWidget(covariant PlayerLyricPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.seekPreview != widget.seekPreview) {
      oldWidget.seekPreview.removeListener(_onSeekPreviewChanged);
      widget.seekPreview.addListener(_onSeekPreviewChanged);
    }
  }

  @override
  void dispose() {
    widget.seekPreview.removeListener(_onSeekPreviewChanged);
    _lineBoundaryTimer?.cancel();
    _positionSub?.close();
    _playingSub?.close();
    _elapsedClock.stop();
    super.dispose();
  }

  void _onSeekPreviewChanged() {
    if (!mounted) return;
    _lineBoundaryTimer?.cancel();
    _anchorPosition = widget.seekPreview.value ?? _anchorPosition;
    _elapsedClock.reset();
    _scheduleNextLine();
    setState(() {});
  }

  Duration get _displayPosition {
    if (widget.seekPreview.value != null) return widget.seekPreview.value!;
    if (!_playing) return _anchorPosition;
    return interpolatedSyncedLyricsPosition(
      anchorPosition: _anchorPosition,
      elapsedSinceAnchor: _elapsedClock.elapsed,
      isPlaying: _playing,
    );
  }

  void _scheduleNextLine() {
    _lineBoundaryTimer?.cancel();
    if (!mounted ||
        _lines.isEmpty ||
        !_playing ||
        widget.seekPreview.value != null) {
      return;
    }

    final pos = _displayPosition;
    final index = LyricsParser.activeIndex(_lines, pos);

    Duration? nextTrigger;
    if (index >= 0 && index < _lines.length) {
      final line = _lines[index];
      if (line.end != null && line.end! > pos) {
        nextTrigger = line.end;
      }
    }

    final nextIndex = index + 1;
    if (nextIndex < _lines.length) {
      final nextStart = _lines[nextIndex].time;
      // Slight 80ms lead time so the animation completes just as the first word sings.
      const leadTime = Duration(milliseconds: 80);
      final lineEarly = nextStart > leadTime
          ? nextStart - leadTime
          : Duration.zero;
      if (nextTrigger == null || lineEarly < nextTrigger) {
        nextTrigger = lineEarly;
      }
    }

    if (nextTrigger == null) return;
    final remaining = nextTrigger - pos;
    final delay = remaining.isNegative ? Duration.zero : remaining;
    _lineBoundaryTimer = Timer(delay, () {
      if (!mounted || !_playing) return;
      setState(() {});
      _scheduleNextLine();
    });
  }

  void _syncLines(ParsedLyrics lyrics) {
    if (identical(_lastLyrics, lyrics)) return;
    _lastLyrics = lyrics;
    if (!lyrics.synced || lyrics.lines.isEmpty) {
      _lines = const [];
    } else {
      _lines = lyricsWithDisplayEnds(lyricsTimelineWithGaps(lyrics.lines));
    }
    _scheduleNextLine();
  }

  @override
  Widget build(BuildContext context) {
    final enabled = ref.watch(
      settingsProvider.select((s) => s.playerShowLyricSnippet),
    );
    if (!enabled) return const SizedBox.shrink();

    return ListenableBuilder(
      listenable: widget.metadata,
      builder: (context, _) {
        if (widget.metadata.loading ||
            widget.metadata.lyrics.isEmpty ||
            !widget.metadata.lyrics.synced) {
          return const SizedBox.shrink();
        }

        final showTranslation = ref.watch(
          settingsProvider.select((s) => s.playerShowTranslation),
        );
        ref.watch(lyricsTranslationProvider.select((s) => s.results));
        final translation = ref.read(lyricsTranslationProvider.notifier);
        final effectiveLyrics = translation.display(widget.metadata.lyrics);

        _syncLines(effectiveLyrics);
        if (_lines.isEmpty) return const SizedBox.shrink();

        final Duration position = _displayPosition;
        final index = LyricsParser.activeIndex(_lines, position);

        LyricLine? activeLine;
        String currentText = '•••';
        if (index >= 0 && index < _lines.length) {
          final line = _lines[index];
          final translated =
              showTranslation &&
              line.translation != null &&
              line.translation!.trim().isNotEmpty;
          final candidate = translated
              ? line.translation!.trim()
              : line.text.trim();
          if (candidate.isNotEmpty) {
            final end = line.end;
            if (end == null || position <= end + const Duration(seconds: 2)) {
              currentText = candidate;
              // The displayed text and its word timing must come from the same
              // line, or a word-timed translation would show the original text.
              activeLine = translated
                  ? LyricLine(
                      time: line.time,
                      end: line.end,
                      text: candidate,
                      words: line.translationWords,
                    )
                  : line;
            }
          }
        }

        final ambient = Directionality.of(context);
        final lineDirection = lyricTextDirection(currentText, ambient);
        final rtl = lineDirection == TextDirection.rtl;
        final reduceMotion = MediaQuery.disableAnimationsOf(context);
        final textColor = widget.colorScheme.onSurface.withValues(alpha: 0.88);
        final iconColor = widget.colorScheme.onSurface.withValues(alpha: 0.55);

        final textStyle = TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w500,
          color: textColor,
          letterSpacing: -0.2,
        );

        Widget lyricWidget;
        final isWordTimed = activeLine != null && activeLine.hasWordTiming;

        if (currentText == '•••' || activeLine == null) {
          lyricWidget = Text(
            '•••',
            key: const ValueKey('•••'),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: textStyle.copyWith(
              letterSpacing: 2.0,
              color: textColor.withValues(alpha: 0.55),
            ),
          );
        } else if (isWordTimed) {
          lyricWidget = ClipRect(
            key: ValueKey('word-${activeLine.time.inMilliseconds}'),
            child: WordTimedLyricLine(
              line: activeLine,
              initialPosition: position,
              seekPreview: widget.seekPreview,
              style: textStyle,
              maxLines: 1,
            ),
          );
        } else {
          lyricWidget = Text(
            currentText,
            key: ValueKey(
              'line-${activeLine.time.inMilliseconds}-$currentText',
            ),
            textDirection: lineDirection,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: textStyle,
          );
        }

        return Padding(
          padding: const EdgeInsets.only(top: 6, bottom: 2),
          child: Semantics(
            button: widget.onTap != null,
            label: context.l10n.lyricPreviewLabel(currentText),
            child: InkWell(
              onTap: widget.onTap == null
                  ? null
                  : () {
                      HapticFeedback.selectionClick();
                      widget.onTap!();
                    },
              borderRadius: BorderRadius.circular(8),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 3),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // A right-to-left line sits at the trailing edge, beside the chevron.
                    rtl
                        ? Expanded(
                            child: Align(
                              alignment: Alignment.centerRight,
                              child: _switcher(lyricWidget, rtl, reduceMotion),
                            ),
                          )
                        : Flexible(
                            child: _switcher(lyricWidget, rtl, reduceMotion),
                          ),
                    const SizedBox(width: 4),
                    // Points forward: left under a right-to-left layout.
                    Icon(
                      ambient == TextDirection.rtl
                          ? CupertinoIcons.chevron_left
                          : CupertinoIcons.chevron_right,
                      size: 14,
                      color: iconColor,
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  /// Lines enter from the start of the reading direction: from below for LTR,
  /// from the right for RTL. Reduced motion swaps lines without a transition.
  Widget _switcher(Widget lyricWidget, bool rtl, bool reduceMotion) =>
      AnimatedSwitcher(
        duration: reduceMotion
            ? Duration.zero
            : const Duration(milliseconds: 180),
        switchInCurve: Curves.easeOutQuad,
        switchOutCurve: Curves.easeInQuad,
        layoutBuilder: (currentChild, previousChildren) => Stack(
          alignment: rtl ? Alignment.centerRight : Alignment.centerLeft,
          children: [...previousChildren, ?currentChild],
        ),
        transitionBuilder: (child, animation) => FadeTransition(
          opacity: animation,
          child: SlideTransition(
            position: Tween<Offset>(
              begin: rtl ? const Offset(0.2, 0) : const Offset(0, 0.2),
              end: Offset.zero,
            ).animate(animation),
            child: child,
          ),
        ),
        child: lyricWidget,
      );
}
