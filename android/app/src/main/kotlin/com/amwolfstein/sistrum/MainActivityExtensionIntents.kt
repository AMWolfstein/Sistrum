package com.amwolfstein.sistrum

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queues an extension OAuth callback for completion after Dart initializes.
 * The backend resolves the one-time state nonce to the owning extension.
 */
internal fun MainActivity.handleExtensionOAuthIntent(intent: Intent?) {
    val uri = intent?.data ?: return
    if (!MainActivity.extensionAuthCallbacks.offer(uri)) return
    // Keep the Intent intact until the result is handled. A cold start has
    // no extension owner yet; Dart drains this inbox after initialization.
    backendChannel?.invokeMethod("extensionAuthCallbackReceived", null)
}

internal suspend fun MainActivity.processPendingExtensionCallbacks() {
    MainActivity.extensionAuthCallbacks.drain { callback ->
        try {
            withContext(Dispatchers.IO) {
                coreBackend.completeAuthCallback(callback.state, callback.code) {}
            }
            android.util.Log.i("Sistrum", "Extension callback completed")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("Sistrum", "Extension callback failed (${e.javaClass.simpleName})")
        }
        val current = intent?.data
        if (current?.getQueryParameter("state") == callback.state) intent.data = null
    }
}
