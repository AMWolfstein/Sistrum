package com.amwolfstein.sistrum.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

val LocalReducedMotion = staticCompositionLocalOf { false }

/** Observe D-6's system setting for the lifetime of the theme's composition. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val readScale = remember(resolver) {
        { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    }
    val changes = remember(resolver) {
        reducedMotionFlow(readScale) { onChange ->
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChange()
            }
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
            val unregister = { resolver.unregisterContentObserver(observer) }
            unregister
        }
    }
    val reducedMotion by changes.collectAsState(initial = readScale() == 0f)
    return reducedMotion
}

/** Register before reading so a scale change during subscription cannot be missed. */
internal fun reducedMotionFlow(
    readScale: () -> Float,
    observeChanges: (() -> Unit) -> (() -> Unit),
): Flow<Boolean> = callbackFlow {
    val update = { trySend(readScale() == 0f); Unit }
    val unregister = observeChanges(update)
    try {
        update()
        awaitClose()
    } finally {
        unregister()
    }
}.distinctUntilChanged()
