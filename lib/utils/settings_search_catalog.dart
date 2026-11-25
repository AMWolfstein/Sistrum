import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/services/spatial_audio.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/exclusive_output_labels.dart';

/// A control or submenu that can be discovered from the root Settings search.
///
/// The catalog contains only presentation metadata. The owning settings page
/// remains responsible for reading and changing the actual setting value.
class SettingsSearchEntry {
  const SettingsSearchEntry({
    required this.icon,
    required this.title,
    this.subtitle,
    this.keywords = const [],
    String? targetLabel,
  }) : targetLabel = targetLabel ?? title;

  final IconData icon;
  final String title;
  final String? subtitle;
  final List<String> keywords;

  /// Label of the actual destination widget. Usually the result title, but a
  /// hidden or toolbar-only action may point at its nearest visible section.
  final String targetLabel;

  bool matches(SettingsSearchQuery query) =>
      query.matches([title, ?subtitle, ...keywords]);
}

/// Normalizes the query once per keystroke, rather than once for every entry.
class SettingsSearchQuery {
  SettingsSearchQuery(String value)
    : terms = value
          .trim()
          .toLowerCase()
          .split(RegExp(r'\s+'))
          .where((term) => term.isNotEmpty)
          .toList(growable: false);

  final List<String> terms;

  bool get isEmpty => terms.isEmpty;

  bool matches(Iterable<String> values) {
    if (isEmpty) return true;
    final haystack = values.join(' ').toLowerCase();
    return terms.every(haystack.contains);
  }
}

/// Localized index of controls found inside every first-level settings page.
class SettingsSearchCatalog {
  SettingsSearchCatalog(AppLocalizations l10n, {bool androidAudio = false})
    : appearance = [
        SettingsSearchEntry(
          icon: Icons.rounded_corner,
          title: l10n.appearanceGlassOutlines,
          subtitle: l10n.appearanceGlassOutlinesSubtitle,
          keywords: const [
            'mornye',
            'liquid glass',
            'border',
            'rim',
            'outline',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.color_lens_outlined,
          title: l10n.sectionColor,
          keywords: const ['palette', 'accent'],
        ),
        SettingsSearchEntry(
          icon: Icons.contrast,
          title: l10n.sectionTheme,
          subtitle:
              '${l10n.appearanceThemeSystem}, ${l10n.appearanceThemeLight}, '
              '${l10n.appearanceThemeDark}',
          keywords: const ['light mode', 'dark mode'],
        ),
        SettingsSearchEntry(
          icon: Icons.blur_on,
          title: l10n.appearanceGlassMode,
          subtitle: l10n.appearanceGlassModeSubtitle,
          keywords: const [
            'liquid glass',
            'none',
            'blur',
            'performance',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.format_size,
          title: l10n.appearanceInterfaceSize,
          subtitle: l10n.appearanceInterfaceSizeDescription,
          keywords: const ['scale', 'zoom', 'ui size', 'density', 'skala'],
        ),
        SettingsSearchEntry(
          icon: Icons.language,
          title: l10n.appearanceLanguage,
          keywords: const ['locale', 'translation'],
        ),
      ],
      library = [
        if (!isDesktopPlatform && !isAndroidTv)
          SettingsSearchEntry(
            icon: Icons.queue_music,
            title: l10n.swipeToQueue,
            subtitle: l10n.swipeToQueueSubtitle,
            keywords: const ['gesture', 'play next', 'add to queue'],
          ),
        SettingsSearchEntry(
          icon: Icons.library_music_outlined,
          title: l10n.libraryEnableLocalLibrary,
          subtitle: l10n.libraryEnableLocalLibrarySubtitle,
          keywords: const ['local music'],
        ),
        SettingsSearchEntry(
          icon: Icons.folder_outlined,
          title: l10n.libraryFolder,
          keywords: const ['local path', 'scan folder'],
        ),
        SettingsSearchEntry(
          icon: Icons.content_copy_outlined,
          title: l10n.libraryShowDuplicateIndicator,
          subtitle: l10n.libraryShowDuplicateIndicatorSubtitle,
          keywords: const ['duplicate badge'],
        ),
        SettingsSearchEntry(
          icon: Icons.difference_outlined,
          title: l10n.libraryReviewDuplicates,
          subtitle: l10n.libraryReviewDuplicatesSubtitle,
          keywords: const ['duplicate songs'],
        ),
        SettingsSearchEntry(
          icon: Icons.autorenew_rounded,
          title: l10n.libraryAutoScan,
          keywords: const ['automatic scan', 'daily', 'weekly'],
        ),
        SettingsSearchEntry(
          icon: Icons.refresh,
          title: l10n.libraryScan,
          subtitle: l10n.libraryScanSubtitle,
          keywords: const ['rescan', 'refresh music'],
        ),
        SettingsSearchEntry(
          icon: Icons.sync,
          title: l10n.libraryForceFullScan,
          subtitle: l10n.libraryForceFullScanSubtitle,
          keywords: const ['rescan all'],
        ),
        SettingsSearchEntry(
          icon: Icons.travel_explore,
          title: l10n.libraryReEnrichAll,
          subtitle: l10n.libraryReEnrichAllSubtitle,
          keywords: const [
            're-enrich',
            'reenrich',
            'metadata',
            'tags',
            'retag',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.image_search_outlined,
          title: l10n.libraryFindArtwork,
          subtitle: l10n.libraryFindArtworkSubtitle,
          keywords: const [
            'album art',
            'cover',
            'artwork',
            'animated cover',
            'motion cover',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.cleaning_services_outlined,
          title: l10n.libraryCleanupMissingFiles,
          subtitle: l10n.libraryCleanupMissingFilesSubtitle,
          keywords: const ['remove missing'],
        ),
        SettingsSearchEntry(
          icon: Icons.manage_search,
          title: l10n.libraryFindMovedDownloads,
          subtitle: l10n.libraryFindMovedDownloadsSubtitle,
          keywords: const ['moved files', 'relink', 'resync', 'missing files'],
        ),
        SettingsSearchEntry(
          icon: Icons.delete_outline,
          title: l10n.libraryClear,
          subtitle: l10n.libraryClearSubtitle,
          keywords: const ['reset local library'],
        ),
      ],
      playback = [
        SettingsSearchEntry(
          icon: Icons.history_rounded,
          title: 'Last.fm',
          subtitle: l10n.lastfmDescription,
          keywords: const ['lastfm', 'scrobble', 'scrobbling', 'now playing'],
        ),
        SettingsSearchEntry(
          icon: Icons.history_rounded,
          title: 'Libre.fm',
          subtitle: l10n.scrobbleServiceDescription('Libre.fm'),
          keywords: const ['librefm', 'libre fm', 'scrobble', 'scrobbling'],
        ),
        SettingsSearchEntry(
          icon: Icons.history_rounded,
          title: 'ListenBrainz',
          subtitle: l10n.scrobbleServiceDescription('ListenBrainz'),
          keywords: const [
            'listenbrainz',
            'listen brainz',
            'musicbrainz',
            'scrobble',
            'scrobbling',
            'listens',
            'token',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.equalizer,
          title: l10n.equalizerTitle,
          subtitle: l10n.equalizerDescription,
          keywords: const [
            'eq',
            'equalizer',
            'bass',
            'treble',
            'preamp',
            'compressor',
            'limiter',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.speed,
          title: l10n.playbackTempoTitle,
          subtitle: l10n.playbackTempoDescription,
          keywords: const [
            'speed',
            'tempo',
            'pitch',
            'key',
            'semitone',
            'faster',
            'slower',
            'nightcore',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.playlist_play_rounded,
          title: l10n.autoplayFromLibrary,
          subtitle: l10n.autoplayDescription,
          keywords: const ['autoplay', 'queue', 'recommendation'],
        ),
        SettingsSearchEntry(
          icon: Icons.shuffle_rounded,
          title: l10n.autoplayRandomness,
          subtitle: l10n.autoplayRandomnessDescription,
          keywords: const ['random', 'variety', 'repeat', 'recent', 'shuffle'],
        ),
        SettingsSearchEntry(
          icon: Icons.auto_awesome_outlined,
          title: l10n.autoplayPersonalization,
          subtitle: l10n.autoplayPersonalizationDescription,
          keywords: const ['machine learning', 'recommendation', 'personal'],
        ),
        SettingsSearchEntry(
          icon: Icons.multitrack_audio,
          title: l10n.songAnalysis,
          subtitle: l10n.songAnalysisDescription,
          keywords: const ['sound', 'tempo', 'bpm', 'key', 'recommendation'],
        ),
        SettingsSearchEntry(
          icon: Icons.playlist_remove,
          title: l10n.queueNoDuplicates,
          subtitle: l10n.queueNoDuplicatesDescription,
          keywords: const ['duplicate', 'queue', 'twice', 'play next', 'once'],
        ),
        SettingsSearchEntry(
          icon: Icons.lock_outline,
          title: l10n.queueLock,
          subtitle: l10n.queueLockDescription,
          keywords: const ['lock', 'queue', 'reorder', 'swipe', 'accidental'],
        ),
        SettingsSearchEntry(
          icon: Icons.motion_photos_on_outlined,
          title: l10n.motionArtwork,
          subtitle: l10n.motionArtworkDescription,
          keywords: const ['canvas', 'video', 'animation'],
        ),
        SettingsSearchEntry(
          icon: Icons.graphic_eq,
          title: l10n.libraryPlaybackNormalization,
          subtitle: l10n.libraryPlaybackNormalizationSubtitle,
          keywords: const [
            'replaygain',
            'volume',
            'loudness',
            'lufs',
            'preamp',
            'album gain',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.volume_up_outlined,
          title: l10n.confirmLoudVolume,
          subtitle: l10n.confirmLoudVolumeDescription,
          keywords: const [
            'volume',
            'limit',
            'limiter',
            'loud',
            'hearing',
            'ears',
            'safe',
            'warning',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.fullscreen,
          title: l10n.playerCoverStyle,
          subtitle: l10n.playerCoverStyleFullScreenDescription,
          keywords: const ['artwork', 'cover', 'full screen', 'immersive'],
        ),
        if (!isDesktopPlatform)
          SettingsSearchEntry(
            icon: Icons.fullscreen,
            title: l10n.autoHideLyricsControls,
            subtitle: l10n.autoHideLyricsControlsDescription,
            keywords: const [
              'lyrics',
              'fullscreen',
              '5 seconds',
              'controls',
              'idle',
            ],
          ),
        SettingsSearchEntry(
          icon: Icons.vibration,
          title: l10n.musicHaptics,
          subtitle: l10n.musicHapticsDescription,
          keywords: const ['haptic', 'vibrate', 'vibration', 'beat', 'getar'],
        ),
        SettingsSearchEntry(
          icon: Icons.volume_off_outlined,
          title: l10n.playbackPauseOnMute,
          subtitle: l10n.playbackPauseOnMuteDescription,
          keywords: const ['mute', 'volume', 'pause', 'resume'],
        ),
        if (!isDesktopPlatform)
          SettingsSearchEntry(
            icon: Icons.headphones_outlined,
            title: l10n.playbackOnHeadphonesConnected,
            subtitle: l10n.playbackHeadphonesChoices,
            keywords: const [
              'earphone',
              'headphone',
              'car',
              'bluetooth',
              'connect',
              'reconnect',
              'disconnect',
              'resume',
              'car',
            ],
          ),
        SettingsSearchEntry(
          icon: Icons.linear_scale,
          title: l10n.gaplessPlayback,
          subtitle: l10n.gaplessPlaybackDescription,
          keywords: const ['gapless', 'gap', 'silence', 'transition', 'album'],
        ),
        SettingsSearchEntry(
          icon: Icons.compare_arrows,
          title: 'AutoMix',
          subtitle: l10n.autoMixDescription,
          keywords: const [
            'crossfade',
            'beat matching',
            'duration',
            'pitch',
            'echo',
            'filter',
            'speed',
          ],
        ),
        if (androidAudio) ...[
          SettingsSearchEntry(
            icon: Icons.play_circle_outline,
            title: l10n.newPlaybackEngine,
            subtitle: l10n.newPlaybackEngineDescription,
            keywords: const [
              'engine',
              'beta',
              'crackle',
              'static',
              'distortion',
              'media player',
            ],
          ),
          if (!isAndroidTv)
            SettingsSearchEntry(
              icon: Icons.fast_forward_outlined,
              title: l10n.skipSilence,
              subtitle: l10n.skipSilenceDescription,
              keywords: const ['silence', 'quiet', 'gap', 'skip', 'hening'],
            ),
          SettingsSearchEntry(
            icon: Icons.usb,
            title: l10n.usbBitPerfect,
            subtitle: l10n.usbBitPerfectDescription,
            keywords: const ['usb', 'dac', 'dsd', 'dop', 'hardware volume'],
          ),
          SettingsSearchEntry(
            icon: Icons.headphones,
            title: l10n.dapExclusive,
            subtitle: l10n.dapExclusiveDescription,
            keywords: const ['oboe', 'aaudio', 'exclusive', 'hi-res'],
            targetLabel: l10n.usbBitPerfect,
          ),
        ],
        // Windows and Linux leave spatial audio to the system; Android TV
        // output is HDMI or a soundbar.
        if ((androidAudio ||
                spatialAudioPlatform != SpatialAudioPlatform.none) &&
            !isAndroidTv)
          SettingsSearchEntry(
            icon: Icons.spatial_audio_outlined,
            title: l10n.spatialAudio,
            subtitle: l10n.spatialAudioDescription,
            keywords: const [
              'spatial',
              'surround',
              'head tracking',
              'dolby atmos',
              '3d',
              'spasial',
            ],
          ),
        if (isDesktopPlatform)
          SettingsSearchEntry(
            icon: Icons.speaker_outlined,
            title: exclusiveOutputLabels(l10n).title,
            subtitle: exclusiveOutputLabels(l10n).description,
            keywords: const [
              'exclusive',
              'wasapi',
              'hog mode',
              'alsa',
              'bit-perfect',
              'mixer',
            ],
          ),
      ],
      lyrics = [
        SettingsSearchEntry(
          icon: Icons.light_mode_outlined,
          title: l10n.lyricsKeepScreenOn,
          subtitle: l10n.lyricsKeepScreenOnSubtitle,
          keywords: const ['screen timeout', 'awake', 'sleep', 'layar'],
        ),
        SettingsSearchEntry(
          icon: Icons.lyrics_outlined,
          title: l10n.lyricsPlayerPreview,
          subtitle: l10n.lyricsPlayerPreviewSubtitle,
          keywords: const [
            'lyric preview',
            'snippet',
            'now playing',
            'karaoke',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.record_voice_over_outlined,
          title: l10n.lyricsRomanize,
          subtitle: l10n.lyricsRomanizeSubtitle,
          keywords: const [
            'romanization',
            'pronunciation',
            'romaji',
            'pinyin',
            'transliteration',
            'latin',
            'offline',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.swap_vert_rounded,
          title: l10n.lyricsRomanizedMainLine,
          subtitle: l10n.lyricsRomanizedMainLineSubtitle,
          keywords: const ['romanization', 'romaji', 'pinyin', 'main line'],
        ),
        SettingsSearchEntry(
          icon: Icons.auto_awesome_outlined,
          title: l10n.lyricsAiTranslation,
          keywords: const [
            'translate lyrics',
            'api key',
            'openai',
            'gemini',
            'claude',
            'openrouter',
            'ollama',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.subtitles_outlined,
          title: l10n.optionsEmbedLyrics,
          keywords: const ['write lyrics', 'lrc'],
        ),
        SettingsSearchEntry(
          icon: Icons.lyrics_outlined,
          title: l10n.lyricsMode,
          keywords: const ['synced', 'plain', 'lyrics type'],
        ),
        SettingsSearchEntry(
          icon: Icons.translate_outlined,
          title: l10n.downloadNeteaseIncludeTranslation,
          keywords: const ['translated lyrics'],
        ),
        SettingsSearchEntry(
          icon: Icons.text_fields_outlined,
          title: l10n.downloadNeteaseIncludeRomanization,
          keywords: const ['romanized lyrics', 'latin'],
        ),
        SettingsSearchEntry(
          icon: Icons.record_voice_over_outlined,
          title: l10n.downloadAppleQqMultiPerson,
          keywords: const ['duet', 'multi singer'],
        ),
        SettingsSearchEntry(
          icon: Icons.graphic_eq_outlined,
          title: l10n.downloadAppleElrcWordSync,
          keywords: const ['word synced', 'elrc'],
        ),
        SettingsSearchEntry(
          icon: Icons.language_outlined,
          title: l10n.downloadMusixmatchLanguage,
          keywords: const ['lyrics locale'],
        ),
      ],
      download = [
        SettingsSearchEntry(
          icon: Icons.extension_outlined,
          title: l10n.sectionService,
          keywords: const ['download provider', 'default service'],
        ),
        SettingsSearchEntry(
          icon: Icons.tune,
          title: l10n.downloadAskBeforeDownload,
          subtitle: l10n.downloadAskQualitySubtitle,
          keywords: const ['quality picker', 'ask quality'],
        ),
        SettingsSearchEntry(
          icon: Icons.audiotrack,
          title: l10n.downloadLossyFormat,
          keywords: const ['mp3', 'aac', 'opus'],
        ),
        SettingsSearchEntry(
          icon: Icons.wifi,
          title: l10n.settingsDownloadNetwork,
          subtitle: l10n.settingsDownloadNetworkSubtitle,
          keywords: const ['wifi only', 'mobile data'],
        ),
        SettingsSearchEntry(
          icon: Icons.downloading_outlined,
          title: l10n.downloadNativeWorker,
          subtitle: l10n.downloadNativeWorkerSubtitle,
          keywords: const ['background worker', 'android worker'],
        ),
        SettingsSearchEntry(
          icon: Icons.security_outlined,
          title: l10n.downloadNetworkCompatibilityMode,
          keywords: const ['network fallback', 'connection compatibility'],
        ),
        SettingsSearchEntry(
          icon: Icons.lan_outlined,
          title: l10n.downloadAllowLocalNetwork,
          keywords: const ['lan', 'private network'],
        ),
        SettingsSearchEntry(
          icon: Icons.manage_search,
          title: l10n.optionsPrimaryProvider,
          subtitle: l10n.optionsPrimaryProviderSubtitle,
          keywords: const ['search source', 'metadata source'],
        ),
        SettingsSearchEntry(
          icon: Icons.tab_outlined,
          title: l10n.optionsDefaultSearchTab,
          subtitle: l10n.optionsDefaultSearchTabSubtitle,
          keywords: const ['track album artist playlist'],
        ),
        SettingsSearchEntry(
          icon: Icons.sync,
          title: l10n.optionsAutoFallback,
          subtitle: l10n.optionsAutoFallbackSubtitle,
          keywords: const ['automatic provider fallback'],
        ),
        SettingsSearchEntry(
          icon: Icons.extension_outlined,
          title: l10n.downloadFallbackExtensions,
          subtitle: l10n.downloadFallbackExtensionsSubtitle,
          keywords: const ['provider order'],
        ),
        SettingsSearchEntry(
          icon: Icons.public,
          title: l10n.downloadSongLinkRegion,
          keywords: const ['country', 'region'],
        ),
        SettingsSearchEntry(
          icon: Icons.file_download_outlined,
          title: l10n.settingsAutoExportFailed,
          subtitle: l10n.settingsAutoExportFailedSubtitle,
          keywords: const ['failed downloads', 'export errors'],
        ),
      ],
      cache = [
        SettingsSearchEntry(
          icon: Icons.folder_outlined,
          title: l10n.cacheAppDirectory,
          subtitle: l10n.cacheAppDirectoryDesc,
          keywords: const ['application cache'],
        ),
        SettingsSearchEntry(
          icon: Icons.timer_outlined,
          title: l10n.cacheTempDirectory,
          subtitle: l10n.cacheTempDirectoryDesc,
          keywords: const ['temporary files'],
        ),
        SettingsSearchEntry(
          icon: Icons.library_music_outlined,
          title: l10n.cacheLibraryCover,
          subtitle: l10n.cacheLibraryCoverDesc,
          keywords: const ['local artwork cache'],
        ),
        SettingsSearchEntry(
          icon: Icons.graphic_eq_outlined,
          title: l10n.cacheAudioAnalysis,
          subtitle: l10n.cacheAudioAnalysisDesc,
          keywords: const ['spectrogram cache'],
        ),
        SettingsSearchEntry(
          icon: Icons.cleaning_services_outlined,
          title: l10n.cacheCleanupUnused,
          subtitle: l10n.cacheCleanupUnusedDesc,
          keywords: const ['clear cache', 'free space'],
        ),
        SettingsSearchEntry(
          icon: Icons.delete_sweep_outlined,
          title: l10n.cacheClearAll,
          targetLabel: l10n.cacheSectionMaintenance,
          keywords: const ['delete all cache'],
        ),
      ],
      backup = [
        SettingsSearchEntry(
          icon: Icons.vpn_key_outlined,
          title: l10n.backupIncludeSecrets,
          subtitle: l10n.backupIncludeSecretsDescription,
          keywords: const ['tokens', 'credentials'],
        ),
        SettingsSearchEntry(
          icon: Icons.ios_share,
          title: l10n.backupExportButton,
          subtitle: l10n.backupExportSectionDescription,
          keywords: const ['create backup', 'save settings'],
        ),
        SettingsSearchEntry(
          icon: Icons.settings_backup_restore,
          title: l10n.backupImportButton,
          subtitle: l10n.backupImportSectionDescription,
          keywords: const ['restore backup', 'load settings'],
        ),
      ],
      logs = [
        SettingsSearchEntry(
          icon: Icons.filter_list,
          title: l10n.logFilterLevel,
          subtitle: l10n.logFilterBySeverity,
          targetLabel: l10n.logFilterSection,
          keywords: const ['severity', 'error warning debug'],
        ),
        SettingsSearchEntry(
          icon: Icons.search,
          title: l10n.logSearchHint,
          targetLabel: l10n.logFilterSection,
          keywords: const ['find logs'],
        ),
        SettingsSearchEntry(
          icon: Icons.copy,
          title: l10n.logCopyLogs,
          targetLabel: l10n.logFilterSection,
          keywords: const ['clipboard'],
        ),
        SettingsSearchEntry(
          icon: Icons.share,
          title: l10n.logShareLogs,
          targetLabel: l10n.logFilterSection,
          keywords: const ['export logs'],
        ),
        SettingsSearchEntry(
          icon: Icons.delete_outline,
          title: l10n.logClearLogs,
          targetLabel: l10n.logFilterSection,
          keywords: const ['delete logs'],
        ),
      ],
      about = [
        SettingsSearchEntry(
          icon: Icons.info_outline,
          title: l10n.aboutVersion,
          keywords: const ['build number', 'app version'],
        ),
      ],
      performance = [
        SettingsSearchEntry(
          icon: Icons.speed_outlined,
          title: l10n.perfHubTitle,
          subtitle: l10n.perfHubSubtitle,
          keywords: const [
            'fps',
            'frame',
            'jank',
            'render',
            'refresh',
            'budget',
          ],
        ),
        SettingsSearchEntry(
          icon: Icons.compare_arrows,
          title: l10n.perfHubCompareButton,
          targetLabel: l10n.perfHubCompareSection,
          keywords: const ['liquid none', 'glass mode benchmark'],
        ),
      ];

  final List<SettingsSearchEntry> appearance;
  final List<SettingsSearchEntry> library;
  final List<SettingsSearchEntry> playback;
  final List<SettingsSearchEntry> lyrics;
  final List<SettingsSearchEntry> download;
  final List<SettingsSearchEntry> cache;
  final List<SettingsSearchEntry> backup;
  final List<SettingsSearchEntry> logs;
  final List<SettingsSearchEntry> about;
  final List<SettingsSearchEntry> performance;
}
