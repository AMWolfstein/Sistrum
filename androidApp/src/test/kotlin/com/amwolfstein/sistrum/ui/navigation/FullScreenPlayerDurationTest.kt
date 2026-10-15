package com.amwolfstein.sistrum.ui.navigation

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullScreenPlayerDurationTest {

    @Test fun libraryDurationIsAlreadyMilliseconds() {
        // 3:32.741 stored as LocalTrack.durationMillis; the screen showed 3545:41 when it multiplied by 1000.
        val metadata = buildJsonObject { put("duration", JsonPrimitive(212_741L)) }
        assertEquals(212_741L, metadataDurationMs(metadata))
        assertEquals("3:32", formatPlaybackTime(metadataDurationMs(metadata)!!))
    }

    @Test fun missingOrInvalidDurationFallsBackToPlaceholder() {
        assertNull(metadataDurationMs(null))
        assertNull(metadataDurationMs(buildJsonObject { put("duration", JsonPrimitive(0L)) }))
        assertNull(metadataDurationMs(buildJsonObject { put("duration", JsonPrimitive("abc")) }))
    }
}
