package com.amwolfstein.sistrum.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import com.amwolfstein.sistrum.library.LocalAlbumRef
import com.amwolfstein.sistrum.library.LocalArtistRef
import com.amwolfstein.sistrum.library.LocalLibrarySnapshot
import com.amwolfstein.sistrum.library.LocalTrack
import com.amwolfstein.sistrum.ui.screens.isFavorite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Repeated edits to the same target share a stable "dedupe:" mutation id (the
 * playlist_mutations leak fix) and must replace the earlier row. With the DAO on
 * OnConflictStrategy.IGNORE, every edit after the first was silently dropped. These run
 * against a real Room database through the store's own flows, not a model of its state.
 */
class PlaylistMutationPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    @Before fun setUp(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "playlist-mutation-test-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
        val album = LocalAlbumRef("local:album:a", "Album")
        val artist = LocalArtistRef("local:artist:x", "Artist")
        store.writeLocalLibrary(
            snapshot = LocalLibrarySnapshot(0L, listOf("t1", "t2", "t3").map { LocalTrack(it, it, listOf(artist), album) }),
            audioRows = emptyMap(),
            artworkRows = emptyList(),
        )
        Unit
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    /**
     * The store's flows are shareIn(replay = 1), so a fresh first() can return the cached
     * value from before the last write. Wait (bounded) for the expected value instead.
     */
    private suspend fun <T> Flow<T>.await(expected: String, predicate: (T) -> Boolean): T =
        withTimeoutOrNull(5_000) { first(predicate) } ?: fail("timed out waiting for $expected; last value: ${first()}") as Nothing

    private suspend fun awaitFavorite(trackId: String, favorite: Boolean) {
        store.tracks.await("$trackId favorite=$favorite") { tracks -> tracks.single { it.id == trackId }.isFavorite() == favorite }
    }
    private suspend fun awaitPlaylist(id: String, expected: String, predicate: (LibraryPlaylist) -> Boolean) {
        store.playlists.await("$id $expected") { playlists -> playlists.singleOrNull { it.id == id }?.let(predicate) == true }
    }
    private suspend fun awaitArtwork(id: String, hash: String?) =
        awaitPlaylist(id, "artwork=$hash") { playlistArtworkKey(it.metadataJson) == hash }
    private suspend fun awaitFavoritesArtwork(hash: String?) {
        store.favoritesMetadata.await("favorites artwork=$hash") { playlistArtworkKey(it) == hash }
    }
    private fun mutationRowCount(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM playlist_mutations").use { it.moveToFirst(); it.getInt(0) }

    private fun mutation(playlistId: String, operation: PlaylistMutationOperation, at: Long, payload: PlaylistMutationPayload = PlaylistMutationPayload()) =
        PlaylistMutation("random-$at-${operation.name}", playlistId, operation, at, payload)

    private suspend fun createPlaylist(id: String) =
        store.createLocalPlaylist(mutation(id, PlaylistMutationOperation.CREATE, 1, PlaylistMutationPayload(name = "Road trip")))

    private fun stagedArtwork(seed: Char): StagedPlaylistArtwork {
        val hash = seed.toString().repeat(64)
        val relativePath = "playlist-artwork/$hash.jpg"
        File(filesDir, relativePath).apply { parentFile?.mkdirs(); writeText(hash) }
        return StagedPlaylistArtwork(hash, "image/jpeg", 64, relativePath)
    }

    @Test fun unfavoritingAndRefavoritingATrackPersists() = runBlocking {
        store.setFavorite("t1", true)
        awaitFavorite("t1", true)
        store.setFavorite("t1", false)
        awaitFavorite("t1", false)
        store.setFavorite("t1", true)
        awaitFavorite("t1", true)
    }

    @Test fun removingAndReaddingATrackPersists() = runBlocking {
        createPlaylist("p1")
        store.queuePlaylistMutation(mutation("p1", PlaylistMutationOperation.ADD_TRACK, 10, PlaylistMutationPayload(trackId = "t1")))
        awaitPlaylist("p1", "contains t1") { it.trackIds == listOf("t1") }
        store.queuePlaylistMutation(mutation("p1", PlaylistMutationOperation.REMOVE_TRACK, 20, PlaylistMutationPayload(trackId = "t1")))
        awaitPlaylist("p1", "is empty") { it.trackIds.isEmpty() }
        store.queuePlaylistMutation(mutation("p1", PlaylistMutationOperation.ADD_TRACK, 30, PlaylistMutationPayload(trackId = "t1")))
        awaitPlaylist("p1", "contains t1 again") { it.trackIds == listOf("t1") }
    }

    @Test fun secondRenamePersists() = runBlocking {
        createPlaylist("p1")
        store.queuePlaylistMutation(mutation("p1", PlaylistMutationOperation.UPDATE, 10, PlaylistMutationPayload(name = "First")))
        awaitPlaylist("p1", "named First") { it.name == "First" }
        store.queuePlaylistMutation(mutation("p1", PlaylistMutationOperation.UPDATE, 20, PlaylistMutationPayload(name = "Second")))
        awaitPlaylist("p1", "named Second") { it.name == "Second" }
    }

    @Test fun coverChangeThenClearPersistsAndDeletesReplacedFiles() = runBlocking {
        createPlaylist("p1")
        val first = stagedArtwork('1')
        val second = stagedArtwork('2')
        store.setPlaylistArtwork("p1", first, 10)
        awaitArtwork("p1", first.sha256)
        store.setPlaylistArtwork("p1", second, 20)
        awaitArtwork("p1", second.sha256)
        assertFalse("replaced cover file is deleted", File(filesDir, first.relativePath).exists())
        store.queuePlaylistMutation(mutation("p1", PlaylistMutationOperation.REMOVE_ARTWORK, 30))
        awaitArtwork("p1", null)
        assertFalse("cleared cover file is deleted", File(filesDir, second.relativePath).exists())
    }

    @Test fun favoritesCoverChangeThenClearPersists() = runBlocking {
        val first = stagedArtwork('3')
        val second = stagedArtwork('4')
        store.setPlaylistArtwork("favorites", first, 10)
        awaitFavoritesArtwork(first.sha256)
        store.setPlaylistArtwork("favorites", second, 20)
        awaitFavoritesArtwork(second.sha256)
        assertFalse("replaced favorites cover file is deleted", File(filesDir, first.relativePath).exists())
        store.queuePlaylistMutation(mutation("favorites", PlaylistMutationOperation.REMOVE_ARTWORK, 30))
        awaitFavoritesArtwork(null)
        assertFalse(File(filesDir, second.relativePath).exists())
    }

    @Test fun repeatedEditsStillCollapseIntoOneRow() = runBlocking {
        val before = mutationRowCount()
        repeat(20) { i -> store.setFavorite("t2", i % 2 == 0) }
        assertEquals("20 toggles of one track keep a single row", before + 1, mutationRowCount())
        awaitFavorite("t2", false)
    }
}
