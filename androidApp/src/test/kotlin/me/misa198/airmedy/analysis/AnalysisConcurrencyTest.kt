package me.misa198.airmedy.analysis

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for [AnalysisConcurrency.workerCount]: every thermal/core/headroom rule and the core cap. */
class AnalysisConcurrencyTest {

    @Test
    fun severeThermalPauses() {
        for (status in 3..6) {
            assertEquals(0, AnalysisConcurrency.workerCount(8, status, null, 2))
        }
    }

    @Test
    fun moderateThermalUsesOne() {
        assertEquals(1, AnalysisConcurrency.workerCount(8, PowerManager.THERMAL_STATUS_MODERATE, null, 3))
    }

    @Test
    fun lowHeadroomUsesOne() {
        assertEquals(1, AnalysisConcurrency.workerCount(8, 0, 0.9f, 3))
        assertEquals(1, AnalysisConcurrency.workerCount(8, 0, 0.85f, 3))
    }

    @Test
    fun ampleHeadroomUsesMaxWorkers() {
        assertEquals(2, AnalysisConcurrency.workerCount(8, 0, 0.5f, 2))
        assertEquals(2, AnalysisConcurrency.workerCount(8, 0, null, 2))
    }

    @Test
    fun fewCoresUseOne() {
        assertEquals(1, AnalysisConcurrency.workerCount(4, 0, null, 3))
        assertEquals(1, AnalysisConcurrency.workerCount(3, 0, null, 3))
        assertEquals(1, AnalysisConcurrency.workerCount(2, 0, null, 3))
        assertEquals("a single-core device still analyses", 1, AnalysisConcurrency.workerCount(1, 0, null, 3))
    }

    @Test
    fun coreCapHalvesAvailableCores() {
        assertEquals(3, AnalysisConcurrency.workerCount(6, 0, null, 10))
        assertEquals(4, AnalysisConcurrency.workerCount(8, 0, null, 10))
    }

    @Test
    fun maxWorkersVariants() {
        assertEquals(1, AnalysisConcurrency.workerCount(8, 0, null, 1))
        assertEquals(2, AnalysisConcurrency.workerCount(8, 0, null, 2))
        assertEquals(3, AnalysisConcurrency.workerCount(8, 0, null, 3))
    }

    @Test
    fun maxWorkersAtLeastOne() {
        assertEquals(1, AnalysisConcurrency.workerCount(8, 0, null, 0))
        assertEquals(1, AnalysisConcurrency.workerCount(8, 0, null, -3))
    }
}
