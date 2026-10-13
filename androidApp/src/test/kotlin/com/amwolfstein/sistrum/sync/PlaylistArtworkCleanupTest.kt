package com.amwolfstein.sistrum.sync

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cleanup keeps a staged hash only while an existing playlist shows it. The mutation
 * lists below are the rows the store actually holds after each user action: creation
 * writes the local row plus a random-id SET_ARTWORK, later artwork edits share the
 * "dedupe:artwork:<playlist>" row (so a replacement overwrites it), and nothing is ever
 * acknowledged or deleted.
 */
class PlaylistArtworkCleanupTest {

    private val filesDir: File = createTempDirectory("playlist-artwork-cleanup").toFile()
    private val h1 = "1".repeat(64)
    private val h2 = "2".repeat(64)
    private val h3 = "3".repeat(64)

    @AfterTest fun cleanup() { filesDir.deleteRecursively() }

    private fun stage(vararg hashes: String): Map<String, File> = hashes.associateWith { hash ->
        File(filesDir, "playlist-artwork/$hash.jpg").apply { parentFile?.mkdirs(); writeText(hash) }
    }

    private fun mutation(id: String, playlistId: String, operation: PlaylistMutationOperation, at: Long, artwork: String? = null) =
        PlaylistMutation(id, playlistId, operation, at, PlaylistMutationPayload(artworkSha256 = artwork))

    /** What cleanupUnusedPlaylistArtwork does, minus Room: decide, then delete the files. */
    private fun runCleanup(staged: Collection<String>, local: List<LocalPlaylistEntity>, pending: List<PlaylistMutation>): Set<String> {
        val inUse = playlistArtworkInUse(emptyList(), local, pending)
        val unused = staged.filter { it !in inUse }
        deleteStagedPlaylistArtworkFiles(filesDir, unused.map { "playlist-artwork/$it.jpg" })
        return unused.toSet()
    }

    private val createdWithH1 = listOf(LocalPlaylistEntity("p1", "Road trip", "create-p1", artworkSha256 = h1))
    private val creationArtwork = mutation("random-artwork-id", "p1", PlaylistMutationOperation.SET_ARTWORK, 11, h1)

    @Test fun `deleting a playlist removes its orphaned artwork file`() {
        val files = stage(h1)
        val pending = listOf(creationArtwork, mutation("delete-p1", "p1", PlaylistMutationOperation.DELETE, 20))

        assertEquals(setOf(h1), runCleanup(files.keys, createdWithH1, pending))
        assertFalse(files.getValue(h1).exists())
    }

    @Test fun `replacing artwork removes the old file and keeps the new one`() {
        val files = stage(h1, h2)
        val pending = listOf(creationArtwork, mutation("dedupe:artwork:p1", "p1", PlaylistMutationOperation.SET_ARTWORK, 30, h2))

        assertEquals(setOf(h1), runCleanup(files.keys, createdWithH1, pending))
        assertFalse(files.getValue(h1).exists())
        assertTrue(files.getValue(h2).exists())
    }

    @Test fun `replacing artwork twice frees the intermediate image too`() {
        val files = stage(h1, h2, h3)
        // The dedupe row now holds h3; h2's SET_ARTWORK was overwritten in place.
        val pending = listOf(creationArtwork, mutation("dedupe:artwork:p1", "p1", PlaylistMutationOperation.SET_ARTWORK, 40, h3))

        assertEquals(setOf(h1, h2), runCleanup(files.keys, createdWithH1, pending))
        assertTrue(files.getValue(h3).exists())
    }

    @Test fun `removing artwork removes its file`() {
        val files = stage(h1)
        val pending = listOf(creationArtwork, mutation("dedupe:artwork:p1", "p1", PlaylistMutationOperation.REMOVE_ARTWORK, 30))

        assertEquals(setOf(h1), runCleanup(files.keys, createdWithH1, pending))
        assertFalse(files.getValue(h1).exists())
    }

    @Test fun `artwork still used by another playlist is kept`() {
        val files = stage(h1)
        val local = createdWithH1 + LocalPlaylistEntity("p2", "Gym", "create-p2", artworkSha256 = h1)
        val pending = listOf(creationArtwork, mutation("delete-p1", "p1", PlaylistMutationOperation.DELETE, 20))

        assertEquals(emptySet(), runCleanup(files.keys, local, pending))
        assertTrue(files.getValue(h1).exists())
    }

    @Test fun `favorites artwork survives although favorites has no base playlist row`() {
        val files = stage(h3)
        val pending = listOf(mutation("dedupe:artwork:favorites", "favorites", PlaylistMutationOperation.SET_ARTWORK, 10, h3))

        assertEquals(emptySet(), runCleanup(files.keys, emptyList(), pending))
        assertTrue(files.getValue(h3).exists())
    }

    @Test fun `artwork of a playlist that still exists is kept`() {
        val files = stage(h1)
        assertEquals(emptySet(), runCleanup(files.keys, createdWithH1, listOf(creationArtwork)))
        assertTrue(files.getValue(h1).exists())
    }

    @Test fun `file deletion stays inside the playlist artwork directory`() {
        val outside = File(filesDir, "library.db").apply { writeText("keep") }
        val deleted = deleteStagedPlaylistArtworkFiles(filesDir, listOf("library.db", "playlist-artwork/../library.db"))
        assertEquals(0, deleted)
        assertTrue(outside.exists())
    }

    @Test fun `display projection is unchanged and does not invent mutation-only playlists`() {
        val pending = listOf(mutation("dedupe:artwork:favorites", "favorites", PlaylistMutationOperation.SET_ARTWORK, 10, h3))
        assertEquals(listOf("p1"), projectPlaylists(emptyList(), createdWithH1, pending).map(LibraryPlaylist::id))
        assertEquals(setOf("p1", "favorites"), projectPlaylists(emptyList(), createdWithH1, pending, includeMutationOnlyPlaylists = true).map(LibraryPlaylist::id).toSet())
    }
}
