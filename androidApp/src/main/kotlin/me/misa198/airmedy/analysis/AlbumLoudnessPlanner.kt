package me.misa198.airmedy.analysis

import java.security.MessageDigest
import me.misa198.airmedy.analysis.loudness.LoudnessHistogram

/**
 * Decides which `album_loudness` records to write or delete from the stored per-track
 * histograms, without decoding. Pure: it never touches the database.
 */
object AlbumLoudnessPlanner {
    fun isAlbumMember(track: PlannerTrack): Boolean {
        if (!track.available) return false
        if (track.albumId.isBlank() || track.album.isBlank()) return false
        if (track.status == "failed") return false
        val parentFolder = track.audioPath.substringBeforeLast('/').substringAfterLast('/')
        if (track.tagAlbum.isBlank() && track.album == parentFolder) return false
        return true
    }

    fun membersFingerprint(members: List<PlannerTrack>): String {
        val text = members.sortedBy { it.trackId }
            .joinToString("\n") { "${it.trackId}:${it.fingerprint}:${it.analyzerVersion.orEmpty()}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun plan(
        tracks: List<PlannerTrack>,
        existing: Map<String, AlbumRecord>,
        analyzerVersion: String = ANALYZER_VERSION,
    ): AlbumPlan {
        val membersByAlbum = tracks.filter(::isAlbumMember).groupBy(PlannerTrack::albumId)
        val write = mutableMapOf<String, AlbumRecord>()
        val delete = mutableSetOf<String>()

        for ((albumId, members) in membersByAlbum) {
            val decoded = members.map { member ->
                member.histogram?.let { runCatching { LoudnessHistogram.decode(it) }.getOrNull() }
            }
            val complete = decoded.all { it != null } &&
                members.all { (it.status == "ok" || it.status == "silent") && it.analyzerVersion == analyzerVersion }

            if (!complete) {
                if (albumId in existing) delete += albumId
                continue
            }

            val fingerprint = membersFingerprint(members)
            if (existing[albumId]?.membersFingerprint == fingerprint) continue

            val merged = LoudnessHistogram()
            decoded.filterNotNull().forEach(merged::merge)
            val loudness = merged.integratedLufs()
            val peak = members.mapNotNull { if (it.status == "ok") it.truePeakDbtp else null }.maxOrNull()
            if (loudness == null || peak == null) {
                if (albumId in existing) delete += albumId
                continue
            }
            write[albumId] = AlbumRecord(loudness, peak, members.size, fingerprint)
        }

        existing.keys.filter { it !in membersByAlbum }.forEach { delete += it }

        return AlbumPlan(write, delete)
    }
}
