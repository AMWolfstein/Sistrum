// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'settings.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppSettings _$AppSettingsFromJson(Map<String, dynamic> json) => AppSettings(
  downloadDirectory: json['downloadDirectory'] as String? ?? '',
  storageMode: json['storageMode'] as String? ?? 'app',
  downloadTreeUri: json['downloadTreeUri'] as String? ?? '',
  autoFallback: json['autoFallback'] as bool? ?? true,
  artistTagMode: json['artistTagMode'] as String? ?? artistTagModeJoined,
  tagCredits: json['tagCredits'] == null
      ? const TagCreditSettings()
      : TagCreditSettings.fromJson(json['tagCredits'] as Map<String, dynamic>),
  playbackNormalization: json['playbackNormalization'] as bool? ?? false,
  playbackNormalizationPreamp: json['playbackNormalizationPreamp'] == null
      ? 0
      : playbackNormalizationPreampFromJson(
          json['playbackNormalizationPreamp'],
        ),
  playbackNormalizationMode: json['playbackNormalizationMode'] == null
      ? 'track'
      : playbackNormalizationModeFromJson(json['playbackNormalizationMode']),
  pauseOnMute: json['pauseOnMute'] as bool? ?? true,
  playOnHeadphonesConnected:
      json['playOnHeadphonesConnected'] as bool? ?? false,
  gaplessPlayback: json['gaplessPlayback'] as bool? ?? true,
  autoMix: json['autoMix'] as bool? ?? false,
  autoMixDuration: json['autoMixDuration'] == null
      ? 0
      : autoMixDurationFromJson(json['autoMixDuration']),
  autoMixEffect:
      $enumDecodeNullable(
        _$AutoMixEffectEnumMap,
        json['autoMixEffect'],
        unknownValue: AutoMixEffect.auto,
      ) ??
      AutoMixEffect.auto,
  autoMixSpeed: json['autoMixSpeed'] == null
      ? 1
      : autoMixSpeedFromJson(json['autoMixSpeed']),
  autoMixPitch: json['autoMixPitch'] == null
      ? 0
      : autoMixPitchFromJson(json['autoMixPitch']),
  autoMixEcho: json['autoMixEcho'] as bool? ?? false,
  autoMixLowPass: json['autoMixLowPass'] as bool? ?? false,
  usbBitPerfect: json['usbBitPerfect'] as bool? ?? false,
  usbDirect: json['usbDirect'] as bool? ?? false,
  usbDsdOverPcm: json['usbDsdOverPcm'] as bool? ?? false,
  usbAllowFixedVolume: json['usbAllowFixedVolume'] as bool? ?? false,
  dapExclusive: json['dapExclusive'] as bool? ?? false,
  autoplay: json['autoplay'] as bool? ?? false,
  autoplayPersonalization: json['autoplayPersonalization'] as bool? ?? true,
  songAnalysis: json['songAnalysis'] as bool? ?? false,
  songAnalysisOnlyWhileCharging:
      json['songAnalysisOnlyWhileCharging'] as bool? ?? true,
  musicHaptics: json['musicHaptics'] as bool? ?? false,
  musicHapticsSensitivity: json['musicHapticsSensitivity'] == null
      ? 0.5
      : musicHapticsLevelFromJson(json['musicHapticsSensitivity']),
  musicHapticsStrength: json['musicHapticsStrength'] == null
      ? 0.5
      : musicHapticsLevelFromJson(json['musicHapticsStrength']),
  playerCoverStyle: json['playerCoverStyle'] == null
      ? ''
      : playerCoverStyleFromJson(json['playerCoverStyle']),
  autoHideLyricsControls: json['autoHideLyricsControls'] as bool? ?? true,
  isFirstLaunch: json['isFirstLaunch'] as bool? ?? true,
  checkForUpdates: json['checkForUpdates'] as bool? ?? true,
  updateChannel: json['updateChannel'] as String? ?? 'stable',
  createPlaylistFolder: json['createPlaylistFolder'] as bool? ?? false,
  useAlbumArtistForFolders: json['useAlbumArtistForFolders'] as bool? ?? true,
  usePrimaryArtistOnly: json['usePrimaryArtistOnly'] as bool? ?? false,
  filterContributingArtistsInAlbumArtist:
      json['filterContributingArtistsInAlbumArtist'] as bool? ?? false,
  historySortMode: json['historySortMode'] == null
      ? 'latest'
      : librarySortModeFromJson(json['historySortMode']),
  libraryQualityLabelMode:
      json['libraryQualityLabelMode'] as String? ??
      AppSettings.libraryQualityLabelBitDepthOnly,
  enableLogging: json['enableLogging'] as bool? ?? false,
  useExtensionProviders: json['useExtensionProviders'] as bool? ?? true,
  downloadFallbackExtensionIds:
      (json['downloadFallbackExtensionIds'] as List<dynamic>?)
          ?.map((e) => e as String)
          .toList(),
  searchProvider: json['searchProvider'] as String?,
  searchViewMode: json['searchViewMode'] as String? ?? 'list',
  separateSingles: json['separateSingles'] as bool? ?? false,
  singleFilenameFormat:
      json['singleFilenameFormat'] as String? ?? '{title} - {artist}',
  albumFolderStructure:
      json['albumFolderStructure'] as String? ?? 'artist_album',
  showExtensionStore: json['showExtensionStore'] as bool? ?? true,
  heroAnimationsEnabled: json['heroAnimationsEnabled'] as bool? ?? true,
  forceBackdropBlur: json['forceBackdropBlur'] as bool? ?? false,
  extensionVerificationBrowserMode:
      json['extensionVerificationBrowserMode'] as String? ?? 'in_app_first',
  locale: json['locale'] as String? ?? 'system',
  lyricsMode: json['lyricsMode'] as String? ?? 'embed',
  keepScreenOnLyrics: json['keepScreenOnLyrics'] as bool? ?? true,
  listeningStatisticsEnabled:
      json['listeningStatisticsEnabled'] as bool? ?? true,
  useAllFilesAccess: json['useAllFilesAccess'] as bool? ?? false,
  networkCompatibilityMode: json['networkCompatibilityMode'] as bool? ?? false,
  allowLocalNetwork: json['allowLocalNetwork'] as bool? ?? false,
  songLinkRegion: json['songLinkRegion'] as String? ?? 'US',
  localLibraryEnabled: json['localLibraryEnabled'] as bool? ?? false,
  localLibraryPath: json['localLibraryPath'] as String? ?? '',
  localLibraryShowDuplicates:
      json['localLibraryShowDuplicates'] as bool? ?? true,
  localLibraryAutoScan: json['localLibraryAutoScan'] as String? ?? 'off',
  hasCompletedTutorial: json['hasCompletedTutorial'] as bool? ?? false,
  lyricsProviders:
      (json['lyricsProviders'] as List<dynamic>?)
          ?.map((e) => e as String)
          .toList() ??
      const ['apple_music', 'lrcred', 'lyricsplus', 'lrclib'],
  playerMode: json['playerMode'] as String? ?? 'internal',
  playerShowPronunciation: json['playerShowPronunciation'] as bool? ?? true,
  playerShowTranslation: json['playerShowTranslation'] as bool? ?? true,
  playerShowVolume: json['playerShowVolume'] as bool? ?? true,
  playerShowLyricSnippet: json['playerShowLyricSnippet'] as bool? ?? true,
);

Map<String, dynamic> _$AppSettingsToJson(
  AppSettings instance,
) => <String, dynamic>{
  'downloadDirectory': instance.downloadDirectory,
  'storageMode': instance.storageMode,
  'downloadTreeUri': instance.downloadTreeUri,
  'autoFallback': instance.autoFallback,
  'artistTagMode': instance.artistTagMode,
  'tagCredits': instance.tagCredits.toJson(),
  'playbackNormalization': instance.playbackNormalization,
  'playbackNormalizationPreamp': instance.playbackNormalizationPreamp,
  'playbackNormalizationMode': instance.playbackNormalizationMode,
  'pauseOnMute': instance.pauseOnMute,
  'playOnHeadphonesConnected': instance.playOnHeadphonesConnected,
  'gaplessPlayback': instance.gaplessPlayback,
  'autoMix': instance.autoMix,
  'autoMixDuration': instance.autoMixDuration,
  'autoMixEffect': _$AutoMixEffectEnumMap[instance.autoMixEffect]!,
  'autoMixSpeed': instance.autoMixSpeed,
  'autoMixPitch': instance.autoMixPitch,
  'autoMixEcho': instance.autoMixEcho,
  'autoMixLowPass': instance.autoMixLowPass,
  'usbBitPerfect': instance.usbBitPerfect,
  'usbDirect': instance.usbDirect,
  'usbDsdOverPcm': instance.usbDsdOverPcm,
  'usbAllowFixedVolume': instance.usbAllowFixedVolume,
  'dapExclusive': instance.dapExclusive,
  'autoplay': instance.autoplay,
  'autoplayPersonalization': instance.autoplayPersonalization,
  'songAnalysis': instance.songAnalysis,
  'songAnalysisOnlyWhileCharging': instance.songAnalysisOnlyWhileCharging,
  'musicHaptics': instance.musicHaptics,
  'musicHapticsSensitivity': instance.musicHapticsSensitivity,
  'musicHapticsStrength': instance.musicHapticsStrength,
  'playerCoverStyle': instance.playerCoverStyle,
  'autoHideLyricsControls': instance.autoHideLyricsControls,
  'isFirstLaunch': instance.isFirstLaunch,
  'checkForUpdates': instance.checkForUpdates,
  'updateChannel': instance.updateChannel,
  'createPlaylistFolder': instance.createPlaylistFolder,
  'useAlbumArtistForFolders': instance.useAlbumArtistForFolders,
  'usePrimaryArtistOnly': instance.usePrimaryArtistOnly,
  'filterContributingArtistsInAlbumArtist':
      instance.filterContributingArtistsInAlbumArtist,
  'historySortMode': instance.historySortMode,
  'libraryQualityLabelMode': instance.libraryQualityLabelMode,
  'enableLogging': instance.enableLogging,
  'useExtensionProviders': instance.useExtensionProviders,
  'downloadFallbackExtensionIds': instance.downloadFallbackExtensionIds,
  'searchProvider': instance.searchProvider,
  'searchViewMode': instance.searchViewMode,
  'separateSingles': instance.separateSingles,
  'singleFilenameFormat': instance.singleFilenameFormat,
  'albumFolderStructure': instance.albumFolderStructure,
  'showExtensionStore': instance.showExtensionStore,
  'heroAnimationsEnabled': instance.heroAnimationsEnabled,
  'forceBackdropBlur': instance.forceBackdropBlur,
  'extensionVerificationBrowserMode': instance.extensionVerificationBrowserMode,
  'locale': instance.locale,
  'lyricsMode': instance.lyricsMode,
  'keepScreenOnLyrics': instance.keepScreenOnLyrics,
  'listeningStatisticsEnabled': instance.listeningStatisticsEnabled,
  'useAllFilesAccess': instance.useAllFilesAccess,
  'networkCompatibilityMode': instance.networkCompatibilityMode,
  'allowLocalNetwork': instance.allowLocalNetwork,
  'songLinkRegion': instance.songLinkRegion,
  'localLibraryEnabled': instance.localLibraryEnabled,
  'localLibraryPath': instance.localLibraryPath,
  'localLibraryShowDuplicates': instance.localLibraryShowDuplicates,
  'localLibraryAutoScan': instance.localLibraryAutoScan,
  'hasCompletedTutorial': instance.hasCompletedTutorial,
  'lyricsProviders': instance.lyricsProviders,
  'playerMode': instance.playerMode,
  'playerShowPronunciation': instance.playerShowPronunciation,
  'playerShowTranslation': instance.playerShowTranslation,
  'playerShowVolume': instance.playerShowVolume,
  'playerShowLyricSnippet': instance.playerShowLyricSnippet,
};

const _$AutoMixEffectEnumMap = {
  AutoMixEffect.auto: 'auto',
  AutoMixEffect.crossfade: 'crossfade',
  AutoMixEffect.muffled: 'muffled',
  AutoMixEffect.echo: 'echo',
  AutoMixEffect.pitch: 'pitch',
  AutoMixEffect.custom: 'custom',
};
