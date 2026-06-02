package com.meterreader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
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

class MeterReaderService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var cameraCapture: CameraCapture
    private lateinit var visionClient: VisionApiClient
    private lateinit var db: AppDatabase

    private val captureRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch { performCaptureCycle() }
            handler.postDelayed(this, CAPTURE_INTERVAL_MS)
        }
    }

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
                Log.i(TAG, "Manual capture requested")
                serviceScope.launch { performCaptureCycle() }
            }
            else -> {
                // Normal start: schedule periodic captures (avoid double-posting)
                handler.removeCallbacks(captureRunnable)
                handler.post(captureRunnable)
                Log.i(TAG, "Service started — capture every 30 min")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureRunnable)
        cameraCapture.releaseCamera()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Capture cycle ─────────────────────────────────────────────────────────

    private suspend fun performCaptureCycle() {
        Log.i(TAG, "Starting capture cycle")

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
            Log.i(TAG, "Reading saved: $value m³")
            sendNotification("Meter reading: ${"%.2f".format(value)} m³")
        } else {
            Log.w(TAG, "OCR returned no valid number. Raw: $ocrText")
            sendNotification("OCR failed — check camera angle")
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
