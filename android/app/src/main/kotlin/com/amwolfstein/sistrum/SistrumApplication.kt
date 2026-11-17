package com.amwolfstein.sistrum

import android.app.Application
import com.ryanheise.audioservice.AudioServicePlugin

class SistrumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCrashReporter.initialize(this)
        removeRetiredWidgetData()
        // audio_service creates the cached engine before a FlutterFragment can
        // supply shell args, including when playback starts without an Activity.
        AudioServicePlugin.setFlutterEngineArguments(flutterRendererShellArgs())
        AudioServicePlugin.setFlutterEngineConfigurator(SharedEngineChannels::configure)
        AudioServicePlugin.setQueueArtResolver(CoverContentProvider.queueArtResolver(this))
        AudioServicePlugin.setResourceResolver { resource ->
            when (resource) {
                "mipmap/ic_launcher" -> R.mipmap.ic_launcher
                "drawable/ic_launcher_monochrome" -> R.drawable.ic_launcher_monochrome
                "drawable/ic_stat_sistrum" -> R.drawable.ic_stat_sistrum
                "drawable/ic_notification_heart" -> R.drawable.ic_notification_heart
                "drawable/ic_notification_heart_filled" -> R.drawable.ic_notification_heart_filled
                "drawable/ic_notification_shuffle" -> R.drawable.ic_notification_shuffle
                "drawable/ic_notification_shuffle_on" -> R.drawable.ic_notification_shuffle_on
                "drawable/ic_widget_previous" -> R.drawable.ic_widget_previous
                "drawable/ic_widget_next" -> R.drawable.ic_widget_next
                "drawable/ic_widget_play" -> R.drawable.ic_widget_play
                "drawable/ic_widget_pause" -> R.drawable.ic_widget_pause
                else -> 0
            }
        }
    }

    // The home-screen widgets are gone; drop what they saved. Deleting a
    // file that is already gone is a no-op, so this stays safe on every start.
    private fun removeRetiredWidgetData() {
        try {
            deleteSharedPreferences("player_widget")
            deleteSharedPreferences("download_widget_state")
            java.io.File(filesDir, "player-widget-cover.png").delete()
            java.io.File(filesDir, "player-widget-cover.tmp").delete()
        } catch (error: Exception) {
            android.util.Log.w("SistrumApplication", "Retired widget data cleanup failed", error)
        }
    }
}
