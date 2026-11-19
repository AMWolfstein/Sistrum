import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// What an overlay is, which decides how its glass is drawn.
enum MornyeOverlayKind {
  /// A small floating menu: it reads the backdrop through the glass lens.
  menu,

  /// A dialog: it reads the backdrop through the glass lens.
  dialog,

  /// A sheet that covers much of the screen: it keeps its tint and reads no
  /// backdrop, because a lens that large re-reads the page behind it on every
  /// frame and the Clear blur is too small to show.
  sheet,

  /// A transient message, as thin as a snackbar.
  message,

  /// A bar that rides over scrolling content.
  bar,
}

/// The dark, cover-tinted theme the player's menus wear: the dominant colour of
/// the playing cover, with its saturation capped and its lightness fixed.
///
/// Without [forceDark] it only applies on a dark theme that has no artwork
/// colour of its own and while a cover colour is known, so a light overlay, a
/// page with its own colour, or no playing cover keep their material.
/// [imageSource] defaults to the cover of the track that is playing.
class MornyeCoverTheme extends ConsumerWidget {
  const MornyeCoverTheme({
    super.key,
    required this.child,
    this.imageSource,
    this.forceDark = false,
  });

  final Widget child;

  /// A cover string in any form [CoverSourceColorBuilder] reads.
  final String? imageSource;

  /// Wear the dark theme whatever the surrounding theme is.
  final bool forceDark;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ambient = Theme.of(context);
    // A page that already has an artwork colour keeps it, and a light overlay
    // keeps its light material.
    if (!forceDark &&
        (ambient.brightness != Brightness.dark ||
            ambient.extension<MornyeTheme>()?.chromeSurface != null)) {
      return child;
    }
    final source =
        imageSource ??
        ref.watch(
          currentMediaItemProvider.select((s) => s.value?.artUri?.toString()),
        );
    return CoverSourceColorBuilder(
      imageSource: source,
      builder: (context, sourceColor) {
        // Without a cover colour the overlay keeps its neutral material.
        if (!forceDark && sourceColor == null) return child;
        final dominant = HSLColor.fromColor(
          sourceColor ?? const Color(0xff9e9e9e),
        );
        final surface = dominant
            .withSaturation(dominant.saturation.clamp(0.0, 0.34))
            .withLightness(0.28)
            .toColor();
        return Theme(
          data: MornyeTheme.fromContext(
            context,
            brightness: Brightness.dark,
            chromeSurface: surface,
          ),
          child: child,
        );
      },
    );
  }
}

/// The one surface of Mornye's menus, dialogs, sheets, messages and bars: the
/// cover-tinted theme of the player's menus, and one rule for when the glass
/// reads the backdrop ([MornyeOverlayKind]). The content sits in its own
/// repaint layer, so a repaint of the glass does not repaint the content.
class MornyeOverlaySurface extends StatelessWidget {
  const MornyeOverlaySurface({
    super.key,
    required this.child,
    this.kind = MornyeOverlayKind.dialog,
    this.radius = 32,
    this.tintOpacity = 0.78,
    this.tintColor,
    this.smoothCorners = false,
    this.highlightBorder = false,
    this.highlightIntensity = 1,
    this.forceDark = false,
  });

  final Widget child;
  final MornyeOverlayKind kind;
  final double radius;
  final double tintOpacity;
  final Color? tintColor;
  final bool smoothCorners;
  final bool highlightBorder;
  final double highlightIntensity;

  /// Wear the dark cover theme whatever the surrounding theme is, for overlays
  /// opened from the player.
  final bool forceDark;

  @override
  Widget build(BuildContext context) {
    return MornyeCoverTheme(
      forceDark: forceDark,
      child: MornyeGlassPanel.overlay(
        radius: radius,
        tintOpacity: tintOpacity,
        tintColor: tintColor,
        smoothCorners: smoothCorners,
        highlightBorder: highlightBorder,
        highlightIntensity: highlightIntensity,
        samplesBackdrop: kind != MornyeOverlayKind.sheet,
        child: RepaintBoundary(child: child),
      ),
    );
  }
}
