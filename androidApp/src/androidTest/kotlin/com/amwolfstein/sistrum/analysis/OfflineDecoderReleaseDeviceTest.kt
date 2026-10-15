package com.amwolfstein.sistrum.analysis

import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 005 B5: each analysed track runs a temporary player on its own two threads. Quitting those threads right after
 * `release()` dropped the player's last events (renderer disabled, codec callbacks), and the platform logged
 * "sending message to a Handler on a dead thread" for every track (owner's diagnostics log, 2026-10-15).
 */
@RunWith(AndroidJUnit4::class)
class OfflineDecoderReleaseDeviceTest {

    @Test
    fun releasingTheDecodePlayerLeavesNoMessageForADeadThread() {
        val file = File(CORPUS_DIR, "flac_untagged.flac")
        assumeTrue("corpus file missing: $file", file.isFile)
        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
        val decoder = OfflineDecoder(InstrumentationRegistry.getInstrumentation().targetContext)
        repeat(3) {
            var frames = 0L
            val outcome = decoder.decode(file.absolutePath, null, { _, n, _, _ -> frames += n }, { false }, 0L)
            assertEquals(DecodeOutcome.Completed::class, outcome::class)
            assertTrue("decoded no audio", frames > 0)
        }
        // Late events arrive within milliseconds of release; wait past the threads' grace period.
        SystemClock.sleep(3_000)
        val deadThreadLines = ownLogcatSince(since).lines().filter { "on a dead thread" in it && "Exception" !in it }
        assertTrue("messages sent to quit decode threads:\n${deadThreadLines.joinToString("\n")}", deadThreadLines.isEmpty())
        // The grace period only delays quitting: no decode thread outlives it.
        assertTrue("decode threads still alive", decoder.decodeThreads.none { it.isAlive })
    }

    private fun ownLogcatSince(since: String): String {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-T", since, "--pid=${Process.myPid()}", "*:W")
            .redirectErrorStream(true).start()
        return process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }

    private companion object {
        const val CORPUS_DIR = "/sdcard/Music/SistrumTestCorpus"
    }
}
