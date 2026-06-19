package com.webstudio.lumagallery.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val TAG = "PhotoRepository"

class PhotoRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("luma_gallery_prefs", Context.MODE_PRIVATE)
    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
    private val displayDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    }

    @Volatile private var cachedMedia: List<Photo>? = null
    @Volatile private var lastLoadTime = 0L
    private val cacheValidityMs = 5000L
    private val cacheMutex = Mutex()

    suspend fun loadAllMedia(): List<Photo> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        cachedMedia?.takeIf { currentTime - lastLoadTime < cacheValidityMs }?.let { return@withContext it }

        cacheMutex.withLock {
            cachedMedia?.takeIf { currentTime - lastLoadTime < cacheValidityMs }?.let { return@withLock it }

            Log.d(TAG, "Loading fresh media from device...")
            val startTime = System.currentTimeMillis()

            val media = mutableListOf<Photo>()
            media.addAll(loadImages())
            media.addAll(loadVideos())

            val result = media.map { photo ->
                photo.copy(
                    isFavorite = isFavorite(photo.id),
                    isHidden = isHidden(photo.id),
                    isInRecycleBin = isInRecycleBin(photo.id),
                    deletedAt = getDeletedAt(photo.id)
                )
            }.sortedByDescending { maxOf(it.dateTaken, it.dateAdded * 1000) }

            cachedMedia = result
            lastLoadTime = System.currentTimeMillis()

            Log.d(TAG, "Loaded ${result.size} media items in ${System.currentTimeMillis() - startTime}ms")
            result
        }
    }

    fun invalidateCache() {
        cachedMedia = null
        lastLoadTime = 0L
    }

    private suspend fun loadImages(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val startTime = System.currentTimeMillis()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.SIZE} > 0",
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val filePath = cursor.getString(dataColumn) ?: ""

                        // Skip file existence check for better performance
                        val folderPath = File(filePath).parent ?: ""
                        val folderName = File(folderPath).name

                        photos.add(
                            Photo(
                                id = id,
                                uri = contentUri,
                                displayName = cursor.getString(nameColumn) ?: "",
                                dateAdded = cursor.getLong(dateAddedColumn),
                                dateTaken = cursor.getLong(dateTakenColumn),
                                size = cursor.getLong(sizeColumn),
                                mimeType = cursor.getString(mimeTypeColumn) ?: "",
                                width = cursor.getInt(widthColumn),
                                height = cursor.getInt(heightColumn),
                                folderPath = folderPath,
                                folderName = folderName,
                                isVideo = false
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading image item: ${e.message}")
                        continue
                    }
                }
            }
            Log.d(TAG, "Loaded ${photos.size} images in ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading images", e)
        }

        photos
    }

    private suspend fun loadVideos(): List<Photo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Photo>()
        val startTime = System.currentTimeMillis()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Video.Media.SIZE} > 0",
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val filePath = cursor.getString(dataColumn) ?: ""

                        val folderPath = File(filePath).parent ?: ""
                        val folderName = File(folderPath).name

                        videos.add(
                            Photo(
                                id = id,
                                uri = contentUri,
                                displayName = cursor.getString(nameColumn) ?: "",
                                dateAdded = cursor.getLong(dateAddedColumn),
                                dateTaken = cursor.getLong(dateTakenColumn),
                                size = cursor.getLong(sizeColumn),
                                mimeType = cursor.getString(mimeTypeColumn) ?: "",
                                width = cursor.getInt(widthColumn),
                                height = cursor.getInt(heightColumn),
                                folderPath = folderPath,
                                folderName = folderName,
                                isVideo = true,
                                duration = cursor.getLong(durationColumn)
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading video item: ${e.message}")
                        continue
                    }
                }
            }
            Log.d(TAG, "Loaded ${videos.size} videos in ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading videos", e)
        }

        videos
    }

    suspend fun loadFolderGroups(): List<FolderGroup> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Loading folder groups...")
        val startTime = System.currentTimeMillis()

        val allMedia = loadAllMedia()
        val regularFolders = allMedia
            .filter { it.folderName.isNotEmpty() && !it.isInRecycleBin && !it.isHidden }
            .groupBy { it.folderPath }
            .map { (path, mediaList) ->
                val sortedPhotos = mediaList.sortedByDescending {
                    maxOf(it.dateTaken, it.dateAdded * 1000)
                }
                FolderGroup(
                    folderName = mediaList.firstOrNull()?.folderName ?: "Unknown",
                    folderPath = path,
                    photos = sortedPhotos.take(4),
                    count = mediaList.size
                )
            }
            .filter { it.count > 0 }
            .sortedWith(compareByDescending<FolderGroup> { it.count }.thenBy { it.folderName })

        val specialFolders = mutableListOf<FolderGroup>()

        val favorites = allMedia.filter { it.isFavorite && !it.isInRecycleBin && !it.isHidden }
        if (favorites.isNotEmpty()) {
            specialFolders.add(
                FolderGroup(
                    folderName = "Favorites",
                    folderPath = "favorites",
                    photos = favorites.take(4),
                    count = favorites.size,
                    isSpecialFolder = true
                )
            )
        }

        val result = regularFolders + specialFolders
        Log.d(TAG, "Loaded ${result.size} folders in ${System.currentTimeMillis() - startTime}ms")
        result
    }

    suspend fun getPhotosByFolder(folderPath: String): List<Photo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting photos for folder: $folderPath")
        val allMedia = loadAllMedia()
        when (folderPath) {
            "favorites" -> allMedia.filter { it.isFavorite && !it.isInRecycleBin && !it.isHidden }
            else -> allMedia.filter { it.folderPath == folderPath && !it.isInRecycleBin && !it.isHidden }
        }.sortedByDescending { maxOf(it.dateTaken, it.dateAdded * 1000) }
    }

    suspend fun searchPhotos(query: String): List<Photo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching photos with query: $query")
        val allMedia = loadAllMedia()
        allMedia
            .filter { !it.isInRecycleBin && !it.isHidden }
            .filter { photo ->
                photo.displayName.contains(query, ignoreCase = true) ||
                        photo.folderName.contains(query, ignoreCase = true)
            }
    }

    suspend fun getPhotosByDate(): List<DateGroup> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting photos by date...")
        val startTime = System.currentTimeMillis()

        val allMedia = loadAllMedia()
            .filter { !it.isInRecycleBin && !it.isHidden }
            .sortedByDescending { maxOf(it.dateTaken, it.dateAdded * 1000) }

        val calendar = Calendar.getInstance()
        val fmt = dateFormat.get()!!
        val today = fmt.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = fmt.format(calendar.time)

        val result = allMedia.groupBy { photo ->
            val date = Date(maxOf(photo.dateTaken, photo.dateAdded * 1000))
            dateFormat.get()!!.format(date)
        }.map { (date, photos) ->
            val displayDate = when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> {
                    val parsedDate = dateFormat.get()!!.parse(date)
                    if (parsedDate != null) {
                        displayDateFormat.get()!!.format(parsedDate)
                    } else {
                        date
                    }
                }
            }
            DateGroup(date, displayDate, photos)
        }

        Log.d(TAG, "Grouped into ${result.size} date groups in ${System.currentTimeMillis() - startTime}ms")
        result
    }

    suspend fun getRecycleBinItems(): List<RecycleBinItem> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting recycle bin items...")
        val allMedia = loadAllMedia()
        val currentTime = System.currentTimeMillis()

        allMedia
            .filter { it.isInRecycleBin }
            .mapNotNull { photo ->
                val expiresAt = photo.deletedAt + (30L * 24 * 60 * 60 * 1000)
                if (currentTime < expiresAt) {
                    RecycleBinItem(photo, photo.deletedAt, expiresAt)
                } else {
                    null
                }
            }
            .sortedByDescending { it.deletedAt }
    }

    suspend fun getHiddenPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting hidden photos...")
        loadAllMedia().filter { it.isHidden && !it.isInRecycleBin }
    }

    fun toggleFavorite(photoId: Long) {
        val key = "favorite_$photoId"
        val isFav = prefs.getBoolean(key, false)
        prefs.edit { putBoolean(key, !isFav) }
        invalidateCache()
    }

    private fun isFavorite(photoId: Long): Boolean {
        return prefs.getBoolean("favorite_$photoId", false)
    }

    fun toggleHidden(photoId: Long) {
        val key = "hidden_$photoId"
        val isHid = prefs.getBoolean(key, false)
        prefs.edit { putBoolean(key, !isHid) }
        invalidateCache()
    }

    private fun isHidden(photoId: Long): Boolean {
        return prefs.getBoolean("hidden_$photoId", false)
    }

    fun moveToRecycleBin(photoId: Long) {
        prefs.edit {
            putBoolean("recycle_$photoId", true)
            putLong("deleted_at_$photoId", System.currentTimeMillis())
        }
        invalidateCache()
    }

    fun restoreFromRecycleBin(photoId: Long) {
        prefs.edit {
            remove("recycle_$photoId")
            remove("deleted_at_$photoId")
        }
        invalidateCache()
    }

    @SuppressLint("NewApi")
    suspend fun permanentlyDelete(context: Context, photoId: Long, photoUri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(photoUri))
                DeleteResult.NeedsPermission(pi)
            } else {
                try {
                    val deleted = context.contentResolver.delete(photoUri, null, null) > 0
                    if (deleted) {
                        prefs.edit {
                            remove("recycle_$photoId")
                            remove("deleted_at_$photoId")
                        }
                        invalidateCache()
                        Log.d(TAG, "Permanently deleted: $photoUri")
                        DeleteResult.Success
                    } else {
                        DeleteResult.Failure
                    }
                } catch (secEx: SecurityException) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        val recoverable = secEx as? RecoverableSecurityException
                        if (recoverable != null) {
                            DeleteResult.NeedsPermission(recoverable.userAction.actionIntent)
                        } else {
                            Log.e(TAG, "SecurityException deleting $photoUri", secEx)
                            DeleteResult.Failure
                        }
                    } else {
                        Log.e(TAG, "SecurityException deleting $photoUri", secEx)
                        DeleteResult.Failure
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error permanently deleting: $photoUri", e)
            DeleteResult.Failure
        }
    }

    @SuppressLint("NewApi")
    suspend fun bulkPermanentlyDelete(context: Context, photos: List<Photo>): DeleteResult = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext DeleteResult.Success
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, photos.map { it.uri })
                DeleteResult.NeedsPermission(pi)
            } else {
                var anyFailed = false
                photos.forEach { photo ->
                    try {
                        val deleted = context.contentResolver.delete(photo.uri, null, null) > 0
                        if (deleted) {
                            prefs.edit {
                                remove("recycle_${photo.id}")
                                remove("deleted_at_${photo.id}")
                            }
                        } else {
                            anyFailed = true
                        }
                    } catch (e: SecurityException) {
                        anyFailed = true
                        Log.e(TAG, "SecurityException bulk deleting ${photo.uri}", e)
                    }
                }
                invalidateCache()
                if (!anyFailed) DeleteResult.Success else DeleteResult.Failure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error bulk permanently deleting", e)
            DeleteResult.Failure
        }
    }

    private fun isInRecycleBin(photoId: Long): Boolean {
        return prefs.getBoolean("recycle_$photoId", false)
    }

    private fun getDeletedAt(photoId: Long): Long {
        return prefs.getLong("deleted_at_$photoId", 0L)
    }

    suspend fun cleanupExpiredItems() = withContext(Dispatchers.IO) {
        val allMedia = loadAllMedia()
        val currentTime = System.currentTimeMillis()
        val editor = prefs.edit()
        var cleanedCount = 0

        allMedia.filter { it.isInRecycleBin }.forEach { photo ->
            val expiresAt = photo.deletedAt + (30L * 24 * 60 * 60 * 1000)
            if (currentTime >= expiresAt) {
                editor
                    .remove("recycle_${photo.id}")
                    .remove("deleted_at_${photo.id}")
                cleanedCount++
            }
        }

        if (cleanedCount > 0) {
            editor.apply()
            invalidateCache()
            Log.d(TAG, "Cleaned up $cleanedCount expired items")
        }
    }

    fun setHiddenPassword(password: String) {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2Hash(password, salt)
        prefs.edit {
            putString("hidden_password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            putString("hidden_password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            remove("hidden_password")
        }
    }

    fun verifyHiddenPassword(password: String): Boolean {
        val hash = prefs.getString("hidden_password_hash", null)
        val salt = prefs.getString("hidden_password_salt", null)
        if (hash != null && salt != null) {
            val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
            val computed = pbkdf2Hash(password, saltBytes)
            return MessageDigest.isEqual(Base64.decode(hash, Base64.NO_WRAP), computed)
        }
        // Migrate legacy plaintext password on first successful verify
        val legacy = prefs.getString("hidden_password", null) ?: return false
        if (legacy == password) {
            setHiddenPassword(password)
            return true
        }
        return false
    }

    fun hasHiddenPassword(): Boolean =
        prefs.contains("hidden_password_hash") || prefs.contains("hidden_password")

    private fun pbkdf2Hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 600_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    @SuppressLint("NewApi")
    suspend fun renamePhoto(context: Context, photoId: Long, photoUri: Uri, newName: String): WriteResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(photoUri))
                WriteResult.NeedsPermission(pi, PendingWrite.Rename(photoId, photoUri, newName))
            } catch (e: Exception) {
                Log.e(TAG, "Error creating write request for rename $photoId", e)
                WriteResult.Failure
            }
        }
        try {
            applyRenameInternal(context, photoUri, newName)
        } catch (secEx: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val recoverable = secEx as? RecoverableSecurityException
                if (recoverable != null) {
                    return@withContext WriteResult.NeedsPermission(
                        recoverable.userAction.actionIntent,
                        PendingWrite.Rename(photoId, photoUri, newName)
                    )
                }
            }
            Log.e(TAG, "SecurityException renaming $photoUri", secEx)
            WriteResult.Failure
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming photo $photoId", e)
            WriteResult.Failure
        }
    }

    suspend fun applyRename(context: Context, photoUri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            applyRenameInternal(context, photoUri, newName) is WriteResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Error applying rename to $photoUri", e)
            false
        }
    }

    private fun applyRenameInternal(context: Context, photoUri: Uri, newName: String): WriteResult {
        val sanitized = newName.trim().replace(Regex("[/\\\\]"), "")
        if (sanitized.isBlank()) return WriteResult.Failure
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitized)
        }
        val updated = context.contentResolver.update(photoUri, values, null, null) > 0
        return if (updated) {
            invalidateCache()
            WriteResult.Success
        } else WriteResult.Failure
    }

    suspend fun copyPhoto(context: Context, photo: Photo, destFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        copyPhotoInternal(context, photo, destFolderPath) != null
    }

    private fun collectionForPath(relativePath: String, isVideo: Boolean): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return when (relativePath.substringBefore('/').lowercase()) {
            "download", "downloads" ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            "movies" ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else ->
                if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    }

    private fun copyPhotoInternal(context: Context, photo: Photo, destFolderPath: String): Uri? {
        return try {
            val resolver = context.contentResolver
            val externalPrefix = "/storage/emulated/0/"
            val relativePath = if (destFolderPath.startsWith(externalPrefix))
                destFolderPath.removePrefix(externalPrefix) + "/"
            else "$destFolderPath/"

            val collection = collectionForPath(relativePath, photo.isVideo)

            val targetName = uniqueDisplayName(resolver, collection, relativePath, photo.displayName)
            Log.d(TAG, "Copy: src=${photo.uri} relativePath=$relativePath name=$targetName mime=${photo.mimeType}")

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
                put(MediaStore.MediaColumns.MIME_TYPE, photo.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val newUri = try {
                resolver.insert(collection, values)
            } catch (e: Exception) {
                Log.e(TAG, "Copy: insert threw for relativePath=$relativePath name=$targetName", e)
                null
            }
            if (newUri == null) {
                Log.e(TAG, "Copy: insert returned null (collection=$collection, relativePath=$relativePath, name=$targetName)")
                return null
            }

            val streamed = try {
                resolver.openOutputStream(newUri)?.use { out ->
                    resolver.openInputStream(photo.uri)?.use { input ->
                        input.copyTo(out)
                        true
                    } ?: run {
                        Log.e(TAG, "Copy: openInputStream returned null for ${photo.uri}")
                        false
                    }
                } ?: run {
                    Log.e(TAG, "Copy: openOutputStream returned null for $newUri")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Copy: stream copy failed", e)
                false
            }

            if (!streamed) {
                resolver.delete(newUri, null, null)
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(newUri, finalize, null, null)
            } else {
                MediaScannerConnection.scanFile(context, arrayOf(File(destFolderPath, targetName).absolutePath), null) { _, _ -> }
            }
            invalidateCache()
            newUri
        } catch (e: Exception) {
            Log.e(TAG, "Error copying photo ${photo.id}", e)
            null
        }
    }

    private fun uniqueDisplayName(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
        desired: String
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var candidate = desired
        var i = 1
        while (displayNameExists(resolver, collection, relativePath, candidate)) {
            if (i > 999) {
                candidate = "$base (${UUID.randomUUID().toString().take(8)})$ext"
                break
            }
            candidate = "$base ($i)$ext"
            i++
        }
        return candidate
    }

    private fun displayNameExists(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
        name: String
    ): Boolean {
        return try {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(relativePath, name),
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "displayNameExists query failed", e)
            false
        }
    }

    @SuppressLint("NewApi")
    suspend fun movePhoto(context: Context, photo: Photo, destFolderPath: String): WriteResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(photo.uri))
                WriteResult.NeedsPermission(pi, PendingWrite.Move(photo, destFolderPath))
            } catch (e: Exception) {
                Log.e(TAG, "Error creating write request for move ${photo.id}", e)
                WriteResult.Failure
            }
        }
        try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try {
                    applyMoveInternal(context, photo, destFolderPath)
                } catch (secEx: SecurityException) {
                    val recoverable = secEx as? RecoverableSecurityException
                    if (recoverable != null) {
                        WriteResult.NeedsPermission(
                            recoverable.userAction.actionIntent,
                            PendingWrite.Move(photo, destFolderPath)
                        )
                    } else {
                        Log.e(TAG, "SecurityException moving ${photo.uri}", secEx)
                        WriteResult.Failure
                    }
                }
            } else {
                // API ≤ 28: copy + delete fallback
                val copied = copyPhoto(context, photo, destFolderPath)
                if (copied) {
                    val deleted = context.contentResolver.delete(photo.uri, null, null) > 0
                    if (deleted) {
                        prefs.edit {
                            remove("recycle_${photo.id}")
                            remove("deleted_at_${photo.id}")
                            remove("favorite_${photo.id}")
                            remove("hidden_${photo.id}")
                        }
                        invalidateCache()
                        WriteResult.Success
                    } else WriteResult.Failure
                } else WriteResult.Failure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving photo ${photo.id}", e)
            WriteResult.Failure
        }
    }

    suspend fun applyMove(context: Context, photo: Photo, destFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // On R+ the user has granted write/delete access via createWriteRequest, so use
            // copy+delete which is far more reliable than update(RELATIVE_PATH) — the latter
            // silently returns 0 for any destination outside the canonical media dirs.
            val newUri = copyPhotoInternal(context, photo, destFolderPath)
            if (newUri == null) {
                Log.e(TAG, "applyMove: copy step failed for ${photo.uri} -> $destFolderPath")
                return@withContext false
            }
            val deleted = try {
                context.contentResolver.delete(photo.uri, null, null) > 0
            } catch (e: SecurityException) {
                Log.e(TAG, "applyMove: delete denied for ${photo.uri}", e)
                false
            }
            if (!deleted) {
                Log.e(TAG, "applyMove: delete returned 0 for ${photo.uri}; new copy at $newUri left in place")
                return@withContext false
            }
            prefs.edit {
                remove("recycle_${photo.id}")
                remove("deleted_at_${photo.id}")
                remove("favorite_${photo.id}")
                remove("hidden_${photo.id}")
            }
            invalidateCache()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying move to ${photo.uri}", e)
            false
        }
    }

    fun clearPrefsForIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        prefs.edit {
            ids.forEach { id ->
                remove("recycle_$id")
                remove("deleted_at_$id")
                remove("favorite_$id")
                remove("hidden_$id")
            }
        }
        invalidateCache()
    }

    private fun applyMoveInternal(context: Context, photo: Photo, destFolderPath: String): WriteResult {
        val externalPrefix = "/storage/emulated/0/"
        val relativePath = if (destFolderPath.startsWith(externalPrefix)) {
            destFolderPath.removePrefix(externalPrefix) + "/"
        } else {
            "$destFolderPath/"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val updated = context.contentResolver.update(photo.uri, values, null, null) > 0
        return if (updated) {
            invalidateCache()
            WriteResult.Success
        } else WriteResult.Failure
    }

    fun getCaption(photoId: Long): String? = prefs.getString("caption_$photoId", null)

    fun setCaption(photoId: Long, caption: String) {
        prefs.edit { putString("caption_$photoId", caption) }
    }
}