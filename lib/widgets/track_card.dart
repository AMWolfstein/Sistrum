import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/app_content_card.dart';
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
/// Screens provide [leading], [subtitle], [trailing], and an optional
/// [background] layer for download progress.
class TrackCard extends StatelessWidget {
  const TrackCard({
    super.key,
    required this.leading,
    required this.title,
    this.subtitle,
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

  /// Secondary line. A widget rather than a string because callers mix in
  /// clickable artist names, quality badges and status text. `Text` without
  /// a colour takes the row's supporting colour: `onSurfaceVariant`, or the
  /// content colour of a filled current or selected row, so leave text
  /// uncoloured rather than reading a colour from outside the row.
  final Widget? subtitle;

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
                      Text(
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
                if (trailing != null && !isSelectionMode) ...[
                  SizedBox(width: tokens.gapSm),
                  // Mornye rows never fill with a content colour and keep
                  // their ambient theme.
                  trailing!,
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

/// Grid counterpart of [TrackCard]: square artwork with overlays, then the
/// title and subtitle underneath.
class TrackGridCard extends StatelessWidget {
  const TrackGridCard({
    super.key,
    required this.cover,
    required this.title,
    this.subtitle,
    this.overlays = const [],
    this.onTap,
    this.onLongPress,
    this.onContextMenu,
    this.isSelected = false,
    this.semanticLabel,
  });

  /// Artwork, already sized to fill the square. Clipping is handled here.
  final Widget cover;

  final String title;

  /// Secondary line. A widget so cells can host a clickable artist name; plain
  /// `Text` children inherit the shared subtitle style.
  final Widget? subtitle;

  /// Progress rings, status badges and selection ticks stacked over the cover.
  final List<Widget> overlays;

  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// Desktop right-click, with the global pointer position.
  final ValueChanged<Offset>? onContextMenu;
  final bool isSelected;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = context.tokens;
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final borderRadius = tokens.borderRadiusThumb;

    return DesktopContextMenuRegion(
      onContextMenu: onContextMenu,
      child: _buildCell(context, tokens, theme, colorScheme, borderRadius),
    );
  }

  Widget _buildCell(
    BuildContext context,
    AppTokens tokens,
    ThemeData theme,
    ColorScheme colorScheme,
    BorderRadius borderRadius,
  ) {
    return Semantics(
      label: semanticLabel,
      button: onTap != null,
      selected: isSelected,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        borderRadius: borderRadius,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: 1,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  ClipRRect(borderRadius: borderRadius, child: cover),
                  if (isSelected)
                    DecoratedBox(
                      decoration: BoxDecoration(
                        borderRadius: borderRadius,
                        border: Border.all(
                          color: colorScheme.primary,
                          width: 2,
                        ),
                      ),
                    ),
                  ...overlays,
                ],
              ),
            ),
            SizedBox(height: tokens.gapXs + 2),
            Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
            if (subtitle != null)
              DefaultTextStyle.merge(
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelSmall!.copyWith(
                  color: colorScheme.onSurfaceVariant,
                ),
                child: subtitle!,
              ),
          ],
        ),
      ),
    );
  }
}

/// Compact play affordance for a track cover with a full-size touch target.
///
/// The visible circle intentionally stays smaller than its hit box so it does
/// not obscure the artwork while remaining accessible to touch users.
class TrackGridPlayButton extends StatelessWidget {
  const TrackGridPlayButton({
    super.key,
    required this.tooltip,
    required this.onPressed,
    this.icon = Icons.play_arrow,
    this.compact = false,
  });

  static const double visualDiameter = 36;
  static const double compactDiameter = 28;

  final String tooltip;
  final VoidCallback? onPressed;
  final IconData icon;

  /// A smaller circle, for a lesser action such as opening the details. The
  /// touch target stays the same.
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final tokens = context.tokens;
    final colorScheme = Theme.of(context).colorScheme;

    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      padding: EdgeInsets.zero,
      alignment: AlignmentDirectional.bottomEnd,
      style: IconButton.styleFrom(
        minimumSize: Size.square(tokens.minTouchTarget),
        padding: EdgeInsets.zero,
      ),
      icon: Container(
        width: compact ? compactDiameter : visualDiameter,
        height: compact ? compactDiameter : visualDiameter,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: colorScheme.primary,
          shape: BoxShape.circle,
        ),
        child: Icon(
          icon,
          size: compact ? 16 : 18,
          color: colorScheme.onPrimary,
        ),
      ),
    );
  }
}

/// Square artwork placeholder shared by every track row and grid cell.
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
