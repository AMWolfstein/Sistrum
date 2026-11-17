import 'dart:async';
import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:sistrum/l10n/supported_locales.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/services/app_state_database.dart';
import 'package:sistrum/services/file_access_check.dart';
import 'package:sistrum/services/autoplay_personalization.dart';
import 'package:sistrum/services/autoplay_ranker.dart';
import 'package:sistrum/services/autoplay_taste.dart';
import 'package:sistrum/services/background_favorites.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/download_file_recovery.dart';
import 'package:sistrum/services/library_collections_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/media_library_browser.dart';
import 'package:sistrum/services/media_session_art_cache.dart';
import 'package:sistrum/services/media_session_filter.dart';
import 'package:sistrum/services/music_playback_deck.dart';
import 'package:sistrum/services/music_player_service.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/playback_deck.dart';
import 'package:sistrum/services/playback_notification.dart';
import 'package:sistrum/services/song_analysis.dart';
import 'package:sistrum/services/source_deletion_events.dart';
import 'package:sistrum/services/source_metadata_events.dart';
import 'package:sistrum/services/system_volume_service.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('MusicPlayerRuntime');

/// A song the player could not play because of its [format]; [skipped]
/// when the next song plays in its place.
typedef UnplayableSong = ({String format, bool skipped});

/// The process services used by a player. Tests can replace effects without
/// changing the queue, transport, interruption or automation algorithms.
class PlaybackDependencies {
  const PlaybackDependencies({
    this.createDeck = _createDeck,
    this.createPreviewDeck = _createPreviewDeck,
    this.audioSession = _getAudioSession,
    this.readMetadata = PlatformBridge.readFileMetadata,
    this.fileAccess = checkFileAccess,
    this.recoverMovedDownload = _recoverMovedDownload,
    this.initializeHandler = _initializeAudioHandler,
    this.synchronizePlaybackState = _synchronizePlaybackState,
    this.createPersonalization = AutoplayPersonalization.new,
    this.createTaste = AutoplayTaste.new,
    this.createRanker = AutoplayRanker.new,
    this.createSongAnalysis = _createSongAnalysis,
    AppStateDatabase? sessionDatabase,
    LibraryDatabase? libraryDatabase,
    HistoryDatabase? historyDatabase,
    LibraryCollectionsDatabase? collectionsDatabase,
    ListeningRecorder? recorder,
    SourceDeletionEvents? sourceDeletionEvents,
    SourceMetadataEvents? sourceMetadataEvents,
    Stream<double>? volumeChanges,
    bool? isAndroid,
  }) : _sessionDatabase = sessionDatabase,
       _libraryDatabase = libraryDatabase,
       _historyDatabase = historyDatabase,
       _collectionsDatabase = collectionsDatabase,
       _recorder = recorder,
       _sourceDeletionEvents = sourceDeletionEvents,
       _sourceMetadataEvents = sourceMetadataEvents,
       _volumeChanges = volumeChanges,
       _isAndroid = isAndroid;

  final PlaybackDeck Function(String playerId) createDeck;

  /// The search and catalog preview player.
  final PlaybackDeck Function(String playerId) createPreviewDeck;
  final Future<AudioSession> Function() audioSession;
  final Future<Map<String, dynamic>> Function(String, {String? displayName})
  readMetadata;
  final Future<FileAccessCheck> Function(String) fileAccess;
  final Future<String?> Function(
    String source,
    String historyId,
    AppSettings settings,
  )
  recoverMovedDownload;
  final Future<MusicPlayerHandler> Function(MusicPlayerHandler Function())
  initializeHandler;
  final Future<void> Function() synchronizePlaybackState;
  final AutoplayPersonalization Function() createPersonalization;
  final AutoplayTaste Function() createTaste;
  final AutoplayRanker Function() createRanker;

  /// Analysis of how the Library's songs sound, reading songs from it.
  final SongAnalysis Function(LibraryDatabase library) createSongAnalysis;

  final AppStateDatabase? _sessionDatabase;
  final LibraryDatabase? _libraryDatabase;
  final HistoryDatabase? _historyDatabase;
  final LibraryCollectionsDatabase? _collectionsDatabase;
  final ListeningRecorder? _recorder;
  final SourceDeletionEvents? _sourceDeletionEvents;
  final SourceMetadataEvents? _sourceMetadataEvents;
  final Stream<double>? _volumeChanges;
  final bool? _isAndroid;

  AppStateDatabase get sessionDatabase =>
      _sessionDatabase ?? AppStateDatabase.instance;
  LibraryDatabase get libraryDatabase =>
      _libraryDatabase ?? LibraryDatabase.instance;
  HistoryDatabase get historyDatabase =>
      _historyDatabase ?? HistoryDatabase.instance;
  LibraryCollectionsDatabase get collectionsDatabase =>
      _collectionsDatabase ?? LibraryCollectionsDatabase.instance;
  ListeningRecorder get recorder => _recorder ?? listeningRecorder;
  SourceDeletionEvents get sourceDeletionEvents =>
      _sourceDeletionEvents ?? SourceDeletionEvents.instance;
  SourceMetadataEvents get sourceMetadataEvents =>
      _sourceMetadataEvents ?? SourceMetadataEvents.instance;
  Stream<double> get volumeChanges =>
      _volumeChanges ?? SystemVolumeService.instance.changes;
  bool get isAndroid => _isAndroid ?? Platform.isAndroid;

  static PlaybackDeck _createDeck(String playerId) {
    return AudioplayersPlaybackDeck(playerId: playerId);
  }

  // playing without the equalizer.
  static PlaybackDeck _createPreviewDeck(String playerId) =>
      AudioplayersPlaybackDeck(playerId: playerId, equalizerAvailable: false);
  static Future<AudioSession> _getAudioSession() => AudioSession.instance;
  static SongAnalysis _createSongAnalysis(LibraryDatabase library) =>
      SongAnalysis(library: library);
  static Future<String?> _recoverMovedDownload(
    String source,
    String historyId,
    AppSettings settings,
  ) => DownloadFileRecovery.instance.recover(
    source,
    historyId: historyId,
    settings: settings,
  );

  static Future<void> _synchronizePlaybackState() async {
    // The session's switch takes each state from the player a microtask
    // later; let the one just published reach it first.
    await Future<void>.delayed(Duration.zero);
    await AudioService.synchronizePlaybackState();
  }

  static Future<MusicPlayerHandler> _initializeAudioHandler(
    MusicPlayerHandler Function() builder,
  ) async {
    // send it only the states their sessions cannot advance themselves.
    installMediaSessionFilter();
    late final MusicPlayerHandler handler;
    await AudioService.init(
      builder: () => SwitchAudioHandler(handler = builder()),
      cacheManager: mediaSessionArtCache(),
      config: const AudioServiceConfig(
        androidNotificationChannelId: 'com.amwolfstein.sistrum.playback',
        androidNotificationChannelName: 'Playback',
        // The notification mark omits the adaptive launcher's safe-zone padding.
        androidNotificationIcon: 'drawable/ic_stat_sistrum',
        artDownscaleWidth: 512,
        artDownscaleHeight: 512,
        // Keep background resume eligible for audio focus. The Android fork
        // releases the CPU wake lock on pause and demotes the service on stop.
        androidStopForegroundOnPause: false,
        androidBrowsableRootExtras: MediaLibraryBrowser.rootExtras,
      ),
    );
    return handler;
  }
}

/// Owns a player lifetime and its latest immutable settings. Configuration is
/// available before lazy initialization and remains live while it is pending.
class MusicPlayerRuntime {
  MusicPlayerRuntime({
    AppSettings settings = const AppSettings(),
    this.dependencies = const PlaybackDependencies(),
  }) : _settings = settings;

  final PlaybackDependencies dependencies;
  AutoplayPersonalization? _personalization;
  // What learning observes also teaches [taste] what plays together and
  // [ranker] how much each part of a score counts.
  AutoplayPersonalization get personalization =>
      _personalization ??= (dependencies.createPersonalization()
        ..feedback.listen(taste.record)
        ..feedback.listen(ranker.record));
  AutoplayTaste? _taste;
  AutoplayTaste get taste => _taste ??= dependencies.createTaste();
  AutoplayRanker? _ranker;
  AutoplayRanker get ranker => _ranker ??= dependencies.createRanker();
  SongAnalysis? _songAnalysis;

  /// How the Library's songs sound, analyzed in the background for Autoplay.
  SongAnalysis get songAnalysis =>
      _songAnalysis ??= dependencies.createSongAnalysis(
        dependencies.libraryDatabase,
      )..configure(_settings);
  AppSettings _settings;
  MusicPlayerHandler? _handler;
  void Function()? _unsubscribeSourceDeletion;
  void Function()? _unsubscribeSourceMetadata;
  Future<MusicPlayerHandler>? _initFuture;
  Future<void>? _restoreFuture;
  final _handlers = StreamController<MusicPlayerHandler?>.broadcast(sync: true);
  final _mutedPlaybackEvents = StreamController<void>.broadcast();
  final _unplayableSongs = StreamController<UnplayableSong>.broadcast();
  bool _initializing = false;
  bool _disposed = false;
  Future<void>? _disposeFuture;
  String _unknownTitle = 'Unknown title', _unknownArtist = 'Unknown artist';
  PlaybackNotification _notification = const PlaybackNotification();
  Future<bool> Function(MediaItem)? _toggleFavorite;
  StreamSubscription<MediaItem?>? _backgroundFavorites;

  TagCreditScope? _tagCredits;

  /// Pushed by the app whenever the library or its credit rules change.
  void configureTagCredits(TagCreditScope scope) => _tagCredits = scope;

  /// The Library that media browsers such as Android Auto show.
  late final browser = MediaLibraryBrowser(
    library: dependencies.libraryDatabase,
    history: dependencies.historyDatabase,
    collections: dependencies.collectionsDatabase,
    includeLocal: () => _settings.localLibraryEnabled,
    tagCredits: () =>
        _tagCredits ??
        TagCreditScope(
          rules: TagCreditRules.fromSettings(_settings.tagCredits),
        ),
    strings: () => appLocalizationsFor(_settings.locale),
  );

  AppSettings get settings => _settings;
  MusicPlayerHandler? get handler => _initializing ? null : _handler;
  String get unknownTitle => _unknownTitle;
  String get unknownArtist => _unknownArtist;
  PlaybackNotification get notification => _notification;

  // Transient feedback is never replayed when a screen or app shell reopens.
  Stream<void> get mutedPlaybackEvents => _mutedPlaybackEvents.stream;

  void notifyMutedPlayback() {
    if (!_disposed) _mutedPlaybackEvents.add(null);
  }

  /// Songs whose format this device cannot play, as they fail.
  Stream<UnplayableSong> get unplayableSongs => _unplayableSongs.stream;

  void notifyUnplayableSong(String format, {required bool skipped}) {
    if (!_disposed) _unplayableSongs.add((format: format, skipped: skipped));
  }

  void configure(AppSettings settings) {
    final previous = _settings;
    _settings = settings;
    _songAnalysis?.configure(settings);
    _handler?.applySettings(previous);
    if (previous.playerMode == 'internal' &&
        settings.playerMode != 'internal') {
      _restoreFuture = null;
      final handler = _handler;
      if (handler != null) {
        unawaited(
          handler.stop(keepResume: true).catchError((
            Object error,
            StackTrace stack,
          ) {
            _log.e('Failed to stop the internal player', error, stack);
          }),
        );
      }
    }
  }

  /// Starts analyzing Library songs in the background, and the song playing
  /// soon after it starts.
  void startSongAnalysis() {
    if (!_disposed) songAnalysis.start(mediaItemEvents());
  }

  void updateStrings({
    required String unknownTitle,
    required String unknownArtist,
  }) {
    _unknownTitle = unknownTitle;
    _unknownArtist = unknownArtist;
  }

  /// Media controls for playback that started while the app shell is closed,
  /// such as from a car. Its star loves songs through [favorites] and shows
  /// whether the current song is loved, until the shell configures them.
  void configureBackgroundNotification({
    required PlaybackNotification presentation,
    required BackgroundFavorites favorites,
  }) {
    if (_toggleFavorite != null || _disposed) return;
    _notification = presentation;
    _toggleFavorite = favorites.toggle;
    _handler?.refreshNotificationControls();
    ({String id, Object? source})? shown;
    _backgroundFavorites = mediaItemEvents().listen((item) async {
      final current = item == null
          ? null
          : (id: item.id, source: item.extras?['source']);
      if (item == null || current == shown) return;
      shown = current;
      try {
        final loved = await favorites.isLoved(item);
        if (_backgroundFavorites != null && shown == current) {
          updateFavorite(item, loved);
        }
      } catch (error) {
        _log.w('Could not read whether the song is loved: $error');
      }
    });
  }

  void configureNotification({
    required PlaybackNotification presentation,
    required Future<bool> Function(MediaItem) toggleFavorite,
  }) {
    unawaited(_backgroundFavorites?.cancel());
    _backgroundFavorites = null;
    _notification = presentation;
    _toggleFavorite = toggleFavorite;
    _handler?.refreshNotificationControls();
  }

  Future<bool?> toggleFavorite(MediaItem item) async =>
      _toggleFavorite?.call(item);

  void updateFavorite(MediaItem item, bool loved) {
    _notification = _notification.withFavorite(item, loved);
    _handler?.refreshNotificationControls();
  }

  void attach(MusicPlayerHandler handler) {
    if (_disposed) throw StateError('This playback runtime was disposed');
    final existing = _handler;
    if (existing != null && !identical(existing, handler)) {
      throw StateError('This playback runtime already owns a handler');
    }
    _handler = handler;
    if (!identical(existing, handler)) {
      _unsubscribeSourceDeletion = dependencies.sourceDeletionEvents.subscribe(
        handler.onSourceDeleted,
        beforeDelete: handler.prepareSourceDeletion,
      );
      _unsubscribeSourceMetadata = dependencies.sourceMetadataEvents.subscribe(
        handler.onSourceMetadataChanged,
      );
    }
    if (!_initializing && !identical(existing, handler)) _handlers.add(handler);
  }

  void detach(MusicPlayerHandler handler) {
    if (!identical(_handler, handler)) return;
    _unsubscribeSourceDeletion?.call();
    _unsubscribeSourceDeletion = null;
    _unsubscribeSourceMetadata?.call();
    _unsubscribeSourceMetadata = null;
    _handler = null;
    _restoreFuture = null;
    if (!_handlers.isClosed) _handlers.add(null);
  }

  Future<MusicPlayerHandler> initialize() {
    if (_disposed) {
      return Future.error(StateError('This playback runtime was disposed'));
    }
    final pending = _initFuture;
    if (pending != null) return pending;
    final existing = _handler;
    if (existing != null) return Future.value(existing);
    return _initFuture = _initialize();
  }

  Future<MusicPlayerHandler> _initialize() async {
    _initializing = true;
    MusicPlayerHandler? created;
    try {
      // Defer even a synchronous test initializer until _initFuture is stored.
      await Future<void>.value();
      final handler = await dependencies.initializeHandler(() {
        if (_disposed) throw StateError('This playback runtime was disposed');
        return created = MusicPlayerHandler(runtime: this);
      });
      if (_disposed || handler.isDisposed || !identical(_handler, handler)) {
        throw StateError(
          'Playback initialization returned an inactive handler',
        );
      }
      _initializing = false;
      _handlers.add(handler);
      return handler;
    } catch (error, stack) {
      _log.e('Failed to initialize playback', error, stack);
      await created?.dispose();
      rethrow;
    } finally {
      _initializing = false;
      _initFuture = null;
    }
  }

  Future<void> restoreSession() {
    if (_settings.playerMode != 'internal') return Future.value();
    return _restoreFuture ??= restorePlaybackSession(this).then<void>((_) {});
  }

  /// [MusicPlayerHandler.recoverRestoredArtwork] after [restoreSession].
  Future<void> recoverRestoredArtwork() async {
    await restoreSession();
    await handler?.recoverRestoredArtwork();
  }

  /// The default runtime lives for the app lifetime. Scoped runtimes close
  /// subscriptions and reject new initialization when their owner disposes.
  Future<void> dispose() => _disposeFuture ??= _dispose();

  Future<void> _dispose() async {
    _disposed = true;
    _toggleFavorite = null;
    unawaited(_backgroundFavorites?.cancel());
    _backgroundFavorites = null;
    await _handler?.dispose();
    try {
      await _initFuture;
    } catch (_) {
      // Initialization may have failed because its pending handler was closed.
    }
    await _personalization?.dispose();
    await _taste?.dispose();
    await _ranker?.dispose();
    await _songAnalysis?.dispose();
    await _handlers.close();
    await _mutedPlaybackEvents.close();
    await _unplayableSongs.close();
  }

  Stream<T> _events<T>(
    Stream<T> Function(MusicPlayerHandler) select, {
    List<T> initial = const [],
  }) => Stream<T>.multi((listener) {
    StreamSubscription<T>? events;
    var revision = 0;
    void selectHandler(MusicPlayerHandler? handler) {
      final selectedRevision = ++revision;
      unawaited(events?.cancel());
      events = null;
      if (handler == null) {
        for (final value in initial) {
          listener.addSync(value);
        }
      } else {
        events = select(handler).listen((value) {
          if (selectedRevision == revision) listener.addSync(value);
        }, onError: listener.addErrorSync);
      }
    }

    // Register first: UI listeners can cancel immediately while initialization
    // is pending, and can follow a replacement handler after disposal.
    final ready = _handlers.stream.listen(
      selectHandler,
      onDone: listener.closeSync,
    );
    selectHandler(handler);
    listener.onCancel = () {
      revision++;
      unawaited(ready.cancel());
      unawaited(events?.cancel());
    };
  });

  Stream<MediaItem?> mediaItemEvents() =>
      _events((handler) => handler.mediaItem, initial: [null]);
  Stream<PlaybackState> playbackStateEvents() =>
      _events((handler) => handler.playbackState);
  Stream<List<MediaItem>> queueEvents() =>
      _events((handler) => handler.queue, initial: [const []]);
}

final musicPlayerRuntime = MusicPlayerRuntime();
