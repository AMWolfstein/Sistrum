import 'package:audio_service/audio_service.dart';
import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';

/// Navigation for the title/artist, separate from playback and file actions.
class MornyePlayerNavigationMenu extends StatelessWidget {
  const MornyePlayerNavigationMenu({super.key, required this.mediaItem});

  final MediaItem mediaItem;

  @override
  Widget build(BuildContext context) {
    return _MornyePlayerMenuSurface(
      mediaItem: mediaItem,
      child: MornyeContextMenu(
        inheritSurface: true,
        dense: true,
        groups: [
          [
            if ((mediaItem.artist ?? '').trim().isNotEmpty)
              MornyeMenuAction(
                icon: CupertinoIcons.mic,
                label: context.l10n.mornyeGoToArtist,
                subtitle: mediaItem.artist,
                onPressed: () => Navigator.of(context).pop('artist'),
              ),
            if ((mediaItem.album ?? '').trim().isNotEmpty)
              MornyeMenuAction(
                icon: CupertinoIcons.square_stack,
                label: context.l10n.homeGoToAlbum,
                subtitle: mediaItem.album,
                onPressed: () => Navigator.of(context).pop('album'),
              ),
          ],
        ],
      ),
    );
  }
}

/// A floating menu that inherits the player's dark appearance.
class MornyePlayerActionsSheet extends ConsumerWidget {
  const MornyePlayerActionsSheet({
    super.key,
    required this.mediaItem,
    this.sleepTimerSubtitle,
    this.tempoSubtitle,
    this.volumeShown = true,
    this.deletable = false,
  });

  final MediaItem mediaItem;
  final String? sleepTimerSubtitle;

  /// The speed and pitch while either differs from normal.
  final String? tempoSubtitle;

  /// Whether the player shows its volume slider, which the menu toggles.
  final bool volumeShown;

  /// Whether the playing song is a file on this device, which can be deleted.
  final bool deletable;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final track = ref.watch(playerCollectionTrackProvider(mediaItem)).value;
    final loved = ref.watch(
      libraryCollectionsProvider.select(
        (state) => track != null && state.isLoved(track),
      ),
    );
    return _MornyePlayerMenuSurface(
      mediaItem: mediaItem,
      child: MornyeContextMenu(
        inheritSurface: true,
        dense: true,
        quickActions: [
          _action(
            context,
            'favorite',
            loved ? context.l10n.mornyeFavorited : context.l10n.mornyeFavorite,
            CupertinoIcons.star_fill,
            selected: loved,
          ),
          _action(
            context,
            'share',
            context.l10n.trackMetadataShare,
            CupertinoIcons.share_solid,
          ),
        ],
        groups: [
          [
            _action(
              context,
              'playlist',
              context.l10n.collectionAddToPlaylist,
              CupertinoIcons.text_badge_plus,
            ),
          ],
          [
            if ((mediaItem.album ?? '').trim().isNotEmpty)
              _action(
                context,
                'album',
                context.l10n.homeGoToAlbum,
                CupertinoIcons.square_stack,
                subtitle: mediaItem.album,
              ),
            if ((mediaItem.artist ?? '').trim().isNotEmpty)
              _action(
                context,
                'artist',
                context.l10n.mornyeGoToArtist,
                CupertinoIcons.mic,
                subtitle: mediaItem.artist,
              ),
            _action(
              context,
              'details',
              context.l10n.nowPlayingDetails,
              CupertinoIcons.info,
            ),
            _action(
              context,
              'shareLyrics',
              context.l10n.lyricsShareTitle,
              CupertinoIcons.quote_bubble,
            ),
          ],
          [
            _action(
              context,
              'equalizer',
              context.l10n.equalizerTitle,
              CupertinoIcons.slider_horizontal_3,
            ),
            _action(
              context,
              'tempo',
              context.l10n.playbackTempoTitle,
              CupertinoIcons.gauge,
              subtitle: tempoSubtitle,
            ),
            _action(
              context,
              'volume',
              volumeShown
                  ? context.l10n.nowPlayingHideVolume
                  : context.l10n.nowPlayingShowVolume,
              volumeShown
                  ? CupertinoIcons.speaker_slash
                  : CupertinoIcons.speaker_2,
            ),
            _action(
              context,
              'sleepTimer',
              context.l10n.nowPlayingSleepTimer,
              CupertinoIcons.moon_zzz,
              subtitle: sleepTimerSubtitle,
            ),
          ],
          if (deletable)
            [
              _action(
                context,
                'delete',
                context.l10n.trackRemoveFromDevice,
                CupertinoIcons.trash,
                destructive: true,
              ),
            ],
        ],
      ),
    );
  }

  MornyeMenuAction _action(
    BuildContext context,
    String value,
    String label,
    IconData icon, {
    String? subtitle,
    bool selected = false,
    bool destructive = false,
  }) => MornyeMenuAction(
    icon: icon,
    label: label,
    subtitle: subtitle,
    selected: selected,
    destructive: destructive,
    onPressed: () => Navigator.of(context).pop(value),
  );
}

/// Reuse the cached cover palette; no per-frame background capture is needed.
class _MornyePlayerMenuSurface extends StatelessWidget {
  const _MornyePlayerMenuSurface({
    required this.mediaItem,
    required this.child,
  });

  final MediaItem mediaItem;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return CoverSourceColorBuilder(
      imageSource: mediaItem.artUri?.toString(),
      builder: (context, sourceColor) {
        final dominant = HSLColor.fromColor(sourceColor ?? Colors.grey);
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
