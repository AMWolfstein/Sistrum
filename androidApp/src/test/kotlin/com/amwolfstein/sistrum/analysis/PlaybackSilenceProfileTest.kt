package com.amwolfstein.sistrum.analysis

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class PlaybackSilenceProfileTest {
    private fun document(version: String = SilenceProfile.VERSION, status: String = "ok", fingerprint: String = "sha") =
        buildJsonObject {
            put("silenceVersion", version)
            put("status", status)
            put("source_fingerprint", fingerprint)
            put("silenceLeadMs", 2000)
            put("silenceTailMs", 3000)
        }

    @Test fun currentMeasuredAndSilentDocumentsReadTheirEdges() {
        assertEquals(SilenceProfile(2000, 3000), playbackSilenceProfile(document(), "sha"))
        assertEquals(SilenceProfile(2000, 3000), playbackSilenceProfile(document(status = "silent"), "sha"))
    }

    @Test fun absentObsoleteFailedAndChangedSourceProfilesAreUnavailable() {
        assertNull(playbackSilenceProfile(buildJsonObject {}, "sha"))
        assertNull(playbackSilenceProfile(document(version = "old"), "sha"))
        assertNull(playbackSilenceProfile(document(status = "failed"), "sha"))
        assertNull(playbackSilenceProfile(document(fingerprint = "old"), "sha"))
    }

    @Test fun malformedCurrentMeasurementsAreRefusedInsteadOfBecomingMeasuredZero() {
        for (bad in listOf(
            JsonObject(document() - "silenceLeadMs"),
            JsonObject(document() + buildJsonObject { put("silenceLeadMs", -1) }),
            JsonObject(document() + buildJsonObject { put("silenceTailMs", "broken") }),
            JsonObject(document() + buildJsonObject { put("silenceTailMs", "9223372036854775808") }),
        )) {
            assertThrows(IllegalArgumentException::class.java) { playbackSilenceProfile(bad, "sha") }
        }
        val zero = JsonObject(document() + buildJsonObject { put("silenceLeadMs", 0); put("silenceTailMs", 0) })
        assertEquals(SilenceProfile(0, 0), playbackSilenceProfile(zero, "sha"))
    }
}
