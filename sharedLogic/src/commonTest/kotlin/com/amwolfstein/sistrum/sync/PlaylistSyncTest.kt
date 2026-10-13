package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PlaylistSyncTest {
    @Test fun `validates mutation data required by each operation`() {
        val add = PlaylistMutation("m1", "p1", PlaylistMutationOperation.ADD_TRACK, 1)
        assertEquals("A track ID is required", add.validationError())
        assertNull(add.copy(payload = PlaylistMutationPayload(trackId = "t1")).validationError())
    }

    @Test fun `validates desired favorite state`() {
        val favorite = PlaylistMutation("m1", "favorites", PlaylistMutationOperation.SET_FAVORITE, 1)
        assertEquals("Invalid favorite mutation", favorite.validationError())
        assertNull(favorite.copy(payload = PlaylistMutationPayload(trackId = "t1", isFavorite = true)).validationError())
    }

    @Test fun `invalid artwork hash is rejected`() {
        val mutation = PlaylistMutation("m", "p", PlaylistMutationOperation.SET_ARTWORK, 1, PlaylistMutationPayload(artworkSha256 = "bad"))
        assertFalse(mutation.validationError() == null)
    }
}
