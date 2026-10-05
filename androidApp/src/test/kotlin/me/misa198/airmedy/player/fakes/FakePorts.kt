package me.misa198.airmedy.player.fakes

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import me.misa198.airmedy.mood.MoodRadioTrack
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.player.Clock
import me.misa198.airmedy.player.FocusPort
import me.misa198.airmedy.player.LibraryPort
import me.misa198.airmedy.player.ListeningSink
import me.misa198.airmedy.player.ListeningTracker
import me.misa198.airmedy.player.ListeningWrite
import me.misa198.airmedy.player.NowPlayingPort
import me.misa198.airmedy.player.PlaybackCoordinator
import me.misa198.airmedy.player.PlaybackFlows
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackItemResolver
import me.misa198.airmedy.player.PlaybackLog
import me.misa198.airmedy.player.PlaybackQueue
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackSession
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.player.ScrobbleSink
import me.misa198.airmedy.player.SessionStorePort
import me.misa198.airmedy.player.TrackAnalysis
import me.misa198.airmedy.player.TransportState
import me.misa198.airmedy.sync.LibraryTrack

/** Records every now-playing call; [transportStates] captures both publish and set-transport updates in order. */
internal class FakeNowPlaying : NowPlayingPort {
    val transportStates = mutableListOf<TransportState>()
    val transportStatePositions = mutableListOf<Long>()
    val published = mutableListOf<Pair<PlaybackItem, TransportState>>()
    val publishedPositions = mutableListOf<Long>()
    val queues = mutableListOf<PlaybackQueueSnapshot>()
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

    override suspend fun publishQueue(snapshot: PlaybackQueueSnapshot) {
        queues += snapshot
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
    var stored: PlaybackSession? = null
    var cleared = 0

    override suspend fun load(): PlaybackSession? = stored

    override suspend fun save(session: PlaybackSession) {
        stored = session
        saved += session
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

internal class FakeLibrary : LibraryPort {
    var analysisEnabled = true
    var moodTracks: List<MoodRadioTrack> = emptyList()
    var analyses: Map<String, TrackAnalysis> = emptyMap()

    override suspend fun activeAnalyses(): Map<String, TrackAnalysis> = analyses

    override suspend fun tracks(): List<LibraryTrack> = emptyList()

    override suspend fun disableNormalization() = Unit

    override suspend fun libraryAnalysisEnabled(): Boolean = analysisEnabled

    override suspend fun moodRadioTracks(): List<MoodRadioTrack> = moodTracks
}

/** Resolves any id to a deterministic [PlaybackItem]; ids in [missing] resolve to null. */
internal class FakeResolver : PlaybackItemResolver {
    val missing = mutableSetOf<String>()

    override suspend fun resolve(trackId: String): PlaybackItem? {
        if (trackId in missing) return null
        return PlaybackItem(
            trackId = trackId,
            title = "Title $trackId",
            artist = "Artist",
            audioPath = "/music/$trackId.flac",
            artworkPath = "/art/$trackId.jpg",
        )
    }
}

/**
 * Reusable harness wiring a [PlaybackCoordinator] to fake ports, an engine factory and a
 * deterministic queue. Call from inside `runTest`; `markRestored()` is invoked up front.
 */
internal class PlaybackCoordinatorHarness(
    private val scope: TestScope,
    val queue: PlaybackQueue = PlaybackQueue(Random(42)),
    durationFor: (PlaybackItem) -> Long = { FakePlayerEngine.DefaultDurationMs },
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

    private var idCounter = 0
    val listeningTracker = ListeningTracker("test-device") { "id-${idCounter++}" }
    private var artworkCrossfadeId = 0L

    val coordinator = PlaybackCoordinator(
        scope = scope,
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
    )

    val engine: FakePlayerEngine get() = engines.current

    init {
        coordinator.markRestored()
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
}
