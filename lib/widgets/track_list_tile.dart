import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/local_playback.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/widgets/song_menu.dart';
import 'package:sistrum/widgets/track_card.dart';

/// Track row of the playlist screen and the catalog track lists. Tap plays
/// the local copy when one exists, and the play circle does the same. Long
/// press starts selection. Callers supply [leading] (track number, cover art,
/// ...) and choose whether the artist name links to the artist screen.
class TrackListTile extends ConsumerWidget {
  final Track track;
  final Widget leading;
  final bool clickableArtist;
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
    this.isSelectionMode = false,
    this.isSelected = false,
    this.onToggleSelection,
    this.onEnterSelectionMode,
    this.leadingArtwork = false,
  }) : assert(!isSelectionMode || onToggleSelection != null);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playback = ref.watch(
      catalogTrackPlaybackUiProvider(catalogTrackKey(track)),
    );
    final hasLocalCopy = _hasLocalCopy(ref, track);
    void play() => playLocalIfAvailable(context, ref, track);

    return TrackCard(
      style: TrackCardStyle.flat,
      isSelectionMode: isSelectionMode,
      isSelected: isSelected,
      isCurrent: playback.isCurrent,
      isPlaying: playback.isPlaying,
      leadingArtwork: leadingArtwork,
      leading: leading,
      title: track.name,
      subtitle: clickableArtist && !isSelectionMode
          ? ClickableArtistName(artistName: track.artistName)
          : Text(
              track.artistName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
      isExplicit: track.isExplicit,
      onPlay: hasLocalCopy ? play : null,
      menu: isSelectionMode ? null : SongMenuButton(track: track),
      onTap: isSelectionMode ? onToggleSelection : play,
      onLongPress: isSelectionMode ? null : onEnterSelectionMode,
    );
  }

  static bool _hasLocalCopy(WidgetRef ref, Track track) {
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
