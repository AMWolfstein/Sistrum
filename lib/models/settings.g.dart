// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'settings.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppSettings _$AppSettingsFromJson(Map<String, dynamic> json) => AppSettings(
  audioQuality: json['audioQuality'] as String? ?? 'LOSSLESS',
  downloadDirectory: json['downloadDirectory'] as String? ?? '',
  downloadDirectoryBookmark: json['downloadDirectoryBookmark'] as String? ?? '',
  storageMode: json['storageMode'] as String? ?? 'app',
  downloadTreeUri: json['downloadTreeUri'] as String? ?? '',
  embedMetadata: json['embedMetadata'] as bool? ?? true,
  embeddedCoverMaxDimension:
      (json['embeddedCoverMaxDimension'] as num?)?.toInt() ?? 0,
  artistTagMode: json['artistTagMode'] as String? ?? artistTagModeJoined,
  metadataSeparators: json['metadataSeparators'] == null
      ? const MetadataSeparators()
      : MetadataSeparators.fromJson(
          json['metadataSeparators'] as Map<String, dynamic>,
        ),
  embedLyrics: json['embedLyrics'] as bool? ?? true,
  embedReplayGain: json['embedReplayGain'] as bool? ?? false,
  playbackNormalization: json['playbackNormalization'] as bool? ?? false,
  playbackNormalizationPreamp: json['playbackNormalizationPreamp'] == null
      ? 0
      : playbackNormalizationPreampFromJson(
          json['playbackNormalizationPreamp'],
        ),
  playbackNormalizationMode: json['playbackNormalizationMode'] == null
      ? 'track'
      : playbackNormalizationModeFromJson(json['playbackNormalizationMode']),
  confirmLoudVolume: json['confirmLoudVolume'] as bool? ?? true,
  loudVolumeLevel: json['loudVolumeLevel'] == null
      ? 80
      : loudVolumeLevelFromJson(json['loudVolumeLevel']),
  pauseOnMute: json['pauseOnMute'] as bool? ?? true,
  headphoneResume: readHeadphoneResume(json, 'headphoneResume') == null
      ? 'off'
      : headphoneResumeFromJson(readHeadphoneResume(json, 'headphoneResume')),
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
  playbackSpeed: json['playbackSpeed'] == null
      ? 1
      : playbackSpeedFromJson(json['playbackSpeed']),
  playbackKeepPitch: json['playbackKeepPitch'] as bool? ?? true,
  playbackPitch: json['playbackPitch'] == null
      ? 0
      : playbackPitchFromJson(json['playbackPitch']),
  usbBitPerfect: json['usbBitPerfect'] as bool? ?? false,
  usbDirect: json['usbDirect'] as bool? ?? false,
  usbDsdOverPcm: json['usbDsdOverPcm'] as bool? ?? false,
  usbAllowFixedVolume: json['usbAllowFixedVolume'] as bool? ?? false,
  dapExclusive: json['dapExclusive'] as bool? ?? false,
  media3Engine: json['media3Engine'] as bool? ?? false,
  skipSilence: json['skipSilence'] as bool? ?? false,
  spatialAudio: json['spatialAudio'] as bool? ?? true,
  queueNoDuplicates: json['queueNoDuplicates'] as bool? ?? false,
  queueLocked: json['queueLocked'] as bool? ?? false,
  exclusiveOutput: json['exclusiveOutput'] as bool? ?? false,
  exclusiveOutputDevice: json['exclusiveOutputDevice'] as String? ?? '',
  autoplay: json['autoplay'] as bool? ?? false,
  autoplayPersonalization: json['autoplayPersonalization'] as bool? ?? true,
  autoplayRandomness: json['autoplayRandomness'] == null
      ? 0
      : autoplayRandomnessFromJson(json['autoplayRandomness']),
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
  autoHideLyricsControlsWithAccessibility:
      json['autoHideLyricsControlsWithAccessibility'] as bool? ?? false,
  isFirstLaunch: json['isFirstLaunch'] as bool? ?? true,
  historyViewMode: json['historyViewMode'] as String? ?? 'grid',
  historyFilterMode: json['historyFilterMode'] as String? ?? 'all',
  historySortMode: json['historySortMode'] == null
      ? 'latest'
      : librarySortModeFromJson(json['historySortMode']),
  libraryAlbumSortMode: json['libraryAlbumSortMode'] == null
      ? 'a-z'
      : libraryAlbumSortModeFromJson(json['libraryAlbumSortMode']),
  libraryArtistShowsSongs: json['libraryArtistShowsSongs'] as bool? ?? false,
  defaultLibraryView: json['defaultLibraryView'] as String? ?? 'last',
  libraryQualityLabelMode:
      json['libraryQualityLabelMode'] as String? ??
      AppSettings.libraryQualityLabelBitDepthOnly,
  swipeToQueue: json['swipeToQueue'] as bool? ?? true,
  librarySongTap:
      json['librarySongTap'] as String? ?? AppSettings.librarySongTapDetails,
  enableLogging: json['enableLogging'] as bool? ?? false,
  searchViewMode: json['searchViewMode'] as String? ?? 'list',
  homeShortcuts:
      (json['homeShortcuts'] as List<dynamic>?)
          ?.map((e) => e as String)
          .toList() ??
      const ['shuffle', 'equalizer', 'listeningStats', 'loved'],
  homeContinueListening: json['homeContinueListening'] as bool? ?? true,
  heroAnimationsEnabled: json['heroAnimationsEnabled'] as bool? ?? true,
  forceBackdropBlur: json['forceBackdropBlur'] as bool? ?? false,
  interfaceScale: json['interfaceScale'] == null
      ? 1
      : interfaceScaleFromJson(json['interfaceScale']),
  locale: json['locale'] as String? ?? 'system',
  lyricsMode: json['lyricsMode'] as String? ?? 'embed',
  keepScreenOnLyrics: json['keepScreenOnLyrics'] as bool? ?? true,
  lockScreenLyrics: json['lockScreenLyrics'] as bool? ?? false,
  motionArtworkEnabled: json['motionArtworkEnabled'] as bool? ?? true,
  saveFolderCover: json['saveFolderCover'] as bool? ?? false,
  removeEmptyFolders: json['removeEmptyFolders'] as bool? ?? true,
  listeningStatisticsEnabled:
      json['listeningStatisticsEnabled'] as bool? ?? true,
  useAllFilesAccess: json['useAllFilesAccess'] as bool? ?? false,
  localLibraryEnabled: json['localLibraryEnabled'] as bool? ?? false,
  localLibraryPath: json['localLibraryPath'] as String? ?? '',
  localLibraryBookmark: json['localLibraryBookmark'] as String? ?? '',
  localLibraryShowDuplicates:
      json['localLibraryShowDuplicates'] as bool? ?? true,
  localLibraryAutoScan: json['localLibraryAutoScan'] as String? ?? 'off',
  hasCompletedTutorial: json['hasCompletedTutorial'] as bool? ?? false,
  lyricsRomanize: readLyricsRomanize(json, 'lyricsRomanize') as bool? ?? true,
  lyricsRomanizedMainLine: json['lyricsRomanizedMainLine'] as bool? ?? false,
  playerMode: json['playerMode'] as String? ?? 'internal',
  playerShowPronunciation: json['playerShowPronunciation'] as bool? ?? true,
  playerShowTranslation: json['playerShowTranslation'] as bool? ?? true,
  playerShowVolume: json['playerShowVolume'] as bool? ?? true,
  playerShowLyricSnippet: json['playerShowLyricSnippet'] as bool? ?? false,
);

Map<String, dynamic> _$AppSettingsToJson(AppSettings instance) =>
    <String, dynamic>{
      'audioQuality': instance.audioQuality,
      'downloadDirectory': instance.downloadDirectory,
      'downloadDirectoryBookmark': instance.downloadDirectoryBookmark,
      'storageMode': instance.storageMode,
      'downloadTreeUri': instance.downloadTreeUri,
      'embedMetadata': instance.embedMetadata,
      'embeddedCoverMaxDimension': instance.embeddedCoverMaxDimension,
      'artistTagMode': instance.artistTagMode,
      'metadataSeparators': instance.metadataSeparators.toJson(),
      'embedLyrics': instance.embedLyrics,
      'embedReplayGain': instance.embedReplayGain,
      'playbackNormalization': instance.playbackNormalization,
      'playbackNormalizationPreamp': instance.playbackNormalizationPreamp,
      'playbackNormalizationMode': instance.playbackNormalizationMode,
      'confirmLoudVolume': instance.confirmLoudVolume,
      'loudVolumeLevel': instance.loudVolumeLevel,
      'pauseOnMute': instance.pauseOnMute,
      'headphoneResume': instance.headphoneResume,
      'gaplessPlayback': instance.gaplessPlayback,
      'autoMix': instance.autoMix,
      'autoMixDuration': instance.autoMixDuration,
      'autoMixEffect': _$AutoMixEffectEnumMap[instance.autoMixEffect]!,
      'autoMixSpeed': instance.autoMixSpeed,
      'autoMixPitch': instance.autoMixPitch,
      'autoMixEcho': instance.autoMixEcho,
      'autoMixLowPass': instance.autoMixLowPass,
      'playbackSpeed': instance.playbackSpeed,
      'playbackKeepPitch': instance.playbackKeepPitch,
      'playbackPitch': instance.playbackPitch,
      'usbBitPerfect': instance.usbBitPerfect,
      'usbDirect': instance.usbDirect,
      'usbDsdOverPcm': instance.usbDsdOverPcm,
      'usbAllowFixedVolume': instance.usbAllowFixedVolume,
      'dapExclusive': instance.dapExclusive,
      'media3Engine': instance.media3Engine,
      'skipSilence': instance.skipSilence,
      'spatialAudio': instance.spatialAudio,
      'queueNoDuplicates': instance.queueNoDuplicates,
      'queueLocked': instance.queueLocked,
      'exclusiveOutput': instance.exclusiveOutput,
      'exclusiveOutputDevice': instance.exclusiveOutputDevice,
      'autoplay': instance.autoplay,
      'autoplayPersonalization': instance.autoplayPersonalization,
      'autoplayRandomness': instance.autoplayRandomness,
      'songAnalysis': instance.songAnalysis,
      'songAnalysisOnlyWhileCharging': instance.songAnalysisOnlyWhileCharging,
      'musicHaptics': instance.musicHaptics,
      'musicHapticsSensitivity': instance.musicHapticsSensitivity,
      'musicHapticsStrength': instance.musicHapticsStrength,
      'playerCoverStyle': instance.playerCoverStyle,
      'autoHideLyricsControls': instance.autoHideLyricsControls,
      'autoHideLyricsControlsWithAccessibility':
          instance.autoHideLyricsControlsWithAccessibility,
      'isFirstLaunch': instance.isFirstLaunch,
      'historyViewMode': instance.historyViewMode,
      'historyFilterMode': instance.historyFilterMode,
      'historySortMode': instance.historySortMode,
      'libraryAlbumSortMode': instance.libraryAlbumSortMode,
      'libraryArtistShowsSongs': instance.libraryArtistShowsSongs,
      'defaultLibraryView': instance.defaultLibraryView,
      'libraryQualityLabelMode': instance.libraryQualityLabelMode,
      'swipeToQueue': instance.swipeToQueue,
      'librarySongTap': instance.librarySongTap,
      'enableLogging': instance.enableLogging,
      'searchViewMode': instance.searchViewMode,
      'homeShortcuts': instance.homeShortcuts,
      'homeContinueListening': instance.homeContinueListening,
      'heroAnimationsEnabled': instance.heroAnimationsEnabled,
      'forceBackdropBlur': instance.forceBackdropBlur,
      'interfaceScale': instance.interfaceScale,
      'locale': instance.locale,
      'lyricsMode': instance.lyricsMode,
      'keepScreenOnLyrics': instance.keepScreenOnLyrics,
      'lockScreenLyrics': instance.lockScreenLyrics,
      'motionArtworkEnabled': instance.motionArtworkEnabled,
      'saveFolderCover': instance.saveFolderCover,
      'removeEmptyFolders': instance.removeEmptyFolders,
      'listeningStatisticsEnabled': instance.listeningStatisticsEnabled,
      'useAllFilesAccess': instance.useAllFilesAccess,
      'localLibraryEnabled': instance.localLibraryEnabled,
      'localLibraryPath': instance.localLibraryPath,
      'localLibraryBookmark': instance.localLibraryBookmark,
      'localLibraryShowDuplicates': instance.localLibraryShowDuplicates,
      'localLibraryAutoScan': instance.localLibraryAutoScan,
      'hasCompletedTutorial': instance.hasCompletedTutorial,
      'lyricsRomanize': instance.lyricsRomanize,
      'lyricsRomanizedMainLine': instance.lyricsRomanizedMainLine,
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
