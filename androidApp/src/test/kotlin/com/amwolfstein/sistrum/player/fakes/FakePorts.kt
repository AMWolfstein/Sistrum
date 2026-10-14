package com.amwolfstein.sistrum.player.fakes

import kotlin.random.Random
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import com.amwolfstein.sistrum.mood.MoodRadioTrack
import com.amwolfstein.sistrum.player.ArtworkCrossfadeTransition
import com.amwolfstein.sistrum.player.Clock
import com.amwolfstein.sistrum.player.DecodeFailureEntry
import com.amwolfstein.sistrum.player.DecodeFailureSink
import com.amwolfstein.sistrum.player.FocusPort
import com.amwolfstein.sistrum.player.LibraryPort
import com.amwolfstein.sistrum.player.ListeningSink
import com.amwolfstein.sistrum.player.ListeningTracker
import com.amwolfstein.sistrum.player.ListeningWrite
import com.amwolfstein.sistrum.player.NowPlayingPort
import com.amwolfstein.sistrum.player.PlaybackCoordinator
import com.amwolfstein.sistrum.player.PlaybackFlows
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.PlaybackItemResolver
import com.amwolfstein.sistrum.player.PlaybackLog
import com.amwolfstein.sistrum.player.PlaybackQueue
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.player.PlaybackSession
import com.amwolfstein.sistrum.player.PlaybackState
import com.amwolfstein.sistrum.player.QueueWindowEntry
import com.amwolfstein.sistrum.player.RepeatMode
import com.amwolfstein.sistrum.player.SavedPosition
import com.amwolfstein.sistrum.player.ScrobbleSink
import com.amwolfstein.sistrum.player.SessionStorePort
import com.amwolfstein.sistrum.player.TrackAnalysis
import com.amwolfstein.sistrum.player.TransportState
import com.amwolfstein.sistrum.sync.LibraryTrack

/** Records every now-playing call; [transportStates] captures both publish and set-transport updates in order. */
internal class FakeNowPlaying : NowPlayingPort {
    val transportStates = mutableListOf<TransportState>()
    val transportStatePositions = mutableListOf<Long>()
    val published = mutableListOf<Pair<PlaybackItem, TransportState>>()
    val publishedPositions = mutableListOf<Long>()
    val queues = mutableListOf<PlaybackQueueSnapshot>()
    val windows = mutableListOf<List<QueueWindowEntry>>()
    val foregroundItems = mutableListOf<PlaybackItem>()
    val notifications = mutableListOf<PlaybackItem>()
    var foreground = 0
    var stoppedForeground = 0
    var deactivated = 0

    override fun publishNowPlaying(
        item: PlaybackItem,
        state: TransportState,
        positionMs: Long,
        durationMs: Long,
        activeQueueItemId: Long,
    ) {
        transportStates += state
        published += item to state
        publishedPositions += positionMs
    }

    override fun setTransportState(state: TransportState, positionMs: Long, activeQueueItemId: Long) {
        transportStates += state
        transportStatePositions += positionMs
    }

    override fun deactivate() {
        deactivated += 1
    }

    override suspend fun publishQueue(snapshot: PlaybackQueueSnapshot, window: List<QueueWindowEntry>) {
        queues += snapshot
        windows += window
    }

    override fun showForeground(item: PlaybackItem) {
        foreground += 1
        foregroundItems += item
    }

    override fun updateNotification(item: PlaybackItem) {
        notifications += item
    }

    override fun stopForeground() {
        stoppedForeground += 1
    }
}

internal class FakeFocus : FocusPort {
    var grant = true
    var requests = 0
    var abandonments = 0

    override fun request(): Boolean {
        requests += 1
        return grant
    }

    override fun abandon() {
        abandonments += 1
    }
}

internal class FakeListening : ListeningSink {
    val writes = mutableListOf<ListeningWrite>()

    override fun offer(write: ListeningWrite): Boolean {
        writes += write
        return true
    }
}

internal class FakeScrobble : ScrobbleSink {
    val starts = mutableListOf<Pair<String, Long>>()
    val seeks = mutableListOf<Long>()
    val reports = mutableListOf<Triple<PlaybackItem, Long, Long>>()

    override fun startPlayback(trackId: String, positionMs: Long) {
        starts += trackId to positionMs
    }

    override fun seek(positionMs: Long) {
        seeks += positionMs
    }

    override fun reportPlayback(item: PlaybackItem, positionMs: Long, durationMs: Long) {
        reports += Triple(item, positionMs, durationMs)
    }
}

internal class FakeSessionStore : SessionStorePort {
    val saved = mutableListOf<PlaybackSession>()
    val positionSaves = mutableListOf<SavedPosition>()
    var stored: PlaybackSession? = null
    var cleared = 0

    /** When set, [save] and [savePosition] suspend here until it completes (drainer coalescing tests). */
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun load(): PlaybackSession? = stored

    override suspend fun save(session: PlaybackSession) {
        saveGate?.await()
        stored = session
        saved += session
    }

    override suspend fun savePosition(position: SavedPosition) {
        saveGate?.await()
        positionSaves += position
    }

    override suspend fun clear() {
        stored = null
        cleared += 1
    }
}

internal class FakeClock : Clock {
    var now: Long = 0L
    var elapsed: Long = 0L

    override fun nowMs(): Long = now

    override fun elapsedMs(): Long = elapsed
}

internal class FakeLog : PlaybackLog {
    val lines = mutableListOf<String>()

    override fun d(message: String) {
        lines += message
    }

    override fun w(message: String, error: Throwable?) {
        lines += message
    }

    override fun e(message: String) {
        lines += message
    }
}

/** Records coordinator decode failures in order without writing a host file. */
internal class FakeDecodeFailureSink : DecodeFailureSink {
    val entries = mutableListOf<DecodeFailureEntry>()

    override fun record(entry: DecodeFailureEntry) {
        entries += entry
    }
}

internal class FakeLibrary : LibraryPort {
    var analysisEnabled = true
    var moodTracks: List<MoodRadioTrack> = emptyList()
    var analyses: Map<String, TrackAnalysis> = emptyMap()
    var libraryTracks: List<LibraryTrack> = emptyList()

    var activeAnalysesCalls = 0
    var disableNormalizationCalls = 0

    override suspend fun activeAnalyses(): Map<String, TrackAnalysis> {
        activeAnalysesCalls++
        return analyses
    }

    override suspend fun tracks(): List<LibraryTrack> = libraryTracks

    override suspend fun disableNormalization() {
        disableNormalizationCalls++
    }

    override suspend fun libraryAnalysisEnabled(): Boolean = analysisEnabled

    override suspend fun moodRadioTracks(): List<MoodRadioTrack> = moodTracks
}

/** Resolves any id to a deterministic [PlaybackItem]; ids in [missing] resolve to null. */
internal class FakeResolver : PlaybackItemResolver {
    val missing = mutableSetOf<String>()
    val albumIds = mutableMapOf<String, String>()

    /** Number of times [resolve] was invoked; FR-087 asserts no whole-library resolution. */
    var resolveCalls = 0

    /** When set, [resolve] throws it (FR-084: a throwing resolver during play). */
    var failWith: Throwable? = null

    /** When true, [resolve] suspends forever until the caller is cancelled (FR-084). */
    var suspendForever: Boolean = false

    override suspend fun resolve(trackId: String): PlaybackItem? {
        resolveCalls += 1
        failWith?.let { throw it }
        if (suspendForever) awaitCancellation()
        if (trackId in missing) return null
        return PlaybackItem(
            trackId = trackId,
            title = "Title $trackId",
            artist = "Artist",
            audioPath = "/music/$trackId.flac",
            artworkPath = "/art/$trackId.jpg",
            albumId = albumIds[trackId] ?: "",
        )
    }
}

/**
 * Reusable harness wiring a [PlaybackCoordinator] to fake ports, an engine factory and a
 * deterministic queue. Call from inside `runTest`; `markRestored()` is invoked up front
 * unless [markRestoredOnInit] is false, so restore-gate tests can control it.
 */
internal class PlaybackCoordinatorHarness(
    private val scope: TestScope,
    val queue: PlaybackQueue = PlaybackQueue(Random(42)),
    durationFor: (PlaybackItem) -> Long = { FakePlayerEngine.DefaultDurationMs },
    markRestoredOnInit: Boolean = true,
    coordinatorScope: CoroutineScope = scope,
) {
    val flows = PlaybackFlows(
        state = MutableStateFlow<PlaybackState>(PlaybackState.Idle),
        queueState = MutableStateFlow(PlaybackQueueSnapshot()),
        crossfadeSeconds = MutableStateFlow(0),
        blendArtworkDuringCrossfade = MutableStateFlow(false),
        artworkCrossfade = MutableStateFlow<ArtworkCrossfadeTransition?>(null),
        moodRadioActive = MutableStateFlow(false),
    )
    val engines = FakeEngineFactory(durationFor)
    val resolver = FakeResolver()
    val library = FakeLibrary()
    val nowPlaying = FakeNowPlaying()
    val focus = FakeFocus()
    val listening = FakeListening()
    val scrobble = FakeScrobble()
    val sessionStore = FakeSessionStore()
    val clock = FakeClock()
    val log = FakeLog()
    val decodeFailures = FakeDecodeFailureSink()

    private var idCounter = 0
    val listeningTracker = ListeningTracker("test-device") { "id-${idCounter++}" }
    private var artworkCrossfadeId = 0L

    val coordinator = PlaybackCoordinator(
        scope = coordinatorScope,
        queue = queue,
        flows = flows,
        engineFactory = { engines() },
        resolver = resolver,
        library = library,
        nowPlaying = nowPlaying,
        focus = focus,
        listening = listening,
        scrobble = scrobble,
        sessionStore = sessionStore,
        clock = clock,
        log = log,
        listeningTracker = listeningTracker,
        nextArtworkCrossfadeId = { ++artworkCrossfadeId },
        decodeFailures = decodeFailures,
    )

    val engine: FakePlayerEngine get() = engines.current

    init {
        if (markRestoredOnInit) coordinator.markRestored()
    }

    suspend fun send(
        action: String,
        positionMs: Long = 0L,
        trackIds: List<String> = emptyList(),
        startIndex: Int = 0,
        enabled: Boolean = false,
        repeat: RepeatMode? = null,
    ) {
        coordinator.dispatch(action, positionMs, trackIds, startIndex, enabled, repeat).join()
        scope.testScheduler.advanceUntilIdle()
    }

    suspend fun tick() {
        coordinator.tick()
        scope.testScheduler.advanceUntilIdle()
    }

    /** Runs the service's restore transaction the way [PlaybackService] does. */
    suspend fun restore(session: PlaybackSession, availableTrackIds: Set<String>) {
        coordinator.withCommandLock { coordinator.restoreSaved(session, availableTrackIds) }
        scope.testScheduler.advanceUntilIdle()
    }

    /** Runs queued tasks without issuing a command; used by the restore-gate test. */
    fun advance() {
        scope.testScheduler.advanceUntilIdle()
    }
}

/**
 * Dispatcher that stores dispatched [Runnable]s and runs them last-in-first-out on demand. Used by
 * FR-081/FR-084 command-path tests to prove arrival order: with today's per-command `scope.launch`,
 * two commands run in reverse order here, which the FR-081 tests must reject.
 */
internal class ReversingDispatcher : CoroutineDispatcher() {
    private val pending = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        pending.addLast(block)
    }

    /** Runs stored runnables last-in-first-out, repeatedly until none remain. */
    fun runAll() {
        while (pending.isNotEmpty()) pending.removeLast().run()
    }
}
