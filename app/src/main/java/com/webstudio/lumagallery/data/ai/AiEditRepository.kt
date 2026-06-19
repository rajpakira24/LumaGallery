package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.webstudio.lumagallery.BuildConfig
import java.io.IOException
import java.net.UnknownHostException

class AiEditRepository(
    private val bgRemover: OnDeviceBgRemover = OnDeviceBgRemover(),
    private val gemini: GeminiImageClient = GeminiImageClient(BuildConfig.GEMINI_API_KEY),
    private val openRouter: OpenRouterVisionClient = OpenRouterVisionClient(BuildConfig.OPENROUTER_API_KEY)
) {

    val hasGeminiKey: Boolean get() = gemini.isAvailable
    val hasOpenRouterKey: Boolean get() = openRouter.isAvailable

    suspend fun removeBackground(src: Bitmap): AiResult = runOp {
        bgRemover.removeBackground(src)
    }

    suspend fun upscale(src: Bitmap): AiResult {
        if (!hasGeminiKey) return AiResult.MissingApiKey
        return tryCloud { gemini.upscale(src) }
    }

    suspend fun eraseObject(src: Bitmap, mask: Bitmap, prompt: String = DEFAULT_ERASE_PROMPT): AiResult {
        if (!hasGeminiKey) return AiResult.MissingApiKey
        return tryCloud { gemini.inpaint(src, mask, prompt) }
    }

    suspend fun promptEdit(src: Bitmap, prompt: String): AiResult {
        if (!hasGeminiKey) return AiResult.MissingApiKey
        return tryCloud { gemini.promptEdit(src, prompt) }
    }

    suspend fun generateImage(prompt: String): AiResult {
        if (!hasGeminiKey) return AiResult.MissingApiKey
        return tryCloud { gemini.generateFromText(prompt) }
    }

    suspend fun describePhoto(src: Bitmap): AiResult {
        if (!hasOpenRouterKey) return AiResult.MissingApiKey
        return try {
            AiResult.TextSuccess(openRouter.describePhoto(src))
        } catch (e: OpenRouterVisionClient.QuotaException) {
            AiResult.QuotaExceeded
        } catch (e: UnknownHostException) {
            AiResult.NetworkError
        } catch (e: IOException) {
            AiResult.Failure(e.message ?: "OpenRouter failed")
        }
    }

    private suspend fun tryCloud(op: suspend () -> Bitmap): AiResult {
        return try {
            AiResult.Success(op())
        } catch (e: GeminiImageClient.QuotaException) {
            AiResult.QuotaExceeded
        } catch (e: UnknownHostException) {
            AiResult.NetworkError
        } catch (e: IOException) {
            AiResult.Failure(e.message ?: "Gemini failed")
        }
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
