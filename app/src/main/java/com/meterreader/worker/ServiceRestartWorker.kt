package com.meterreader.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.meterreader.service.ServiceManager
import java.util.concurrent.TimeUnit

private const val TAG = "ServiceRestartWorker"
private const val WORK_NAME = "meter_service_restart"

class ServiceRestartWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.i(TAG, "WorkManager: restarting MeterReaderService")
        ServiceManager.startService(applicationContext)
        return Result.success()
    }

    companion object {
        /**
         * Enqueues a periodic WorkManager task that ensures the foreground
         * service is alive. WorkManager survives app kills and reboots.
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
            Log.i(TAG, "Periodic restart worker scheduled")
        }
    }
}
