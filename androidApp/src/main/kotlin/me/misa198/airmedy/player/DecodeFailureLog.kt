package me.misa198.airmedy.player

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Exactly the fields FR-064a allows: time, file name, format, codec, provider and error. */
@Serializable
internal data class DecodeFailureEntry(
    val timeMs: Long,
    val fileName: String,
    val format: String,
    val codec: String,
    val provider: String,
    val error: String,
)

internal fun interface DecodeFailureSink {
    fun record(entry: DecodeFailureEntry)
}

/**
 * FR-064a: a bounded, on-device decode-failure log the user can export or clear. Stored as JSON
 * lines so a partially written file never loses the readable entries. Only the file name is kept,
 * never a full path or any other library data.
 */
internal class DecodeFailureLog(
    private val file: File,
    private val maxEntries: Int = 200,
) : DecodeFailureSink {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    override fun record(entry: DecodeFailureEntry) {
        synchronized(lock) {
            write((readEntries() + entry).takeLast(maxEntries))
        }
    }

    /** Oldest first; unreadable lines are skipped. */
    fun entries(): List<DecodeFailureEntry> = synchronized(lock) { readEntries() }

    fun exportText(): String = buildString {
        appendLine(ExportHeader)
        for (entry in entries()) {
            appendLine(
                "${formatTime(entry.timeMs)}  ${entry.fileName}  format=${entry.format}  " +
                    "codec=${entry.codec}  provider=${entry.provider}  error=${entry.error}",
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file.writeText("") }
        }
    }

    private fun readEntries(): List<DecodeFailureEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                if (line.isBlank()) null
                else runCatching { json.decodeFromString(DecodeFailureEntry.serializer(), line) }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    private fun write(entries: List<DecodeFailureEntry>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        val text = if (entries.isEmpty()) "" else entries.joinToString(separator = "\n", postfix = "\n") {
            json.encodeToString(DecodeFailureEntry.serializer(), it)
        }
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun formatTime(timeMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timeMs))

    companion object {
        private const val ExportHeader = "Sistrum decode-failure log"

        @Volatile
        private var shared: DecodeFailureLog? = null

        /** One instance per process, so the service's writes and the settings page's clear share one lock. */
        fun forContext(context: Context): DecodeFailureLog = shared ?: synchronized(this) {
            shared ?: DecodeFailureLog(File(context.applicationContext.filesDir, "decode_failures.jsonl"))
                .also { shared = it }
        }
    }
}
