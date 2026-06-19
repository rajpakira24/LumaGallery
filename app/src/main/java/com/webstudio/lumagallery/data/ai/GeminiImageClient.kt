package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Gemini 2.5 Flash Image — generative image editing via the Generative Language API.
 *
 * Free tier: yes (rate-limited per minute / per day). API key obtained from
 * https://aistudio.google.com/apikey and stored in `local.properties` as `GEMINI_API_KEY`.
 */
class GeminiImageClient(private val apiKey: String) {

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun upscale(src: Bitmap): Bitmap = editWithPrompt(
        src,
        prompt = "Upscale this image 2x preserving fine detail. Do not change content."
    )

    suspend fun generateFromText(prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val req = GenerateRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(responseModalities = listOf("IMAGE"))
        )
        callGenerateContent(req)
    }

    suspend fun promptEdit(src: Bitmap, prompt: String): Bitmap = editWithPrompt(src, prompt)

    suspend fun inpaint(src: Bitmap, mask: Bitmap, prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val srcB64 = src.toJpegBase64()
        val maskB64 = mask.toJpegBase64()
        val req = GenerateRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "Edit the first image. The second image is a white-mask " +
                                "indicating the region to modify. $prompt"),
                        Part(inlineData = InlineData("image/jpeg", srcB64)),
                        Part(inlineData = InlineData("image/jpeg", maskB64))
                    )
                )
            ),
            generationConfig = GenerationConfig(responseModalities = listOf("IMAGE"))
        )
        callGenerateContent(req)
    }

    private suspend fun editWithPrompt(src: Bitmap, prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val b64 = src.toJpegBase64()
        val req = GenerateRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData("image/jpeg", b64))
                    )
                )
            ),
            generationConfig = GenerationConfig(responseModalities = listOf("IMAGE"))
        )
        callGenerateContent(req)
    }

    private fun callGenerateContent(payload: GenerateRequest): Bitmap {
        val body = json.encodeToString(GenerateRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val url = "$ENDPOINT?key=$apiKey"
        val response = client.newCall(Request.Builder().url(url).post(body).build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.take(500) ?: ""
                Log.d(TAG, "Gemini failed: ${resp.code} $msg")
                if (resp.code == 429) throw QuotaException("Gemini quota exceeded")
                throw IOException("Gemini HTTP ${resp.code}")
            }
            val text = resp.body?.string() ?: throw IOException("Empty body")
            val parsed = json.decodeFromString(GenerateResponse.serializer(), text)
            val b64 = parsed.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull { it.inlineData != null }
                ?.inlineData?.data
                ?: throw IOException("No image in Gemini response")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Could not decode Gemini image bytes")
        }
    }

    private fun Bitmap.toJpegBase64(quality: Int = 90): String {
        val bos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    @Serializable
    private data class GenerateRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )

    @Serializable
    private data class Content(val parts: List<Part>)

    @Serializable
    private data class Part(
        val text: String? = null,
        @SerialName("inline_data") val inlineData: InlineData? = null
    )

    @Serializable
    private data class InlineData(
        @SerialName("mime_type") val mimeType: String,
        val data: String
    )

    @Serializable
    private data class GenerationConfig(
        @SerialName("response_modalities") val responseModalities: List<String>
    )

    @Serializable
    private data class GenerateResponse(val candidates: List<Candidate>? = null)

    @Serializable
    private data class Candidate(val content: Content? = null)

    class QuotaException(msg: String) : IOException(msg)

    companion object {
        private const val TAG = "GeminiImageClient"
        private const val MODEL = "gemini-2.5-flash-image-preview"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    }
}
