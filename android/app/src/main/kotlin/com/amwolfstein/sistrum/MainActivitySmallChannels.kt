package com.amwolfstein.sistrum

import android.content.Intent
import android.os.Build
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

internal fun MainActivity.registerAudioOutputChannel(messenger: BinaryMessenger) {
    audioOutputChannel = MethodChannel(messenger, "com.amwolfstein.sistrum/audio_output").also { channel ->
        channel.setMethodCallHandler { call, result ->
            if (call.method != "show") {
                result.notImplemented()
            } else {
                try {
                    val shown = Build.VERSION.SDK_INT >= 34 && try {
                        android.media.MediaRouter2.getInstance(this).showSystemOutputSwitcher()
                    } catch (_: Exception) {
                        false
                    }
                    if (!shown) {
                        // Older releases and unavailable system pickers still offer Bluetooth settings.
                        startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    result.success(true)
                } catch (_: Exception) {
                    result.success(false)
                }
            }
        }
    }
}
