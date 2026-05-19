package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Selfie Segmentation — runs on-device, free, no API key. Produces a bitmap
 * with the foreground subject (best for people) preserved and the background made transparent.
 *
 * Note: optimized for people; non-person subjects may not segment cleanly. Good enough for the
 * primary "remove background from a portrait" use case.
 */
class OnDeviceBgRemover {

    private val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()
    private val segmenter = Segmentation.getClient(options)

    suspend fun removeBackground(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(src, 0)
        val mask: SegmentationMask = suspendCancellableCoroutine { cont ->
            segmenter.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { e ->
                    Log.d(TAG, "Selfie segmentation failed", e)
                    cont.resumeWithException(e)
                }
        }
        applyMaskAsAlpha(src, mask)
    }

    private fun applyMaskAsAlpha(src: Bitmap, mask: SegmentationMask): Bitmap {
        val w = mask.width
        val h = mask.height
        val buffer = mask.buffer
        buffer.rewind()

        // Scale src to mask dims if mismatched
        val scaled = if (src.width == w && src.height == h) src
        else Bitmap.createScaledBitmap(src, w, h, true)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val confidence = buffer.float
            val srcPixel = pixels[i]
            val alpha = (confidence.coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = Color.argb(
                alpha,
                Color.red(srcPixel),
                Color.green(srcPixel),
                Color.blue(srcPixel)
            )
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != src) scaled.recycle()
        return out
    }

    companion object {
        private const val TAG = "OnDeviceBgRemover"
    }
}
