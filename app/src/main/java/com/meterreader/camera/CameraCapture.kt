package com.meterreader.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "CameraCapture"

class CameraCapture(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Captures a single JPEG frame from the back camera using ImageCapture
     * (no preview required — safe for background use).
     * Returns a base64-encoded JPEG string, or throws on failure.
     */
    @SuppressLint("RestrictedApi")
    suspend fun captureImageAsBase64(lifecycleOwner: LifecycleOwner): String =
        suspendCancellableCoroutine { cont ->
            val executor: Executor = ContextCompat.getMainExecutor(context)

            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imageCapture
                    )

                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val base64 = imageProxyToBase64(image)
                                    image.close()
                                    releaseCamera()
                                    cont.resume(base64)
                                } catch (e: Exception) {
                                    image.close()
                                    releaseCamera()
                                    cont.resumeWithException(e)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Image capture failed", exception)
                                releaseCamera()
                                cont.resumeWithException(exception)
                            }
                        }
                    )

                    cont.invokeOnCancellation { releaseCamera() }

                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                    cont.resumeWithException(e)
                }
            }, executor)
        }

    private fun imageProxyToBase64(image: ImageProxy): String {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing camera", e)
        }
        cameraProvider = null
    }
}
