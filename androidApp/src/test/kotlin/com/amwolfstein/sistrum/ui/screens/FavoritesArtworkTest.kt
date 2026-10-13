package com.amwolfstein.sistrum.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.PlaylistMutation
import com.amwolfstein.sistrum.sync.PlaylistMutationOperation
import com.amwolfstein.sistrum.sync.PlaylistMutationPayload
import com.amwolfstein.sistrum.sync.favoritesMetadataFrom
import com.amwolfstein.sistrum.sync.playlistArtworkKey

/**
 * Favorites has no playlist row, so its SET_ARTWORK was dropped by the projection and the
 * custom cover was saved but never shown. The store now exposes Favorites' own metadata.
 */
class FavoritesArtworkTest {

    private val h1 = "a".repeat(64)
    private val h2 = "b".repeat(64)

    private fun artwork(id: String, playlistId: String, at: Long, hash: String?) = PlaylistMutation(
        id, playlistId,
        if (hash != null) PlaylistMutationOperation.SET_ARTWORK else PlaylistMutationOperation.REMOVE_ARTWORK,
        at, PlaylistMutationPayload(artworkSha256 = hash),
    )

    @Test fun `favorites metadata carries its custom cover`() {
        val metadata = favoritesMetadataFrom(listOf(artwork("dedupe:artwork:favorites", "favorites", 10, h1)))
        assertEquals(h1, playlistArtworkKey(metadata))
    }

    @Test fun `replaced and removed covers follow the latest mutation`() {
        // Artwork edits share the "dedupe:artwork:favorites" row, so only the latest one exists.
        assertEquals(h2, playlistArtworkKey(favoritesMetadataFrom(listOf(artwork("dedupe:artwork:favorites", "favorites", 20, h2)))))
        assertNull(playlistArtworkKey(favoritesMetadataFrom(listOf(artwork("dedupe:artwork:favorites", "favorites", 30, null)))))
    }

    @Test fun `other playlists' artwork never leaks into favorites`() {
        assertNull(playlistArtworkKey(favoritesMetadataFrom(listOf(artwork("dedupe:artwork:p1", "p1", 10, h1)))))
    }

    @Test fun `favorite toggles leave favorites without a cover`() {
        val toggle = PlaylistMutation("dedupe:favorite:t1", "favorites", PlaylistMutationOperation.SET_FAVORITE, 5, PlaylistMutationPayload(trackId = "t1", isFavorite = true))
        assertNull(playlistArtworkKey(favoritesMetadataFrom(listOf(toggle))))
    }

    @Test fun `playlist list shows the favorites cover`() {
        val favorites = playlistsWithFavorites(emptyList(), favoritesMetadata = favoritesMetadataFrom(listOf(artwork("x", "favorites", 10, h1)))).first()
        assertEquals(FavoritesPlaylistId, favorites.id)
        assertEquals("/files/playlist-artwork/$h1.jpg", playlistManualArtworkPath(favorites, mapOf(h1 to "/files/playlist-artwork/$h1.jpg")))
    }

    @Test fun `favorites details screen shows the custom cover`() {
        val state = PlaylistDetailsUiState(
            playlists = listOf(LibraryPlaylist("p1", "Road trip", emptyList(), "{}")),
            artworkPathByKey = mapOf(h1 to "/files/playlist-artwork/$h1.jpg"),
            favoritesMetadata = favoritesMetadataFrom(listOf(artwork("x", "favorites", 10, h1))),
        )
        val details = playlistDetailsUiStateFor(state, FavoritesPlaylistId)
        assertEquals("/files/playlist-artwork/$h1.jpg", details.customArtworkPath)
        assertEquals(listOf("/files/playlist-artwork/$h1.jpg"), details.artworkPaths)
    }

    @Test fun `without a custom cover favorites is unchanged`() {
        val details = playlistDetailsUiStateFor(PlaylistDetailsUiState(), FavoritesPlaylistId)
        assertNull(details.customArtworkPath)
    }
}
