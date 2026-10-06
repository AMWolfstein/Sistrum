package me.misa198.airmedy.ui.screens

import me.misa198.airmedy.player.decoders.SkipReason
import me.misa198.airmedy.player.decoders.parseSkipReason
import me.misa198.airmedy.player.decoders.text
import me.misa198.airmedy.sync.SkippedEntry
import me.misa198.airmedy.sync.SkippedFilesSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SkippedSummaryLinesTest {

    @Test
    fun `merges the same reason across formats and sums the counts`() {
        val summary = SkippedFilesSummary(
            scanAtMillis = 1L,
            entries = listOf(
                SkippedEntry("dsf", "dsf", "unsupported format (DSD)", 3),
                SkippedEntry("dff", "dff", "unsupported format (DSD)", 2),
            ),
        )

        assertEquals(listOf(5 to "unsupported format (DSD)"), skippedSummaryLines(summary))
    }

    @Test
    fun `sorts by count descending then reason`() {
        val summary = SkippedFilesSummary(
            scanAtMillis = 1L,
            entries = listOf(
                SkippedEntry("wma", "wma", "unsupported format (WMA)", 1),
                SkippedEntry("dsf", "dsf", "unsupported format (DSD)", 3),
                SkippedEntry("m4a", "alac", "no decoder on this device (ALAC)", 3),
            ),
        )

        assertEquals(
            listOf(
                3 to "no decoder on this device (ALAC)",
                3 to "unsupported format (DSD)",
                1 to "unsupported format (WMA)",
            ),
            skippedSummaryLines(summary),
        )
    }

    @Test
    fun `null and empty summaries have no lines`() {
        assertEquals(emptyList<Pair<Int, String>>(), skippedSummaryLines(null))
        assertEquals(emptyList<Pair<Int, String>>(), skippedSummaryLines(SkippedFilesSummary(1L, emptyList())))
    }

    @Test
    fun `parses the two prefixed reason forms and leaves refusals as other`() {
        assertEquals(SkipReason.UnsupportedFormat("DSD"), parseSkipReason("unsupported format (DSD)"))
        assertEquals(SkipReason.NoDecoder("ALAC"), parseSkipReason("no decoder on this device (ALAC)"))
        assertEquals(SkipReason.Other("compressed AIFF-C (ima4)"), parseSkipReason("compressed AIFF-C (ima4)"))
    }

    @Test
    fun `reason text round-trips through parse`() {
        listOf(
            SkipReason.UnsupportedFormat("DSD"),
            SkipReason.NoDecoder("E-AC-3 JOC"),
            SkipReason.Other("float AIFF-C (fl32)"),
        ).forEach { reason ->
            assertEquals(reason, parseSkipReason(reason.text()))
        }
    }
}
