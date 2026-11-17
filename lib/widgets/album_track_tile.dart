import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/track_card.dart';

/// Album-style track row: a leading cover or track number, title/subtitle, and
/// a trailing play button (when [onPlay] is given) that's hidden once selection
/// mode is active.
///
/// A thin arrangement over [TrackCard]; the container, selection tick, padding
/// and typography all come from there.
class AlbumTrackTile extends StatelessWidget {
  const AlbumTrackTile({
    super.key,
    this.trackNumber,
    this.cover,
    required this.trackName,
    required this.subtitle,
    required this.isSelectionMode,
    required this.isSelected,
    required this.onToggleSelection,
    required this.onOpen,
    this.onEnterSelectionMode,
    this.onPlay,
    this.isPlaying = false,
    this.toggleOnLongPress = false,
  });

  final int? trackNumber;

  /// Shown in place of the track number, for library rows.
  final Widget? cover;
  final String trackName;
  final Widget subtitle;
  final bool isSelectionMode;
  final bool isSelected;
  final VoidCallback? onToggleSelection;
  final VoidCallback onOpen;
  final VoidCallback? onEnterSelectionMode;
  final VoidCallback? onPlay;

  /// The current track is playing: the button offers pause.
  final bool isPlaying;

  /// A long press toggles the row while selecting, like library rows always did.
  final bool toggleOnLongPress;

  @override
  Widget build(BuildContext context) {
    return TrackCard(
      style: TrackCardStyle.flat,
      isSelectionMode: isSelectionMode,
      isSelected: isSelected,
      onTap: isSelectionMode ? onToggleSelection : onOpen,
      onLongPress: isSelectionMode
          ? (toggleOnLongPress ? onToggleSelection : null)
          : onEnterSelectionMode,
      leading:
          cover ??
          SizedBox(
            width: 24,
            // Takes the row's supporting colour, which follows a current fill.
            child: Text(
              trackNumber?.toString() ?? '-',
              style: const TextStyle(fontWeight: FontWeight.w500),
              textAlign: TextAlign.center,
            ),
          ),
      title: trackName,
      subtitle: subtitle,
      trailing: onPlay == null
          ? null
          : AlbumTrackPlayButton(isPlaying: isPlaying, onPlay: onPlay),
    );
  }
}

/// The play or pause button at the end of an album track row.
class AlbumTrackPlayButton extends StatelessWidget {
  const AlbumTrackPlayButton({
    super.key,
    required this.isPlaying,
    required this.onPlay,
  });

  final bool isPlaying;
  final VoidCallback? onPlay;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: isPlaying ? context.l10n.actionPause : context.l10n.tooltipPlay,
      onPressed: onPlay,
      icon: Icon(
        (isPlaying ? CupertinoIcons.pause_fill : CupertinoIcons.play_fill),
        color: colorScheme.primary,
      ),
      style: IconButton.styleFrom(
        minimumSize: Size.square(context.tokens.minTouchTarget),
        backgroundColor: Colors.transparent,
      ),
    );
  }
}
