package com.amwolfstein.sistrum.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.amwolfstein.sistrum.BuildConfig
import com.amwolfstein.sistrum.player.DecodeFailureLog
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One diagnostics event: when, how bad, which part of the app, and what happened. */
@Serializable
internal data class DiagnosticEntry(
    val timeMs: Long,
    val level: String,
    val area: String,
    val message: String,
)

/**
 * Bounded on-device log of problems the app ran into (crashes, audio and playback events, warnings),
 * kept next to the decode-failure log (FR-064a) and exported together with it. JSON lines, so a
 * partially written file never loses the readable entries. Nothing leaves the device unless the user
 * shares or saves the export.
 */
internal class DiagnosticsLog(
    private val file: File,
    private val maxEntries: Int = 500,
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    fun record(entry: DiagnosticEntry) {
        synchronized(lock) {
            write((readEntries() + entry).takeLast(maxEntries))
        }
    }

    /** Oldest first; unreadable lines are skipped. */
    fun entries(): List<DiagnosticEntry> = synchronized(lock) { readEntries() }

    fun clear() {
        synchronized(lock) {
            runCatching { file.writeText("") }
        }
    }

    private fun readEntries(): List<DiagnosticEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                if (line.isBlank()) null
                else runCatching { json.decodeFromString(DiagnosticEntry.serializer(), line) }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    private fun write(entries: List<DiagnosticEntry>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        val text = if (entries.isEmpty()) "" else entries.joinToString(separator = "\n", postfix = "\n") {
            json.encodeToString(DiagnosticEntry.serializer(), it)
        }
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}

/**
 * Process-wide entry point. [init] is called from every component's onCreate (activity and playback
 * service); [record] is safe from any thread, including audio callbacks: it only queues the write.
 */
internal object Diagnostics {
    const val LEVEL_ERROR = "E"
    const val LEVEL_WARN = "W"
    const val LEVEL_INFO = "I"

    private const val TAG = "Diagnostics"
    private const val LOGCAT_LINES = 1500

    @Volatile private var log: DiagnosticsLog? = null
    @Volatile private var nowMs: () -> Long = System::currentTimeMillis
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "sistrum-diagnostics").apply { isDaemon = true } }

    fun init(context: Context) {
        if (log != null) return
        synchronized(this) {
            if (log != null) return
            log = DiagnosticsLog(File(context.applicationContext.filesDir, "diagnostics.jsonl"))
            installCrashHandler()
        }
    }

    /** For host tests: a log file and clock without an Android context. */
    fun initForTest(diagnostics: DiagnosticsLog?, clock: () -> Long = System::currentTimeMillis) {
        log = diagnostics
        nowMs = clock
    }

    fun log(): DiagnosticsLog? = log

    fun record(level: String, area: String, message: String) {
        val target = log ?: return
        val entry = DiagnosticEntry(nowMs(), level, area, message)
        runCatching { writer.execute { runCatching { target.record(entry) } } }
    }

    /** Waits for queued writes (tests and the crash handler, which must persist before the process dies). */
    fun flush(timeoutMs: Long = 1_000) {
        runCatching { writer.submit {}.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // Written synchronously: the process is about to die.
                log?.record(DiagnosticEntry(nowMs(), LEVEL_ERROR, "crash", "thread=${thread.name}\n${Log.getStackTraceString(error)}"))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Device header, decode failures, diagnostics events and this process's recent warnings/errors from logcat. */
    fun exportText(context: Context): String = buildString {
        appendLine("Sistrum diagnostics log")
        appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) id=${BuildConfig.APPLICATION_ID}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        appendLine("exported=${formatTime(System.currentTimeMillis())}")
        appendLine()
        appendLine("== Events")
        for (entry in log?.entries().orEmpty()) {
            appendLine("${formatTime(entry.timeMs)}  ${entry.level}/${entry.area}  ${entry.message}")
        }
        appendLine()
        appendLine("== Decode failures")
        append(DecodeFailureLog.forContext(context).exportText().lineSequence().drop(1).joinToString("\n"))
        appendLine()
        appendLine("== Recent warnings and errors (logcat, this app only)")
        append(recentLogcat())
    }

    /** An app may read its own process's log lines without any permission. */
    private fun recentLogcat(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString(), "--pid=${Process.myPid()}", "*:W",
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }.getOrElse { error ->
        Log.w(TAG, "logcat unavailable", error)
        "(logcat unavailable: ${error.message})\n"
    }

    private fun formatTime(timeMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timeMs))
}
