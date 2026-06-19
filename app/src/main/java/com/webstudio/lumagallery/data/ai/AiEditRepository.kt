package com.webstudio.lumagallery.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.webstudio.lumagallery.BuildConfig
import java.io.IOException
import java.net.UnknownHostException

class AiEditRepository(
    context: Context,
    private val bgRemover: OnDeviceBgRemover = OnDeviceBgRemover(),
    private val proxy: AiProxyClient = AiProxyClient(
        functionUrl = BuildConfig.AI_PROXY_URL,
        integrity = PlayIntegrityTokenProvider(context, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER),
    ),
) {

    val aiEnabled: Boolean get() = BuildConfig.AI_PROXY_URL.isNotBlank()

    suspend fun removeBackground(src: Bitmap): AiResult = runOp { bgRemover.removeBackground(src) }

    suspend fun upscale(src: Bitmap): AiResult = cloud { proxy.editImage("upscale", src, null, null) }

    suspend fun eraseObject(src: Bitmap, mask: Bitmap, prompt: String = DEFAULT_ERASE_PROMPT): AiResult =
        cloud { proxy.editImage("inpaint", src, mask, prompt) }

    suspend fun promptEdit(src: Bitmap, prompt: String): AiResult =
        cloud { proxy.editImage("prompt_edit", src, null, prompt) }

    suspend fun generateImage(prompt: String): AiResult =
        cloud { proxy.editImage("generate", BLANK, null, prompt) }

    suspend fun describePhoto(src: Bitmap): AiResult {
        if (!aiEnabled) return AiResult.MissingApiKey
        return try {
            AiResult.TextSuccess(proxy.describe(src))
        } catch (e: AiProxyClient.QuotaException) {
            AiResult.QuotaExceeded
        } catch (e: AiProxyClient.AttestationException) {
            AiResult.Failure("device verification failed")
        } catch (e: UnknownHostException) {
            AiResult.NetworkError
        } catch (e: IOException) {
            AiResult.Failure(e.message ?: "AI proxy failed")
        }
    }

    private suspend fun cloud(op: suspend () -> Bitmap): AiResult {
        if (!aiEnabled) return AiResult.MissingApiKey
        return try {
            AiResult.Success(op())
        } catch (e: AiProxyClient.QuotaException) {
            AiResult.QuotaExceeded
        } catch (e: AiProxyClient.AttestationException) {
            AiResult.Failure("device verification failed")
        } catch (e: UnknownHostException) {
            AiResult.NetworkError
        } catch (e: IOException) {
            AiResult.Failure(e.message ?: "AI proxy failed")
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
        private val BLANK: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
