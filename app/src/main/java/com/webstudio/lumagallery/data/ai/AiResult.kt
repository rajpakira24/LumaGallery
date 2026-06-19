package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap

sealed class AiResult {
    data class Success(val bitmap: Bitmap) : AiResult()
    data class TextSuccess(val text: String) : AiResult()
    object MissingApiKey : AiResult()
    object NetworkError : AiResult()
    object QuotaExceeded : AiResult()
    data class RateLimited(val retryAfterSec: Int) : AiResult()
    data class Failure(val message: String) : AiResult()
}
