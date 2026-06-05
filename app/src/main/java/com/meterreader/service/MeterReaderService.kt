package com.meterreader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.meterreader.MainActivity
import com.meterreader.R
import com.meterreader.camera.CameraCapture
import com.meterreader.data.AppDatabase
import com.meterreader.data.MeterReading
import com.meterreader.ocr.NumberExtractor
import com.meterreader.ocr.VisionApiClient
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

private const val TAG = "MeterReaderService"

const val CHANNEL_ID_SERVICE   = "meter_service"
const val CHANNEL_ID_READINGS  = "meter_readings"
const val NOTIFICATION_ID_FG   = 1
const val NOTIFICATION_ID_READ = 2
const val CAPTURE_INTERVAL_MS  = 30 * 60 * 1000L   // 30 minutes
const val PRUNE_DAYS           = 90L

/** Send this action to trigger an immediate capture outside the normal schedule. */
const val ACTION_MANUAL_CAPTURE = "com.meterreader.ACTION_MANUAL_CAPTURE"

/** Fired by AlarmManager for each scheduled capture; reschedules the next alarm. */
const val ACTION_SCHEDULED_CAPTURE = "com.meterreader.ACTION_SCHEDULED_CAPTURE"

private const val REQUEST_CODE_SCHEDULED_CAPTURE = 1001

// Keep the CPU awake just long enough for capture + Vision upload while the
// screen is off. Auto-released by the OS after the timeout as a safety net.
private const val WAKELOCK_TAG = "MeterReader:CaptureCycle"
private const val WAKELOCK_TIMEOUT_MS = 60_000L

class MeterReaderService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var cameraCapture: CameraCapture
    private lateinit var visionClient: VisionApiClient
    private lateinit var db: AppDatabase

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        cameraCapture = CameraCapture(applicationContext)
        visionClient  = VisionApiClient()
        db            = AppDatabase.getInstance(applicationContext)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID_FG, buildForegroundNotification())

        when (intent?.action) {
            ACTION_MANUAL_CAPTURE -> {
                // Manual capture is one-shot and must NOT touch the schedule.
                Log.i(TAG, "Manual capture requested")
                serviceScope.launch { performCaptureCycle() }
            }
            ACTION_SCHEDULED_CAPTURE -> {
                // Alarm-driven capture: run it, then arm the next alarm so the
                // 30-min cadence continues for as long as the service lives.
                Log.i(TAG, "Scheduled capture fired")
                serviceScope.launch {
                    performCaptureCycle()
                    scheduleNextCapture()
                }
            }
            else -> {
                // Normal start (legally from the foreground): capture now for
                // instant feedback, then arm the recurring alarm.
                Log.i(TAG, "Service started — capturing now, then every 30 min via AlarmManager")
                serviceScope.launch { performCaptureCycle() }
                scheduleNextCapture()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelScheduledCapture()
        cameraCapture.releaseCamera()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Alarm scheduling ──────────────────────────────────────────────────────

    private fun scheduleNextCapture() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + CAPTURE_INTERVAL_MS
        val pi = scheduledCapturePendingIntent()

        // Exact alarms need no permission below API 31; above it, honour the
        // user's "Alarms & reminders" setting and fall back to inexact.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "Next capture scheduled in ${CAPTURE_INTERVAL_MS / 60_000} min (exact=$canExact)")
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked while running — degrade gracefully.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Log.w(TAG, "Exact alarm denied; used inexact alarm", e)
        }
    }

    private fun cancelScheduledCapture() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(scheduledCapturePendingIntent())
    }

    private fun scheduledCapturePendingIntent(): PendingIntent {
        val intent = Intent(this, MeterReaderService::class.java).apply {
            action = ACTION_SCHEDULED_CAPTURE
        }
        return PendingIntent.getService(
            this,
            REQUEST_CODE_SCHEDULED_CAPTURE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ── Capture cycle ─────────────────────────────────────────────────────────

    private suspend fun performCaptureCycle() {
        Log.i(TAG, "Starting capture cycle")

        // Hold a partial wake lock so capture + upload survive screen-off CPU
        // suspension. The 60s timeout guarantees release even if we crash.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
        }
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)

        try {
            val base64 = try {
                cameraCapture.captureImageAsBase64(this)
            } catch (e: Exception) {
                Log.e(TAG, "Camera capture failed", e)
                saveInvalidReading("Camera error: ${e.message}")
                return
            }

            val ocrText = visionClient.extractText(base64)
            if (ocrText == null) {
                Log.w(TAG, "Vision API returned null — network or quota error")
                saveInvalidReading(null)
                sendNotification("OCR failed — check camera angle or network")
                return
            }

            val value = NumberExtractor.extractTotalizer(ocrText)
            val isValid = value != null

            db.meterReadingDao().insert(
                MeterReading(
                    timestamp  = System.currentTimeMillis(),
                    rawValue   = value,
                    unit       = "m³",
                    rawOcrText = ocrText,
                    isValid    = isValid
                )
            )
            pruneOldRecords()

            if (isValid) {
                // Stamp the last-success time so the watchdog can detect stalls.
                MeterStatus.recordSuccess(applicationContext)
                Log.i(TAG, "Reading saved: $value m³")
                sendNotification("Meter reading: ${"%.2f".format(value)} m³")
            } else {
                Log.w(TAG, "OCR returned no valid number. Raw: $ocrText")
                sendNotification("OCR failed — check camera angle")
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private suspend fun saveInvalidReading(errorNote: String?) {
        db.meterReadingDao().insert(
            MeterReading(
                timestamp  = System.currentTimeMillis(),
                rawValue   = null,
                unit       = "m³",
                rawOcrText = errorNote,
                isValid    = false
            )
        )
    }

    private suspend fun pruneOldRecords() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PRUNE_DAYS)
        db.meterReadingDao().deleteOlderThan(cutoff)
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel(
            CHANNEL_ID_SERVICE,
            "Meter Reader Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while the service is active"
            nm.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_ID_READINGS,
            "Meter Readings",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications after each successful capture"
            nm.createNotificationChannel(this)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.service_active))
            .setContentText(getString(R.string.service_description))
            .setSmallIcon(R.drawable.ic_meter_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun sendNotification(message: String) {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_READINGS)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_meter_notification)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_READ, notification)
    }
}
