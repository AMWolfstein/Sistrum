// SPDX-License-Identifier: GPL-3.0-or-later
package me.misa198.airmedy.analysis
import org.junit.Test
import org.junit.Assert.assertEquals
class MetadataBoundaryTest {
    @Test fun shortDsdHasNoEburMetadataAndUsesSamplePeakFallback() {
        val path="owned/decoded/owned/dsd/dsd128-1ch.dsf.wav"
        val row=OracleSupport.rows().single { it["file"].asString==path }
        val r=OracleSupport.analyze(path)
        assertEquals("short-file loudness",OracleSupport.number(row,"loudness_lufs"),r.loudness_lufs,.1)
        assertEquals("short-file LRA",OracleSupport.number(row,"loudness_range"),r.loudness_range,.1)
        assertEquals("short-file true peak fallback",OracleSupport.number(row,"true_peak"),r.true_peak,.1)
    }
}
