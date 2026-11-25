import 'dart:async';

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:flutter/gestures.dart' show DragStartBehavior;
import 'package:material_ui/material_ui.dart';
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/player_motion_artwork_provider.dart';
import 'package:sistrum/providers/player_artwork_video_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/screens/now_playing_screen.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/string_utils.dart';
import 'package:sistrum/widgets/audio_quality_badges.dart';
import 'package:sistrum/widgets/overflow_marquee.dart';
import 'package:sistrum/widgets/player_artwork.dart';
import 'package:sistrum/widgets/player_track_crossfade.dart';
import 'package:sistrum/widgets/player_track_swipe.dart';
import 'package:sistrum/widgets/swipe_commit_haptic.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_glass_press.dart';

class MiniPlayer extends ConsumerStatefulWidget {
  const MiniPlayer({super.key, this.compact = false, this.bottomPadding = 8});

  final bool compact;
  final double bottomPadding;

  @override
  ConsumerState<MiniPlayer> createState() => _MiniPlayerState();
}

class _MiniPlayerState extends ConsumerState<MiniPlayer> {
  Uri? _warmedArtUri;

  @override
  void initState() {
    super.initState();
    ref.listenManual(
      currentMediaItemProvider.select((item) => item.value?.artUri),
      (_, artUri) => _warmCoverColour(artUri),
    );
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _warmCoverColour(ref.read(currentMediaItemProvider).value?.artUri);
  }

  // The Mornye full player shades itself from the cover's source colour;
  // resolving it with the track lets the player open on its final colour.
  void _warmCoverColour(Uri? artUri) {
    if (artUri == _warmedArtUri) return;
    _warmedArtUri = artUri;
    final source = artUri?.toString();
    if (source != null && source.isNotEmpty) {
      unawaited(CoverPalette.resolveSourceColor(source));
    }
  }

  @override
  Widget build(BuildContext context) {
    return _MiniPlayerBody(
      compact: widget.compact,
      bottomPadding: widget.bottomPadding,
    );
  }
}

class _MiniPlayerBody extends ConsumerStatefulWidget {
  const _MiniPlayerBody({required this.compact, required this.bottomPadding});

  final bool compact;
  final double bottomPadding;

  @override
  ConsumerState<_MiniPlayerBody> createState() => _MiniPlayerBodyState();
}

class _MiniPlayerBodyState extends ConsumerState<_MiniPlayerBody> {
  final _surfaceKey = GlobalKey();
  final _artworkKey = GlobalKey();

  Rect? _bounds(GlobalKey key) {
    final box = key.currentContext?.findRenderObject();
    if (box is! RenderBox || !box.attached || !box.hasSize) return null;
    final overlay = Navigator.of(
      context,
      rootNavigator: true,
    ).overlay?.context.findRenderObject();
    return MatrixUtils.transformRect(
      box.getTransformTo(overlay),
      Offset.zero & box.size,
    );
  }

  /// Upward travel of the current vertical drag, in logical pixels.
  double _dragUp = 0;
  bool _dragCancelled = false;
  final _openHaptic = SwipeCommitHaptic();

  void _onVerticalDragUpdate(DragUpdateDetails details) {
    _dragUp -= details.primaryDelta ?? 0;
    _openHaptic.update(_dragUp > 48);
  }

  void _openNowPlaying() {
    Navigator.of(context, rootNavigator: true).push(
      NowPlayingRoute(
        child: const NowPlayingScreen(),
        miniPlayerGeometry: () {
          if (!mounted) return null;
          final surface = _bounds(_surfaceKey);
          final artwork = _bounds(_artworkKey);
          if (surface == null || artwork == null) return null;
          return (surface: surface, artwork: artwork);
        },
      ),
    );
  }

  // Opens on release rather than under the finger: pushing a route cancels
  // every active pointer, so the drag could not keep steering the page.
  void _onVerticalDragEnd(DragEndDetails details) {
    final travel = _dragUp;
    _dragUp = 0;
    final velocity = -(details.primaryVelocity ?? 0);
    if (!_dragCancelled && (velocity > 300 || (velocity >= 0 && travel > 48))) {
      _openNowPlaying();
    }
  }

  void _onHorizontalRelease(DragEndDetails details, double travel) {
    final velocity = details.primaryVelocity ?? 0;
    if (_dragCancelled ||
        (travel.abs() < PlayerTrackSwipe.followCommitDistance &&
            velocity.abs() < 550)) {
      return;
    }
    final direction = velocity.abs() >= 550 ? velocity : travel;
    final controller = ref.read(musicPlayerControllerProvider);
    if (!ref.read(playbackPlayingProvider)) {
      if (!ref.read(playbackLoadingProvider)) unawaited(controller.dismiss());
      return;
    }
    if (direction < 0) {
      controller.next();
    } else {
      controller.previous();
    }
  }

  Widget _titles(
    BuildContext context,
    MediaItem mediaItem, {
    required int? marqueeCycles,
    required bool playing,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        OverflowMarquee(
          resetKey: (mediaItem.id, mediaItem.title),
          maxCycles: marqueeCycles,
          playing: playing,
          child: ExplicitTrackTitle(
            title: mediaItem.title,
            explicit: parseExplicitFlag(mediaItem.extras?['explicit']) == true,
            style: Theme.of(
              context,
            ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        OverflowMarquee(
          resetKey: (mediaItem.id, mediaItem.artist),
          maxCycles: marqueeCycles,
          playing: playing,
          child: Text(
            mediaItem.artist ?? '',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Color.lerp(
                colorScheme.onSurfaceVariant,
                colorScheme.onSurface,
                colorScheme.brightness == Brightness.dark ? 0.7 : 0.6,
              ),
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaItem = ref.watch(currentMediaItemProvider).value;
    if (mediaItem == null) return const SizedBox.shrink();

    final isPlaying = ref.watch(playbackPlayingProvider);
    final isLoading = ref.watch(playbackLoadingProvider);

    final controller = ref.read(musicPlayerControllerProvider);
    final colorScheme = Theme.of(context).colorScheme;
    final compact = widget.compact;
    // The None level runs on constrained Android hardware. It skips keeping a
    // paused video decoder for the full player's motion cover.
    final fullEffects = ref.watch(mornyeLiquidGlassProvider);
    // Every marquee frame re-filters the glass chrome around it, so Mornye
    // stops the title after a few passes on every glass level.
    const marqueeCycles = 2;
    if (fullEffects &&
        ref.watch(settingsProvider.select((s) => s.motionArtworkEnabled)) &&
        !MediaQuery.disableAnimationsOf(context)) {
      final artwork = ref
          .watch(
            songMotionArtworkProvider((
              album: mediaItem.album ?? '',
              artist: mediaItem.artist ?? '',
              source: mediaItem.extras?['source']?.toString() ?? '',
            )),
          )
          .value;
      if (artwork != null) {
        ref.watch(playerArtworkVideoProvider(artwork.source));
      }
    }

    final surface = DecoratedBox(
      key: _surfaceKey,
      position: DecorationPosition.foreground,
      decoration: const BoxDecoration(),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: _openNowPlaying,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 12),
                child: Row(
                  children: [
                    Hero(
                      tag: kNowPlayingArtworkHeroTag,
                      child: ClipRRect(
                        key: _artworkKey,
                        borderRadius: BorderRadius.circular(6),
                        child: SizedBox(
                          width: context.tokens.coverMini,
                          height: context.tokens.coverMini,
                          child: PlayerArtwork(
                            artUri: mediaItem.artUri?.toString(),
                            colorScheme: colorScheme,
                            cacheWidth: PlayerArtwork.miniCacheWidth,
                            iconSize: 22,
                            crossfade: playerTrackChangeMotion(context),
                          ),
                        ),
                      ),
                    ),
                    SizedBox(width: compact ? 8 : 12),
                    Expanded(
                      // The lines follow a sideways swipe; the cover stays.
                      child: PlayerTrackCrossfade(
                        trackKey: mediaItem.id,
                        child: PlayerTrackSwipeTitles(
                          current: mediaItem,
                          builder: (mediaItem) => _titles(
                            context,
                            mediaItem,
                            marqueeCycles: marqueeCycles,
                            playing: isPlaying,
                          ),
                        ),
                      ),
                    ),
                    AppIconButton(
                      color: colorScheme.onSurface,
                      tooltip: isPlaying
                          ? context.l10n.actionPause
                          : context.l10n.tooltipPlay,
                      icon: isLoading
                          ? const AppLoadingIndicator(
                              size: 20,
                              strokeWidth: 2.5,
                            )
                          : AppIcon(
                              isPlaying
                                  ? CupertinoIcons.pause_fill
                                  : CupertinoIcons.play_fill,
                            ),
                      onPressed: isLoading
                          ? null
                          : () => controller.togglePlayPause(isPlaying),
                    ),
                    if (!compact)
                      AppIconButton(
                        color: colorScheme.onSurface,
                        tooltip: context.l10n.nowPlayingNextTrack,
                        icon: const AppIcon(CupertinoIcons.forward_fill),
                        onPressed: controller.next,
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
    // Swiping up opens the full player. Sideways swipes dismiss a paused
    // local player; otherwise they change tracks. The title lines
    // follow the finger and return to rest on release.
    // Desktop has no touch gestures: a click opens the player instead.
    final swipeable = isDesktopPlatform
        ? surface
        : PlayerTrackSwipe.follow(
            onRelease: _onHorizontalRelease,
            child: GestureDetector(
              // Measure from touch-down so the slop counts toward the swipe.
              dragStartBehavior: DragStartBehavior.down,
              onVerticalDragStart: (_) {
                _dragUp = 0;
                _openHaptic.update(false);
              },
              onVerticalDragUpdate: _onVerticalDragUpdate,
              onVerticalDragEnd: _onVerticalDragEnd,
              onVerticalDragCancel: () => _dragUp = 0,
              child: PlayerTrackSwipeRegion(
                child: Listener(
                  // Flutter ends an accepted drag even after a pointer cancel.
                  onPointerDown: (_) => _dragCancelled = false,
                  onPointerCancel: (_) => _dragCancelled = true,
                  child: surface,
                ),
              ),
            ),
          );
    return Padding(
      padding: EdgeInsets.only(bottom: widget.bottomPadding),
      child: MornyeGlassPress(
        radius: 32,
        child: MornyeGlass.navigation(
          strongTint: true,
          tintOpacity: MornyeTheme.chromeOpacity(context),
          blurEnabled: ref.watch(mornyeBlurEnabledProvider),
          child: swipeable,
        ),
      ),
    );
  }
}
