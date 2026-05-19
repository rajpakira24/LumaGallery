package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.webstudio.lumagallery.BuildConfig
import java.io.IOException
import java.net.UnknownHostException

/**
 * Single entry point for AI image edits. Routes between on-device (ML Kit) and
 * cloud (Gemini → Qwen fallback) based on the operation and API key availability.
 */
class AiEditRepository(
    private val bgRemover: OnDeviceBgRemover = OnDeviceBgRemover(),
    private val gemini: GeminiImageClient = GeminiImageClient(BuildConfig.GEMINI_API_KEY),
    private val qwen: QwenImageClient = QwenImageClient(BuildConfig.DASHSCOPE_API_KEY)
) {

    val hasAnyCloudKey: Boolean get() = gemini.isAvailable || qwen.isAvailable

    suspend fun removeBackground(src: Bitmap): AiResult = runOp {
        bgRemover.removeBackground(src)
    }

    suspend fun upscale(src: Bitmap): AiResult {
        if (!hasAnyCloudKey) return AiResult.MissingApiKey
        return tryCloud(
            primary = { gemini.upscale(src) },
            fallback = { qwen.upscale(src) }
        )
    }

    suspend fun eraseObject(src: Bitmap, mask: Bitmap, prompt: String = DEFAULT_ERASE_PROMPT): AiResult {
        if (!hasAnyCloudKey) return AiResult.MissingApiKey
        return tryCloud(
            primary = { gemini.inpaint(src, mask, prompt) },
            fallback = { qwen.inpaint(src, mask, prompt) }
        )
    }

    suspend fun promptEdit(src: Bitmap, prompt: String): AiResult {
        if (!hasAnyCloudKey) return AiResult.MissingApiKey
        return tryCloud(
            primary = { gemini.promptEdit(src, prompt) },
            fallback = { qwen.promptEdit(src, prompt) }
        )
    }

    private suspend fun tryCloud(
        primary: suspend () -> Bitmap,
        fallback: suspend () -> Bitmap
    ): AiResult {
        if (gemini.isAvailable) {
            try {
                return AiResult.Success(primary())
            } catch (e: GeminiImageClient.QuotaException) {
                Log.d(TAG, "Gemini quota — trying Qwen fallback")
                if (!qwen.isAvailable) return AiResult.QuotaExceeded
            } catch (e: UnknownHostException) {
                return AiResult.NetworkError
            } catch (e: IOException) {
                Log.d(TAG, "Gemini IO error — trying Qwen fallback", e)
                if (!qwen.isAvailable) return AiResult.Failure(e.message ?: "Gemini failed")
            }
        }
        if (qwen.isAvailable) {
            return try {
                AiResult.Success(fallback())
            } catch (e: QwenImageClient.QuotaException) {
                AiResult.QuotaExceeded
            } catch (e: UnknownHostException) {
                AiResult.NetworkError
            } catch (e: IOException) {
                AiResult.Failure(e.message ?: "DashScope failed")
            }
        }
        return AiResult.MissingApiKey
    }

    private suspend fun runOp(block: suspend () -> Bitmap): AiResult = try {
        AiResult.Success(block())
    } catch (e: Exception) {
        Log.d(TAG, "AI op failed", e)
        AiResult.Failure(e.message ?: "AI op failed")
    }

    companion object {
        private const val TAG = "AiEditRepository"
        private const val DEFAULT_ERASE_PROMPT =
            "Remove the masked subject and fill the area naturally with surrounding texture."
    }
}
