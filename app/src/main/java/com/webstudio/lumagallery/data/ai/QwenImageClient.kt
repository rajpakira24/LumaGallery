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
 * Alibaba DashScope (通义万相 / Qwen-Image-Edit) — Chinese free-tier fallback client.
 * Used when Gemini is missing or rate-limited.
 *
 * Free tier: yes (~500 calls/month on Wanxiang). API key from
 * https://dashscope.console.aliyun.com — store as `DASHSCOPE_API_KEY` in `local.properties`.
 *
 * Uses the synchronous multimodal-generation endpoint for image-to-image.
 */
class QwenImageClient(private val apiKey: String) {

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun upscale(src: Bitmap): Bitmap = edit(
        src,
        prompt = "Upscale this image 2x while preserving fine detail and content."
    )

    suspend fun promptEdit(src: Bitmap, prompt: String): Bitmap = edit(src, prompt)

    suspend fun inpaint(src: Bitmap, mask: Bitmap, prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val srcB64 = src.toJpegBase64()
        val maskB64 = mask.toJpegBase64()
        val req = MultimodalRequest(
            model = MODEL_EDIT,
            input = Input(
                messages = listOf(
                    Message(
                        role = "user",
                        content = listOf(
                            ContentPart(image = "data:image/jpeg;base64,$srcB64"),
                            ContentPart(image = "data:image/jpeg;base64,$maskB64"),
                            ContentPart(text = "Use the second image as a white-mask of the " +
                                    "region to modify in the first image. $prompt")
                        )
                    )
                )
            )
        )
        call(req)
    }

    private suspend fun edit(src: Bitmap, prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val b64 = src.toJpegBase64()
        val req = MultimodalRequest(
            model = MODEL_EDIT,
            input = Input(
                messages = listOf(
                    Message(
                        role = "user",
                        content = listOf(
                            ContentPart(image = "data:image/jpeg;base64,$b64"),
                            ContentPart(text = prompt)
                        )
                    )
                )
            )
        )
        call(req)
    }

    private fun call(payload: MultimodalRequest): Bitmap {
        val body = json.encodeToString(MultimodalRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val msg = resp.body?.string()?.take(500) ?: ""
                Log.d(TAG, "DashScope failed: ${resp.code} $msg")
                if (resp.code == 429) throw QuotaException("DashScope quota exceeded")
                throw IOException("DashScope HTTP ${resp.code}")
            }
            val text = resp.body?.string() ?: throw IOException("Empty body")
            val parsed = json.decodeFromString(MultimodalResponse.serializer(), text)
            val out = parsed.output ?: throw IOException("No output in DashScope response")
            val imgUrl = out.choices?.firstOrNull()
                ?.message?.content?.firstOrNull { !it.image.isNullOrBlank() }?.image
                ?: throw IOException("No image in DashScope response")

            val bytes = if (imgUrl.startsWith("data:")) {
                val b64 = imgUrl.substringAfter(",", "")
                Base64.decode(b64, Base64.DEFAULT)
            } else {
                client.newCall(Request.Builder().url(imgUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Image download HTTP ${r.code}")
                    r.body?.bytes() ?: throw IOException("Empty image body")
                }
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Could not decode DashScope image bytes")
        }
    }

    private fun Bitmap.toJpegBase64(quality: Int = 90): String {
        val bos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    @Serializable
    private data class MultimodalRequest(
        val model: String,
        val input: Input
    )

    @Serializable
    private data class Input(val messages: List<Message>)

    @Serializable
    private data class Message(val role: String, val content: List<ContentPart>)

    @Serializable
    private data class ContentPart(
        val image: String? = null,
        val text: String? = null
    )

    @Serializable
    private data class MultimodalResponse(
        val output: Output? = null,
        @SerialName("request_id") val requestId: String? = null
    )

    @Serializable
    private data class Output(val choices: List<Choice>? = null)

    @Serializable
    private data class Choice(val message: OutputMessage? = null)

    @Serializable
    private data class OutputMessage(val content: List<ContentPart>? = null)

    class QuotaException(msg: String) : IOException(msg)

    companion object {
        private const val TAG = "QwenImageClient"
        private const val MODEL_EDIT = "qwen-image-edit"
        private const val ENDPOINT =
            "https://dashscope-intl.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
    }
}
