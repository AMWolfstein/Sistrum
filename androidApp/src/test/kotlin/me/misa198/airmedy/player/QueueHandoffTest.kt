package me.misa198.airmedy.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueHandoffTest {
    @Test
    fun `twenty thousand ids round trip exactly`() {
        val handoff = QueueHandoff(nowMs = { 0L })
        val ids = List(20_000) { "track-$it" }

        val token = handoff.put(ids)
        assertEquals(ids, handoff.take(token))
    }

    @Test
    fun `a token is consumed once`() {
        val handoff = QueueHandoff(nowMs = { 0L })
        val token = handoff.put(listOf("a", "b"))

        assertEquals(listOf("a", "b"), handoff.take(token))
        assertNull(handoff.take(token))
    }

    @Test
    fun `a stale token is ignored but taken at the exact ttl boundary`() {
        var now = 0L
        val handoff = QueueHandoff(nowMs = { now })
        val staleToken = handoff.put(listOf("stale"))
        val exactToken = handoff.put(listOf("exact"))

        now = 60_001L
        assertNull(handoff.take(staleToken))

        now = 60_000L
        assertEquals(listOf("exact"), handoff.take(exactToken))
    }

    @Test
    fun `unknown and null tokens return null`() {
        val handoff = QueueHandoff(nowMs = { 0L })
        handoff.put(listOf("known"))

        assertNull(handoff.take("unknown"))
        assertNull(handoff.take(null))
    }

    @Test
    fun `more than max entries evicts the oldest`() {
        val handoff = QueueHandoff(nowMs = { 0L }, maxEntries = 2)
        val tokenA = handoff.put(listOf("a"))
        val tokenB = handoff.put(listOf("b"))
        val tokenC = handoff.put(listOf("c"))

        assertNull(handoff.take(tokenA))
        assertEquals(listOf("b"), handoff.take(tokenB))
        assertEquals(listOf("c"), handoff.take(tokenC))
    }

    @Test
    fun `the stored list is a copy of the callers list`() {
        val handoff = QueueHandoff(nowMs = { 0L })
        val mutable = mutableListOf("a", "b")
        val token = handoff.put(mutable)

        mutable.add("c")

        assertEquals(listOf("a", "b"), handoff.take(token))
    }
}
