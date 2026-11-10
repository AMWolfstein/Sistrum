import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:mornye/widgets/mornye_glass_lens_fade.dart';
import 'package:mornye/theme/cover_palette.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/services/shell_navigation_service.dart';
import 'package:mornye/widgets/album_detail_header.dart';
import 'package:mornye/widgets/cached_cover_image.dart';
import 'package:mornye/widgets/sliver_layout_info_builder.dart';

/// Share the same artwork-derived dark surface while loading and after the
/// artist metadata arrives, including when the app itself uses light mode.
class MornyeArtistSurface extends StatefulWidget {
  const MornyeArtistSurface({
    super.key,
    required this.imageSource,
    required this.child,
    this.logoSource,
    this.neutralActions = false,
  });

  final String? imageSource;
  final String? logoSource;
  final Widget child;

  /// Album and playlist actions use white controls over the artwork tint.
  final bool neutralActions;

  @override
  State<MornyeArtistSurface> createState() => _MornyeArtistSurfaceState();
}

class _MornyeArtistSurfaceState extends State<MornyeArtistSurface> {
  // Page rebuilds reuse the derived themes while their inputs are unchanged.
  ThemeData? _baseTheme;
  TargetPlatform? _basePlatform;
  late ThemeData _darkTheme;
  late ThemeData _outerTheme;
  (Color, Color, Color, bool)? _innerKey;
  late ThemeData _innerTheme;

  @override
  void dispose() {
    ShellNavigationService.clearChromeBrightness(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // app accent; artist pages keep their artwork tint.
    final baseTheme = Theme.of(context);
    if (!identical(baseTheme, _baseTheme) ||
        defaultTargetPlatform != _basePlatform) {
      _baseTheme = baseTheme;
      _basePlatform = defaultTargetPlatform;
      _innerKey = null;
      _darkTheme = MornyeTheme.fromContext(
        context,
        brightness: Brightness.dark,
      );
      _outerTheme = _darkTheme.copyWith(
        colorScheme: _darkTheme.colorScheme.copyWith(primary: Colors.grey),
      );
    }
    final darkTheme = _darkTheme;
    return Theme(
      data: _outerTheme,
      child: CoverPaletteBuilder(
        imageSource: widget.imageSource,
        builder: (context, palette) {
          final source = widget.imageSource;
          // Album surfaces use only the source colour, which the player may
          // already have resolved without a scheme.
          final dominant = HSLColor.fromColor(
            source == null
                ? Colors.grey
                : (widget.neutralActions
                          ? CoverPalette.peekSourceColor(source)
                          : null) ??
                      CoverPalette.sourceColor(source, Brightness.dark) ??
                      Colors.grey,
          );
          // Averaging a portrait mixes its highlights and foliage into gray.
          // Artist pages use the extracted accent at a deeper tone instead;
          // album surfaces and monochrome artwork keep their neutral shading.
          final useAccent =
              !widget.neutralActions && dominant.saturation >= 0.08;
          final accent = HSLColor.fromColor(palette.primary);
          // Keep a faint artwork tint on artist pages; album pages use the
          // lighter neutral surface.
          final surface = useAccent
              ? accent
                    .withSaturation((accent.saturation * 0.6).clamp(0.0, 0.6))
                    .withLightness(0.08 + dominant.lightness * 0.03)
                    .toColor()
              : dominant
                    .withSaturation(
                      (dominant.saturation * 0.45).clamp(0.0, 0.22),
                    )
                    .withLightness(
                      widget.neutralActions
                          ? 0.24 + dominant.lightness * 0.20
                          : 0.05,
                    )
                    .toColor();
          final route = ModalRoute.of(context);
          if (route != null) {
            ShellNavigationService.setChromeBrightness(
              owner: this,
              route: route,
              brightness: Brightness.dark,
              surface: surface,
            );
          }
          return CoverPaletteBuilder(
            imageSource: widget.neutralActions ? null : widget.logoSource,
            builder: (context, _) {
              final logo = widget.logoSource;
              // Use the logo's original pixels, including white lettering,
              // without Material's pastel accent mapping.
              final logoColor = logo == null
                  ? null
                  : CoverPalette.sourceColor(logo, Brightness.dark);
              // Keep the artwork accent for colorful portraits. A neutral
              // portrait can acquire a blue Material accent; use white there,
              // while allowing an explicit logo color to take precedence.
              final primary = widget.neutralActions
                  ? Colors.white
                  : logoColor ?? (useAccent ? palette.primary : Colors.white);
              final primaryLuminance = primary.computeLuminance();
              final surfaceContrast =
                  (primaryLuminance + 0.05) /
                  (surface.computeLuminance() + 0.05);
              final onPrimary = widget.neutralActions
                  ? Colors.black
                  : logoColor == null && useAccent
                  ? palette.onPrimary
                  : surfaceContrast >= 4.5
                  ? surface
                  : primaryLuminance > 0.179
                  ? Colors.black
                  : Colors.white;
              final innerKey = (
                surface,
                primary,
                onPrimary,
                widget.neutralActions,
              );
              if (innerKey != _innerKey) {
                _innerKey = innerKey;
                _innerTheme = darkTheme.copyWith(
                  scaffoldBackgroundColor: surface,
                  colorScheme: darkTheme.colorScheme.copyWith(
                    primary: primary,
                    onPrimary: onPrimary,
                    onSurfaceVariant: widget.neutralActions
                        ? Colors.white70
                        : null,
                    surface: surface,
                    surfaceContainer: Color.alphaBlend(
                      Colors.white.withValues(alpha: 0.08),
                      surface,
                    ),
                    surfaceContainerHigh: Color.alphaBlend(
                      Colors.white.withValues(alpha: 0.12),
                      surface,
                    ),
                  ),
                );
              }
              final page = HeaderPalette(
                scheme: _innerTheme.colorScheme,
                child: widget.child,
              );
              return Theme(data: _innerTheme, child: page);
            },
          );
        },
      ),
    );
  }
}

/// Portrait and centered identity from Mornye's MobileArtistView. Actions stay
/// supplied by the screen so remote artists retain download/favorite behavior.
class MornyeArtistHeader extends StatelessWidget {
  const MornyeArtistHeader({
    super.key,
    required this.name,
    required this.artwork,
    required this.actions,
    required this.showTitle,
    this.toolbarActions = const [],
    this.listeners,
    this.logoUrl,
    this.badge,
  });

  final String name;
  final Widget artwork;
  final List<Widget> actions;

  /// Grouped in the glass capsule at the toolbar's trailing end.
  final List<Widget> toolbarActions;
  final bool showTitle;

  final String? listeners;
  final String? logoUrl;
  final Widget? badge;

  // A SliverMainAxisGroup clamps negative overlap, preventing this header from
  // receiving the overscroll used for its stretch animation.
  @override
  Widget build(BuildContext context) {
    final slivers = buildSlivers(context);
    return slivers.length == 1
        ? slivers.single
        : SliverMainAxisGroup(slivers: slivers);
  }

  /// The mobile header is two slivers so its toolbar stays pinned; use these
  /// directly in the scroll view. [build] wraps them in a group.
  List<Widget> buildSlivers(BuildContext context) =>
      _buildMobileSlivers(context);

  double _textHeight(
    BuildContext context,
    String text,
    TextStyle style,
    double maxWidth,
  ) {
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: DefaultTextStyle.of(context).style.merge(style),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      textAlign: TextAlign.center,
    )..layout(maxWidth: maxWidth);
    final height = painter.height;
    painter.dispose();
    return height;
  }

  /// The header's full height, the identity block's, and the listeners row's.
  ({double expanded, double identity, double listeners}) _mobileMetrics(
    BuildContext context,
    double width,
  ) {
    final textWidth = (width - 48).clamp(0.0, double.infinity);
    final nameHeight = _textHeight(
      context,
      name,
      TextStyle(fontSize: _nameSize, fontWeight: FontWeight.bold),
      textWidth,
    );
    final hasLogo = logoUrl?.trim().isNotEmpty == true;
    final identityHeight = hasLogo
        ? nameHeight.clamp(_logoSize.height, double.infinity)
        : nameHeight;
    final listenersHeight = listeners == null
        ? 0.0
        : 7 +
              _textHeight(
                context,
                listeners!,
                const TextStyle(fontSize: 13),
                textWidth,
              );
    final photoHeight = (width * 0.76).clamp(240.0, 340.0);
    final contentHeight =
        identityHeight + listenersHeight + (actions.isEmpty ? 0 : 94) + 32;
    return (
      expanded: photoHeight + contentHeight,
      identity: identityHeight,
      listeners: listenersHeight,
    );
  }

  /// The photo or video and the identity scroll away with the page, like the
  /// album header; only the toolbar stays pinned above them. Nothing here is
  /// rebuilt or repainted per scroll frame.
  List<Widget> _buildMobileSlivers(BuildContext context) {
    final surface = Theme.of(context).colorScheme.surface;
    final back = Padding(
      padding: const EdgeInsetsDirectional.only(start: 12),
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        widthFactor: 1,
        child: HeaderCircleButton(
          icon: Icons.arrow_back,
          tooltip: MaterialLocalizations.of(context).backButtonTooltip,
          onPressed: () => Navigator.pop(context),
        ),
      ),
    );
    return [
      SliverLayoutInfoBuilder(
        select: (constraints) => constraints.crossAxisExtent,
        builder: (context, width) {
          final metrics = _mobileMetrics(context, width);
          // The scroll offset at which the photo and identity have gone under
          // the toolbar: its buttons fade their glass out as they approach it.
          final collapseOffset =
              metrics.expanded -
              kToolbarHeight -
              MediaQuery.paddingOf(context).top;
          return SliverLayoutInfoBuilder(
            select: (constraints) => constraints.scrollOffset >= collapseOffset,
            builder: (context, collapsed) => MornyeOverlayToolbar(
              child: SliverAppBar(
                pinned: true,
                backgroundColor: collapsed ? surface : Colors.transparent,
                surfaceTintColor: Colors.transparent,
                scrolledUnderElevation: 0,
                leadingWidth: 64,
                leading: MornyeGlassLensFade(
                  collapseExtent: collapseOffset,
                  child: back,
                ),
                actions: toolbarActions.isEmpty
                    ? null
                    : [
                        MornyeGlassLensFade(
                          collapseExtent: collapseOffset,
                          child: MornyeActionCapsule(children: toolbarActions),
                        ),
                      ],
                actionsPadding: const EdgeInsetsDirectional.only(end: 12),
                title: AnimatedOpacity(
                  opacity: showTitle || collapsed ? 1 : 0,
                  duration: const Duration(milliseconds: 180),
                  child: Text(
                    name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ),
          );
        },
      ),
      SliverLayoutInfoBuilder(
        select: (constraints) => constraints.crossAxisExtent,
        builder: (context, width) {
          final metrics = _mobileMetrics(context, width);
          final collapseOffset =
              metrics.expanded -
              kToolbarHeight -
              MediaQuery.paddingOf(context).top;
          return SliverLayoutInfoBuilder(
            select: (constraints) => constraints.scrollOffset >= collapseOffset,
            builder: (context, scrolledAway) => SliverToBoxAdapter(
              child: SizedBox(
                key: const ValueKey('artist-header'),
                height: metrics.expanded,
                // A video stops once the header is under the toolbar.
                child: TickerMode(
                  enabled: !scrolledAway,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      MornyeStretchArtwork(
                        child: Stack(
                          fit: StackFit.expand,
                          children: [
                            ClipRect(child: RepaintBoundary(child: artwork)),
                            DecoratedBox(
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  begin: Alignment.topCenter,
                                  end: Alignment.bottomCenter,
                                  stops: const [0, 0.48, 0.82, 1],
                                  colors: [
                                    Colors.black.withValues(alpha: 0.15),
                                    surface.withValues(alpha: 0),
                                    surface.withValues(alpha: 0.55),
                                    surface,
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      Align(
                        alignment: Alignment.bottomCenter,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(24, 0, 24, 32),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              if (badge != null)
                                Padding(
                                  padding: const EdgeInsets.only(bottom: 8),
                                  child: badge,
                                ),
                              ConstrainedBox(
                                constraints: BoxConstraints(
                                  maxHeight: metrics.identity,
                                ),
                                child: _buildIdentity(context),
                              ),
                              if (listeners != null) ...[
                                const SizedBox(height: 7),
                                Text(
                                  listeners!,
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    fontSize: 13,
                                    color: Colors.white.withValues(alpha: 0.7),
                                  ),
                                ),
                              ],
                              if (actions.isNotEmpty) ...[
                                const SizedBox(height: 16),
                                SizedBox(
                                  height: 74,
                                  child: Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    spacing: 24,
                                    children: actions,
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    ];
  }

  static double get _nameSize => 30;
  static Size get _logoSize => const Size(320, 96);

  Widget _buildIdentity(BuildContext context) {
    final fallback = Text(
      name,
      textAlign: TextAlign.center,
      style: TextStyle(
        fontSize: _nameSize,
        fontWeight: FontWeight.bold,
        color: Colors.white,
      ),
    );
    final logoSize = _logoSize;
    final logo = logoUrl?.trim();
    if (logo == null || logo.isEmpty) return fallback;
    final pixelRatio = MediaQuery.devicePixelRatioOf(context);
    return Semantics(
      label: name,
      image: true,
      excludeSemantics: true,
      child: Image(
        image: ResizeImage(
          cachedCoverImageProvider(logo),
          width: (logoSize.width * pixelRatio).ceil(),
          height: (logoSize.height * pixelRatio).ceil(),
          policy: ResizeImagePolicy.fit,
        ),
        fit: BoxFit.contain,
        frameBuilder: (_, child, frame, _) => frame == null
            ? fallback
            : ConstrainedBox(
                constraints: BoxConstraints.loose(logoSize),
                child: child,
              ),
        errorBuilder: (_, _, _) => fallback,
      ),
    );
  }
}
