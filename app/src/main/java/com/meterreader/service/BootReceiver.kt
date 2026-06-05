package com.meterreader.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BootReceiver"

/**
 * After a reboot (or app update) the camera foreground service is dead and we
 * are running in the background. A camera-type "while-in-use" foreground
 * service CANNOT be started from the background on Android 12+, so we must NOT
 * try to restart it here. Instead we post a high-priority notification; tapping
 * it opens MainActivity, which legally starts the service from the foreground.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Boot/update detected — prompting user to resume (cannot start camera FGS from background)")
                ResumeNotifier.postResumeNotification(context)
            }
        }
    }
}
