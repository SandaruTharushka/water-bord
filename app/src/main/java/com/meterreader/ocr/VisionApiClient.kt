package com.meterreader.ocr

import android.util.Log
import com.meterreader.BuildConfig
import com.squareup.okhttp3.OkHttpClient
import com.squareup.okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private const val TAG = "VisionApiClient"
private const val BASE_URL = "https://vision.googleapis.com/"
private const val TIMEOUT_SECONDS = 30L

interface VisionApiService {
    @POST("v1/images:annotate")
    suspend fun annotate(
        @Query("key") apiKey: String,
        @Body request: VisionRequest
    ): VisionResponse
}

class VisionApiClient {

    private val service: VisionApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VisionApiService::class.java)
    }

    /**
     * Sends a base64-encoded JPEG to Google Vision TEXT_DETECTION.
     * Returns the raw OCR text, or null on failure.
     */
    suspend fun extractText(imageBase64: String): String? {
        return try {
            val request = VisionRequest(
                requests = listOf(
                    AnnotateImageRequest(
                        image = ImageContent(content = imageBase64),
                        features = listOf(Feature(type = "TEXT_DETECTION"))
                    )
                )
            )

            val response = service.annotate(
                apiKey = BuildConfig.VISION_API_KEY,
                request = request
            )

            val annotateResponse = response.responses?.firstOrNull()
            if (annotateResponse?.error != null) {
                Log.e(TAG, "Vision API error ${annotateResponse.error.code}: ${annotateResponse.error.message}")
                return null
            }

            annotateResponse?.fullTextAnnotation?.text
        } catch (e: Exception) {
            Log.e(TAG, "Vision API call failed", e)
            null
        }
    }
}
