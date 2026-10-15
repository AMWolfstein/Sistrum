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

    @Test fun recordWithoutInitIsANoOp() {
        Diagnostics.initForTest(null)
        Diagnostics.record(Diagnostics.LEVEL_INFO, "audio", "ignored")
        Diagnostics.flush()
    }
}
