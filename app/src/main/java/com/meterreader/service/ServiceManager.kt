package com.meterreader.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

private const val TAG = "ServiceManager"

object ServiceManager {

    fun startService(context: Context) {
        val intent = Intent(context, MeterReaderService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Service start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, MeterReaderService::class.java)
        context.stopService(intent)
        Log.i(TAG, "Service stop requested")
    }
}
