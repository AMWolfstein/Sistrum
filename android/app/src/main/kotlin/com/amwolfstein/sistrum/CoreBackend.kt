package com.amwolfstein.sistrum

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Native boundary to the Rust backend; each process holds one stateful instance. */
internal interface CoreBackend {
    val implementation: String
    fun invokeApplication(method: String, arguments: Any?): Any?
    fun setRuntimeState(dataDirectory: String, payload: String) {
        invokeApplication(
            "prepareRuntimeState",
            mapOf("data_dir" to dataDirectory, "runtime_state" to payload),
        )
    }
    fun buildFilename(template: String, metadataJson: String): String
    fun sanitizeFilename(filename: String): String
    fun fileMetadataImplementation(path: String): String
    fun readFileMetadata(path: String, hint: String): String
    fun readAudioMetadata(path: String, hint: String, cacheKey: String): String
    fun readAudioMetadataObject(path: String, hint: String, cacheKey: String): JSONObject =
        JSONObject(readAudioMetadata(path, hint, cacheKey))
    fun setLibraryCoverCacheDirectory(path: String)
    /** Stores the Library thumbnail of the folder image open at the
     * `/proc/self/fd` [path]. Null without a Library cover directory. */
    fun cacheLibraryFolderCover(path: String): String?
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
    fun extractCoverToFile(audioPath: String, outputPath: String, hint: String = "")
    fun createTemporaryMediaFile(context: Context, prefix: String, suffix: String): File
    fun writeM4aFreeformTags(path: String, metadataJson: String): String

    fun openDownloadDirectory(path: String): AutoCloseable

    fun releaseIdleResources()
    fun releaseMemoryUnderPressure()
}
