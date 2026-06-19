package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiProxyClient(
    private val functionUrl: String,
    private val integrity: IntegrityTokenProvider,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val encodeJpeg: (Bitmap, Int) -> ByteArray = ::defaultEncode,
    private val decode: (ByteArray) -> Bitmap? = ::defaultDecode,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun editImage(op: String, src: Bitmap, mask: Bitmap?, prompt: String?): Bitmap =
        withContext(Dispatchers.IO) {
            val imgB64 = b64(encodeJpeg(src, 90))
            val maskB64 = mask?.let { b64(encodeJpeg(it, 90)) }
            val hash = requestHash(op, imgB64)
            val token = integrity.token(hash)
            val resp = post(ProxyRequest(op, imgB64, maskB64, prompt, hash, token))
            val parsed = json.decodeFromString(ImageResponse.serializer(), resp)
            val bytes = Base64.decode(parsed.imageB64 ?: throw IOException("no image"), Base64.DEFAULT)
            decode(bytes) ?: throw IOException("decode failed")
        }

    suspend fun describe(src: Bitmap): String = withContext(Dispatchers.IO) {
        val imgB64 = b64(encodeJpeg(src, 85))
        val hash = requestHash("describe", imgB64)
        val token = integrity.token(hash)
        val resp = post(ProxyRequest("describe", imgB64, null, null, hash, token))
        json.decodeFromString(TextResponse.serializer(), resp).text?.trim()
            ?: throw IOException("no text")
    }

    private fun post(body: ProxyRequest): String {
        val reqBody = json.encodeToString(ProxyRequest.serializer(), body)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(functionUrl).post(reqBody).build()
        http.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            AiUsageState.update(
                AiUsage(
                    remainingMin = resp.header("X-RateLimit-Remaining-Min")?.toIntOrNull(),
                    remainingHour = resp.header("X-RateLimit-Remaining-Hour")?.toIntOrNull(),
                    remainingDay = resp.header("X-RateLimit-Remaining-Day")?.toIntOrNull(),
                    imageRemainingDay = resp.header("X-RateLimit-Image-Remaining-Day")?.toIntOrNull(),
                    resetDaySec = resp.header("X-RateLimit-Reset-Day")?.toIntOrNull(),
                )
            )
            when (resp.code) {
                401 -> throw AttestationException()
                429 -> {
                    val err = runCatching {
                        json.decodeFromString(ErrorBody.serializer(), bodyStr)
                    }.getOrNull()
                    if (err?.error == "rate_limited") {
                        val retry = resp.header("Retry-After")?.toIntOrNull()
                            ?: err.retryAfter ?: 0
                        throw RateLimitException(retry)
                    }
                    throw QuotaException()
                }
                else -> if (!resp.isSuccessful) throw IOException("proxy HTTP ${resp.code}")
            }
            return bodyStr
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun requestHash(op: String, imgB64: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(md.digest((op + imgB64).toByteArray()), Base64.NO_WRAP)
    }

    class QuotaException : IOException("quota")
    class AttestationException : IOException("attestation failed")
    class RateLimitException(val retryAfterSec: Int) : IOException("rate limited")

    @Serializable
    private data class ProxyRequest(
        val op: String,
        @SerialName("image_b64") val imageB64: String,
        @SerialName("mask_b64") val maskB64: String? = null,
        val prompt: String? = null,
        @SerialName("request_hash") val requestHash: String,
        @SerialName("integrity_token") val integrityToken: String,
    )

    @Serializable
    private data class ErrorBody(
        val error: String? = null,
        @SerialName("retry_after") val retryAfter: Int? = null,
    )

    @Serializable
    private data class ImageResponse(@SerialName("image_b64") val imageB64: String? = null)

    @Serializable
    private data class TextResponse(val text: String? = null)

    companion object {
        private fun defaultEncode(bmp: Bitmap, quality: Int): ByteArray {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            return bos.toByteArray()
        }
        private fun defaultDecode(bytes: ByteArray): Bitmap? =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
