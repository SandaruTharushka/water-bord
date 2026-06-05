package com.meterreader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.meterreader.MainActivity
import com.meterreader.R

const val CHANNEL_ID_RESUME    = "meter_resume"
const val NOTIFICATION_ID_RESUME = 3

/**
 * Posts a high-priority notification asking the user to reopen the app.
 *
 * A camera-type ("while-in-use") foreground service CANNOT be started from the
 * background on Android 12+. So after a reboot, or when readings have gone
 * stale, we never start the service directly — we prompt the user, and tapping
 * the notification opens [MainActivity], which legally starts the service from
 * the foreground.
 *
 * The channel is created on demand here because this may run (e.g. right after
 * BOOT_COMPLETED) before the service — which owns the other channels — has ever
 * started.
 */
object ResumeNotifier {

    fun postResumeNotification(context: Context) {
        ensureChannel(context)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RESUME)
            .setContentTitle(context.getString(R.string.resume_title))
            .setContentText(context.getString(R.string.resume_text))
            .setSmallIcon(R.drawable.ic_meter_notification)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_RESUME, notification)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID_RESUME) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_RESUME,
                    "Resume Meter Readings",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Prompts you to reopen the app so capturing can resume"
                }
            )
        }
    }
}
