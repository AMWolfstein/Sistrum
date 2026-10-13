package com.amwolfstein.sistrum.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject

class LibrarySyncProtocolTest {
    @Test
    fun decodesDesktopManifestWithNullOptionalCollections() {
        val manifest = LibrarySyncProtocol.json.decodeFromString(
            LibrarySyncManifest.serializer(),
            """{"version":1,"plan_id":"plan","revision":"${"b".repeat(64)}","scope":{},"tracks":[],"playlists":null,"lyrics":{},"analysis":{},"assets":[]}""",
        )

        assertEquals(emptyList(), manifest.playlists.orEmpty())
        assertEquals(false, manifest.libraryAnalysisEnabled)
    }

    @Test
    fun roundTripsLibraryAnalysisFlag() {
        val manifest = LibrarySyncManifest(1, "plan", "b".repeat(64), buildJsonObject { }, lyrics = buildJsonObject { }, analysis = buildJsonObject { }, libraryAnalysisEnabled = true)
        assertEquals(true, LibrarySyncProtocol.json.decodeFromString(LibrarySyncManifest.serializer(), LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest)).libraryAnalysisEnabled)
    }
}
