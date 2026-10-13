package com.amwolfstein.sistrum.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class LibrarySyncAsset(
    val id: String,
    val kind: String,
    val sha256: String,
    val size: Long,
)

/** Library plan manifest, persisted with every locally scanned plan (see `localLibraryManifest`). */
@Serializable
data class LibrarySyncManifest(
    val version: Int,
    @SerialName("plan_id") val planId: String,
    val revision: String,
    val scope: JsonObject,
    // Go encodes nil slices as null. A plan without playlists therefore has
    // "playlists": null, which is a valid desktop manifest.
    val tracks: List<JsonObject>? = null,
    val playlists: List<JsonObject>? = null,
    val lyrics: JsonObject,
    val analysis: JsonObject,
    val assets: List<LibrarySyncAsset>? = null,
    @SerialName("library_analysis_enabled") val libraryAnalysisEnabled: Boolean = false,
)

object LibrarySyncProtocol {
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true; explicitNulls = false }
}
