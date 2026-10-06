package me.misa198.airmedy.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * FR-065a: a hidden (skipped) file must keep its last admitted tags instead of being
 * re-parsed, but only when it is the same file the prior scan admitted. A changed file
 * (different identity hash) is new; an old schema version still reuses the prior values
 * as the best known and re-parses on the next admitted scan.
 */
class HiddenTrackBasisTest {

    private fun prior(identityHash: String, schemaVersion: Int) = PriorTrackScanState(
        identityHash = identityHash,
        schemaVersion = schemaVersion,
        year = 0,
        releaseDate = "",
        bpm = 0,
        label = "",
        isrc = "",
        copyright = "",
    )

    @Test fun `no prior scan means nothing to reuse`() {
        assertEquals(HiddenTrackBasis(usePrior = false, schemaVersion = 0), hiddenTrackBasis(null, "hash"))
    }

    @Test fun `a different identity hash is not the same file`() {
        assertEquals(HiddenTrackBasis(usePrior = false, schemaVersion = 0), hiddenTrackBasis(prior("other", 7), "hash"))
    }

    @Test fun `the same identity at the current schema reuses the prior values`() {
        assertEquals(HiddenTrackBasis(usePrior = true, schemaVersion = 7), hiddenTrackBasis(prior("hash", 7), "hash"))
    }

    @Test fun `the same identity at an old schema still reuses the prior values`() {
        assertEquals(HiddenTrackBasis(usePrior = true, schemaVersion = 5), hiddenTrackBasis(prior("hash", 5), "hash"))
    }
}
