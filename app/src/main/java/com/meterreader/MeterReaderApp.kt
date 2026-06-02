package com.meterreader

import android.app.Application
import com.meterreader.worker.ServiceRestartWorker

class MeterReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Schedule WorkManager watchdog that keeps the service alive
        ServiceRestartWorker.schedule(this)
    }
}
