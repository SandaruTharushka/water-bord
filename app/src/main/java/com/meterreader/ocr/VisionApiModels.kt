package com.meterreader.ocr

import com.google.gson.annotations.SerializedName

// ── Request ──────────────────────────────────────────────────────────────────

data class VisionRequest(
    @SerializedName("requests") val requests: List<AnnotateImageRequest>
)

data class AnnotateImageRequest(
    @SerializedName("image") val image: ImageContent,
    @SerializedName("features") val features: List<Feature>
)

data class ImageContent(
    @SerializedName("content") val content: String  // base64 encoded JPEG
)

data class Feature(
    @SerializedName("type") val type: String,
    @SerializedName("maxResults") val maxResults: Int = 10
)

// ── Response ─────────────────────────────────────────────────────────────────

data class VisionResponse(
    @SerializedName("responses") val responses: List<AnnotateImageResponse>?
)

data class AnnotateImageResponse(
    @SerializedName("fullTextAnnotation") val fullTextAnnotation: FullTextAnnotation?,
    @SerializedName("error") val error: ApiError?
)

data class FullTextAnnotation(
    @SerializedName("text") val text: String?
)

data class ApiError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String
)
