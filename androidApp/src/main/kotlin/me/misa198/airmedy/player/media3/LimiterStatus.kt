package me.misa198.airmedy.player.media3

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide holder for the latest [LimiterState] published by any Media3 limiter session
 * (FR-053/FR-055). The UI lives in the same process as the player but has no reference to the
 * factory that owns the session, so the factory publishes here and the settings page observes it.
 */
internal object LimiterStatus {
    private val _state = MutableStateFlow<LimiterState?>(null)

    val state: StateFlow<LimiterState?> = _state

    fun publish(state: LimiterState) {
        _state.value = state
    }
}
