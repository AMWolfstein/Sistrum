package me.misa198.airmedy.analysis

/**
 * Decides which `album_loudness` records to write or delete from the stored per-track
 * histograms, without decoding. Pure: it never touches the database.
 *
 * T051b stub; T051c implements.
 */
object AlbumLoudnessPlanner {
    fun isAlbumMember(track: PlannerTrack): Boolean = false

    fun membersFingerprint(members: List<PlannerTrack>): String = ""

    fun plan(
        tracks: List<PlannerTrack>,
        existing: Map<String, AlbumRecord>,
        analyzerVersion: String = ANALYZER_VERSION,
    ): AlbumPlan = AlbumPlan(emptyMap(), emptySet())
}
