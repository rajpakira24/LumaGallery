package com.webstudio.lumagallery.data.edit

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

object StickerIO {
    /** Encode [bitmap] as WebP, dropping quality until it fits [maxBytes]. */
    fun encodeWebpUnderSize(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var quality = 100
        var bytes = encode(bitmap, quality)
        while (bytes.size > maxBytes && quality > 20) {
            quality -= 10
            bytes = encode(bitmap, quality)
        }
        return bytes
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bitmap.compress(fmt, quality, bos)
        return bos.toByteArray()
    }

    /** Write WebP bytes (size-capped) to [file], creating parent dirs. Returns the file. */
    fun writeWebp(file: File, bitmap: Bitmap, maxBytes: Int): File {
        file.parentFile?.mkdirs()
        file.writeBytes(encodeWebpUnderSize(bitmap, maxBytes))
        return file
    }

    const val STICKER_MAX_BYTES = 100 * 1024
    const val TRAY_MAX_BYTES = 50 * 1024
    const val STICKER_SIZE = 512
    const val TRAY_SIZE = 96
}
