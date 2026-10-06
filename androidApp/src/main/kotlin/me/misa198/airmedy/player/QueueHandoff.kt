package me.misa198.airmedy.player

import android.os.SystemClock
import java.util.UUID

/**
 * In-process handoff for queue id lists that no longer fit safely in an
 * Intent extra (Binder transaction limit). The controller parks the list and
 * hands the service a short token; the service consumes the list once by token.
 */
internal class QueueHandoff(
    private val nowMs: () -> Long,
    private val ttlMs: Long = 60_000L,
    private val maxEntries: Int = 16,
) {
    private data class Entry(val trackIds: List<String>, val createdAtMs: Long)

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun put(trackIds: List<String>): String {
        val token = UUID.randomUUID().toString()
        synchronized(this) {
            val now = nowMs()
            entries.keys.removeAll { now - entries.getValue(it).createdAtMs > ttlMs }
            while (entries.size >= maxEntries) {
                entries.keys.firstOrNull()?.let { entries.remove(it) }
            }
            entries[token] = Entry(trackIds.toList(), now)
        }
        return token
    }

    fun take(token: String?): List<String>? {
        if (token == null) return null
        synchronized(this) {
            val entry = entries.remove(token) ?: return null
            if (nowMs() - entry.createdAtMs > ttlMs) return null
            return entry.trackIds
        }
    }

    companion object {
        val shared: QueueHandoff by lazy { QueueHandoff(nowMs = { SystemClock.elapsedRealtime() }) }
    }
}
