package com.amwolfstein.sistrum.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import androidx.work.WorkManager
import java.util.UUID
import com.amwolfstein.sistrum.R

/** Builds the foreground "analyze now" notification and creates its low-importance channel. */
internal object AnalysisNotification {
    /** The channel id for the "analyze now" notification. */
    const val CHANNEL_ID = "loudness_analysis"

    /** Creates the channel idempotently, without sound or vibration. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.loudness_analysis_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Builds the determinate "N of M tracks" notification whose Cancel action cancels only this "now" work. */
    fun build(context: Context, workId: UUID, analyzed: Int, total: Int): Notification {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val cancelAction = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.getString(R.string.loudness_analysis_cancel),
            cancelIntent,
        ).build()
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.loudness_analysis_now_title))
            .setContentText(context.resources.getQuantityString(R.plurals.loudness_analysis_now_progress, total, analyzed, total))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, analyzed, false)
            .addAction(cancelAction)
            .build()
    }
}
