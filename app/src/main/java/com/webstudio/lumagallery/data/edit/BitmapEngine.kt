package com.webstudio.lumagallery.data.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect

/**
 * Pure bitmap transforms. Every function returns a fresh bitmap; callers own
 * recycling of inputs/outputs (see [EditHistory]).
 */
object BitmapEngine {

    /** Cap the longest side of an upscaled bitmap to keep memory bounded. */
    private const val MAX_UPSCALE_DIMENSION = 4096

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val m = Matrix().apply {
            if (horizontal) postScale(-1f, 1f, src.width / 2f, src.height / 2f)
            else postScale(1f, -1f, src.width / 2f, src.height / 2f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Simple on-device 2x upscale (bilinear). Free, no network. Returns a new bitmap.
     * Caps the longest output side at [MAX_UPSCALE_DIMENSION] to avoid OOM on large
     * images: if a true 2x would exceed the cap, scales up to fit the cap instead.
     */
    fun upscale2x(src: Bitmap): Bitmap {
        val longestTarget = maxOf(src.width, src.height) * 2
        val factor = if (longestTarget > MAX_UPSCALE_DIMENSION) {
            MAX_UPSCALE_DIMENSION.toFloat() / maxOf(src.width, src.height)
        } else {
            2f
        }
        val outW = (src.width * factor).toInt().coerceAtLeast(1)
        val outH = (src.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    /** Blends [overlay] on top of [base], scaling overlay to base dimensions if needed. */
    fun composeOverlay(base: Bitmap, overlay: Bitmap): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        if (overlay.width != base.width || overlay.height != base.height) {
            val srcR = Rect(0, 0, overlay.width, overlay.height)
            val dstR = Rect(0, 0, base.width, base.height)
            canvas.drawBitmap(overlay, srcR, dstR, null)
        } else {
            canvas.drawBitmap(overlay, 0f, 0f, null)
        }
        return out
    }

    /**
     * Combines brightness/contrast/saturation/warmth into one ColorMatrix.
     * Ranges:
     *  - brightness: -1f..1f (0 = neutral)
     *  - contrast: 0f..2f (1 = neutral)
     *  - saturation: 0f..2f (1 = neutral, 0 = grayscale)
     *  - warmth: -1f..1f (0 = neutral, +1 = warmer, -1 = cooler)
     */
    fun buildAdjustmentMatrix(
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        warmth: Float = 0f
    ): ColorMatrix {
        val cm = ColorMatrix()

        cm.postConcat(ColorMatrix().apply { setSaturation(saturation) })

        val scale = contrast
        val translate = (-0.5f * scale + 0.5f + brightness) * 255f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val w = warmth * 30f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, w * 0.33f,
                    0f, 0f, 1f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        return cm
    }
}
