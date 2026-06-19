package com.webstudio.lumagallery.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webstudio.lumagallery.data.ai.AiEditRepository
import com.webstudio.lumagallery.data.ai.AiResult
import com.webstudio.lumagallery.data.edit.BitmapEngine
import com.webstudio.lumagallery.data.edit.EditHistory
import com.webstudio.lumagallery.data.edit.FilterPreset
import com.webstudio.lumagallery.data.edit.PhotoIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditUiState(
    val bitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isModified: Boolean = false,
    val isLoading: Boolean = true,
    val processingLabel: String? = null,
    val savedUri: Uri? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val aiEnabled: Boolean = false,
    val stickerUri: Uri? = null
) {
    val displayBitmap: Bitmap? get() = previewBitmap ?: bitmap
    val isBusy: Boolean get() = isLoading || processingLabel != null
}

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepo = AiEditRepository(application)
    private val history = EditHistory()

    private val _uiState = MutableStateFlow(
        EditUiState(
            aiEnabled = com.webstudio.lumagallery.BuildConfig.AI_PROXY_URL.isNotBlank()
        )
    )
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private var loadedUri: Uri? = null

    fun loadPhoto(uri: Uri) {
        if (loadedUri == uri && _uiState.value.bitmap != null) return
        loadedUri = uri
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val bmp = PhotoIO.loadPreviewBitmap(getApplication(), uri)
                history.clear()
                _uiState.value = _uiState.value.copy(
                    bitmap = bmp,
                    previewBitmap = null,
                    canUndo = false,
                    canRedo = false,
                    isModified = false,
                    isLoading = false,
                    processingLabel = null,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.d(TAG, "loadPhoto failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load photo: ${e.message}"
                )
            }
        }
    }

    // ---------- Transforms (commit immediately) ----------

    fun rotate(degrees: Float) = applyTransform("Rotating") { BitmapEngine.rotate(it, degrees) }

    fun flip(horizontal: Boolean) = applyTransform("Flipping") { BitmapEngine.flip(it, horizontal) }

    fun applyFilter(preset: FilterPreset) {
        if (preset == FilterPreset.Original) return
        applyTransform("Applying filter") { BitmapEngine.applyColorMatrix(it, preset.matrix()) }
    }

    /** Replaces the bitmap wholesale (used for crop, draw flatten, and AI results). */
    fun replaceBitmap(newBitmap: Bitmap) {
        val curr = _uiState.value.bitmap ?: return
        history.pushUndo(curr)
        _uiState.value = _uiState.value.copy(
            bitmap = newBitmap,
            previewBitmap = null,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            isModified = true,
            processingLabel = null
        )
    }

    // ---------- Live color preview ----------

    fun setColorPreview(brightness: Float, contrast: Float, saturation: Float, warmth: Float) {
        val src = _uiState.value.bitmap ?: return
        viewModelScope.launch {
            val matrix: ColorMatrix = BitmapEngine.buildAdjustmentMatrix(
                brightness, contrast, saturation, warmth
            )
            val preview = withContext(Dispatchers.Default) {
                BitmapEngine.applyColorMatrix(src, matrix)
            }
            val old = _uiState.value.previewBitmap
            _uiState.value = _uiState.value.copy(previewBitmap = preview)
            old?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    fun commitColorPreview() {
        val preview = _uiState.value.previewBitmap ?: return
        val curr = _uiState.value.bitmap ?: return
        history.pushUndo(curr)
        _uiState.value = _uiState.value.copy(
            bitmap = preview,
            previewBitmap = null,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            isModified = true
        )
    }

    fun discardColorPreview() {
        _uiState.value.previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        _uiState.value = _uiState.value.copy(previewBitmap = null)
    }

    // ---------- AI ops ----------

    fun aiRemoveBackground() = runAi("Removing background") { src -> aiRepo.removeBackground(src) }

    fun aiUpscale() = runAi("Upscaling") { src -> aiRepo.upscale(src) }

    fun aiEraseObject(mask: Bitmap, prompt: String) = runAi("Erasing") { src ->
        aiRepo.eraseObject(src, mask, prompt)
    }

    fun aiPromptEdit(prompt: String) = runAi("Editing with prompt") { src ->
        aiRepo.promptEdit(src, prompt)
    }

    fun createOnDeviceSticker() {
        val src = _uiState.value.bitmap ?: return
        _uiState.value = _uiState.value.copy(processingLabel = "Creating sticker", errorMessage = null)
        viewModelScope.launch {
            try {
                val sticker = aiRepo.removeBackground(src).let { result ->
                    when (result) {
                        is AiResult.Success -> result.bitmap
                        is AiResult.Failure -> throw IllegalStateException(result.message)
                        AiResult.MissingApiKey -> throw IllegalStateException("Sticker failed")
                        AiResult.NetworkError -> throw IllegalStateException("Network error")
                        AiResult.QuotaExceeded -> throw IllegalStateException("Quota exceeded")
                        else -> throw IllegalStateException("Unexpected result")
                    }
                }
                val savedUri = PhotoIO.savePngSticker(getApplication(), sticker)
                history.pushUndo(src)
                _uiState.value = _uiState.value.copy(
                    bitmap = sticker,
                    previewBitmap = null,
                    canUndo = history.canUndo,
                    canRedo = history.canRedo,
                    isModified = true,
                    processingLabel = null,
                    stickerUri = savedUri
                )
            } catch (e: Exception) {
                Log.d(TAG, "createOnDeviceSticker failed", e)
                _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "Sticker failed: ${e.message}"
                )
            }
        }
    }

    private fun runAi(label: String, op: suspend (Bitmap) -> AiResult) {
        val src = _uiState.value.bitmap ?: return
        _uiState.value = _uiState.value.copy(processingLabel = label, errorMessage = null)
        viewModelScope.launch {
            val result = op(src)
            when (result) {
                is AiResult.Success -> replaceBitmap(result.bitmap)
                AiResult.MissingApiKey -> _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "Cloud AI is not configured."
                )
                AiResult.NetworkError -> _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "Network error — check your connection."
                )
                AiResult.QuotaExceeded -> _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "Free-tier quota exhausted. Try again later."
                )
                is AiResult.RateLimited -> _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "AI limit reached — try again in ${formatRetry(result.retryAfterSec)}"
                )
                is AiResult.Failure -> _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "AI op failed: ${result.message}"
                )
                else -> _uiState.value = _uiState.value.copy(processingLabel = null)
            }
        }
    }

    // ---------- History ----------

    fun undo() {
        val curr = _uiState.value.bitmap ?: return
        val prev = history.popUndo(curr) ?: return
        _uiState.value = _uiState.value.copy(
            bitmap = prev,
            previewBitmap = null,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            isModified = true
        )
    }

    fun redo() {
        val curr = _uiState.value.bitmap ?: return
        val next = history.popRedo(curr) ?: return
        _uiState.value = _uiState.value.copy(
            bitmap = next,
            previewBitmap = null,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            isModified = true
        )
    }

    // ---------- Save ----------

    fun save() {
        val bmp = _uiState.value.bitmap ?: return
        _uiState.value = _uiState.value.copy(processingLabel = "Saving")
        viewModelScope.launch {
            try {
                val uri = PhotoIO.saveBitmap(getApplication(), bmp)
                _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    savedUri = uri,
                    isModified = false
                )
            } catch (e: Exception) {
                Log.d(TAG, "save failed", e)
                _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "Save failed: ${e.message}"
                )
            }
        }
    }

    fun consumeSavedUri() {
        _uiState.value = _uiState.value.copy(savedUri = null)
    }

    fun consumeStickerUri() {
        _uiState.value = _uiState.value.copy(stickerUri = null)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun consumeInfo() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    private fun applyTransform(label: String, transform: suspend (Bitmap) -> Bitmap) {
        val src = _uiState.value.bitmap ?: return
        _uiState.value = _uiState.value.copy(processingLabel = label)
        viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.Default) { transform(src) }
                history.pushUndo(src)
                _uiState.value = _uiState.value.copy(
                    bitmap = next,
                    previewBitmap = null,
                    canUndo = history.canUndo,
                    canRedo = history.canRedo,
                    isModified = true,
                    processingLabel = null
                )
            } catch (e: Exception) {
                Log.d(TAG, "applyTransform($label) failed", e)
                _uiState.value = _uiState.value.copy(
                    processingLabel = null,
                    errorMessage = "$label failed: ${e.message}"
                )
            }
        }
    }

    private fun formatRetry(sec: Int): String =
        if (sec >= 60) "${(sec + 59) / 60} min" else "$sec s"

    override fun onCleared() {
        super.onCleared()
        history.clear()
        _uiState.value.previewBitmap?.let { if (!it.isRecycled) it.recycle() }
    }

    companion object {
        private const val TAG = "EditViewModel"
    }
}
