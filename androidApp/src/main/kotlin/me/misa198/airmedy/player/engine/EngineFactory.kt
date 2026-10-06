package me.misa198.airmedy.player.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Builds the engine for the persisted [EngineKind] selection, read fresh on every
 * [create] so a running engine is never swapped (SC-008). Media3 joins the map in T034.
 */
internal class EngineFactory(
    private val selection: Flow<EngineKind>,
    private val builders: Map<EngineKind, () -> PlayerEngine>,
) {
    init {
        require(EngineKind.Native in builders) { "EngineFactory requires a Native builder" }
    }

    /** Reads the current selection and builds it, falling back to Native for a kind with no builder. */
    suspend fun create(): PlayerEngine {
        val kind = selection.first()
        val builder = builders[kind] ?: builders.getValue(EngineKind.Native)
        return builder()
    }
}
