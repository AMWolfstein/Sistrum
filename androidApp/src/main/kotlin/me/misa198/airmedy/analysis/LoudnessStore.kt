package me.misa198.airmedy.analysis

import me.misa198.airmedy.player.normalization.AlbumLoudness
import me.misa198.airmedy.sync.SyncDatabase

/**
 * Persists measured loudness into `sync_documents` of the active plan and reads back what is
 * still pending. The read side ([me.misa198.airmedy.sync.AndroidLibrarySyncStore.activeAnalyses])
 * consumes the `analysis` rows this store writes.
 *
 * T051b stub; T051c implements.
 */
internal class LoudnessStore(private val database: SyncDatabase) {

    suspend fun pendingTracks(analyzerVersion: String = ANALYZER_VERSION): List<PendingTrack> = emptyList()

    suspend fun writeResult(result: TrackLoudnessResult) {}

    suspend fun progress(): AnalysisProgress = AnalysisProgress(0, 0)

    suspend fun recomputeAlbums() {}

    suspend fun albumLoudness(albumId: String): AlbumLoudness? = null

    suspend fun saveCheckpoint(trackId: String, fingerprint: String, state: ByteArray) {}

    suspend fun loadCheckpoint(trackId: String, fingerprint: String): ByteArray? = null

    suspend fun clearCheckpoint(trackId: String) {}
}
