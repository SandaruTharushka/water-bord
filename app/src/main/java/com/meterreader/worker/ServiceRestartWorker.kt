package com.meterreader.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.meterreader.service.MeterStatus
import com.meterreader.service.ResumeNotifier
import java.util.concurrent.TimeUnit

private const val TAG = "ServiceRestartWorker"
private const val WORK_NAME = "meter_service_restart"

/**
 * Periodic watchdog. It does NOT — and on Android 12+ legally cannot — start
 * the camera foreground service from the background. Instead it checks whether
 * readings have gone stale and, if so, posts a notification prompting the user
 * to reopen the app, which legally starts the service from the foreground.
 */
class ServiceRestartWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        if (MeterStatus.isStale(applicationContext)) {
            Log.i(TAG, "Readings are stale — prompting user to resume")
            ResumeNotifier.postResumeNotification(applicationContext)
        } else {
            Log.i(TAG, "Readings are fresh — no action needed")
        }
        return Result.success()
    }

    companion object {
        /**
         * Enqueues a periodic watchdog that detects stalled capturing and nudges
         * the user to reopen the app. WorkManager survives app kills and reboots.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceRestartWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Periodic watchdog scheduled")
        }
    }
}
