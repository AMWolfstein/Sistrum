package me.misa198.airmedy.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.misa198.airmedy.mood.MoodRadioBatchSize
import me.misa198.airmedy.mood.MoodRadioRefillThreshold
import me.misa198.airmedy.mood.selectMoodRadio
import me.misa198.airmedy.player.engine.EngineEvent
import me.misa198.airmedy.player.engine.ItemGain
import me.misa198.airmedy.player.engine.PlayerEngine

private const val DuckedFocusGain = 0.2f
private const val PreviousRestartThresholdMs = 3_000L

private val PreloadResyncActions = setOf(
    PlaybackService.ActionSetShuffle, PlaybackService.ActionSetRepeat, PlaybackService.ActionPlayNext,
    PlaybackService.ActionRemove, PlaybackService.ActionReorder, PlaybackService.ActionStartMoodRadio,
)

private val MoodRadioStoppingActions = setOf(
    PlaybackService.ActionPlay, PlaybackService.ActionShuffle, PlaybackService.ActionStop, PlaybackService.ActionClearQueue,
    PlaybackService.ActionPlayNext, PlaybackService.ActionAppend, PlaybackService.ActionRemove, PlaybackService.ActionReorder,
)

internal class PlaybackCoordinator(
    private val scope: CoroutineScope,
    val queue: PlaybackQueue,
    private val flows: PlaybackFlows,
    private val engineFactory: suspend () -> PlayerEngine,
    private val resolver: PlaybackItemResolver,
    private val library: LibraryPort,
    private val nowPlaying: NowPlayingPort,
    private val focus: FocusPort,
    private val listening: ListeningSink,
    private val scrobble: ScrobbleSink,
    private val sessionStore: SessionStorePort,
    private val clock: Clock,
    private val log: PlaybackLog,
    val listeningTracker: ListeningTracker,
    private val nextArtworkCrossfadeId: () -> Long,
) {
    private val restored = CompletableDeferred<Unit>()
    private var moodRadioSeedId: String? = null
    private var moodRadioLastRefillAttempt: Pair<String?, Int>? = null
    private var resumeOnFocusGain = false
    private var focusHeld = false

    private var engine: PlayerEngine? = null
    private var preloadedItem: PlaybackItem? = null
    private var outputDisconnected = false
    private var endedPending = false
    private var listeningFadeOutgoing: String? = null
    private var listeningFadeStartedAt = 0L
    private var listeningFadeStartedElapsed = 0L
    private var listeningFadeMaxMs = 0L
    private var normalizationSettings = NormalizationSettings()
    private var equalizerSettings = EqualizerSettings()
    private var isDucked = false
    private var pendingStart: PendingStart? = null

    private val commandLock = Any()
    private val commandQueue = ArrayDeque<Command>()
    private val preRestoreCommands = ArrayDeque<Command>()
    private var draining = false

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { cancelAllPending() }
    }

    private sealed interface Command {
        class Action(
            val action: String,
            val positionMs: Long,
            val trackIds: List<String>,
            val startIndex: Int,
            val enabled: Boolean,
            val repeat: RepeatMode?,
            val job: CompletableJob,
        ) : Command

        class SelectIndex(val index: Long, val job: CompletableJob) : Command

        class Block(val block: suspend () -> Any?, val result: CompletableDeferred<Any?>) : Command

        fun cancel() {
            when (this) {
                is Action -> job.cancel()
                is SelectIndex -> job.cancel()
                is Block -> result.cancel()
            }
        }
    }

    /** A start whose Playing publication waits for the engine's [EngineEvent.OutputStarted]. */
    private sealed interface PendingStart {
        val item: PlaybackItem

        /** A fresh track start (startTracking) or an output-recovery recreate (!startTracking). */
        data class Fresh(override val item: PlaybackItem, val startTracking: Boolean) : PendingStart

        /** A resume of the already-prepared item. */
        data class Resume(override val item: PlaybackItem, val resumeListening: Boolean) : PendingStart
    }

    fun markRestored() {
        synchronized(commandLock) {
            // Completed under the lock so no command can overtake the held ones.
            if (!restored.complete(Unit)) return
            if (preRestoreCommands.isNotEmpty()) {
                commandQueue.addAll(preRestoreCommands)
                preRestoreCommands.clear()
            }
            if (!draining && commandQueue.isNotEmpty()) startDrainerLocked()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> withCommandLock(block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        val result = CompletableDeferred<Any?>()
        enqueue(Command.Block({ block() }, result))
        try {
            return result.await() as T
        } catch (cancellation: CancellationException) {
            result.cancel()
            throw cancellation
        }
    }

    private fun enqueue(command: Command) {
        synchronized(commandLock) {
            if (scope.coroutineContext[Job]?.isActive == false) {
                command.cancel()
                return
            }
            if (command is Command.Action || command is Command.SelectIndex) {
                if (!restored.isCompleted) {
                    preRestoreCommands.addLast(command)
                    return
                }
            }
            commandQueue.addLast(command)
            if (!draining) startDrainerLocked()
        }
    }

    /** Starts the single drainer; if the scope is already dead the completion handler cancels everything. */
    private fun startDrainerLocked() {
        draining = true
        scope.launch { drain() }.invokeOnCompletion { cause ->
            if (cause != null) cancelAllPending()
        }
    }

    private suspend fun drain() {
        try {
            while (true) {
                val command: Command? = synchronized(commandLock) {
                    if (commandQueue.isEmpty()) {
                        draining = false
                        null
                    } else {
                        commandQueue.removeFirst()
                    }
                }
                command ?: return
                runCommand(command)
            }
        } catch (cancellation: CancellationException) {
            cancelAllPending()
            throw cancellation
        }
    }

    private fun cancelAllPending() {
        synchronized(commandLock) {
            commandQueue.forEach { it.cancel() }
            commandQueue.clear()
            preRestoreCommands.forEach { it.cancel() }
            preRestoreCommands.clear()
            draining = false
        }
    }

    private suspend fun runCommand(command: Command) {
        when (command) {
            is Command.Action -> runJobCommand(command.action, command.job) {
                handleActionCommand(
                    command.action,
                    command.positionMs,
                    command.trackIds,
                    command.startIndex,
                    command.enabled,
                    command.repeat,
                )
            }
            is Command.SelectIndex -> runJobCommand(PlaybackService.ActionSelect, command.job) {
                val trackId = queue.snapshot().activeTrackIds.getOrNull(command.index.toInt())
                if (trackId != null) handleActionCommand(PlaybackService.ActionSelect, trackIds = listOf(trackId))
            }
            is Command.Block -> runBlockCommand(command)
        }
    }

    private suspend fun runJobCommand(action: String, job: CompletableJob, block: suspend () -> Unit) {
        try {
            block()
            job.complete()
        } catch (cancellation: CancellationException) {
            if (!currentCoroutineContext().isActive) {
                job.cancel()
                throw cancellation
            }
            log.e("Command failed: action=$action error=${cancellation.message}")
            runCatching { fail(queue.snapshot().currentTrackId, "$action failed: ${cancellation.message}") }
            job.complete()
        } catch (error: Throwable) {
            log.e("Command failed: action=$action error=${error.message}")
            runCatching { fail(queue.snapshot().currentTrackId, "$action failed: ${error.message}") }
            job.complete()
        }
    }

    private suspend fun runBlockCommand(command: Command.Block) {
        if (command.result.isCancelled) return
        val value = try {
            command.block()
        } catch (cancellation: CancellationException) {
            if (!currentCoroutineContext().isActive) {
                command.result.cancel()
                throw cancellation
            }
            command.result.completeExceptionally(cancellation)
            return
        } catch (error: Throwable) {
            command.result.completeExceptionally(error)
            return
        }
        command.result.complete(value)
    }

    fun dispatch(
        action: String,
        positionMs: Long = 0L,
        trackIds: List<String> = emptyList(),
        startIndex: Int = 0,
        enabled: Boolean = false,
        repeat: RepeatMode? = null,
    ): Job {
        val job = Job()
        enqueue(Command.Action(action, positionMs, trackIds, startIndex, enabled, repeat, job))
        return job
    }

    private suspend fun handleActionCommand(
        action: String,
        positionMs: Long = 0L,
        trackIds: List<String> = emptyList(),
        startIndex: Int = 0,
        enabled: Boolean = false,
        repeat: RepeatMode? = null,
    ) {
        pollEngineEvents()
        log.d("Handling action=$action queueSize=${queue.snapshot().activeTrackIds.size}")
        if (action in MoodRadioStoppingActions) stopMoodRadio()
        when (action) {
            PlaybackService.ActionPlay -> handleTransition(runCatching { queue.play(PlaybackRequest(trackIds, startIndex)) }
                .getOrElse { QueueTransition.Stop }, PlaybackEndReason.SKIPPED)
            PlaybackService.ActionShuffle -> handleTransition(runCatching { queue.playShuffled(PlaybackRequest(trackIds, startIndex)) }
                .getOrElse { QueueTransition.Stop }, PlaybackEndReason.SKIPPED)
            PlaybackService.ActionPause -> {
                resumeOnFocusGain = false
                restoreFocusGain()
                pauseCurrent()
            }
            PlaybackService.ActionPauseForTransientFocusLoss -> pauseForTransientFocusLoss()
            PlaybackService.ActionDuck -> duckForFocusLoss()
            PlaybackService.ActionRestoreFocus -> restoreAfterFocusGain()
            PlaybackService.ActionResume -> {
                resumeOnFocusGain = false
                resumeCurrent()
            }
            PlaybackService.ActionStop -> stopPlayback()
            PlaybackService.ActionClearQueue -> handleTransition(queue.clear())
            PlaybackService.ActionNext -> handleTransition(queue.next(), PlaybackEndReason.SKIPPED, preservePlaybackState = true)
            PlaybackService.ActionPrevious -> {
                if (positionMs() > PreviousRestartThresholdMs) seekCurrent(0L)
                else handleTransition(queue.previous(), PlaybackEndReason.SKIPPED, preservePlaybackState = true)
            }
            PlaybackService.ActionSeek -> seekCurrent(positionMs)
            PlaybackService.ActionSetShuffle -> handleTransition(queue.setShuffle(enabled))
            PlaybackService.ActionSetRepeat -> repeat?.let(queue::setRepeatMode)
            PlaybackService.ActionPlayNext -> queue.playNext(trackIds)
            PlaybackService.ActionAppend -> queue.append(trackIds)
            PlaybackService.ActionStartMoodRadio -> trackIds.firstOrNull()?.let { startMoodRadio(it) }
            PlaybackService.ActionSelect -> trackIds.firstOrNull()?.let { handleTransition(queue.select(it), PlaybackEndReason.SKIPPED) }
            PlaybackService.ActionRemove -> trackIds.firstOrNull()?.let { handleTransition(queue.removeFromQueue(it), PlaybackEndReason.SKIPPED) }
            PlaybackService.ActionReorder -> queue.reorderQueue(trackIds)
        }
        if (action in PreloadResyncActions) {
            // The old source must not remain part of a fade whose queued
            // successor has just changed.
            if (isCrossfading()) {
                clearArtworkCrossfade()
                snapCrossfade()
            }
            preloadNext()
        }
        publishQueue()
    }

    private suspend fun startMoodRadio(seedId: String) {
        if (!library.libraryAnalysisEnabled()) {
            log.d("Mood Radio ignored: library analysis is disabled")
            return
        }
        val tracks = library.moodRadioTracks()
        val selected = selectMoodRadio(seedId, tracks, emptySet(), MoodRadioBatchSize)
        if (selected.isEmpty()) {
            log.d("Mood Radio has no candidates seed=$seedId analyzed=${tracks.count { it.energy != null && it.danceability != null && it.brightness != null && it.tempo != null }}")
            return
        }
        moodRadioSeedId = seedId
        moodRadioLastRefillAttempt = null
        flows.moodRadioActive.value = true
        if (queue.snapshot().currentTrackId == seedId) queue.replaceKeepingCurrent(listOf(seedId) + selected.map { it.id })
        else handleTransition(queue.play(PlaybackRequest(listOf(seedId) + selected.map { it.id })), PlaybackEndReason.SKIPPED)
        log.d("Mood Radio started seed=$seedId added=${selected.size} queueSize=${queue.snapshot().activeTrackIds.size}")
    }

    private suspend fun refillMoodRadioIfNeeded(): Boolean {
        val seedId = moodRadioSeedId ?: return false
        val snapshot = queue.snapshot()
        if (snapshot.activeTrackIds.size - snapshot.currentIndex - 1 >= MoodRadioRefillThreshold) return false
        val attempt = snapshot.currentTrackId to snapshot.activeTrackIds.size
        if (attempt == moodRadioLastRefillAttempt) return false
        moodRadioLastRefillAttempt = attempt
        if (!library.libraryAnalysisEnabled()) return false
        val selected = selectMoodRadio(seedId, library.moodRadioTracks(), snapshot.activeTrackIds.toSet(), MoodRadioBatchSize)
        if (selected.isEmpty()) return false
        queue.append(selected.map { it.id })
        log.d("Mood Radio refilled seed=$seedId added=${selected.size} queueSize=${queue.snapshot().activeTrackIds.size}")
        return true
    }

    internal fun stopMoodRadio() { moodRadioSeedId = null; moodRadioLastRefillAttempt = null; flows.moodRadioActive.value = false }

    /** FR-091: media-session skip-to-queue-item; routed through the serialized command path. */
    fun selectQueueItem(index: Long): Job {
        val job = Job()
        enqueue(Command.SelectIndex(index, job))
        return job
    }

    private fun pauseForTransientFocusLoss() {
        resumeOnFocusGain = flows.state.value is PlaybackState.Playing || pendingStart != null
        restoreFocusGain()
        pauseCurrent()
    }

    private suspend fun restoreAfterFocusGain() {
        restoreFocusGain()
        if (!resumeOnFocusGain) return
        resumeOnFocusGain = false
        resumeCurrent()
    }

    internal suspend fun tick() {
        try {
            refreshPlaybackPosition()
            val playing = flows.state.value as? PlaybackState.Playing
            if (playing != null) enqueueListening(listeningTracker.tick(
                playing.positionMs, playing.durationMs, clock.nowMs(), clock.elapsedMs(),
            ))
            pollEngineEvents()
            if (listeningFadeOutgoing != null && engine?.isCrossfading() != true) finishListeningCrossfade()
            if (engine?.isCrossfading() != true) clearArtworkCrossfade()
            if (audioOutputDisconnectRequiresRecovery(outputDisconnected)) {
                recoverAfterOutputDisconnect()
            } else if (maybeStartCrossfade()) {
                pollEngineEvents()
            } else if (endedPending && flows.state.value is PlaybackState.Playing) {
                endedPending = false
                handleTransition(queue.next(), PlaybackEndReason.COMPLETED)
                publishQueue()
            }
            if (refillMoodRadioIfNeeded()) publishQueue()
            // A crossfade occupies both native source slots. Once its
            // callback retires the outgoing item, populate that slot
            // with the queue's new immediate successor.
            if (canPreloadNext(engine?.isCrossfading() == true)) preloadNext()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            log.e("Playback tick failed: ${error.message}")
            runCatching { fail(queue.snapshot().currentTrackId, "tick failed: ${error.message}") }
        }
    }

    internal suspend fun onPlaybackSettings(seconds: Int, blendArtwork: Boolean) {
        flows.crossfadeSeconds.value = seconds
        flows.blendArtworkDuringCrossfade.value = blendArtwork
        if (!blendArtwork) clearArtworkCrossfade()
        // A preference update must never change a fade already
        // running, but it does refresh the idle source afterward.
        if (engine?.isCrossfading() != true) preloadNext()
    }

    internal fun onEqualizerSettings(settings: EqualizerSettings) {
        equalizerSettings = settings
        engine?.setDsp(settings)
    }

    internal suspend fun onNormalizationSettings(settings: NormalizationSettings) {
        normalizationSettings = settings
        refreshNormalizationGains()
    }

    internal suspend fun restoreSaved(session: PlaybackSession, availableTrackIds: Set<String>) {
        if (availableTrackIds.isEmpty()) {
            clearRestoredSession()
            return
        }
        queue.restore(queueForAvailableTracks(session.queue, availableTrackIds))
        restoreCurrent(session.positionMs)
    }

    internal fun positionMs(): Long = engine?.positionMs() ?: 0L

    internal fun isCrossfading(): Boolean = engine?.isCrossfading() == true

    internal fun snapCrossfade() { engine?.snapCrossfade() }

    internal fun closeEngine() {
        engine?.close()
        outputDisconnected = false
        endedPending = false
        pendingStart = null
    }

    internal suspend fun handleTransition(
        transition: QueueTransition,
        previousReason: PlaybackEndReason = PlaybackEndReason.STOPPED,
        preservePlaybackState: Boolean = false,
    ) {
        when (transition) {
            is QueueTransition.Play -> {
                finishListeningCrossfade()
                enqueueListening(listeningTracker.finish(previousReason, clock.nowMs(), clock.elapsedMs()))
                playCurrent(startPaused = preservePlaybackState && flows.state.value is PlaybackState.Paused)
            }
            QueueTransition.StopAtCurrent -> stopAtCurrentTrack(previousReason)
            QueueTransition.Stop -> stopPlayback()
            QueueTransition.Unchanged -> Unit
        }
    }

    private suspend fun playCurrent(startPositionMs: Long = 0L, startPaused: Boolean = false, startTracking: Boolean = true) {
        finishListeningCrossfade()
        clearArtworkCrossfade()
        val trackId = queue.snapshot().currentTrackId ?: return stopPlayback()
        log.d("Preparing current queue track id=$trackId")
        val item = resolver.resolve(trackId) ?: return fail(trackId, "Audio asset is not available")
        flows.state.value = PlaybackState.Preparing(item)
        nowPlaying.publishNowPlaying(item, TransportState.Buffering, 0L, 0L, activeQueueItemId(queue.snapshot()))
        if (!startPaused && !requestFocusForStart()) return fail(trackId, "Audio focus was not granted")
        try {
            engine?.close()
            // A normalization lookup can suspend. Do not leave a closed decoder
            // reachable while a queued session write may read playback state.
            engine = null
            outputDisconnected = false
            endedPending = false
            pendingStart = null
            val gainDb = normalizationGain(item, queue.peekNext())
            val preparedEngine = engineFactory()
            try {
                preparedEngine.setDsp(equalizerSettings)
                preparedEngine.setFocusGain(if (isDucked) DuckedFocusGain else 1f)
                preparedEngine.prepare(item, ItemGain(gainDb), startPositionMs, startPaused)
                engine = preparedEngine
                if (startPaused) {
                    flows.state.value = PlaybackState.Paused(item, preparedEngine.positionMs(), preparedEngine.durationMs())
                    nowPlaying.publishNowPlaying(item, TransportState.Paused, preparedEngine.positionMs(), preparedEngine.durationMs(), activeQueueItemId(queue.snapshot()))
                    scrobble.startPlayback(item.trackId, preparedEngine.positionMs())
                } else {
                    pendingStart = PendingStart.Fresh(item, startTracking)
                    pollEngineEvents()
                    if (engine == null || flows.state.value is PlaybackState.Failed) return
                }
            } catch (error: Throwable) {
                preparedEngine.close()
                throw error
            }
            preloadNext()
            nowPlaying.showForeground(item)
            log.d("Playback started id=$trackId durationMs=${engine?.durationMs()}")
        } catch (error: Throwable) {
            fail(trackId, error.message ?: "Unable to decode audio")
        }
    }

    /** Restores the selected item paused, so reopening the app never starts audio by itself. */
    private suspend fun restoreCurrent(savedPositionMs: Long) {
        val trackId = queue.snapshot().currentTrackId ?: return clearRestoredSession()
        val item = resolver.resolve(trackId)
            ?: return clearRestoredSession()
        // Publish the retained item before FFmpeg opens it, so Compose can mount the mini player.
        flows.state.value = PlaybackState.Paused(item, savedPositionMs.coerceAtLeast(0L), durationMs = 0L)
        try {
            engine?.close()
            engine = null
            outputDisconnected = false
            endedPending = false
            pendingStart = null
            val gain = normalizationGain(item, queue.peekNext())
            val preparedEngine = engineFactory()
            try {
                preparedEngine.setDsp(equalizerSettings)
                preparedEngine.setFocusGain(if (isDucked) DuckedFocusGain else 1f)
                preparedEngine.prepare(item, ItemGain(gain), savedPositionMs, startPaused = true)
                val positionMs = clampSeekPosition(savedPositionMs, preparedEngine.durationMs())
                engine = preparedEngine
                flows.state.value = PlaybackState.Paused(item, positionMs, preparedEngine.durationMs())
                nowPlaying.publishNowPlaying(item, TransportState.Paused, positionMs, preparedEngine.durationMs(), activeQueueItemId(queue.snapshot()))
                scrobble.startPlayback(item.trackId, positionMs)
                preloadNext()
                nowPlaying.showForeground(item)
                publishQueue()
                log.d("Restored paused playback id=$trackId positionMs=${engine?.positionMs()}")
            } catch (error: Throwable) {
                preparedEngine.close()
                throw error
            }
        } catch (error: Throwable) {
            log.w("Unable to restore playback id=$trackId; clearing session", error)
            clearRestoredSession()
        }
    }

    internal fun pauseCurrent() {
        finishListeningCrossfade()
        clearArtworkCrossfade()
        engine?.pause()
        enqueueListening(listeningTracker.pause(clock.nowMs(), clock.elapsedMs()))
        when (val current = flows.state.value) {
            is PlaybackState.Playing -> {
                val positionMs = engine?.positionMs() ?: current.positionMs
                flows.state.value = PlaybackState.Paused(current.item, positionMs, current.durationMs)
                nowPlaying.publishNowPlaying(current.item, TransportState.Paused, positionMs, current.durationMs, activeQueueItemId(queue.snapshot()))
            }
            is PlaybackState.Preparing -> pendingStart?.let { pending ->
                val positionMs = engine?.positionMs() ?: 0L
                val durationMs = engine?.durationMs() ?: 0L
                flows.state.value = PlaybackState.Paused(pending.item, positionMs, durationMs)
                nowPlaying.publishNowPlaying(pending.item, TransportState.Paused, positionMs, durationMs, activeQueueItemId(queue.snapshot()))
                if (pending is PendingStart.Fresh && pending.startTracking) {
                    scrobble.startPlayback(pending.item.trackId, positionMs)
                }
            }
            else -> Unit
        }
        pendingStart = null
        updateNotification()
    }

    internal fun duckForFocusLoss() {
        isDucked = true
        engine?.setFocusGain(DuckedFocusGain)
    }

    internal fun restoreFocusGain() {
        isDucked = false
        engine?.setFocusGain(1f)
    }

    /** Requests focus only when audio is about to start; re-requesting keeps an existing duck. */
    private fun requestFocusForStart(): Boolean {
        if (!focus.request()) return false
        if (!focusHeld) {
            focusHeld = true
            restoreFocusGain()
        }
        return true
    }

    private fun abandonFocus() {
        if (focusHeld) {
            focus.abandon()
            focusHeld = false
        }
    }

    private fun onOutputStarted() {
        val pending = pendingStart ?: return
        pendingStart = null
        val currentEngine = engine ?: return
        val item = pending.item
        val positionMs = currentEngine.positionMs()
        val durationMs = currentEngine.durationMs()
        flows.state.value = PlaybackState.Playing(item, positionMs, durationMs)
        nowPlaying.publishNowPlaying(item, TransportState.Playing, positionMs, durationMs, activeQueueItemId(queue.snapshot()))
        updateNotification()
        when (pending) {
            is PendingStart.Fresh -> if (pending.startTracking) {
                scrobble.startPlayback(item.trackId, positionMs)
                enqueueListening(listeningTracker.start(item.trackId, positionMs, clock.nowMs(), clock.elapsedMs()))
            } else {
                listeningTracker.resumeAfterInterruption(clock.elapsedMs())
            }
            is PendingStart.Resume -> if (pending.resumeListening) {
                listeningTracker.resume(clock.nowMs(), clock.elapsedMs())
            } else {
                enqueueListening(listeningTracker.start(item.trackId, positionMs, clock.nowMs(), clock.elapsedMs()))
            }
        }
    }

    internal suspend fun resumeCurrent() {
        val paused = flows.state.value as? PlaybackState.Paused
        val currentEngine = engine
        if (paused != null && shouldRestartQueueOnResume(paused.positionMs, paused.durationMs, currentEngine != null)) {
            handleTransition(queue.restart())
            return
        }
        if (paused == null || currentEngine == null || outputDisconnected) {
            if (outputDisconnected) {
                currentEngine?.close()
                engine = null
                outputDisconnected = false
                endedPending = false
                pendingStart = null
            }
            playCurrent(paused?.positionMs ?: 0L)
            return
        }
        if (!requestFocusForStart()) {
            fail(paused.item.trackId, "Audio focus was not granted")
            return
        }
        currentEngine.play()
        val positionMs = currentEngine.positionMs()
        pendingStart = PendingStart.Resume(paused.item, listeningTracker.activeTrackId == paused.item.trackId)
        nowPlaying.publishNowPlaying(paused.item, TransportState.Buffering, positionMs, paused.durationMs, activeQueueItemId(queue.snapshot()))
        pollEngineEvents()
        log.d("Playback resumed id=${paused.item.trackId} positionMs=$positionMs")
    }

    /**
     * A manual route change invalidates the old AAudio stream without being a
     * user pause. Recreate it on the new route and retain its rendered position.
     * A real device removal sends ACTION_AUDIO_BECOMING_NOISY, whose queued
     * pause action wins and leaves playback paused instead.
     */
    private suspend fun recoverAfterOutputDisconnect() {
        val current = flows.state.value as? PlaybackState.Playing ?: return
        val positionMs = engine?.positionMs() ?: current.positionMs
        listeningTracker.suspendForInterruption(clock.elapsedMs())
        engine?.close()
        engine = null
        outputDisconnected = false
        endedPending = false
        pendingStart = null
        log.w("Audio output changed; recreating stream id=${current.item.trackId} positionMs=$positionMs")
        playCurrent(positionMs, startTracking = false)
    }

    internal fun seekCurrent(requestedPositionMs: Long) {
        val preparing = flows.state.value as? PlaybackState.Preparing
        if (preparing != null && pendingStart != null) {
            finishListeningCrossfade()
            clearArtworkCrossfade()
            val targetPositionMs = clampSeekPosition(requestedPositionMs, engine?.durationMs() ?: 0L)
            engine?.seekTo(targetPositionMs) ?: return
            endedPending = false
            return
        }
        val current = flows.state.value
        val item: PlaybackItem
        val durationMs: Long
        val playing: Boolean
        when (current) {
            is PlaybackState.Playing -> {
                item = current.item
                durationMs = current.durationMs
                playing = true
            }
            is PlaybackState.Paused -> {
                item = current.item
                durationMs = current.durationMs
                playing = false
            }
            else -> return
        }
        val targetPositionMs = clampSeekPosition(requestedPositionMs, durationMs)
        finishListeningCrossfade()
        clearArtworkCrossfade()
        engine?.seekTo(targetPositionMs) ?: return
        endedPending = false
        scrobble.seek(targetPositionMs)
        if (playing) {
            flows.state.value = PlaybackState.Playing(item, targetPositionMs, durationMs)
            nowPlaying.publishNowPlaying(item, TransportState.Playing, targetPositionMs, durationMs, activeQueueItemId(queue.snapshot()))
        } else {
            flows.state.value = PlaybackState.Paused(item, targetPositionMs, durationMs)
            nowPlaying.publishNowPlaying(item, TransportState.Paused, targetPositionMs, durationMs, activeQueueItemId(queue.snapshot()))
        }
        log.d("Seek requested id=${item.trackId} targetMs=$targetPositionMs playing=$playing")
    }

    internal fun stopPlayback() {
        resumeOnFocusGain = false
        finishListeningCrossfade()
        enqueueListening(listeningTracker.finish(PlaybackEndReason.STOPPED, clock.nowMs(), clock.elapsedMs()))
        clearArtworkCrossfade()
        engine?.snapCrossfade()
        engine?.close(); engine = null; preloadedItem = null
        outputDisconnected = false
        endedPending = false
        pendingStart = null
        abandonFocus()
        flows.state.value = PlaybackState.Idle
        nowPlaying.setTransportState(TransportState.Stopped, 0L, activeQueueItemId(queue.snapshot()))
        nowPlaying.deactivate()
        nowPlaying.stopForeground()
    }

    /** Retains the final item when repeat-off playback or manual navigation exhausts the queue. */
    private fun stopAtCurrentTrack(reason: PlaybackEndReason = PlaybackEndReason.COMPLETED) {
        val current = flows.state.value
        val item: PlaybackItem
        val durationMs: Long
        when (current) {
            is PlaybackState.Playing -> {
                item = current.item
                durationMs = current.durationMs
            }
            is PlaybackState.Paused -> {
                item = current.item
                durationMs = current.durationMs
            }
            else -> return stopPlayback()
        }
        enqueueListening(listeningTracker.finish(reason, clock.nowMs(), clock.elapsedMs()))
        clearArtworkCrossfade()
        engine?.snapCrossfade()
        engine?.close(); engine = null
        outputDisconnected = false
        endedPending = false
        pendingStart = null
        abandonFocus()
        val positionMs = stoppedCurrentPosition(reason, durationMs)
        flows.state.value = PlaybackState.Paused(item, positionMs, durationMs)
        nowPlaying.publishNowPlaying(item, TransportState.Paused, positionMs, durationMs, activeQueueItemId(queue.snapshot()))
        updateNotification()
        log.d("Playback stopped at final queue track id=${item.trackId} positionMs=$positionMs")
    }

    private fun fail(trackId: String?, reason: String) {
        log.e("Playback failed id=$trackId reason=$reason")
        finishListeningCrossfade()
        enqueueListening(listeningTracker.finish(PlaybackEndReason.STOPPED, clock.nowMs(), clock.elapsedMs()))
        clearArtworkCrossfade()
        engine?.close(); engine = null; preloadedItem = null
        outputDisconnected = false
        endedPending = false
        pendingStart = null
        abandonFocus()
        flows.state.value = PlaybackState.Failed(trackId, reason)
        nowPlaying.setTransportState(TransportState.Error, 0L, activeQueueItemId(queue.snapshot()))
        nowPlaying.deactivate()
        nowPlaying.stopForeground()
    }

    internal suspend fun publishQueue() {
        val snapshot = queue.snapshot()
        flows.queueState.value = snapshot
        nowPlaying.publishQueue(snapshot)
        updateNowPlayingTransportState()
        // Capture before the asynchronous DataStore write. A later command can
        // close the decoder, but cannot change this immutable session snapshot.
        val session = currentSession(snapshot)
        scope.launch { sessionStore.save(session) }
    }

    internal fun currentSession(snapshot: PlaybackQueueSnapshot = queue.snapshot()): PlaybackSession {
        val positionMs = when (val current = flows.state.value) {
            is PlaybackState.Playing -> engine?.positionMs() ?: current.positionMs
            is PlaybackState.Paused -> engine?.positionMs() ?: current.positionMs
            else -> 0L
        }
        return PlaybackSession(snapshot, positionMs.coerceAtLeast(0L))
    }

    internal suspend fun clearRestoredSession() {
        engine?.close(); engine = null; preloadedItem = null
        outputDisconnected = false
        endedPending = false
        pendingStart = null
        queue.clear()
        flows.queueState.value = queue.snapshot()
        nowPlaying.publishQueue(queue.snapshot())
        flows.state.value = PlaybackState.Idle
        nowPlaying.setTransportState(TransportState.None, 0L, activeQueueItemId(queue.snapshot()))
        nowPlaying.deactivate()
        nowPlaying.stopForeground()
        sessionStore.clear()
    }

    /** Keep native idle slot aligned with the queue's immediate next item. */
    internal suspend fun preloadNext() {
        val nextId = queue.peekNext()
        val currentEngine = engine ?: return
        if (nextId == preloadedItem?.trackId && currentEngine.hasPreloaded()) return
        currentEngine.clearPreloaded()
        preloadedItem = nextId?.let { id -> resolver.resolve(id) }
        preloadedItem?.let { item ->
            runCatching { currentEngine.preloadNext(item, ItemGain(normalizationGain(item, queue.peekNext()))); currentEngine.hasPreloaded() }
                .onSuccess { loaded ->
                    if (!loaded) preloadedItem = null
                }
                .onFailure { error ->
                    log.w("Unable to preload next id=${item.trackId}", error)
                    preloadedItem = null
                }
        }
    }

    /** Native promotes audio first; this service transaction promotes queue/UI metadata. */
    internal suspend fun pollEngineEvents() {
        val events = engine?.pollEvents() ?: return
        for (event in events) {
            when (event) {
                is EngineEvent.GaplessAdvanced -> consumeEngineTransition(event)
                is EngineEvent.TransitionStarted -> consumeEngineTransition(event)
                EngineEvent.OutputDisconnected -> outputDisconnected = true
                EngineEvent.Ended -> {
                    if (pendingStart != null) onOutputStarted()
                    endedPending = true
                }
                EngineEvent.OutputStarted -> onOutputStarted()
                is EngineEvent.Error -> pendingStart?.let { pending ->
                    fail(pending.item.trackId, "Output did not start: ${event.cause.message}")
                }
            }
        }
    }

    private suspend fun consumeEngineTransition(event: EngineEvent) {
        if (preloadedItem == null) return
        val incoming = when (event) {
            is EngineEvent.GaplessAdvanced -> event.incoming
            is EngineEvent.TransitionStarted -> event.incoming
            else -> return
        }
        val queueTransition = queue.next()
        if (queueTransition !is QueueTransition.Play || queueTransition.trackId != incoming.trackId) {
            fail(incoming.trackId, "Native transition no longer matches the playback queue")
            return
        }
        preloadedItem = null
        val currentEngine = engine ?: return
        val positionMs = currentEngine.positionMs()
        val durationMs = currentEngine.durationMs()
        if (event is EngineEvent.TransitionStarted) {
            listeningFadeOutgoing = (flows.state.value as? PlaybackState.Playing)?.item?.trackId
            listeningFadeStartedAt = clock.nowMs()
            listeningFadeStartedElapsed = clock.elapsedMs()
            listeningFadeMaxMs = flows.artworkCrossfade.value?.durationMs ?: 0L
        }
        flows.state.value = PlaybackState.Playing(incoming, positionMs, durationMs)
        enqueueListening(listeningTracker.finish(PlaybackEndReason.COMPLETED, clock.nowMs(), clock.elapsedMs()))
        enqueueListening(listeningTracker.start(incoming.trackId, positionMs, clock.nowMs(), clock.elapsedMs()))
        scrobble.startPlayback(incoming.trackId, positionMs)
        nowPlaying.publishNowPlaying(incoming, TransportState.Playing, positionMs, durationMs, activeQueueItemId(queue.snapshot()))
        updateNotification()
        // During a crossfade both native slots are live (incoming + outgoing).
        // Loading i+2 here would reuse the outgoing slot and cut i off instead
        // of letting it fade out. The ticker reloads after the fade completes.
        if (canPreloadNext(currentEngine.isCrossfading())) preloadNext()
        publishQueue()
        log.d("Consumed native transition=$event id=${incoming.trackId}")
    }

    private suspend fun normalizationGain(item: PlaybackItem, nextId: String?): Float {
        val analyses = library.activeAnalyses()
        if (analyses.isEmpty()) {
            if (normalizationSettings.enabled) library.disableNormalization()
            return 0f
        }
        val next = nextId?.let { resolver.resolve(it) }
        val continuousAlbum = normalizationSettings.mode == NormalizationMode.Album &&
            item.albumId.isNotEmpty() && item.albumId == next?.albumId
        val albumAnalyses = if (continuousAlbum) {
            library.tracks().filter { it.albumId == item.albumId }.mapNotNull { analyses[it.id] }
        } else emptyList()
        return normalizationGainDb(normalizationSettings, item.analysis, albumAnalyses, continuousAlbum)
    }

    private suspend fun refreshNormalizationGains() {
        val current = when (val value = flows.state.value) {
            is PlaybackState.Playing -> value.item
            is PlaybackState.Paused -> value.item
            is PlaybackState.Preparing -> value.item
            else -> null
        } ?: return
        val nextId = queue.peekNext()
        engine?.setGains(ItemGain(normalizationGain(current, nextId)), preloadedItem?.let { ItemGain(normalizationGain(it, queue.peekNext())) })
    }

    private fun maybeStartCrossfade(): Boolean {
        val current = flows.state.value as? PlaybackState.Playing ?: return false
        val currentEngine = engine ?: return false
        val incoming = preloadedItem ?: return false
        if (currentEngine.isCrossfading()) return false
        if (!shouldStartCrossfade(
                crossfadeSeconds = flows.crossfadeSeconds.value,
                positionMs = currentEngine.positionMs(),
                durationMs = current.durationMs,
                hasPreloadedNext = preloadedItem != null && currentEngine.hasPreloaded(),
            )
        ) return false
        val effectiveDurationMs = crossfadeDurationMs(
            crossfadeSeconds = flows.crossfadeSeconds.value,
            positionMs = currentEngine.positionMs(),
            durationMs = current.durationMs,
        )
        if (currentEngine.beginCrossfade(effectiveDurationMs)) {
            val id = nextArtworkCrossfadeId()
            flows.artworkCrossfade.value = ArtworkCrossfadeTransition(
                id = id,
                fromArtworkPath = current.item.artworkPath,
                toArtworkPath = incoming.artworkPath,
                durationMs = effectiveDurationMs.coerceAtLeast(1L),
            )
        }
        return true
    }

    internal fun clearArtworkCrossfade() {
        flows.artworkCrossfade.value = null
    }

    private fun updateNotification() {
        val item = when (val current = flows.state.value) {
            is PlaybackState.Playing -> current.item
            is PlaybackState.Paused -> current.item
            else -> return
        }
        nowPlaying.updateNotification(item)
    }

    private fun updateNowPlayingTransportState() {
        when (val current = flows.state.value) {
            is PlaybackState.Playing -> nowPlaying.setTransportState(
                TransportState.Playing, engine?.positionMs() ?: current.positionMs, activeQueueItemId(queue.snapshot()),
            )
            is PlaybackState.Paused -> nowPlaying.setTransportState(
                TransportState.Paused, engine?.positionMs() ?: current.positionMs, activeQueueItemId(queue.snapshot()),
            )
            else -> Unit
        }
    }

    private fun refreshPlaybackPosition() {
        val current = flows.state.value as? PlaybackState.Playing ?: return
        val positionMs = engine?.positionMs()?.let { clampSeekPosition(it, current.durationMs) } ?: return
        if (positionMs == current.positionMs) return
        flows.state.value = current.copy(positionMs = positionMs)
        nowPlaying.setTransportState(TransportState.Playing, positionMs, activeQueueItemId(queue.snapshot()))
        scrobble.reportPlayback(current.item, positionMs, current.durationMs)
    }

    internal fun enqueueListening(writes: List<ListeningWrite>) {
        writes.forEach { write ->
            if (!listening.offer(write)) log.w("Listening write queue is full")
        }
    }

    internal fun finishListeningCrossfade() {
        val outgoing = listeningFadeOutgoing ?: return
        val elapsed = (clock.elapsedMs() - listeningFadeStartedElapsed).coerceAtLeast(0).coerceAtMost(listeningFadeMaxMs)
        enqueueListening(listeningTracker.splitCrossfadeOverlap(outgoing, listeningFadeStartedAt, elapsed))
        listeningFadeOutgoing = null
        listeningFadeStartedAt = 0
        listeningFadeStartedElapsed = 0
        listeningFadeMaxMs = 0
    }
}
