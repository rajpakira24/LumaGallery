package com.webstudio.lumagallery.data.edit

import android.content.ContentValues
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max

object PhotoIO {
    private const val TAG = "PhotoIO"
    const val MAX_PREVIEW_EDGE = 2048

    /**
     * Loads a downsampled, orientation-corrected bitmap from a MediaStore URI.
     * Caps the longest edge at [maxEdge] to keep memory bounded.
     */
    suspend fun loadPreviewBitmap(
        context: Context,
        uri: Uri,
        maxEdge: Int = MAX_PREVIEW_EDGE
    ): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        Log.d(TAG, "loadPreviewBitmap uri=$uri")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.decodeBitmap(uri, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Could not read image bounds: $uri")
        }

        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (max(w, h) > maxEdge) {
            sample *= 2
            w /= 2
            h /= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.decodeBitmap(uri, opts) ?: throw IOException("Could not decode bitmap: $uri")

        applyExifOrientation(context, uri, raw)
    }

    private fun ContentResolver.decodeBitmap(
        uri: Uri,
        opts: BitmapFactory.Options
    ): Bitmap? {
        try {
            openInputStream(uri)?.use { stream ->
                return BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: SecurityException) {
            throw IOException("No permission to read $uri", e)
        } catch (e: Exception) {
            Log.d(TAG, "Stream decode failed for $uri, trying file descriptor", e)
        }

        try {
            openFileDescriptor(uri, "r")?.use { pfd ->
                return BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
            }
        } catch (e: SecurityException) {
            throw IOException("No permission to read $uri", e)
        } catch (e: Exception) {
            throw IOException("Could not open image for decoding: $uri", e)
        }

        throw IOException("Could not open image for decoding: $uri")
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            Log.d(TAG, "EXIF read failed, treating as normal: ${e.message}")
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * Saves a bitmap as a new JPEG into MediaStore at Pictures/LumaGallery/Edits/.
     * Returns the new content URI.
     */
    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        baseName: String? = null,
        quality: Int = 95
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val finalName = (baseName?.takeIf { it.isNotBlank() }
            ?: "LumaGallery_edit_${System.currentTimeMillis()}") + ".jpg"

        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, finalName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/LumaGallery/Edits"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            ?: throw IOException("MediaStore.insert returned null")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("Bitmap.compress returned false")
                }
            } ?: throw IOException("openOutputStream returned null for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, finalize, null, null)
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    suspend fun savePngSticker(
        context: Context,
        bitmap: Bitmap,
        baseName: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val finalName = (baseName?.takeIf { it.isNotBlank() }
            ?: "LumaGallery_sticker_${System.currentTimeMillis()}") + ".png"

        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, finalName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/LumaGallery/Stickers"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            ?: throw IOException("MediaStore.insert returned null")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IOException("Bitmap.compress returned false")
                }
            } ?: throw IOException("openOutputStream returned null for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, finalize, null, null)
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
