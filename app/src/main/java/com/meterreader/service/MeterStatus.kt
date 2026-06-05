package com.meterreader.service

import android.content.Context

/**
 * Tracks the timestamp of the last *successful* meter reading so background
 * watchdogs can detect when unattended capturing has silently stalled
 * (e.g. the while-in-use camera foreground service was killed and — per the
 * Android 12+ constraint — cannot be restarted from the background).
 */
object MeterStatus {

    private const val PREFS = "meter_status"
    private const val KEY_LAST_SUCCESS = "last_success_millis"

    /** Readings are considered stale if none succeeded within this window. */
    const val STALE_THRESHOLD_MS = 70 * 60 * 1000L   // 70 minutes (> 2 missed cycles)

    fun recordSuccess(context: Context, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_SUCCESS, timestamp).apply()
    }

    fun lastSuccessMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SUCCESS, 0L)

    /** True if no successful reading has been recorded within [STALE_THRESHOLD_MS]. */
    fun isStale(context: Context): Boolean =
        System.currentTimeMillis() - lastSuccessMillis(context) > STALE_THRESHOLD_MS

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
