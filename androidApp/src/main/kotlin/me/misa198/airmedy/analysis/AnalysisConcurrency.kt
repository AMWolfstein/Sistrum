package me.misa198.airmedy.analysis

import android.os.PowerManager

/** How many tracks to analyse at once. */
internal object AnalysisConcurrency {
    /**
     * CPH2307 (T051d, 2026-10-13): throughput scaled ~linearly to 3 workers (49.5 / 25.2 / 17.4 s per sample pass for
     * 1 / 2 / 3) with no thermal throttling in short runs; sustained behaviour is checked in T054.
     */
    const val MAX_WORKERS = 3

    /** Worker count: paused when too hot, one on low-core/throttled devices, otherwise capped at half the cores (at least one). */
    fun workerCount(availableCores: Int, thermalStatus: Int, thermalHeadroom: Float?, maxWorkers: Int = MAX_WORKERS): Int {
        if (availableCores < 1) return 0
        val base = when {
            thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 0
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 1
            thermalHeadroom != null && thermalHeadroom >= 0.85f -> 1
            availableCores <= 4 -> 1
            else -> maxWorkers.coerceAtLeast(1)
        }
        return minOf(base, maxOf(1, availableCores / 2))
    }
}
