import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/app_content_card.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/queue_swipe.dart';
import 'package:sistrum/widgets/settings_group.dart';

/// How a [TrackCard] paints its container.
enum TrackCardStyle {
  /// Filled card surface. Used where rows are discrete objects the user acts
  /// on individually: the download queue, history, the local library.
  filled,

  /// Transparent container. Used inside a collection that already has its own
  /// header and reads as one continuous list: album, playlist and folder track
  /// lists.
  flat,
}

/// Marks a filled [TrackCard] as one row of a run of cards, so the Material
/// theme draws the run as an expressive connected list: 2dp apart, large
/// corners at the ends of the run and small corners between rows.
class TrackCardSegment extends InheritedWidget {
  const TrackCardSegment({
    super.key,
    required this.first,
    required this.last,
    required super.child,
  });

  /// Positions row [index] of a run of [count] rows.
  const TrackCardSegment.at({
    Key? key,
    required int index,
    required int count,
    required Widget child,
  }) : this(
         key: key,
         first: index == 0,
         last: index == count - 1,
         child: child,
       );

  final bool first;
  final bool last;

  // Mornye draws no connected run, so no segment is ever found.
  static TrackCardSegment? _of(BuildContext context) => null;

  /// Corners of the enclosing segment, or null outside a connected run.
  static BorderRadius? radiusOf(BuildContext context) {
    final segment = _of(context);
    if (segment == null) return null;
    return connectedRowRadius(
      context,
      first: segment.first,
      last: segment.last,
    );
  }

  /// Outer margin of the enclosing segment, or null outside a connected run.
  static EdgeInsets? marginOf(BuildContext context) {
    final segment = _of(context);
    if (segment == null) return null;
    final tokens = context.tokens;
    return EdgeInsets.fromLTRB(
      tokens.gapLg,
      segment.first ? tokens.gapXs : 1,
      tokens.gapLg,
      segment.last ? tokens.gapXs : 1,
    );
  }

  @override
  bool updateShouldNotify(TrackCardSegment oldWidget) =>
      first != oldWidget.first || last != oldWidget.last;
}

/// The one track row in the app.
///
/// A song row is: [leading] (track number or artwork), the title with an
/// Explicit chip beside it when [isExplicit], the artists line, then the play
/// circle ([onPlay]) and the song's [menu] (a `SongMenuButton`). Tapping the row
/// plays, so callers pass [onTap] as the play action.
///
/// Non-song rows keep their own [trailing] and [subtitle] text.
class TrackCard extends StatelessWidget {
  const TrackCard({
    super.key,
    required this.leading,
    required this.title,
    this.subtitle,
    this.isExplicit = false,
    this.onPlay,
    this.menu,
    this.trailing,
    this.background,
    this.style = TrackCardStyle.filled,
    this.isSelectionMode = false,
    this.isSelected = false,
    this.onTap,
    this.onLongPress,
    this.onContextMenu,
    this.margin,
    this.isCurrent = false,
    this.isPlaying = false,
    this.leadingArtwork = false,
    this.queueSwipe,
  });

  /// Track number, cover art, or anything else that identifies the row.
  /// `Text` without a colour takes the row's supporting colour, like
  /// [subtitle].
  final Widget leading;

  final String title;

  /// The artists line, a single line. A widget rather than a string because
  /// callers may link artist names. `Text` without a colour takes the row's
  /// supporting colour: `onSurfaceVariant`, or the content colour of a filled
  /// current or selected row, so leave text uncoloured rather than reading a
  /// colour from outside the row.
  final Widget? subtitle;

  /// Shows the Explicit chip beside the title.
  final bool isExplicit;

  /// The play circle's action, shown beside [menu]. Null hides the circle.
  /// It shows pause while [isPlaying].
  final VoidCallback? onPlay;

  /// The song's three-dots menu, placed last. Null hides it.
  final Widget? menu;

  /// Hidden while [isSelectionMode] is active, so the row cannot be acted on
  /// while the user is picking items. On a filled Material row it reads a
  /// scheme whose `primary`, `onSurface` and `onSurfaceVariant` are the
  /// fill's content colour, so build its colours inside it.
  final Widget? trailing;

  /// Painted behind the content, clipped to the card. Used for the queue's
  /// progress fill.
  final Widget? background;

  final TrackCardStyle style;
  final bool isSelectionMode;
  final bool isSelected;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// Desktop right-click, with the global pointer position. See
  /// [DesktopContextMenuRegion].
  final ValueChanged<Offset>? onContextMenu;

  /// Overrides the default outer margin. Prefer the default so track lists stay
  /// aligned with each other.
  final EdgeInsetsGeometry? margin;

  /// The row is the player's current track. Mornye keeps the row plain and
  /// shows the title in its accent.
  final bool isCurrent;

  /// The current track is playing, so the indicator moves.
  final bool isPlaying;

  /// [leading] is square artwork, which the Material style rounds toward a
  /// circle while the row is current.
  final bool leadingArtwork;

  /// A sideways swipe that plays the song next or adds it to the queue
  /// ([QueueSwipe]). Pass the same actions the row's menu offers, or null
  /// where the menu has none. Selection mode turns it off.
  final QueueSwipeActions? queueSwipe;

  @override
  Widget build(BuildContext context) => _buildCard(context);

  Widget _buildCard(BuildContext context) {
    final tokens = context.tokens;
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final filled = style == TrackCardStyle.filled;
    final borderRadius =
        (filled ? TrackCardSegment.radiusOf(context) : null) ??
        tokens.borderRadiusCover;
    final segmentMargin = filled ? TrackCardSegment.marginOf(context) : null;
    // The two styles carry their own outer margin so no call site has to
    // repeat it: flat rows sit tight inside a collection list, filled cards
    // get the list margin.
    final cardMargin =
        margin ??
        segmentMargin ??
        (style == TrackCardStyle.flat
            ? EdgeInsets.symmetric(horizontal: tokens.gapSm, vertical: 2)
            : EdgeInsets.symmetric(
                horizontal: tokens.gapLg,
                vertical: tokens.gapXs,
              ));
    final contentPadding = style == TrackCardStyle.flat
        ? EdgeInsetsDirectional.fromSTEB(
            tokens.gapMd,
            tokens.gapMd,
            6,
            tokens.gapMd,
          )
        : EdgeInsetsDirectional.all(tokens.gapMd);

    // A selected row takes the primary fill at reduced alpha; rest is clear.
    final cardColor = isSelected
        ? colorScheme.primaryContainer.withValues(alpha: 0.3)
        : Colors.transparent;
    // Text without a colour of its own in the leading slot or the subtitle
    // takes the row's supporting colour, so it stays readable on any fill.
    final supportingStyle = TextStyle(color: colorScheme.onSurfaceVariant);

    final card = AppContentCard(
      elevation: 0,
      mornyeSurface: false,
      margin: cardMargin,
      color: cardColor,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(borderRadius: borderRadius),
      onTap: onTap,
      onLongPress: onLongPress,
      child: Stack(
        children: [
          if (background != null) Positioned.fill(child: background!),
          // Inside the card, so the swipe's actions join the row's node.
          if (queueSwipe != null)
            const Positioned.fill(child: QueueSwipeSemantics()),
          Padding(
            padding: contentPadding,
            child: Row(
              children: [
                if (isSelectionMode) ...[
                  Semantics(
                    checked: isSelected,
                    label: isSelected
                        ? context.l10n.a11yDeselectTrack
                        : context.l10n.a11ySelectTrack,
                    child: AnimatedSelectionCheckbox(
                      selected: isSelected,
                      colorScheme: colorScheme,
                      size: 24,
                    ),
                  ),
                  SizedBox(width: tokens.gapMd),
                ],
                DefaultTextStyle.merge(style: supportingStyle, child: leading),
                SizedBox(width: tokens.gapMd),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Flexible(
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.bodyLarge?.copyWith(
                                fontWeight: FontWeight.w500,
                                // The accent as status: Mornye's only sign of
                                // the current track.
                                color: isCurrent ? colorScheme.primary : null,
                              ),
                            ),
                          ),
                          // The Explicit chip sits beside the title.
                          if (isExplicit) ...[
                            const SizedBox(width: 6),
                            const ExplicitChip(),
                          ],
                        ],
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        DefaultTextStyle.merge(
                          style: supportingStyle,
                          child: subtitle!,
                        ),
                      ],
                    ],
                  ),
                ),
                if (!isSelectionMode) ...[
                  if (onPlay != null) ...[
                    SizedBox(width: tokens.gapXs),
                    SongPlayButton(isPlaying: isPlaying, onPlay: onPlay),
                  ],
                  // Mornye rows never fill with a content colour and keep
                  // their ambient theme.
                  if (trailing != null) ...[
                    SizedBox(width: tokens.gapSm),
                    trailing!,
                  ],
                  ?menu,
                ],
              ],
            ),
          ),
        ],
      ),
    );
    // The swipe stays in the tree through selection mode, so the row keeps
    // its state when selection starts or ends.
    Widget swipe(Widget row) => queueSwipe == null
        ? row
        : QueueSwipe(
            actions: queueSwipe,
            enabled: !isSelectionMode,
            child: row,
          );
    return swipe(
      DesktopContextMenuRegion(
        onContextMenu: onContextMenu,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            card,
            Padding(
              padding: EdgeInsetsDirectional.only(
                start: tokens.coverList + 40,
                end: 20,
              ),
              child: const Divider(),
            ),
          ],
        ),
      ),
    );
  }
}

/// The Explicit marker beside a song's title: a small light chip with a bold
/// E. Its colours come from `onSurface`, so it reads in light and dark.
class ExplicitChip extends StatelessWidget {
  const ExplicitChip({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Semantics(
      label: context.l10n.explicitLabel,
      child: ExcludeSemantics(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          decoration: BoxDecoration(
            color: colorScheme.onSurface.withValues(alpha: 0.12),
            borderRadius: context.tokens.borderRadiusBadge,
          ),
          child: Text(
            'E',
            style: TextStyle(
              fontSize: context.tokens.badgeFontSize,
              fontWeight: FontWeight.w800,
              height: 1.4,
              color: colorScheme.onSurface,
            ),
          ),
        ),
      ),
    );
  }
}

/// The play circle of a song row: an outlined circle with a play triangle,
/// or a pause bar pair while [isPlaying]. A 32dp pointer target when [dense].
class SongPlayButton extends StatelessWidget {
  const SongPlayButton({
    super.key,
    required this.isPlaying,
    required this.onPlay,
    this.dense = false,
  });

  final bool isPlaying;
  final VoidCallback? onPlay;

  /// A 32dp pointer target for desktop table rows.
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return AppIconButton(
      tooltip: isPlaying ? context.l10n.actionPause : context.l10n.tooltipPlay,
      onPressed: onPlay,
      icon: AppIcon(
        isPlaying ? Icons.pause_circle_outline : Icons.play_circle_outline,
        color: colorScheme.primary,
        size: dense ? 24 : 28,
      ),
      style: IconButton.styleFrom(
        minimumSize: Size.square(dense ? 32 : context.tokens.minTouchTarget),
        padding: dense ? EdgeInsets.zero : null,
        tapTargetSize: dense ? MaterialTapTargetSize.shrinkWrap : null,
        backgroundColor: Colors.transparent,
      ),
    );
  }
}

/// Square artwork placeholder shared by every track row.
class TrackCoverPlaceholder extends StatelessWidget {
  const TrackCoverPlaceholder({super.key, this.size, this.borderRadius});

  /// Omit to fill the available space (grid cells).
  final double? size;

  final BorderRadius? borderRadius;

  @override
  Widget build(BuildContext context) {
    final tokens = context.tokens;
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest,
        borderRadius: borderRadius ?? tokens.borderRadiusThumb,
      ),
      child: Icon(
        Icons.music_note,
        color: colorScheme.onSurfaceVariant,
        size: size == null ? null : size! * 0.45,
      ),
    );
  }
}
