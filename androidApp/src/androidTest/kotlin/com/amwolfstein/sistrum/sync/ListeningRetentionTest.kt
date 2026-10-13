package com.amwolfstein.sistrum.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import com.amwolfstein.sistrum.player.ListeningSession
import com.amwolfstein.sistrum.player.ListeningWrite
import com.amwolfstein.sistrum.player.PlaybackAttempt
import com.amwolfstein.sistrum.player.PlaybackEndReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PlaybackService prunes raw listening records with cleanupListening(now - ListeningRetentionMs),
 * a 180-day window (README: "Raw records are retained for 180 days and daily ... aggregates
 * are retained for all-time totals"). This covers the store side of that contract with an
 * explicit cutoff; the retention length itself stays defined only in PlaybackService.
 */
class ListeningRetentionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    private val day = 24L * 60 * 60 * 1_000
    private val cutoff = 1_780_000_000_000L
    private val before = cutoff - day
    private val after = cutoff + day

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "listening-retention-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    private fun ids(table: String, where: String = "1"): Set<String> = database.openHelper.readableDatabase
        .query("SELECT id FROM $table WHERE $where").use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun dailyRows(): List<String> = listOf("daily_track_listening_stats", "daily_playback_attempt_stats").flatMap { table ->
        database.openHelper.readableDatabase.query("SELECT * FROM $table").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) }) }
        }
    }

    private suspend fun session(id: String, endedAt: Long) = store.recordListening(
        ListeningWrite.Session(ListeningSession(id, "device", "t-$id", endedAt - 60_000, endedAt, 60, qualifiedPlay = true)),
    )

    private suspend fun attempt(id: String, startedAt: Long, endedAt: Long?) {
        val started = PlaybackAttempt(id, "device", "t-$id", startedAt, startPositionMs = 0)
        store.recordListening(ListeningWrite.AttemptStarted(started))
        if (endedAt != null) {
            store.recordListening(ListeningWrite.AttemptFinished(started.copy(endedAt = endedAt, listenedSeconds = 30, endReason = PlaybackEndReason.COMPLETED)))
        }
    }

    @Test fun cleanupDeletesRawRecordsOlderThanTheCutoffAndKeepsTheRest() = runBlocking {
        session("old", endedAt = before)
        session("edge", endedAt = cutoff)
        session("new", endedAt = after)
        attempt("old-finished", startedAt = before - 60_000, endedAt = before)
        attempt("new-finished", startedAt = after - 60_000, endedAt = after)
        attempt("open", startedAt = before - 60_000, endedAt = null)
        val dailyBefore = dailyRows()
        assertTrue("the writes produced daily aggregates", dailyBefore.isNotEmpty())

        store.cleanupListening(cutoff)

        assertEquals("sessions ending at or after the cutoff are kept", setOf("edge", "new"), ids("listening_sessions"))
        assertEquals("finished attempts before the cutoff are deleted", setOf("new-finished"), ids("playback_attempts", "endedAt > 0"))
        assertEquals("an unfinished attempt survives for recoverOpenPlaybackAttempts", setOf("open"), ids("playback_attempts", "endedAt = 0"))
        assertEquals("daily aggregates are kept for all-time totals", dailyBefore, dailyRows())
    }
}
