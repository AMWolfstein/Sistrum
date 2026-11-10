import 'package:flutter/material.dart';
import 'package:mornye/theme/mornye_theme.dart';

/// Keeps touch-first tablets on the bottom navigation used by phones. A rail
const double navigationRailBreakpoint = 1440;

/// Material's expanded window class. Landscape tablets switch the Material
/// theme to a navigation rail and Mornye to compact, centered chrome; portrait
/// tablets stay below it.
const double expandedLayoutBreakpoint = 840;
const double defaultWideContentMaxWidth = 840;

bool useNavigationRailForWidth(double width) =>
    width >= navigationRailBreakpoint;

/// The Material theme follows Material's expanded window class.
bool useMaterialNavigationRailForWidth(double width) =>
    width >= expandedLayoutBreakpoint;

/// Explicit horizontal safe spacing for controls drawn in full-bleed detail
/// headers. Filled expressive circles need breathing room from the device edge.
double detailHeaderEdgeInset(BuildContext context) => 8;

/// pixels to make an unscaled phone density feel miniature, especially when
double adaptiveUiScaleForSize(Size size) {
  if (size.shortestSide < 600 || size.width >= navigationRailBreakpoint) {
    return 1;
  }
  return size.shortestSide >= 800 ? 1.2 : 1.1;
}

EdgeInsets _scaledInsets(EdgeInsets value, double divisor) => EdgeInsets.only(
  left: value.left / divisor,
  top: value.top / divisor,
  right: value.right / divisor,
  bottom: value.bottom / divisor,
);

/// Scales the complete app surface on tablets, including controls, dialogs,
/// navigation, spacing and hit targets. The child receives the correspondingly
/// smaller logical viewport so responsive layouts still reflow instead of
/// being cropped, while [FittedBox] maps that viewport back to the full screen.
class AdaptiveUiScaler extends StatelessWidget {
  const AdaptiveUiScaler({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final scale = adaptiveUiScaleForSize(mediaQuery.size);
    if (scale == 1) return child;

    final scaledSize = Size(
      mediaQuery.size.width / scale,
      mediaQuery.size.height / scale,
    );
    final scaledMediaQuery = mediaQuery.copyWith(
      size: scaledSize,
      // One inner logical pixel occupies [scale] outer logical pixels.
      // Advertising the effective density keeps decoded artwork sharp.
      devicePixelRatio: mediaQuery.devicePixelRatio * scale,
      padding: _scaledInsets(mediaQuery.padding, scale),
      viewPadding: _scaledInsets(mediaQuery.viewPadding, scale),
      viewInsets: _scaledInsets(mediaQuery.viewInsets, scale),
      systemGestureInsets: _scaledInsets(mediaQuery.systemGestureInsets, scale),
    );

    return SizedBox.expand(
      child: FittedBox(
        fit: BoxFit.fill,
        alignment: Alignment.topLeft,
        child: SizedBox.fromSize(
          size: scaledSize,
          child: MediaQuery(data: scaledMediaQuery, child: child),
        ),
      ),
    );
  }
}

/// Widest content span for a surface of [maxWidth]: lists, settings and
/// detail content stop at [contentMaxWidth], a comfortable reading width, and
/// center on wider tablet and landscape surfaces.
double adaptiveContentMaxWidth(
  double maxWidth, {
  double contentMaxWidth = defaultWideContentMaxWidth,
}) => maxWidth < contentMaxWidth ? maxWidth : contentMaxWidth;

/// Horizontal inset that centers content of [maxWidth] at
/// [adaptiveContentMaxWidth]; zero once it fits. Prefer this constraint-based
/// form when the widget may live inside an already clamped box (e.g. a bottom
/// sheet), where screen width would over-inset.
double wideInsetForWidth(
  double maxWidth, {
  double contentMaxWidth = defaultWideContentMaxWidth,
}) => maxWidth > contentMaxWidth ? (maxWidth - contentMaxWidth) / 2 : 0;

/// Horizontal inset that centers full-width list content on tablets/landscape;
/// zero on phones, so rows stop stretching across the whole screen. Mornye
/// instead of stretching across a wide window.
double wideListInset(BuildContext context, {double? contentMaxWidth}) =>
    wideInsetForWidth(
      MediaQuery.sizeOf(context).width,
      contentMaxWidth: contentMaxWidth ?? (defaultWideContentMaxWidth),
    );

/// whole pane, as M3 panes do, with the 24dp margin of expanded and larger
/// edge; elsewhere Home centers with [wideListInset].
double homeContentInset(BuildContext context) => wideListInset(context);

/// The M3 large window class starts at 1200dp; the extra 80dp leaves room
/// for the navigation rail, since the inset is measured on the window width.
const double materialContentMaxWidth = 1280;

/// Discography cover geometry shared by loaded cards and loading placeholders.
double artistAlbumTileSize(BuildContext context) {
  final scale = responsiveScale(context: context, min: 0.82, max: 1.05);
  final textScale = effectiveTextScale(context);
  return 140 * scale * (1 + (textScale - 1) * 0.12);
}

double artistAlbumSectionHeight(BuildContext context) =>
    artistAlbumTileSize(context) +
    64 +
    ((effectiveTextScale(context) - 1) * 14);

/// Search results trade phone rows for cover grids and shelves once their
/// content is this wide (Material's expanded window class).
const double searchCoverLayoutMinWidth = 840;

/// The smallest cover of a search result grid: a wider pane adds columns
/// rather than growing covers far past it.
const double searchCoverMinExtent = 160;

/// The cover side of a search result shelf.
const double searchCoverShelfExtent = 176;

/// The space between search result covers.
const double searchCoverGap = 16;

/// The height of a Mornye Top Results card.
const double searchTopResultHeight = 80;

/// Mornye Top Results cards across a pane [width] wide.
int searchTopResultColumns(double width) => width >= 840 ? 3 : 2;

/// The cover side of a search shelf in a pane [paneWidth] wide. Mornye sizes
/// covers to the pane with the next one peeking: a little over
/// four albums or five artists.
double searchShelfExtentFor(
  BuildContext context,
  double paneWidth, {
  required bool round,
}) {
  if (!context.isMornye) return searchCoverShelfExtent;
  final content = paneWidth - 2 * searchCoverEdge(context);
  final perPane = round ? 5.2 : 4.17;
  return ((content + searchCoverGap) / perPane - searchCoverGap).clamp(
    searchCoverMinExtent - 20,
    280.0,
  );
}

/// Columns of a search result grid [width] wide.
int searchCoverGridColumns(double width) =>
    ((width + searchCoverGap) / (searchCoverMinExtent + searchCoverGap))
        .floor()
        .clamp(2, 12);

/// The cover side of a search result grid [width] wide.
double searchCoverGridExtent(double width) {
  final columns = searchCoverGridColumns(width);
  return (width - searchCoverGap * (columns - 1)) / columns;
}

/// The title and subtitle under a search result cover, grown with the text
/// size so neither line clips.
double searchCoverTextHeight(BuildContext context) {
  final textTheme = Theme.of(context).textTheme;
  final scaler = MediaQuery.textScalerOf(context);
  double line(TextStyle? style, double fallback) =>
      scaler.scale(style?.fontSize ?? fallback) * (style?.height ?? 1.3);
  // The cover gap, both lines with the gap between them, and slack for
  // fonts whose line box runs past their nominal height.
  return 8 +
      line(textTheme.bodyMedium, 14) +
      2 +
      line(textTheme.bodySmall, 12) +
      4;
}

/// The side margin of search result grids and shelves: the section titles'
/// inset, so covers line up under them.
double searchCoverEdge(BuildContext context) =>
    (context.isMornye ? 20 : 16) + homeContentInset(context);

/// Scales fixed sizes with the shortest screen side, clamped to [min]..[max].
double responsiveScale({
  required BuildContext context,
  double min = 0.82,
  double max = 1.08,
  double baseShortestSide = 390,
}) {
  final shortestSide = MediaQuery.sizeOf(context).shortestSide;
  final scale = shortestSide / baseShortestSide;
  if (scale < min) return min;
  if (scale > max) return max;
  return scale;
}

/// The system text scale clamped to 1.0..1.4 for fixed-height layouts.
double effectiveTextScale(BuildContext context) {
  final textScale = MediaQuery.textScalerOf(context).scale(1.0);
  if (textScale < 1.0) return 1.0;
  if (textScale > 1.4) return 1.4;
  return textScale;
}
