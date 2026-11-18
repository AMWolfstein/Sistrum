import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/local_playback.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/widgets/audio_quality_badges.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/widgets/in_library_badge.dart';
import 'package:sistrum/widgets/track_collection_quick_actions.dart';

/// Track row shared by the album and playlist screens. Tap plays the local
/// copy when one exists. Long-press opens the track options sheet. Callers
/// supply [leading] (track number, cover art, ...) and choose whether the
/// artist name links to the artist screen.
class TrackListTile extends ConsumerWidget {
  final Track track;
  final Widget leading;
  final bool clickableArtist;
  final bool showQualityBadges;
  final bool isSelectionMode;
  final bool isSelected;
  final VoidCallback? onToggleSelection;
  final VoidCallback? onEnterSelectionMode;

  /// [leading] is cover art. See [TrackCard.leadingArtwork].
  final bool leadingArtwork;

  const TrackListTile({
    super.key,
    required this.track,
    required this.leading,
    this.clickableArtist = false,
    this.showQualityBadges = true,
    this.isSelectionMode = false,
    this.isSelected = false,
    this.onToggleSelection,
    this.onEnterSelectionMode,
    this.leadingArtwork = false,
  }) : assert(!isSelectionMode || onToggleSelection != null);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final playback = ref.watch(
      catalogTrackPlaybackUiProvider(catalogTrackKey(track)),
    );
    final isInLocalLibrary = _isInLocalLibrary(ref, track);

    return TrackCard(
      style: TrackCardStyle.flat,
      isSelectionMode: isSelectionMode,
      isSelected: isSelected,
      isCurrent: playback.isCurrent,
      isPlaying: playback.isPlaying,
      leadingArtwork: leadingArtwork,
      leading: leading,
      title: track.name,
      subtitle: TrackMetadataBadgesLine(
        primary: clickableArtist && !isSelectionMode
            ? ClickableArtistName(artistName: track.artistName)
            : Text(
                track.artistName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
        badges: [
          ...buildQualityBadges(
            audioQuality: showQualityBadges ? track.audioQuality : null,
            audioModes: showQualityBadges ? track.audioModes : null,
            colorScheme: colorScheme,
            explicit: track.isExplicit,
          ),
          if (isInLocalLibrary) const InLibraryBadge(),
        ],
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          TrackCollectionQuickActions(
            track: track,
            hasLocalPlaybackCandidate: isInLocalLibrary,
          ),
        ],
      ),
      onTap: isSelectionMode
          ? onToggleSelection
          : () => playLocalIfAvailable(context, ref, track),
      onLongPress: isSelectionMode
          ? null
          : onEnterSelectionMode ??
                () => TrackCollectionQuickActions.showTrackOptionsSheet(
                  context,
                  ref,
                  track,
                  hasLocalPlaybackCandidate: isInLocalLibrary,
                ),
    );
  }

  static bool _isInLocalLibrary(WidgetRef ref, Track track) {
    final show = ref.watch(
      settingsProvider.select(
        (s) => s.localLibraryEnabled && s.localLibraryShowDuplicates,
      ),
    );
    return show &&
        ref.watch(
          localLibraryProvider.select(
            (state) => state.existsInLibrary(
              isrc: track.isrc,
              trackName: track.name,
              artistName: track.artistName,
            ),
          ),
        );
  }
}

/// Explicit, Dolby Atmos and In Library badges of a [TrackListTile], for the
/// title cell of the desktop song table. Quality has its own column there.
class TrackListTileBadges extends ConsumerWidget {
  const TrackListTileBadges({super.key, required this.track});

  final Track track;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final showLocalLibraryIndicator = ref.watch(
      settingsProvider.select(
        (s) => s.localLibraryEnabled && s.localLibraryShowDuplicates,
      ),
    );
    final isInLocalLibrary =
        showLocalLibraryIndicator &&
        ref.watch(
          localLibraryProvider.select(
            (state) => state.existsInLibrary(
              isrc: track.isrc,
              trackName: track.name,
              artistName: track.artistName,
            ),
          ),
        );
    final badges = [
      ...buildQualityBadges(
        audioQuality: null,
        audioModes: track.audioModes,
        colorScheme: colorScheme,
        explicit: track.isExplicit,
      ),
      if (isInLocalLibrary) const InLibraryBadge(),
    ];
    if (badges.isEmpty) return const SizedBox.shrink();
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final (index, badge) in badges.indexed) ...[
          if (index > 0) const SizedBox(width: 4),
          badge,
        ],
      ],
    );
  }
}
