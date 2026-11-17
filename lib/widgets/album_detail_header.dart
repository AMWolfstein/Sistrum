import 'dart:math' as math;

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderProxySliver;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/widgets/mornye_glass_lens_fade.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_glass_press.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/track_lists/context_menu.dart';
import 'package:sistrum/widgets/sliver_layout_info_builder.dart';

/// Collapsing album-detail header shared by the album, playlist, mix and
/// library folder screens: full-bleed
/// [background], bottom gradient, centered square cover, title, optional
/// subtitle/meta/actions rows, and a circular back button. Content fades out
/// below 30% expansion; the [title] fades into the toolbar instead.
///
/// Colours come from the app's Mornye scheme and are published to descendants
/// through [HeaderPalette].
class AlbumDetailHeader extends StatelessWidget {
  const AlbumDetailHeader({
    super.key,
    required this.title,
    required this.showTitleInAppBar,
    this.showTitleListenable,
    required this.background,
    this.coverBuilder,
    this.subtitle,
    this.meta,
    this.actions,
    this.appBarActions,
    this.appBarTitle,
    this.leading,
    this.immersive = false,
    this.squareArtwork = true,
  });

  final String title;
  final bool showTitleInAppBar;

  /// Replaces [showTitleInAppBar] when set; see [headerToolbarTitle].
  final ValueListenable<bool>? showTitleListenable;

  /// Full-bleed background content (cover image, motion banner, placeholder).
  final Widget background;

  /// Builds the centered square cover for the given side length; null hides
  /// the cover block (the header wraps it in the shared shadow + rounding).
  final Widget Function(BuildContext context, double coverSize)? coverBuilder;

  /// Artist line under the title (plain text or a clickable artist widget).
  final Widget? subtitle;

  /// "•"-joined meta row under the subtitle.
  final Widget? meta;

  /// Action row (play/shuffle, love, add to playlist) under the meta row.
  final Widget? actions;

  /// Extra toolbar actions (top-right).
  final List<Widget>? appBarActions;

  /// Toolbar title when it should differ from [title] (e.g. a selection
  /// count); defaults to [title].
  final String? appBarTitle;

  /// Replaces the default circular back button.
  final Widget? leading;

  final bool immersive;

  /// Ordinary cover art stays 1:1; provider-supplied banners may fill a header.
  final bool squareArtwork;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return HeaderPalette(
      scheme: scheme,
      child: Builder(
        builder: (context) =>
            SliverMainAxisGroup(slivers: buildSlivers(context)),
      ),
    );
  }

  /// Keep the toolbar at the collection scroll-view level so it stays pinned
  /// after the dynamically sized artwork and metadata have scrolled away.
  List<Widget> buildSlivers(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final edgeInset = detailHeaderEdgeInset(context);
    final screenSize = MediaQuery.sizeOf(context);
    // Landscape tablets are short: the artwork also yields to the height so
    // the title and actions stay on the first screen.
    final artworkSize = math.min(
      screenSize.width.clamp(0.0, 440.0),
      math.max(240.0, screenSize.height * 0.55),
    );
    final details = _buildMornyeDetails(context);
    // Built once, so toolbar rebuilds keep the same glass button element.
    final back = Padding(
      padding: EdgeInsetsDirectional.only(start: edgeInset),
      child:
          leading ??
          HeaderCircleButton(
            icon: CupertinoIcons.back,
            tooltip: MaterialLocalizations.of(context).backButtonTooltip,
            onPressed: () => Navigator.pop(context),
          ),
    );
    // [fadeExtent] is the scroll offset at which the toolbar is a flat bar: the
    // glass of its buttons fades out as it approaches it.
    Widget fading(double? fadeExtent, Widget button) => fadeExtent == null
        ? button
        : MornyeGlassLensFade(collapseExtent: fadeExtent, child: button);
    Widget toolbar({bool collapsed = true, double? fadeExtent}) => SliverAppBar(
      pinned: true,
      backgroundColor: collapsed ? scheme.surface : Colors.transparent,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
      title: headerToolbarTitle(
        showTitleListenable,
        showTitleInAppBar,
        (showTitle) => AnimatedOpacity(
          opacity: showTitle || appBarTitle != null || (immersive && collapsed)
              ? 1
              : 0,
          duration: const Duration(milliseconds: 180),
          child: Text(
            appBarTitle ?? title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
          ),
        ),
      ),
      leadingWidth: kToolbarHeight + edgeInset,
      leading: fading(fadeExtent, back),
      actionsPadding: EdgeInsetsDirectional.only(end: edgeInset),
      actions: appBarActions == null
          ? null
          : [for (final action in appBarActions!) fading(fadeExtent, action)],
    );
    if (immersive) {
      final detailsTop = (artworkSize - 20).clamp(0.0, double.infinity);
      final artwork = Stack(
        fit: StackFit.expand,
        children: [
          background,
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                stops: const [0, 0.48, 1],
                colors: [
                  Colors.black.withValues(alpha: 0.12),
                  scheme.surface.withValues(alpha: 0),
                  scheme.surface,
                ],
              ),
            ),
          ),
        ],
      );
      return [
        // The toolbar paints above the header without reserving a second
        // status-bar inset. Metadata still has its natural, scrollable height.
        Builder(
          builder: (context) {
            final toolbarHeight =
                kToolbarHeight + MediaQuery.paddingOf(context).top;
            final collapseOffset = detailsTop - toolbarHeight;
            return SliverLayoutInfoBuilder(
              select: (constraints) =>
                  constraints.scrollOffset >= collapseOffset,
              builder: (context, collapsed) => MornyeOverlayToolbar(
                child: toolbar(
                  collapsed: collapsed,
                  fadeExtent: collapseOffset,
                ),
              ),
            );
          },
        ),
        SliverToBoxAdapter(
          child: Stack(
            alignment: Alignment.topCenter,
            children: [
              Positioned.fill(
                child: squareArtwork
                    ? Align(
                        alignment: Alignment.topCenter,
                        child: SizedBox.square(
                          dimension: artworkSize,
                          child: MornyeStretchArtwork(child: artwork),
                        ),
                      )
                    : MornyeStretchArtwork(child: artwork),
              ),
              Column(
                children: [
                  SizedBox(height: detailsTop),
                  details,
                ],
              ),
            ],
          ),
        ),
      ];
    }
    return [
      Builder(builder: (_) => toolbar()),
      SliverToBoxAdapter(child: details),
    ];
  }

  Widget _buildMornyeDetails(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(20, immersive ? 0 : 20, 20, 28),
    child: Column(
      children: [
        if (!immersive && coverBuilder != null)
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 280),
            child: AspectRatio(
              aspectRatio: 1,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.16),
                      blurRadius: 20,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: LayoutBuilder(
                    builder: (context, constraints) =>
                        coverBuilder!(context, constraints.maxWidth),
                  ),
                ),
              ),
            ),
          ),
        if (!immersive && coverBuilder != null) const SizedBox(height: 22),
        Text(
          title,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
            fontSize: 22,
            fontWeight: FontWeight.w700,
          ),
        ),
        if (subtitle != null) ...[const SizedBox(height: 5), subtitle!],
        if (meta != null) ...[const SizedBox(height: 5), meta!],
        if (actions != null) ...[
          SizedBox(height: immersive ? 16 : 24),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 460),
            child: actions!,
          ),
        ],
      ],
    ),
  );
}

/// Marks header buttons grouped in one glass capsule, where they drop their
/// own glass circle.
class HeaderActionGroup extends InheritedWidget {
  const HeaderActionGroup({super.key, required super.child});

  static bool of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<HeaderActionGroup>() != null;

  @override
  bool updateShouldNotify(HeaderActionGroup oldWidget) => false;
}

/// Mornye page actions grouped in one glass capsule.
class MornyeActionCapsule extends ConsumerWidget {
  const MornyeActionCapsule({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    // Level with the round back button beside it.
    const radius = 22.0;
    return Center(
      widthFactor: 1,
      child: MornyeGlassPress(
        radius: radius,
        child: MornyeGlass.navigation(
          radius: radius,
          tintColor: dark ? Colors.black : Colors.white,
          tintOpacity: dark ? 0.45 : 0.55,
          blurEnabled: ref.watch(mornyeBlurEnabledProvider),
          child: HeaderActionGroup(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 2),
              child: Row(mainAxisSize: MainAxisSize.min, children: children),
            ),
          ),
        ),
      ),
    );
  }
}

/// Pulling the page down past its top enlarges the artwork from its top edge
/// instead of stretching the layout. Shared by the album and artist headers.
class MornyeStretchArtwork extends StatelessWidget {
  const MornyeStretchArtwork({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    final position = Scrollable.of(context).position;
    return LayoutBuilder(
      builder: (context, constraints) => AnimatedBuilder(
        animation: position,
        child: RepaintBoundary(child: child),
        builder: (context, artwork) {
          final overscroll = position.hasContentDimensions
              ? (position.minScrollExtent - position.pixels).clamp(
                  0.0,
                  double.infinity,
                )
              : 0.0;
          final height = constraints.maxHeight;
          final scale = height > 0 ? 1 + overscroll / height : 1.0;
          // The viewport moves the details down during a pull. Keep the image
          // at the top and enlarge it without changing the scrollable layout.
          return Transform.translate(
            offset: Offset(0, -overscroll),
            child: Transform.scale(
              scale: scale,
              alignment: Alignment.topCenter,
              child: artwork,
            ),
          );
        },
      ),
    );
  }
}

/// Builds a collapsing toolbar title with [showTitle], or with [listenable]'s
/// value when one is given, so crossing the threshold rebuilds only the title.
Widget headerToolbarTitle(
  ValueListenable<bool>? listenable,
  bool showTitle,
  Widget Function(bool showTitle) builder,
) => listenable == null
    ? builder(showTitle)
    : ValueListenableBuilder<bool>(
        valueListenable: listenable,
        builder: (_, showTitle, _) => builder(showTitle),
      );

/// Keeps the pinned toolbar above the artwork without shifting the artwork or
/// metadata down by the toolbar's safe-area height.
class MornyeOverlayToolbar extends SingleChildRenderObjectWidget {
  const MornyeOverlayToolbar({super.key, required super.child});

  @override
  RenderProxySliver createRenderObject(BuildContext context) =>
      _RenderAlbumOverlayToolbar();
}

class _RenderAlbumOverlayToolbar extends RenderProxySliver {
  @override
  void performLayout() {
    super.performLayout();
    final childGeometry = geometry!;
    if (childGeometry.scrollOffsetCorrection != null) return;
    geometry = childGeometry.copyWith(
      scrollExtent: 0,
      layoutExtent: 0,
      cacheExtent: 0,
    );
  }
}

/// Primary "play" pill + translucent shuffle pill used by album-style headers and lists.
/// Colours follow the [HeaderPalette].
class AlbumPlayActions extends StatelessWidget {
  const AlbumPlayActions({
    super.key,
    required this.playLabel,
    required this.shuffleTooltip,
    required this.onPlay,
    required this.onShuffle,
    this.isPlaying = false,
    this.compact = false,
  });

  final String playLabel;
  final String shuffleTooltip;
  final VoidCallback onPlay;
  final VoidCallback onShuffle;
  final bool isPlaying;

  /// Play and shuffle pills sized to their labels, the shuffle translucent, for
  /// a row of header actions beside other buttons.
  final bool compact;

  @override
  Widget build(BuildContext context) {
    if (compact) {
      // Scales down rather than overflowing a narrow header, as in Arabic.
      return FittedBox(
        fit: BoxFit.scaleDown,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _MornyeHeaderButton(
              icon: isPlaying
                  ? CupertinoIcons.pause_fill
                  : CupertinoIcons.play_fill,
              label: playLabel,
              onPressed: onPlay,
              prominent: true,
              compact: true,
            ),
            const SizedBox(width: 8),
            _MornyeHeaderButton(
              icon: CupertinoIcons.shuffle,
              label: shuffleTooltip,
              onPressed: onShuffle,
              compact: true,
            ),
          ],
        ),
      );
    }
    return Row(
      children: [
        Expanded(
          child: HeaderFilledButton(
            icon: isPlaying
                ? CupertinoIcons.pause_fill
                : CupertinoIcons.play_fill,
            label: playLabel,
            onPressed: onPlay,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _MornyeHeaderButton(
            icon: CupertinoIcons.shuffle,
            label: shuffleTooltip,
            onPressed: onShuffle,
          ),
        ),
      ],
    );
  }
}

/// Primary action pill in a detail header (e.g. play all). Reads its
/// colours from [HeaderPalette] so it works on light and dark headers alike.
class HeaderFilledButton extends StatelessWidget {
  const HeaderFilledButton({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.tonal = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  final bool tonal;

  @override
  Widget build(BuildContext context) {
    return _MornyeHeaderButton(
      icon: mornyeIconFor(icon),
      label: label,
      onPressed: onPressed,
      prominent: !tonal,
    );
  }
}

/// Circular header icon button (add-to-playlist, love-all, ...). Sized to the
/// minimum touch target and tinted from the [HeaderPalette].
class HeaderCircleButton extends ConsumerWidget {
  const HeaderCircleButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.iconColor,
    this.tonal = false,
    this.buttonSize,
    this.iconSize = 22,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;

  /// Overrides the palette foreground, e.g. to mark an active "loved" state.
  final Color? iconColor;

  /// Uses the same translucent fill as the album's Play/Shuffle pills.
  final bool tonal;

  final double? buttonSize;
  final double iconSize;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = HeaderPalette.of(context);
    final buttonSize = this.buttonSize;
    final iconSize = this.iconSize;
    if (HeaderActionGroup.of(context)) {
      return IconButton(
        onPressed: onPressed,
        tooltip: tooltip,
        icon: AppIcon(mornyeIconFor(icon), size: iconSize),
        style: IconButton.styleFrom(
          minimumSize: Size.square(buttonSize ?? 44),
          foregroundColor: iconColor ?? scheme.onSurface,
        ),
      );
    }
    if (tonal) {
      return Tooltip(
        message: tooltip,
        child: SizedBox.square(
          dimension: math.max(buttonSize ?? 44, 48),
          child: Center(
            child: SizedBox.square(
              dimension: buttonSize ?? 44,
              child: MornyeGlassPress(
                radius: (buttonSize ?? 44) / 2,
                enabled: onPressed != null,
                child: CupertinoButton(
                  padding: EdgeInsets.zero,
                  minimumSize: Size.square(buttonSize ?? 44),
                  borderRadius: BorderRadius.circular((buttonSize ?? 48) / 2),
                  color: scheme.onSurface.withValues(
                    alpha: scheme.brightness == Brightness.dark ? 0.10 : 0.06,
                  ),
                  onPressed: onPressed,
                  child: AppIcon(
                    mornyeIconFor(icon),
                    size: iconSize,
                    color: onPressed == null
                        ? scheme.onSurfaceVariant
                        : iconColor ?? scheme.primary,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    }
    final blurEnabled = ref.watch(mornyeBlurEnabledProvider);
    final button = IconButton(
      onPressed: onPressed,
      tooltip: tooltip,
      icon: AppIcon(mornyeIconFor(icon), size: iconSize),
      style: IconButton.styleFrom(
        minimumSize: Size.square(buttonSize ?? 44),
        foregroundColor: iconColor ?? scheme.onSurface,
      ),
    );
    return Center(
      widthFactor: 1,
      child: MornyeGlassPress(
        radius: (buttonSize ?? 48) / 2,
        enabled: onPressed != null,
        child: MornyeGlass.navigation(
          radius: (buttonSize ?? 48) / 2,
          tintColor: scheme.brightness == Brightness.dark
              ? Colors.black
              : Colors.white,
          tintOpacity: scheme.brightness == Brightness.dark ? 0.45 : 0.55,
          blurEnabled: blurEnabled,
          child: button,
        ),
      ),
    );
  }
}

/// The page's More button, opening [groups] below itself.
class HeaderMoreButton extends StatelessWidget {
  const HeaderMoreButton({super.key, required this.groups});

  final List<List<ContextMenuAction>> Function() groups;

  @override
  Widget build(BuildContext context) => HeaderCircleButton(
    icon: CupertinoIcons.ellipsis,
    tooltip: MaterialLocalizations.of(context).showMenuTooltip,
    onPressed: () => showContextMenu(
      context,
      position:
          contextMenuPositionBelow(context) ??
          contextMenuOriginOf(context) ??
          Offset.zero,
      groups: groups(),
    ),
  );
}

class _MornyeHeaderButton extends StatelessWidget {
  const _MornyeHeaderButton({
    required this.icon,
    required this.label,
    required this.onPressed,
    this.prominent = false,
    this.compact = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onPressed;
  final bool prominent;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final scheme = HeaderPalette.of(context);
    final filled = prominent;
    final foreground = onPressed == null
        ? scheme.onSurfaceVariant
        : filled
        ? scheme.onPrimary
        : scheme.primary;
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: compact ? 44 : 50),
      child: MornyeGlassPress(
        radius: 28,
        enabled: onPressed != null,
        child: CupertinoButton(
          padding: compact
              ? const EdgeInsets.symmetric(horizontal: 14, vertical: 8)
              : const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          borderRadius: BorderRadius.circular(28),
          color: filled
              ? scheme.primary
              : scheme.onSurface.withValues(
                  alpha: scheme.brightness == Brightness.dark ? 0.10 : 0.06,
                ),
          onPressed: onPressed,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              AppIcon(icon, size: 20, color: foreground),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w600,
                    color: foreground,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// "•"-joined row of [HeaderMetaItem]s shown under the header subtitle
/// (track count, duration, quality, ...).
class HeaderMetaRow extends StatelessWidget {
  const HeaderMetaRow({super.key, required this.items});

  final List<Widget> items;

  @override
  Widget build(BuildContext context) {
    final scheme = HeaderPalette.of(context);
    final parts = <Widget>[];
    for (final item in items) {
      if (parts.isNotEmpty) {
        parts.add(
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            child: Text(
              '•',
              style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 12),
            ),
          ),
        );
      }
      parts.add(item);
    }

    return Wrap(
      alignment: WrapAlignment.center,
      crossAxisAlignment: WrapCrossAlignment.center,
      runSpacing: 4,
      children: parts,
    );
  }
}

/// Single meta label (optional leading icon) for [HeaderMetaRow].
class HeaderMetaItem extends StatelessWidget {
  const HeaderMetaItem(this.label, {super.key, this.icon});

  final String label;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final scheme = HeaderPalette.of(context);
    final textStyle = TextStyle(
      color: scheme.onSurfaceVariant,
      fontSize: 13,
      fontWeight: FontWeight.w500,
    );
    if (icon == null) return Text(label, style: textStyle);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        AppIcon(icon, size: 15, color: scheme.onSurface),
        const SizedBox(width: 4),
        Text(label, style: textStyle),
      ],
    );
  }
}
