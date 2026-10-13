package com.amwolfstein.sistrum.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.amwolfstein.sistrum.R

/**
 * Runs a playlist or favorites database write. A failure (e.g. a full disk) goes to
 * [onFailure] instead of escaping the coroutine, where an uncaught exception in
 * viewModelScope/rememberCoroutineScope would crash the app. Cancellation still propagates.
 * Returns whether [write] completed, so follow-up work (navigation, Last.fm) can depend on it.
 */
internal suspend fun runPlaylistWrite(onFailure: suspend (Exception) -> Unit, write: suspend () -> Unit): Boolean =
    try {
        write()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
        false
    }

/** [runPlaylistWrite] that logs the failure and tells the user with a Toast. */
internal suspend fun runPlaylistWrite(context: Context, write: suspend () -> Unit): Boolean =
    runPlaylistWrite(onFailure = { error -> reportPlaylistWriteFailure(context, error) }, write = write)

private suspend fun reportPlaylistWriteFailure(context: Context, error: Exception) {
    Log.w("SistrumPlaylists", "Could not save playlist change", error)
    withContext(Dispatchers.Main) {
        Toast.makeText(context.applicationContext, R.string.playlist_save_failed, Toast.LENGTH_SHORT).show()
    }
}
