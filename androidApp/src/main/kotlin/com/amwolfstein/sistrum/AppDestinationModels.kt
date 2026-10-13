package com.amwolfstein.sistrum

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.amwolfstein.sistrum.lastfm.LastFmStatus
import com.amwolfstein.sistrum.lyrics.LyricsSettings
import com.amwolfstein.sistrum.lyrics.LyricsSearchResult
import com.amwolfstein.sistrum.player.ArtworkCrossfadeTransition
import com.amwolfstein.sistrum.player.EqualizerSettings
import com.amwolfstein.sistrum.player.NormalizationSettings
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.player.PlaybackState
import com.amwolfstein.sistrum.player.RepeatMode
import com.amwolfstein.sistrum.player.media3.LimiterState
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.screens.AlbumDetailsUiState
import com.amwolfstein.sistrum.ui.screens.AlbumLayoutMode
import com.amwolfstein.sistrum.ui.screens.AlbumSortOption
import com.amwolfstein.sistrum.ui.screens.ArtistDetailsUiState
import com.amwolfstein.sistrum.ui.screens.ArtistSortOption
import com.amwolfstein.sistrum.ui.screens.ComposerDetailsUiState
import com.amwolfstein.sistrum.ui.screens.ComposerSortOption
import com.amwolfstein.sistrum.ui.screens.GenreDetailsUiState
import com.amwolfstein.sistrum.ui.screens.GenreSortOption
import com.amwolfstein.sistrum.ui.screens.HomeUiState
import com.amwolfstein.sistrum.ui.screens.InsightPeriod
import com.amwolfstein.sistrum.ui.screens.InsightUiState
import com.amwolfstein.sistrum.ui.screens.LibraryAlbumsUiState
import com.amwolfstein.sistrum.ui.screens.LibraryArtistsUiState
import com.amwolfstein.sistrum.ui.screens.LibraryComposersUiState
import com.amwolfstein.sistrum.ui.screens.LibraryGenresUiState
import com.amwolfstein.sistrum.ui.screens.LibraryPlaylistsUiState
import com.amwolfstein.sistrum.ui.screens.LibrarySearchUiState
import com.amwolfstein.sistrum.ui.screens.LibraryTracksUiState
import com.amwolfstein.sistrum.ui.screens.PlaylistDetailsUiState
import com.amwolfstein.sistrum.ui.screens.TrackSortOption

internal data class HomeDestinationModel(
    val state: HomeUiState = HomeUiState(),
    val onTrackClick: (List<LibraryTrack>, String) -> Unit = { _, _ -> },
)

internal data class InsightDestinationModel(
    val state: InsightUiState = InsightUiState(),
    val onLibraryPeriodSelected: (InsightPeriod) -> Unit = {},
    val onListeningPeriodSelected: (InsightPeriod) -> Unit = {},
    val onTrackClick: (String) -> Unit = {},
)

internal data class LibraryTracksModel(
    val state: LibraryTracksUiState = LibraryTracksUiState(),
    val onSortOptionSelected: (TrackSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onTrackClick: (String) -> Unit = {},
    val onPlayAll: (Boolean) -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onRecentTrackClick: (String) -> Unit = {},
)

internal data class LibraryArtistsModel(
    val state: LibraryArtistsUiState = LibraryArtistsUiState(),
    val onSortOptionSelected: (ArtistSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val orderedTrackIds: (String) -> List<String> = { emptyList() },
)

internal data class LibraryAlbumsModel(
    val state: LibraryAlbumsUiState = LibraryAlbumsUiState(),
    val onSortOptionSelected: (AlbumSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onLayoutModeSelected: (AlbumLayoutMode) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayAll: (Boolean) -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onTrackPlay: (String, String) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val onAddToFavorites: (List<String>) -> Unit = {},
)

internal data class LibraryGenresModel(
    val state: LibraryGenresUiState = LibraryGenresUiState(),
    val onSortOptionSelected: (GenreSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val orderedTrackIds: (String) -> List<String> = { emptyList() },
)

internal data class LibraryComposersModel(
    val state: LibraryComposersUiState = LibraryComposersUiState(),
    val onSortOptionSelected: (ComposerSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val orderedTrackIds: (String) -> List<String> = { emptyList() },
)

internal data class LibraryPlaylistsModel(
    val state: LibraryPlaylistsUiState = LibraryPlaylistsUiState(),
    val availablePlaylists: List<LibraryPlaylist> = emptyList(),
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onTrackPlay: (String, String) -> Unit = { _, _ -> },
    val onTrackRemove: (String, String) -> Unit = { _, _ -> },
    val onTrackMove: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val onUpdate: (String, String, Uri?, Boolean) -> Unit = { _, _, _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onCreate: (String, Uri?) -> Unit = { _, _ -> },
    val onCreateWithTracks: (String, Uri?, List<String>) -> Unit = { _, _, _ -> },
    val onMembershipChange: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
)

internal data class LibrarySearchModel(
    val state: LibrarySearchUiState = LibrarySearchUiState(),
    val onQueryChange: (String) -> Unit = {},
    val onTrackClick: (String) -> Unit = {},
)

internal data class LibraryDetailActions(
    val albums: AlbumDetailsUiState = AlbumDetailsUiState(),
    val playlists: PlaylistDetailsUiState = PlaylistDetailsUiState(),
    val artists: ArtistDetailsUiState = ArtistDetailsUiState(),
    val genres: GenreDetailsUiState = GenreDetailsUiState(),
    val composers: ComposerDetailsUiState = ComposerDetailsUiState(),
    val onHeroColorChanged: (Color) -> Unit = {},
)

internal data class LibraryDestinationModel(
    val tracks: LibraryTracksModel = LibraryTracksModel(),
    val artists: LibraryArtistsModel = LibraryArtistsModel(),
    val albums: LibraryAlbumsModel = LibraryAlbumsModel(),
    val genres: LibraryGenresModel = LibraryGenresModel(),
    val composers: LibraryComposersModel = LibraryComposersModel(),
    val playlists: LibraryPlaylistsModel = LibraryPlaylistsModel(),
    val search: LibrarySearchModel = LibrarySearchModel(),
    val details: LibraryDetailActions = LibraryDetailActions(),
)

internal data class SettingsDestinationModel(
    val lastFmStatus: LastFmStatus = LastFmStatus(),
    val onLastFmConnect: () -> Unit = {},
    val onLastFmDisconnect: () -> Unit = {},
    val lyricsSettings: LyricsSettings = LyricsSettings(),
    val onLrclibChanged: (Boolean) -> Unit = {},
    val onKugouChanged: (Boolean) -> Unit = {},
    val onEmbeddedChanged: (Boolean) -> Unit = {},
    val onSidecarChanged: (Boolean) -> Unit = {},
    val crossfadeSeconds: Int = 0,
    val lastEnabledCrossfadeSeconds: Int = 4,
    val onCrossfadeSecondsChanged: (Int) -> Unit = {},
    val blendArtworkDuringCrossfade: Boolean = true,
    val onBlendArtworkDuringCrossfadeChanged: (Boolean) -> Unit = {},
    val repeatOneCrossfade: Boolean = true,
    val onRepeatOneCrossfadeChanged: (Boolean) -> Unit = {},
    val crossfadeOnSkip: Boolean = false,
    val onCrossfadeOnSkipChanged: (Boolean) -> Unit = {},
    val showFullscreenQualityBadge: Boolean = true,
    val onShowFullscreenQualityBadgeChanged: (Boolean) -> Unit = {},
    val normalizationAvailable: Boolean = false,
    val normalization: NormalizationSettings = NormalizationSettings(),
    val limiterState: LimiterState? = null,
    val onNormalizationChanged: (NormalizationSettings) -> Unit = {},
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val onEqualizerEnabledChanged: (Boolean) -> Unit = {},
    val onEqualizerPresetSelected: (String) -> Unit = {},
    val onEqualizerBandChanged: (Int, Float) -> Unit = { _, _ -> },
    val onEqualizerProfileCreate: (String) -> Unit = {},
    val onEqualizerProfileReset: (String) -> Unit = {},
    val onEqualizerProfileDelete: (String) -> Unit = {},
    val analysisProgress: com.amwolfstein.sistrum.analysis.AnalysisProgress? = null,
    val nowRunning: Boolean = false,
    val onAnalyzeNow: () -> Unit = {},
    val onStopAnalysis: () -> Unit = {},
)

internal data class AppDestinationModels(
    val home: HomeDestinationModel = HomeDestinationModel(),
    val insight: InsightDestinationModel = InsightDestinationModel(),
    val library: LibraryDestinationModel = LibraryDestinationModel(),
    val settings: SettingsDestinationModel = SettingsDestinationModel(),
)

internal data class PlaybackModel(
    val state: PlaybackState = PlaybackState.Idle,
    val queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    val queueTracks: List<LibraryTrack> = emptyList(),
    val lyrics: String? = null,
    val lyricsLoading: Boolean = false,
    val onSearchLyrics: suspend (LibraryTrack, String, String) -> List<LyricsSearchResult> = { _, _, _ -> emptyList() },
    val onLyricsSelected: suspend (String, LyricsSearchResult) -> Unit = { _, _ -> },
    val artworkCrossfade: ArtworkCrossfadeTransition? = null,
    val blendArtworkDuringCrossfade: Boolean = true,
    val showFullscreenQualityBadge: Boolean = true,
    val systemVolume: Float = 0f,
    val onPrevious: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onQueueTrackSelected: (String) -> Unit = {},
    val onQueueReordered: (List<String>) -> Unit = {},
    val onQueueTrackRemoved: (String) -> Unit = {},
    val onShuffleChange: (Boolean) -> Unit = {},
    val onRepeatModeChange: (RepeatMode) -> Unit = {},
    val onSystemVolumeChange: (Float) -> Unit = {},
    val onMiniPlayerDismiss: () -> Unit = {},
    /** Null where the system output switcher is unavailable (below Android 14); the Cast button is then hidden. */
    val onOpenMediaOutputSwitcher: (() -> Unit)? = null,
    val onFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onTrackPlayNext: (String) -> Unit = {},
    val onTrackAddToQueue: (String) -> Unit = {},
    val moodRadioEligibleTrackIds: Set<String> = emptySet(),
    val onStartMoodRadio: (String) -> Unit = {},
    val moodRadioActive: Boolean = false,
)
