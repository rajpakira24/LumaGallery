package com.webstudio.lumagallery.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val dateTaken: Long,
    val size: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val folderPath: String = "",
    val folderName: String = "",
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isInRecycleBin: Boolean = false,
    val deletedAt: Long = 0L,
    val duration: Long = 0L
)

data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val photos: List<Photo>,
    val count: Int,
    val coverPhoto: Photo? = photos.firstOrNull(),
    val isSpecialFolder: Boolean = false
)

data class RecycleBinItem(
    val photo: Photo,
    val deletedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
) {
    val daysRemaining: Int
        get() = ((expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
}

data class DateGroup(
    val date: String,
    val displayDate: String,
    val photos: List<Photo>
)