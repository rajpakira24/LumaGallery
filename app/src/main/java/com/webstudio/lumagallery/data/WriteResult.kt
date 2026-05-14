package com.webstudio.lumagallery.data

import android.app.PendingIntent
import android.net.Uri

sealed class PendingWrite {
    data class Rename(val photoId: Long, val uri: Uri, val newName: String) : PendingWrite()
    data class Move(val photo: Photo, val destFolderPath: String) : PendingWrite()
}

sealed class WriteResult {
    object Success : WriteResult()
    data class NeedsPermission(
        val pendingIntent: PendingIntent,
        val operation: PendingWrite
    ) : WriteResult()
    object Failure : WriteResult()
}
