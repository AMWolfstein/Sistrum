import 'package:json_annotation/json_annotation.dart';
import 'package:sistrum/models/automix_options.dart';
import 'package:sistrum/models/tag_credit_settings.dart';
import 'package:sistrum/utils/artist_utils.dart';

part 'settings.g.dart';

/// 'default' (a card) or 'fullscreen' once chosen, else empty for the card
/// ([AppSettings.playerCoverStyleFor]).
String playerCoverStyleFromJson(Object? value) =>
    value == 'default' || value == 'fullscreen' ? value as String : '';

/// Pre-amp applied on top of ReplayGain during playback, in whole dB.
/// -7 dB targets -25 LUFS from the -18 LUFS tag reference.
double playbackNormalizationPreampFromJson(Object? value) =>
    value is num && value.isFinite
    ? value.toDouble().roundToDouble().clamp(-15, 6)
    : 0;

String playbackNormalizationModeFromJson(Object? value) =>
    value == 'album' ? 'album' : 'track';

double musicHapticsLevelFromJson(Object? value) =>
    value is num && value.isFinite ? value.toDouble().clamp(0, 1) : 0.5;

/// Sort choices offered by the Library, in display order.
const librarySortModes = [
  'latest',
  'oldest',
  'size-desc',
  'size-asc',
  'a-z',
  'z-a',
  'artist-asc',
  'artist-desc',
  'album-asc',
  'album-desc',
  'release-newest',
  'release-oldest',
  'genre-asc',
  'genre-desc',
];

String librarySortModeFromJson(Object? value) =>
    value is String && librarySortModes.contains(value) ? value : 'latest';

@JsonSerializable(explicitToJson: true)
class AppSettings {
  static const String libraryQualityLabelBitrate = 'bitrate';
  static const String libraryQualityLabelFileFormat = 'file_format';
  static const String libraryQualityLabelBitDepthOnly = 'bit_depth_only';
  static const String libraryQualityLabelBitDepth = 'bit_depth';
  static const String libraryQualityLabelBitDepthBitrate = 'bit_depth_bitrate';

  final String downloadDirectory;
  final String storageMode; // 'app' or 'saf'
  final String downloadTreeUri; // SAF persistable tree URI
  final bool autoFallback;

  final String
  artistTagMode; // 'joined', 'split_vorbis' (Vorbis formats), or 'primary'
  final TagCreditSettings tagCredits;
  // Apply ReplayGain/R128 tags as volume normalization in the built-in player.
  final bool playbackNormalization;
  @JsonKey(fromJson: playbackNormalizationPreampFromJson)
  final double playbackNormalizationPreamp;
  @JsonKey(fromJson: playbackNormalizationModeFromJson)
  final String playbackNormalizationMode; // 'track' or 'album'
  final bool pauseOnMute;
  final bool playOnHeadphonesConnected;
  // Start the next song the moment one ends; AutoMix replaces it while on.
  final bool gaplessPlayback;
  final bool autoMix;
  @JsonKey(fromJson: autoMixDurationFromJson)
  final int autoMixDuration;
  @JsonKey(unknownEnumValue: AutoMixEffect.auto)
  final AutoMixEffect autoMixEffect;
  @JsonKey(fromJson: autoMixSpeedFromJson)
  final double autoMixSpeed;
  @JsonKey(fromJson: autoMixPitchFromJson)
  final double autoMixPitch;
  final bool autoMixEcho;
  final bool autoMixLowPass;
  AutoMixOptions get autoMixOptions => AutoMixOptions(
    durationSeconds: autoMixDuration,
    effect: autoMixEffect,
    speed: autoMixSpeed,
    pitchSemitones: autoMixPitch,
    echo: autoMixEcho,
    lowPass: autoMixLowPass,
  );
  final bool usbBitPerfect;
  final bool usbDirect;
  final bool usbDsdOverPcm;
  final bool usbAllowFixedVolume;
  final bool dapExclusive;

  final bool autoplay;
  final bool autoplayPersonalization;

  /// Analyze how each Library song sounds, on this device, so Autoplay picks
  /// songs that sound like the session. Off until turned on: decoding songs
  /// is heavy on low-end phones.
  final bool songAnalysis;

  /// Android: analyze the Library only while charging. The song playing is
  /// analyzed either way.
  final bool songAnalysisOnlyWhileCharging;
  final bool musicHaptics;
  @JsonKey(fromJson: musicHapticsLevelFromJson)
  final double musicHapticsSensitivity;
  @JsonKey(fromJson: musicHapticsLevelFromJson)
  final double musicHapticsStrength;
  @JsonKey(fromJson: playerCoverStyleFromJson)
  final String playerCoverStyle; // 'default' (card), 'fullscreen' or ''
  final bool autoHideLyricsControls;
  final bool isFirstLaunch;
  final bool checkForUpdates;
  final String updateChannel;
  final bool createPlaylistFolder;
  final bool useAlbumArtistForFolders;
  final bool usePrimaryArtistOnly; // Strip featured artists from folder name
  final bool filterContributingArtistsInAlbumArtist;
  @JsonKey(fromJson: librarySortModeFromJson)
  final String historySortMode;

  /// Library badge text: measured bitrate, file format, bit depth,
  /// bit depth/sample rate, or the combined bit depth/bitrate label.
  final String libraryQualityLabelMode;
  final bool enableLogging;
  final bool useExtensionProviders;
  final List<String>? downloadFallbackExtensionIds;
  final String? searchProvider;

  /// Search results on wide screens: 'list' keeps the phone rows, 'grid'
  /// shows cover grids and shelves.
  final String searchViewMode;
  final bool separateSingles;
  final String singleFilenameFormat;
  final String albumFolderStructure;
  final bool showExtensionStore;

  /// Shared-element (Hero) flights, e.g. the mini player artwork expanding
  /// into the full player. Off skips the flights entirely.
  final bool heroAnimationsEnabled;

  /// Forces the shell's backdrop blur on even when the startup runtime
  /// profile disabled it for this device tier. Off means "follow the
  /// device default".
  final bool forceBackdropBlur;

  final String
  extensionVerificationBrowserMode; // 'external_first' or 'in_app_first'
  final String locale;
  final String lyricsMode;
  final bool keepScreenOnLyrics;
  final bool listeningStatisticsEnabled;
  // Re-download a Hi-Res request at LOSSLESS when the file measures as fake.
  final bool
  useAllFilesAccess; // Android 13+ only: enable MANAGE_EXTERNAL_STORAGE
  final bool
  networkCompatibilityMode; // Try HTTP + allow invalid TLS cert for API requests
  final bool
  allowLocalNetwork; // Allow requests to private/local network targets (local proxy / custom DNS)
  final String
  songLinkRegion; // SongLink userCountry region code used for platform lookup

  final bool localLibraryEnabled;
  final String localLibraryPath;
  final bool localLibraryShowDuplicates;
  final String
  localLibraryAutoScan; // Auto-scan mode: 'off', 'on_open', 'daily', 'weekly'

  final bool hasCompletedTutorial;

  final List<String> lyricsProviders;

  final String playerMode;
  final bool playerShowPronunciation;
  final bool playerShowTranslation;

  /// Whether the phone Now Playing screen shows its volume slider.
  final bool playerShowVolume;

  /// Whether the phone Now Playing screen shows a single-line synced lyric preview.
  final bool playerShowLyricSnippet;

  const AppSettings({
    this.downloadDirectory = '',
    this.storageMode = 'app',
    this.downloadTreeUri = '',
    this.autoFallback = true,
    this.artistTagMode = artistTagModeJoined,
    this.tagCredits = const TagCreditSettings(),
    this.playbackNormalization = false,
    this.playbackNormalizationPreamp = 0,
    this.playbackNormalizationMode = 'track',
    this.pauseOnMute = true,
    this.playOnHeadphonesConnected = false,
    this.gaplessPlayback = true,
    this.autoMix = false,
    this.autoMixDuration = 0,
    this.autoMixEffect = AutoMixEffect.auto,
    this.autoMixSpeed = 1,
    this.autoMixPitch = 0,
    this.autoMixEcho = false,
    this.autoMixLowPass = false,
    this.usbBitPerfect = false,
    this.usbDirect = false,
    this.usbDsdOverPcm = false,
    this.usbAllowFixedVolume = false,
    this.dapExclusive = false,
    this.autoplay = false,
    this.autoplayPersonalization = true,
    this.songAnalysis = false,
    this.songAnalysisOnlyWhileCharging = true,
    this.musicHaptics = false,
    this.musicHapticsSensitivity = 0.5,
    this.musicHapticsStrength = 0.5,
    this.playerCoverStyle = '',
    this.autoHideLyricsControls = true,
    this.isFirstLaunch = true,
    this.checkForUpdates = true,
    this.updateChannel = 'stable',
    this.createPlaylistFolder = false,
    this.useAlbumArtistForFolders = true,
    this.usePrimaryArtistOnly = false,
    this.filterContributingArtistsInAlbumArtist = false,
    this.historySortMode = 'latest',
    this.libraryQualityLabelMode = AppSettings.libraryQualityLabelBitDepthOnly,
    this.enableLogging = false,
    this.useExtensionProviders = true,
    this.downloadFallbackExtensionIds,
    this.searchProvider,
    this.searchViewMode = 'list',
    this.separateSingles = false,
    this.singleFilenameFormat = '{title} - {artist}',
    this.albumFolderStructure = 'artist_album',
    this.showExtensionStore = true,
    this.heroAnimationsEnabled = true,
    this.forceBackdropBlur = false,
    this.extensionVerificationBrowserMode = 'in_app_first',
    this.locale = 'system',
    this.lyricsMode = 'embed',
    this.keepScreenOnLyrics = true,
    this.listeningStatisticsEnabled = true,
    this.useAllFilesAccess = false,
    this.networkCompatibilityMode = false,
    this.allowLocalNetwork = false,
    this.songLinkRegion = 'US',
    this.localLibraryEnabled = false,
    this.localLibraryPath = '',
    this.localLibraryShowDuplicates = true,
    this.localLibraryAutoScan = 'off',
    this.hasCompletedTutorial = false,
    this.lyricsProviders = const [
      'apple_music',
      'lrcred',
      'lyricsplus',
      'lrclib',
    ],
    this.playerMode = 'internal',
    this.playerShowPronunciation = true,
    this.playerShowTranslation = true,
    this.playerShowVolume = true,
    this.playerShowLyricSnippet = true,
  });

  /// The cover art style the player shows: the one chosen, else the card.
  String playerCoverStyleFor() =>
      playerCoverStyle.isNotEmpty ? playerCoverStyle : 'default';

  AppSettings copyWith({
    String? downloadDirectory,
    String? storageMode,
    String? downloadTreeUri,
    bool? autoFallback,
    String? artistTagMode,
    TagCreditSettings? tagCredits,
    bool? playbackNormalization,
    double? playbackNormalizationPreamp,
    String? playbackNormalizationMode,
    bool? pauseOnMute,
    bool? playOnHeadphonesConnected,
    bool? gaplessPlayback,
    bool? autoMix,
    int? autoMixDuration,
    AutoMixEffect? autoMixEffect,
    double? autoMixSpeed,
    double? autoMixPitch,
    bool? autoMixEcho,
    bool? autoMixLowPass,
    bool? usbBitPerfect,
    bool? usbDirect,
    bool? usbDsdOverPcm,
    bool? usbAllowFixedVolume,
    bool? dapExclusive,
    bool? autoplay,
    bool? autoplayPersonalization,
    bool? songAnalysis,
    bool? songAnalysisOnlyWhileCharging,
    bool? musicHaptics,
    double? musicHapticsSensitivity,
    double? musicHapticsStrength,
    String? playerCoverStyle,
    bool? autoHideLyricsControls,
    bool? isFirstLaunch,
    bool? checkForUpdates,
    String? updateChannel,
    bool? createPlaylistFolder,
    bool? useAlbumArtistForFolders,
    bool? usePrimaryArtistOnly,
    bool? filterContributingArtistsInAlbumArtist,
    String? historySortMode,
    String? libraryQualityLabelMode,
    bool? enableLogging,
    bool? useExtensionProviders,
    List<String>? downloadFallbackExtensionIds,
    bool clearDownloadFallbackExtensionIds = false,
    String? searchProvider,
    bool clearSearchProvider = false,
    String? searchViewMode,
    bool? separateSingles,
    String? singleFilenameFormat,
    String? albumFolderStructure,
    bool? showExtensionStore,
    bool? heroAnimationsEnabled,
    bool? forceBackdropBlur,
    String? extensionVerificationBrowserMode,
    String? locale,
    String? lyricsMode,
    bool? keepScreenOnLyrics,
    bool? listeningStatisticsEnabled,
    bool? useAllFilesAccess,
    bool? networkCompatibilityMode,
    bool? allowLocalNetwork,
    String? songLinkRegion,
    bool? localLibraryEnabled,
    String? localLibraryPath,
    bool? localLibraryShowDuplicates,
    String? localLibraryAutoScan,
    bool? hasCompletedTutorial,
    List<String>? lyricsProviders,
    String? playerMode,
    bool? playerShowPronunciation,
    bool? playerShowTranslation,
    bool? playerShowVolume,
    bool? playerShowLyricSnippet,
  }) {
    return AppSettings(
      downloadDirectory: downloadDirectory ?? this.downloadDirectory,
      storageMode: storageMode ?? this.storageMode,
      downloadTreeUri: downloadTreeUri ?? this.downloadTreeUri,
      autoFallback: autoFallback ?? this.autoFallback,
      artistTagMode: artistTagMode ?? this.artistTagMode,
      tagCredits: tagCredits ?? this.tagCredits,
      playbackNormalization:
          playbackNormalization ?? this.playbackNormalization,
      playbackNormalizationPreamp:
          playbackNormalizationPreamp ?? this.playbackNormalizationPreamp,
      playbackNormalizationMode:
          playbackNormalizationMode ?? this.playbackNormalizationMode,
      pauseOnMute: pauseOnMute ?? this.pauseOnMute,
      playOnHeadphonesConnected:
          playOnHeadphonesConnected ?? this.playOnHeadphonesConnected,
      gaplessPlayback: gaplessPlayback ?? this.gaplessPlayback,
      autoMix: autoMix ?? this.autoMix,
      autoMixDuration: autoMixDuration ?? this.autoMixDuration,
      autoMixEffect: autoMixEffect ?? this.autoMixEffect,
      autoMixSpeed: autoMixSpeed ?? this.autoMixSpeed,
      autoMixPitch: autoMixPitch ?? this.autoMixPitch,
      autoMixEcho: autoMixEcho ?? this.autoMixEcho,
      autoMixLowPass: autoMixLowPass ?? this.autoMixLowPass,
      usbBitPerfect: usbBitPerfect ?? this.usbBitPerfect,
      usbDirect: usbDirect ?? this.usbDirect,
      usbDsdOverPcm: usbDsdOverPcm ?? this.usbDsdOverPcm,
      usbAllowFixedVolume: usbAllowFixedVolume ?? this.usbAllowFixedVolume,
      dapExclusive: dapExclusive ?? this.dapExclusive,
      autoplay: autoplay ?? this.autoplay,
      autoplayPersonalization:
          autoplayPersonalization ?? this.autoplayPersonalization,
      songAnalysis: songAnalysis ?? this.songAnalysis,
      songAnalysisOnlyWhileCharging:
          songAnalysisOnlyWhileCharging ?? this.songAnalysisOnlyWhileCharging,
      musicHaptics: musicHaptics ?? this.musicHaptics,
      musicHapticsSensitivity:
          musicHapticsSensitivity ?? this.musicHapticsSensitivity,
      musicHapticsStrength: musicHapticsStrength ?? this.musicHapticsStrength,
      playerCoverStyle: playerCoverStyle ?? this.playerCoverStyle,
      autoHideLyricsControls:
          autoHideLyricsControls ?? this.autoHideLyricsControls,
      isFirstLaunch: isFirstLaunch ?? this.isFirstLaunch,
      checkForUpdates: checkForUpdates ?? this.checkForUpdates,
      updateChannel: updateChannel ?? this.updateChannel,
      createPlaylistFolder: createPlaylistFolder ?? this.createPlaylistFolder,
      useAlbumArtistForFolders:
          useAlbumArtistForFolders ?? this.useAlbumArtistForFolders,
      usePrimaryArtistOnly: usePrimaryArtistOnly ?? this.usePrimaryArtistOnly,
      filterContributingArtistsInAlbumArtist:
          filterContributingArtistsInAlbumArtist ??
          this.filterContributingArtistsInAlbumArtist,
      historySortMode: historySortMode ?? this.historySortMode,
      libraryQualityLabelMode:
          libraryQualityLabelMode ?? this.libraryQualityLabelMode,
      enableLogging: enableLogging ?? this.enableLogging,
      useExtensionProviders:
          useExtensionProviders ?? this.useExtensionProviders,
      downloadFallbackExtensionIds: clearDownloadFallbackExtensionIds
          ? null
          : (downloadFallbackExtensionIds ?? this.downloadFallbackExtensionIds),
      searchProvider: clearSearchProvider
          ? null
          : (searchProvider ?? this.searchProvider),
      searchViewMode: searchViewMode ?? this.searchViewMode,
      separateSingles: separateSingles ?? this.separateSingles,
      singleFilenameFormat: singleFilenameFormat ?? this.singleFilenameFormat,
      albumFolderStructure: albumFolderStructure ?? this.albumFolderStructure,
      showExtensionStore: showExtensionStore ?? this.showExtensionStore,
      heroAnimationsEnabled:
          heroAnimationsEnabled ?? this.heroAnimationsEnabled,
      forceBackdropBlur: forceBackdropBlur ?? this.forceBackdropBlur,
      extensionVerificationBrowserMode:
          extensionVerificationBrowserMode ??
          this.extensionVerificationBrowserMode,
      locale: locale ?? this.locale,
      lyricsMode: lyricsMode ?? this.lyricsMode,
      keepScreenOnLyrics: keepScreenOnLyrics ?? this.keepScreenOnLyrics,
      listeningStatisticsEnabled:
          listeningStatisticsEnabled ?? this.listeningStatisticsEnabled,
      useAllFilesAccess: useAllFilesAccess ?? this.useAllFilesAccess,
      networkCompatibilityMode:
          networkCompatibilityMode ?? this.networkCompatibilityMode,
      allowLocalNetwork: allowLocalNetwork ?? this.allowLocalNetwork,
      songLinkRegion: songLinkRegion ?? this.songLinkRegion,
      localLibraryEnabled: localLibraryEnabled ?? this.localLibraryEnabled,
      localLibraryPath: localLibraryPath ?? this.localLibraryPath,
      localLibraryShowDuplicates:
          localLibraryShowDuplicates ?? this.localLibraryShowDuplicates,
      localLibraryAutoScan: localLibraryAutoScan ?? this.localLibraryAutoScan,
      hasCompletedTutorial: hasCompletedTutorial ?? this.hasCompletedTutorial,
      lyricsProviders: lyricsProviders ?? this.lyricsProviders,
      playerMode: playerMode ?? this.playerMode,
      playerShowPronunciation:
          playerShowPronunciation ?? this.playerShowPronunciation,
      playerShowTranslation:
          playerShowTranslation ?? this.playerShowTranslation,
      playerShowVolume: playerShowVolume ?? this.playerShowVolume,
      playerShowLyricSnippet:
          playerShowLyricSnippet ?? this.playerShowLyricSnippet,
    );
  }

  factory AppSettings.fromJson(Map<String, dynamic> json) =>
      _$AppSettingsFromJson(json);
  Map<String, dynamic> toJson() => _$AppSettingsToJson(this);
}
