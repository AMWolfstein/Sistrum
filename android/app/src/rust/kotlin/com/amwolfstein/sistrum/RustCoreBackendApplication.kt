package com.amwolfstein.sistrum

import java.io.File

internal fun RustCoreBackend.dispatchApplication(method: String, arguments: Any?): Any? {
    val args = arguments as? Map<*, *> ?: emptyMap<Any, Any>()
    fun string(key: String, default: String = "") = args[key] as? String ?: default
    when (method) {
        "prepareRuntimeState" -> synchronized(this) {
            val directory = File(string("data_dir"))
            require(directory.isAbsolute) { "Extension data directory must be absolute" }
            val data = directory.canonicalPath
            check(manager == null || identity?.second == data) {
                "Runtime state directory does not match the initialized owner"
            }
            return null
        }
        "initExtensionSystem" -> {
            initializeOwner(args)
            return null
        }
        "cleanupExtensions" -> {
            shutdownOwner()
            return null
        }
        "buildFilename" -> return buildFilename(string("template"), string("metadata", "{}"))
        "sanitizeFilename" -> return sanitizeFilename(string("filename"))
        "getLogsSince" -> return synchronized(this) {
            manager?.environment()?.use { environment ->
                environment.logBuffer().use { it.since((args["index"] as? Number)?.toLong() ?: 0L) }
            } ?: """{"logs":[],"next_index":0}"""
        }
        "clearLogs" -> return synchronized(this) {
            manager?.environment()?.use { environment -> environment.logBuffer().use { it.clear() } }
            null
        }
        "setLoggingEnabled" -> return synchronized(this) {
            val enabled = args["enabled"] as? Boolean ?: false
            manager?.environment()?.use { environment ->
                environment.logBuffer().use { it.setEnabled(enabled) }
            }
            loggingEnabled = enabled
            null
        }
        // Local tag reads and lyrics embedding only: the Rust side must not
        // consult online lyrics providers for these.
        "getLyricsLRCWithSource" -> return withMediaFiles(listOf(string("file_path"))) {
            it.getLyricsLrcWithSource(mediaPath(string("file_path")), null)
        }
        "embedLyricsToFile" -> return withMediaFiles(listOf(string("file_path"))) {
            it.embedLyricsToFile(mediaPath(string("file_path")), string("lyrics"), null)
        }
    }
    error("Rust application method is not connected yet: $method")
}
