package com.amwolfstein.sistrum.ui.screens

import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.media3.LimiterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimiterNoteTest {

    @Test
    fun `no report yet has no note`() {
        assertNull(limiterNoteRes(null))
    }

    @Test
    fun `available and controlled has no note`() {
        assertNull(limiterNoteRes(LimiterState(available = true, controlled = true)))
    }

    @Test
    fun `unavailable shows the unavailable note`() {
        assertEquals(R.string.playback_limiter_unavailable_note, limiterNoteRes(LimiterState(available = false, controlled = false)))
        assertEquals(R.string.playback_limiter_unavailable_note, limiterNoteRes(LimiterState(available = false, controlled = true)))
    }

    @Test
    fun `available but not controlled shows the not-controlled note`() {
        assertEquals(R.string.playback_limiter_not_controlled_note, limiterNoteRes(LimiterState(available = true, controlled = false)))
    }
}
