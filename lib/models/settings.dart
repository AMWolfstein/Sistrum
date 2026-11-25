import 'package:json_annotation/json_annotation.dart';
import 'package:sistrum/models/automix_options.dart';

part 'settings.g.dart';

/// 'default' (a card) or 'fullscreen' once chosen, else empty for the default
/// card ([AppSettings.playerCoverStyleFor]).
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

/// Volume levels, in percent, above which the volume bar can ask first.
const loudVolumeLevels = [60, 70, 80, 90];

int loudVolumeLevelFromJson(Object? value) =>
    value is int && loudVolumeLevels.contains(value) ? value : 80;

/// When connecting headphones resumes playback: never, only a recent pause
/// caused by headphones or Bluetooth going away, or on every connection
/// (which also pauses on disconnect).
const headphoneResumeModes = ['off', 'after_disconnect', 'always'];

/// Older builds stored two switches. The broad one wins, then the narrow
/// one; an unknown value reads as off.
Object? readHeadphoneResume(Map<dynamic, dynamic> json, String key) {
  final value = json[key];
  if (headphoneResumeModes.contains(value)) return value;
  if (json['playOnHeadphonesConnected'] == true) return 'always';
  if (json['resumeOnOutputReconnect'] == true) return 'after_disconnect';
  return 'off';
}

String headphoneResumeFromJson(Object? value) =>
    headphoneResumeModes.contains(value) ? value! as String : 'off';

double musicHapticsLevelFromJson(Object? value) =>
    value is num && value.isFinite ? value.toDouble().clamp(0, 1) : 0.5;

double autoplayRandomnessFromJson(Object? value) =>
    value is num && value.isFinite ? value.toDouble().clamp(0, 1) : 0;

/// Slowest and fastest playback speed, as a factor of the normal speed.
const double minPlaybackSpeed = 0.5;
const double maxPlaybackSpeed = 2;

double playbackSpeedFromJson(Object? value) => value is num && value.isFinite
    ? value.toDouble().clamp(minPlaybackSpeed, maxPlaybackSpeed)
    : 1;

/// Largest pitch shift either way, in semitones.
const double maxPlaybackPitch = 12;

/// The pitch shift in whole semitones, the steps a key changes by.
double playbackPitchFromJson(Object? value) => value is num && value.isFinite
    ? value.toDouble().roundToDouble().clamp(
        -maxPlaybackPitch,
        maxPlaybackPitch,
      )
    : 0;

/// Smallest and largest Interface size, as a factor of the normal size.
const double minInterfaceScale = 0.8;
const double maxInterfaceScale = 1.3;

double interfaceScaleFromJson(Object? value) => value is num && value.isFinite
    ? value.toDouble().clamp(minInterfaceScale, maxInterfaceScale)
    : 1;

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
  'year-asc',
  'year-desc',
  'release-newest',
  'release-oldest',
  'genre-asc',
  'genre-desc',
];

String librarySortModeFromJson(Object? value) =>
    value is String && librarySortModes.contains(value) ? value : 'latest';

String libraryAlbumSortModeFromJson(Object? value) =>
    const [
      'a-z',
      'size-desc',
      'size-asc',
      'year-asc',
      'year-desc',
    ].contains(value)
    ? value as String
    : 'a-z';

/// Offline romanization starts as the AI romanization switch was, so a
/// user who turned that off is not shown romanization they did not want.
Object? readLyricsRomanize(Map<dynamic, dynamic> json, String key) =>
    json[key] ?? json['lyricsAiRomanize'];

@JsonSerializable(explicitToJson: true)
class AppSettings {
  static const String homeFeedProviderOff = '__off__';
  final String audioQuality;
  final String downloadDirectory;
  final String downloadDirectoryBookmark;
  final String storageMode; // 'app' or 'saf'
  final String downloadTreeUri; // SAF persistable tree URI
  final bool embedLyrics;
  // Apply ReplayGain/R128 tags as volume normalization in the built-in player.
  final bool playbackNormalization;
  @JsonKey(fromJson: playbackNormalizationPreampFromJson)
  final double playbackNormalizationPreamp;
  @JsonKey(fromJson: playbackNormalizationModeFromJson)
  final String playbackNormalizationMode; // 'track' or 'album'
  // Ask before the app's volume bar goes above [loudVolumeLevel] percent.
  final bool confirmLoudVolume;
  @JsonKey(fromJson: loudVolumeLevelFromJson)
  final int loudVolumeLevel; // One of [loudVolumeLevels].
  final bool pauseOnMute;
  // One of [headphoneResumeModes].
  @JsonKey(readValue: readHeadphoneResume, fromJson: headphoneResumeFromJson)
  final String headphoneResume;
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

  /// Playback speed for every song, from [minPlaybackSpeed] to
  /// [maxPlaybackSpeed]; 1 is normal.
  @JsonKey(fromJson: playbackSpeedFromJson)
  final double playbackSpeed;

  /// True keeps the pitch while the speed changes; false lets the pitch
  /// follow the speed, as a record played faster or slower.
  final bool playbackKeepPitch;

  /// Pitch shift in whole semitones on top of [playbackKeepPitch], from
  /// -[maxPlaybackPitch] to [maxPlaybackPitch]; 0 is the original key.
  @JsonKey(fromJson: playbackPitchFromJson)
  final double playbackPitch;
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

  /// Android beta, off by default: play the music player and AutoMix through
  /// the app's own media3 engine instead of the platform MediaPlayer, which
  /// turning it off restores. Songs for the USB and AAudio outputs
  /// ([usbBitPerfect]) stay on the platform player. Applies from the next
  /// song.
  final bool media3Engine;

  /// Android, through [media3Engine] only: jump over silent passages. Off
  /// while AutoMix is on, whose fades follow the clock. While on, media3
  /// decodes to 16-bit, which its silence detection needs.
  final bool skipSilence;

  /// Android, iOS and macOS: let the system spatialize songs (Android
  /// spatialization behavior auto, Apple mono, stereo and multichannel).
  /// Off asks the system never to spatialize them. The app renders nothing.
  final bool spatialAudio;

  /// Adding a song already coming up in the queue moves that entry instead
  /// of queueing a second copy. The current and played entries stay.
  final bool queueNoDuplicates;

  /// The queue UIs stop reordering and swiping songs away; adding songs
  /// from elsewhere still works. Toggled from the queue header too.
  final bool queueLocked;

  /// Desktop: take the output device for the music player alone, bypassing
  /// the system mixer (WASAPI exclusive on Windows, hog mode on macOS, an
  /// ALSA `hw:` device on Linux). AutoMix and previews stay off while it is
  /// on.
  final bool exclusiveOutput;

  /// Linux: the ALSA device [exclusiveOutput] opens, as an mpv device name
  /// (`alsa/hw:CARD=…,DEV=…`). Empty picks the first card.
  final String exclusiveOutputDevice;
  final bool autoplay;
  final bool autoplayPersonalization;
  @JsonKey(fromJson: autoplayRandomnessFromJson)
  final double autoplayRandomness;

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

  /// An explicit auto-hide choice also applies if accessibility changes later.
  /// The default keeps playback controls available to assistive navigation.
  final bool autoHideLyricsControlsWithAccessibility;
  final bool isFirstLaunch;
  @JsonKey(fromJson: librarySortModeFromJson)
  final String historySortMode;
  @JsonKey(fromJson: libraryAlbumSortModeFromJson)
  final String libraryAlbumSortMode;

  /// Phones and tablets: a horizontal swipe on a song row plays it next or
  /// adds it to the queue.
  final bool swipeToQueue;

  /// Search results on wide screens: 'list' keeps the phone rows, 'grid'
  /// shows cover grids and shelves.
  final String searchViewMode;

  /// The Material Home's shortcuts, in order, by `HomeShortcut` name. Read
  /// them through `HomeShortcut.resolve`, which fills in unknown names.
  final List<String> homeShortcuts;

  /// Forces the shell's backdrop blur on even when the startup runtime
  /// profile disabled it for this device tier. Off means "follow the
  /// device default".
  final bool forceBackdropBlur;

  /// Interface size: scales the whole app, on top of the automatic tablet
  /// and TV scale. 1 is the normal size.
  @JsonKey(fromJson: interfaceScaleFromJson)
  final double interfaceScale;
  final String locale;
  final String lyricsMode;
  final bool keepScreenOnLyrics;

  final bool motionArtworkEnabled;

  final bool localLibraryEnabled;
  final String localLibraryPath;
  final String
  localLibraryBookmark; // Base64-encoded iOS security-scoped bookmark
  final bool localLibraryShowDuplicates;
  final String
  localLibraryAutoScan; // Auto-scan mode: 'off', 'on_open', 'daily', 'weekly'

  final bool hasCompletedTutorial;

  /// Romanize lyrics written outside the Latin alphabet on the device,
  /// where their script allows it.
  @JsonKey(readValue: readLyricsRomanize)
  final bool lyricsRomanize;

  /// Show a line's romanization as the line, with the original under it.
  final bool lyricsRomanizedMainLine;

  final bool playerShowPronunciation;
  final bool playerShowTranslation;

  /// Whether the phone Now Playing screen shows its volume slider.
  final bool playerShowVolume;

  /// Whether the Now Playing screen shows the synced line being sung under
  /// the track title. Off by default; Lyrics settings turns it on.
  final bool playerShowLyricSnippet;

  const AppSettings({
    this.audioQuality = 'LOSSLESS',
    this.downloadDirectory = '',
    this.downloadDirectoryBookmark = '',
    this.storageMode = 'app',
    this.downloadTreeUri = '',
    this.embedLyrics = true,
    this.playbackNormalization = false,
    this.playbackNormalizationPreamp = 0,
    this.playbackNormalizationMode = 'track',
    this.confirmLoudVolume = true,
    this.loudVolumeLevel = 80,
    this.pauseOnMute = true,
    this.headphoneResume = 'off',
    this.gaplessPlayback = true,
    this.autoMix = false,
    this.autoMixDuration = 0,
    this.autoMixEffect = AutoMixEffect.auto,
    this.autoMixSpeed = 1,
    this.autoMixPitch = 0,
    this.autoMixEcho = false,
    this.autoMixLowPass = false,
    this.playbackSpeed = 1,
    this.playbackKeepPitch = true,
    this.playbackPitch = 0,
    this.usbBitPerfect = false,
    this.usbDirect = false,
    this.usbDsdOverPcm = false,
    this.usbAllowFixedVolume = false,
    this.dapExclusive = false,
    this.media3Engine = false,
    this.skipSilence = false,
    this.spatialAudio = true,
    this.queueNoDuplicates = false,
    this.queueLocked = false,
    this.exclusiveOutput = false,
    this.exclusiveOutputDevice = '',
    this.autoplay = false,
    this.autoplayPersonalization = true,
    this.autoplayRandomness = 0,
    this.songAnalysis = false,
    this.songAnalysisOnlyWhileCharging = true,
    this.musicHaptics = false,
    this.musicHapticsSensitivity = 0.5,
    this.musicHapticsStrength = 0.5,
    this.playerCoverStyle = '',
    this.autoHideLyricsControls = true,
    this.autoHideLyricsControlsWithAccessibility = false,
    this.isFirstLaunch = true,
    this.historySortMode = 'latest',
    this.libraryAlbumSortMode = 'a-z',
    this.swipeToQueue = true,
    this.searchViewMode = 'list',
    this.homeShortcuts = const [
      'shuffle',
      'equalizer',
      'listeningStats',
      'loved',
    ],
    this.forceBackdropBlur = false,
    this.interfaceScale = 1,
    this.locale = 'system',
    this.lyricsMode = 'embed',
    this.keepScreenOnLyrics = true,
    this.motionArtworkEnabled = true,
    this.localLibraryEnabled = false,
    this.localLibraryPath = '',
    this.localLibraryBookmark = '',
    this.localLibraryShowDuplicates = true,
    this.localLibraryAutoScan = 'off',
    this.hasCompletedTutorial = false,
    this.lyricsRomanize = true,
    this.lyricsRomanizedMainLine = false,
    this.playerShowPronunciation = true,
    this.playerShowTranslation = true,
    this.playerShowVolume = true,
    this.playerShowLyricSnippet = false,
  });

  /// The cover art style the player shows: the one chosen, else a card.
  String playerCoverStyleFor() =>
      playerCoverStyle.isNotEmpty ? playerCoverStyle : 'default';

  bool shouldAutoHideLyricsControls({required bool accessibleNavigation}) =>
      autoHideLyricsControls &&
      (!accessibleNavigation || autoHideLyricsControlsWithAccessibility);

  AppSettings copyWith({
    String? audioQuality,
    String? downloadDirectory,
    String? downloadDirectoryBookmark,
    String? storageMode,
    String? downloadTreeUri,
    bool? embedLyrics,
    bool? playbackNormalization,
    double? playbackNormalizationPreamp,
    String? playbackNormalizationMode,
    bool? confirmLoudVolume,
    int? loudVolumeLevel,
    bool? pauseOnMute,
    String? headphoneResume,
    bool? gaplessPlayback,
    bool? autoMix,
    int? autoMixDuration,
    AutoMixEffect? autoMixEffect,
    double? autoMixSpeed,
    double? autoMixPitch,
    bool? autoMixEcho,
    bool? autoMixLowPass,
    double? playbackSpeed,
    bool? playbackKeepPitch,
    double? playbackPitch,
    bool? usbBitPerfect,
    bool? usbDirect,
    bool? usbDsdOverPcm,
    bool? usbAllowFixedVolume,
    bool? dapExclusive,
    bool? media3Engine,
    bool? skipSilence,
    bool? spatialAudio,
    bool? queueNoDuplicates,
    bool? queueLocked,
    bool? exclusiveOutput,
    String? exclusiveOutputDevice,
    bool? autoplay,
    bool? autoplayPersonalization,
    double? autoplayRandomness,
    bool? songAnalysis,
    bool? songAnalysisOnlyWhileCharging,
    bool? musicHaptics,
    double? musicHapticsSensitivity,
    double? musicHapticsStrength,
    String? playerCoverStyle,
    bool? autoHideLyricsControls,
    bool? autoHideLyricsControlsWithAccessibility,
    bool? isFirstLaunch,
    String? historySortMode,
    String? libraryAlbumSortMode,
    bool? swipeToQueue,
    String? searchViewMode,
    List<String>? homeShortcuts,
    bool? forceBackdropBlur,
    double? interfaceScale,
    String? locale,
    String? lyricsMode,
    bool? keepScreenOnLyrics,
    bool? motionArtworkEnabled,
    bool? localLibraryEnabled,
    String? localLibraryPath,
    String? localLibraryBookmark,
    bool? localLibraryShowDuplicates,
    String? localLibraryAutoScan,
    bool? hasCompletedTutorial,
    bool? lyricsRomanize,
    bool? lyricsRomanizedMainLine,
    bool? playerShowPronunciation,
    bool? playerShowTranslation,
    bool? playerShowVolume,
    bool? playerShowLyricSnippet,
  }) {
    return AppSettings(
      audioQuality: audioQuality ?? this.audioQuality,
      downloadDirectory: downloadDirectory ?? this.downloadDirectory,
      downloadDirectoryBookmark:
          downloadDirectoryBookmark ?? this.downloadDirectoryBookmark,
      storageMode: storageMode ?? this.storageMode,
      downloadTreeUri: downloadTreeUri ?? this.downloadTreeUri,
      embedLyrics: embedLyrics ?? this.embedLyrics,
      playbackNormalization:
          playbackNormalization ?? this.playbackNormalization,
      playbackNormalizationPreamp:
          playbackNormalizationPreamp ?? this.playbackNormalizationPreamp,
      playbackNormalizationMode:
          playbackNormalizationMode ?? this.playbackNormalizationMode,
      confirmLoudVolume: confirmLoudVolume ?? this.confirmLoudVolume,
      loudVolumeLevel: loudVolumeLevel ?? this.loudVolumeLevel,
      pauseOnMute: pauseOnMute ?? this.pauseOnMute,
      headphoneResume: headphoneResume ?? this.headphoneResume,
      gaplessPlayback: gaplessPlayback ?? this.gaplessPlayback,
      autoMix: autoMix ?? this.autoMix,
      autoMixDuration: autoMixDuration ?? this.autoMixDuration,
      autoMixEffect: autoMixEffect ?? this.autoMixEffect,
      autoMixSpeed: autoMixSpeed ?? this.autoMixSpeed,
      autoMixPitch: autoMixPitch ?? this.autoMixPitch,
      autoMixEcho: autoMixEcho ?? this.autoMixEcho,
      autoMixLowPass: autoMixLowPass ?? this.autoMixLowPass,
      playbackSpeed: playbackSpeed ?? this.playbackSpeed,
      playbackKeepPitch: playbackKeepPitch ?? this.playbackKeepPitch,
      playbackPitch: playbackPitch ?? this.playbackPitch,
      usbBitPerfect: usbBitPerfect ?? this.usbBitPerfect,
      usbDirect: usbDirect ?? this.usbDirect,
      usbDsdOverPcm: usbDsdOverPcm ?? this.usbDsdOverPcm,
      usbAllowFixedVolume: usbAllowFixedVolume ?? this.usbAllowFixedVolume,
      dapExclusive: dapExclusive ?? this.dapExclusive,
      media3Engine: media3Engine ?? this.media3Engine,
      skipSilence: skipSilence ?? this.skipSilence,
      spatialAudio: spatialAudio ?? this.spatialAudio,
      queueNoDuplicates: queueNoDuplicates ?? this.queueNoDuplicates,
      queueLocked: queueLocked ?? this.queueLocked,
      exclusiveOutput: exclusiveOutput ?? this.exclusiveOutput,
      exclusiveOutputDevice:
          exclusiveOutputDevice ?? this.exclusiveOutputDevice,
      autoplay: autoplay ?? this.autoplay,
      autoplayPersonalization:
          autoplayPersonalization ?? this.autoplayPersonalization,
      autoplayRandomness: autoplayRandomness ?? this.autoplayRandomness,
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
      autoHideLyricsControlsWithAccessibility:
          autoHideLyricsControlsWithAccessibility ??
          this.autoHideLyricsControlsWithAccessibility,
      isFirstLaunch: isFirstLaunch ?? this.isFirstLaunch,
      historySortMode: historySortMode ?? this.historySortMode,
      libraryAlbumSortMode: libraryAlbumSortMode ?? this.libraryAlbumSortMode,
      swipeToQueue: swipeToQueue ?? this.swipeToQueue,
      searchViewMode: searchViewMode ?? this.searchViewMode,
      homeShortcuts: homeShortcuts ?? this.homeShortcuts,
      forceBackdropBlur: forceBackdropBlur ?? this.forceBackdropBlur,
      interfaceScale: interfaceScale ?? this.interfaceScale,
      locale: locale ?? this.locale,
      lyricsMode: lyricsMode ?? this.lyricsMode,
      keepScreenOnLyrics: keepScreenOnLyrics ?? this.keepScreenOnLyrics,
      motionArtworkEnabled: motionArtworkEnabled ?? this.motionArtworkEnabled,
      localLibraryEnabled: localLibraryEnabled ?? this.localLibraryEnabled,
      localLibraryPath: localLibraryPath ?? this.localLibraryPath,
      localLibraryBookmark: localLibraryBookmark ?? this.localLibraryBookmark,
      localLibraryShowDuplicates:
          localLibraryShowDuplicates ?? this.localLibraryShowDuplicates,
      localLibraryAutoScan: localLibraryAutoScan ?? this.localLibraryAutoScan,
      hasCompletedTutorial: hasCompletedTutorial ?? this.hasCompletedTutorial,
      lyricsRomanize: lyricsRomanize ?? this.lyricsRomanize,
      lyricsRomanizedMainLine:
          lyricsRomanizedMainLine ?? this.lyricsRomanizedMainLine,
      playerShowPronunciation:
          playerShowPronunciation ?? this.playerShowPronunciation,
      playerShowTranslation:
          playerShowTranslation ?? this.playerShowTranslation,
      playerShowVolume: playerShowVolume ?? this.playerShowVolume,
      playerShowLyricSnippet:
          playerShowLyricSnippet ?? this.playerShowLyricSnippet,
    );
  }

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    return _$AppSettingsFromJson(json);
  }
  Map<String, dynamic> toJson() => {
    ..._$AppSettingsToJson(this),
    // Builds before [headphoneResume] read this switch, so a downgrade or
    // a backup opened by one keeps Always.
    'playOnHeadphonesConnected': headphoneResume == 'always',
  };
}
