package com.amwolfstein.sistrum

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Native boundary to the Rust backend; each process holds one stateful instance. */
internal interface CoreBackend {
    val implementation: String
    fun invokeApplication(method: String, arguments: Any?): Any?
    fun completeAuthCallback(state: String, code: String, onResolved: (String) -> Unit)
    fun buildFilename(template: String, metadataJson: String): String
    fun sanitizeFilename(filename: String): String
    fun fileMetadataImplementation(path: String): String
    fun readFileMetadata(path: String, hint: String): String
    fun checkHiResAuthenticity(path: String, optionsJson: String): String
    fun readAudioMetadata(path: String, hint: String, cacheKey: String): String
    fun readAudioMetadataObject(path: String, hint: String, cacheKey: String): JSONObject =
        JSONObject(readAudioMetadata(path, hint, cacheKey))
    fun setLibraryCoverCacheDirectory(path: String)
    fun scanLibraryFolderToNdjsonFile(folder: String, output: String): Long
    fun scanLibraryFolderIncremental(folder: String, existing: String): String
    fun getLibraryScanProgress(): String
    fun cancelLibraryScan()
    fun pauseLibraryScan()
    fun resumeLibraryScan()
    fun parseCueSheet(path: String, audioDirectory: String): String
    fun parseCueSheetWithResolvedAudio(path: String, audioPath: String): String
    fun scanCueForLibraryWithResolvedAudio(path: String, audioPath: String, audioName: String, virtualPrefix: String, modTime: Long, cacheKey: String): String
    fun editFileMetadata(path: String, metadataJson: String): String
    fun reEnrichFile(requestJson: String): String
    fun rewriteSplitArtistTags(path: String, artist: String, albumArtist: String): String
    fun extractCoverToFile(audioPath: String, outputPath: String, hint: String = "")
    fun writeM4aFreeformTags(path: String, metadataJson: String): String
    fun ensureAc4Config(path: String, reference: String): String
    fun writeAc4Metadata(path: String, metadataJson: String, coverPath: String): String
    fun getLyricsLrc(spotifyId: String, trackName: String, artistName: String, filePath: String, durationMs: Long): String
    fun createTemporaryMediaFile(context: Context, prefix: String, suffix: String): File
    fun openDownloadDirectory(path: String): AutoCloseable
    fun releaseIdleResources()
    fun releaseMemoryUnderPressure()
}

internal fun requireSuccessfulExtensionAction(extensionId: String, actionName: String, response: String) {
    val obj = try {
        JSONObject(response)
    } catch (e: Exception) {
        throw IllegalStateException(
            "Extension $actionName for $extensionId returned invalid JSON: ${response.take(240)}"
        )
    }
    if (obj.optBoolean("success", false)) {
        return
    }
    val error = obj.optString("error")
        .ifBlank { obj.optString("message") }
        .ifBlank { response.take(240) }
    throw IllegalStateException("Extension $actionName failed for $extensionId: $error")
}
