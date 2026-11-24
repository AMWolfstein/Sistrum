import 'package:flutter/gestures.dart' show kPrimaryButton;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// A play button that appears over artwork while the pointer is on it, on
/// desktop, over the [DesktopCoverHover] feedback. Place it in the
/// artwork's [Stack]; it fills the stack to track the hover and shows the
/// button in the bottom-leading corner. Without [onPlay] only the hover
/// feedback shows. Phones and tablets get nothing: their tiles keep the tap
/// to open.
class DesktopHoverPlayButton extends StatelessWidget {
  const DesktopHoverPlayButton({
    super.key,
    required this.tooltip,
    required this.onPlay,
  });

  final String tooltip;
  final VoidCallback? onPlay;

  @override
  Widget build(BuildContext context) =>
      DesktopCoverHover(playTooltip: tooltip, onPlay: onPlay);
}

/// Hover feedback for a clickable cover on desktop: the click cursor, an 8%
/// dimming scrim (a little darker while pressed), and a Play button in the bottom-leading corner when [onPlay] is set.
/// Place it in the cover's [Stack], after the artwork; it fills the stack and
/// lets clicks through to the tile. [borderRadius] matches the cover's
/// corners, by default those of the Library grid tiles. Phones and tablets
/// get nothing.
class DesktopCoverHover extends StatefulWidget {
  const DesktopCoverHover({
    super.key,
    this.borderRadius,
    this.playTooltip,
    this.onPlay,
  }) : assert(onPlay == null || playTooltip != null);

  final BorderRadius? borderRadius;
  final String? playTooltip;
  final VoidCallback? onPlay;

  /// Visible circle; the pointer target is [_target].
  static const double _mornyeDiameter = 30;
  static const double _target = 40;

  /// Dims the artwork behind the Mornye button, a little more while pressed.
  static const Color _mornyeScrim = Color(0x4D333333);
  static const Color _mornyePressedScrim = Color(0x66333333);

  /// Corner radius of the Library grid tiles.
  static const double _mornyeRadius = 8;

  @override
  State<DesktopCoverHover> createState() => _DesktopCoverHoverState();
}

class _DesktopCoverHoverState extends State<DesktopCoverHover> {
  bool _hovered = false;
  bool _focused = false;
  bool _pressed = false;

  /// The Play button is built once the pointer or focus first reaches it,
  /// then stays so it can fade out. Until then a bare focus stop holds its
  /// place in the Tab order.
  bool _buttonBuilt = false;
  final _buttonFocus = FocusNode();

  @override
  void dispose() {
    _buttonFocus.dispose();
    super.dispose();
  }

  void _setPressed(bool pressed) {
    if (_pressed != pressed) setState(() => _pressed = pressed);
  }

  void _buildButtonForFocus(bool focused) {
    if (!focused || _buttonBuilt) return;
    setState(() => _buttonBuilt = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _buttonFocus.requestFocus();
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!isDesktopPlatform) return const SizedBox.shrink();
    final visible = _hovered || _focused;
    final pressed = _pressed && _hovered;
    final radius =
        widget.borderRadius ??
        BorderRadius.circular(DesktopCoverHover._mornyeRadius);
    // Hover feedback stays quick: the fast effects spring of the style.
    final fade = AppMotion.effectsFast(
      context,
      const AppMotionSpec(Duration(milliseconds: 100), Curves.easeOut),
    );
    final Widget layer = AnimatedOpacity(
      key: const ValueKey('desktop-hover-scrim'),
      opacity: visible ? 1 : 0,
      duration: fade.duration,
      curve: fade.curve,
      child: AnimatedContainer(
        duration: fade.duration,
        curve: fade.curve,
        decoration: BoxDecoration(
          color: pressed
              ? DesktopCoverHover._mornyePressedScrim
              : DesktopCoverHover._mornyeScrim,
          borderRadius: radius,
        ),
      ),
    );
    return Positioned.fill(
      child: MouseRegion(
        opaque: false,
        cursor: SystemMouseCursors.click,
        hitTestBehavior: HitTestBehavior.translucent,
        onEnter: (_) => setState(() {
          _hovered = true;
          _buttonBuilt = true;
        }),
        onExit: (_) => setState(() {
          _hovered = false;
          _pressed = false;
        }),
        // A Listener sees the press without joining the gesture arena, so
        // the tile underneath keeps its tap, long press and context menu.
        child: Listener(
          behavior: HitTestBehavior.translucent,
          onPointerDown: (event) =>
              _setPressed(event.buttons & kPrimaryButton != 0),
          onPointerUp: (_) => _setPressed(false),
          onPointerCancel: (_) => _setPressed(false),
          child: Stack(
            fit: StackFit.expand,
            children: [
              IgnorePointer(child: layer),
              if (widget.onPlay != null) _playButton(context, visible),
            ],
          ),
        ),
      ),
    );
  }

  Widget _playButton(BuildContext context, bool visible) {
    final fade = AppMotion.effectsFast(
      context,
      const AppMotionSpec(Duration(milliseconds: 120), Curves.linear),
    );
    return Align(
      alignment: AlignmentDirectional.bottomStart,
      child: Padding(
        // The visible circle sits 10 from the Mornye artwork's corner; the
        // target around it is larger.
        padding: const EdgeInsets.all(5),
        child: AnimatedOpacity(
          opacity: visible ? 1 : 0,
          duration: fade.duration,
          curve: fade.curve,
          child: IgnorePointer(
            ignoring: !visible,
            child: Focus(
              canRequestFocus: false,
              onFocusChange: (focused) => setState(() => _focused = focused),
              child: !_buttonBuilt
                  ? Focus(
                      onFocusChange: _buildButtonForFocus,
                      child: const SizedBox.square(
                        dimension: DesktopCoverHover._target,
                      ),
                    )
                  : IconButton(
                      focusNode: _buttonFocus,
                      tooltip: widget.playTooltip,
                      onPressed: widget.onPlay,
                      padding: EdgeInsets.zero,
                      style: IconButton.styleFrom(
                        fixedSize: const Size.square(DesktopCoverHover._target),
                        minimumSize: const Size.square(
                          DesktopCoverHover._target,
                        ),
                        shape: const CircleBorder(),
                        backgroundColor: Colors.transparent,
                      ),
                      icon: Container(
                        width: DesktopCoverHover._mornyeDiameter,
                        height: DesktopCoverHover._mornyeDiameter,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.92),
                          shape: BoxShape.circle,
                          boxShadow: const [
                            BoxShadow(blurRadius: 6, color: Color(0x33000000)),
                          ],
                        ),
                        child: Icon(
                          mornyeIconFor(Icons.play_arrow),
                          size: 16,
                          color: Colors.black,
                        ),
                      ),
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Plays a scanned album from its first track, in disc and track order.
Future<void> playLocalAlbum(
  WidgetRef ref,
  Iterable<LocalLibraryItem> items,
) async {
  final tracks = AlbumTrackOrder(
    items,
    discNumber: (track) => track.discNumber,
    trackNumber: (track) => track.trackNumber,
    trackName: (track) => track.trackName,
  ).tracks;
  if (tracks.isEmpty) return;
  await ref
      .read(playbackProvider.notifier)
      .playLocalLibraryQueue(tracks, startItem: tracks.first);
}
