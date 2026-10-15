package com.amwolfstein.sistrum.diagnostics

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsLogTest {
    @get:Rule val folder = TemporaryFolder()

    @After fun reset() = Diagnostics.initForTest(null)

    @Test fun recordsInOrderAndKeepsOnlyTheNewest() {
        val log = DiagnosticsLog(folder.newFile("d.jsonl"), maxEntries = 3)
        for (i in 1..5) log.record(DiagnosticEntry(i.toLong(), "W", "audio", "event $i"))
        assertEquals(listOf(3L, 4L, 5L), log.entries().map { it.timeMs })
    }

    @Test fun survivesReopenSkipsBrokenLinesAndClears() {
        val file = folder.newFile("d.jsonl")
        DiagnosticsLog(file).record(DiagnosticEntry(7, "E", "crash", "boom\nstack"))
        file.appendText("not json\n")
        val reopened = DiagnosticsLog(file)
        assertEquals(listOf("boom\nstack"), reopened.entries().map { it.message })
        reopened.clear()
        assertEquals(emptyList<DiagnosticEntry>(), DiagnosticsLog(file).entries())
    }

    @Test fun facadeQueuesWritesWithLevelAreaAndTime() {
        val log = DiagnosticsLog(File(folder.root, "d.jsonl"))
        Diagnostics.initForTest(log) { 42L }
        Diagnostics.record(Diagnostics.LEVEL_WARN, "audio", "AudioTrack underrun x3")
        Diagnostics.flush()
        assertEquals(listOf(DiagnosticEntry(42L, "W", "audio", "AudioTrack underrun x3")), log.entries())
    }

    @Test fun exportHidesKnownHarmlessPlatformLinesAndCountsThem() {
        // Lines from the owner's moto g75 exports (2026-10-15).
        val logcat = """
            --------- beginning of main
            10-15 15:46:54.096   333   427 W HWUI    : Image decoding logging dropped!
            10-15 15:46:54.467   333   333 W ein.sistrum.dev: Unable to open libdolphin.so: dlopen failed: library "libdolphin.so" not found.
            10-15 15:46:59.164   333   333 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=false callback=ty2@c15903e
            10-15 15:47:00.920   333   413 W AudioSystem-JNI: Unknown native audio format for JAVA API: 503316496
            10-15 15:47:05.753   333   337 W ein.sistrum.dev: Missing inline cache for java.lang.Object xu1.b()
            10-15 15:47:08.181   333   438 E SistrumPlayback: Uncaught playback coroutine exception
            10-15 15:47:08.181   333   438 E SistrumPlayback: h70: lateinit property coordinator has not been initialized
            10-15 15:47:08.360   333   935 W VideoCapabilities: Unsupported mime image/vnd.android.heic
            10-15 15:47:08.455   333   962 W SpeakerLayoutUtil: Built-in speaker's getSpeakerLayoutChannelMask not usable, defaulting to stereo.
            10-15 15:47:08.495   333   962 E MediaCodec: Media Quality Service not found.
            10-15 15:47:08.497   333   975 E ein.sistrum.dev: Failed to query component interface for required system resources: 6
            10-15 15:47:08.507   333   975 W ein.sistrum.dev: AIBinder_linkToDeath is being called with a non-null cookie and no onUnlink callback set.
            10-15 15:47:08.508   333   962 W LoudnessCodecController: MediaCodec mime type not supported for loudness annotation
            10-15 15:47:08.789   333   962 W AudioTrack: getTimestamp_l(1598): retrograde timestamp time corrected, 74022586149468 < 74022595984416
            10-15 15:52:25.683   333   975 W LegacyMessageQueue: Handler (android.media.MediaCodec${'$'}EventHandler) {cec950a} sending message to a Handler on a dead thread
        """.trimIndent() + "\n"
        val expected = """
            --------- beginning of main
            10-15 15:47:08.181   333   438 E SistrumPlayback: Uncaught playback coroutine exception
            10-15 15:47:08.181   333   438 E SistrumPlayback: h70: lateinit property coordinator has not been initialized
            10-15 15:52:25.683   333   975 W LegacyMessageQueue: Handler (android.media.MediaCodec${'$'}EventHandler) {cec950a} sending message to a Handler on a dead thread
            (12 known-harmless lines hidden)
        """.trimIndent() + "\n"
        assertEquals(expected, Diagnostics.withoutKnownHarmless(logcat))
    }

    @Test fun exportOfOnlyHarmlessLinesStillShowsTheCount() {
        assertEquals("(1 known-harmless lines hidden)\n", Diagnostics.withoutKnownHarmless("W HWUI    : Image decoding logging dropped!\n"))
        assertEquals("(0 known-harmless lines hidden)\n", Diagnostics.withoutKnownHarmless(""))
    }

    @Test fun recordWithoutInitIsANoOp() {
        Diagnostics.initForTest(null)
        Diagnostics.record(Diagnostics.LEVEL_INFO, "audio", "ignored")
        Diagnostics.flush()
    }
}
