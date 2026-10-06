package me.misa198.airmedy.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.misa198.airmedy.R

internal sealed interface LibraryScanOutcome {
    data class Completed(val tracks: Int, val albums: Int, val artists: Int) : LibraryScanOutcome

    data object NothingFound : LibraryScanOutcome

    data object Failed : LibraryScanOutcome
}

/** Runs the MediaStore library scan shared by the Scan page, Tag separators and engine changes. */
internal object LibraryScanRunner {
    private val scanMutex = Mutex()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes scans: a second caller waits for the running one to finish. */
    suspend fun scan(context: Context): LibraryScanOutcome = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val separatorPreferences = TagSeparatorPreferences(context)
                val separators = separatorPreferences.current()
                val scanner = MediaStoreLibraryScanner(
                    contentResolver = context.contentResolver,
                    artworkDir = File(context.filesDir, "artwork"),
                    separators = separators,
                )
                val syncStore = AndroidSyncRuntime.syncStore()
                val filter = ScanFilterPreferences(context).currentFilter()
                val result: LocalLibraryScanResult = scanner.scan(prior = syncStore.priorScanState(), filter = filter)
                val written = syncStore.writeLocalLibrary(
                    snapshot = result.snapshot,
                    audioRows = result.audio,
                    artworkRows = result.artwork,
                )
                if (!written) {
                    // An empty result (most often an empty whitelist) would have replaced the whole
                    // library; writeLocalLibrary kept it instead, so tell the user why nothing changed.
                    Log.w("AirmedyScan", "Scan found no tracks; kept the existing library")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context.applicationContext, R.string.scan_found_nothing_library_kept, Toast.LENGTH_LONG).show()
                    }
                    LibraryScanOutcome.NothingFound
                } else {
                    separatorPreferences.setAppliedSignature(separators.signature)
                    val albums = result.snapshot.tracks.map { it.album.id }.distinct().size
                    val artists = result.snapshot.tracks.flatMap { it.artists }.map { it.id }.distinct().size
                    LibraryScanOutcome.Completed(
                        tracks = result.snapshot.tracks.size,
                        albums = albums,
                        artists = artists,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w("AirmedyScan", "Library scan failed", error)
                LibraryScanOutcome.Failed
            }
        }
    }

    /**
     * Starts a scan on a process-lifetime scope so it survives the page that triggered it.
     * Without the read-media permission nothing is scanned.
     */
    fun scanInBackground(context: Context): Job {
        if (context.checkSelfPermission(readMediaPermission()) != PackageManager.PERMISSION_GRANTED) {
            Log.w("AirmedyScan", "Skipping background scan: read-media permission not granted")
            return Job().apply { complete() }
        }
        return backgroundScope.launch { scan(context.applicationContext) }
    }
}

internal fun readMediaPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
