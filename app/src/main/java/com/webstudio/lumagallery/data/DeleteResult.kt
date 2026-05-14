package com.webstudio.lumagallery.data

import android.app.PendingIntent

sealed class DeleteResult {
    object Success : DeleteResult()
    data class NeedsPermission(val pendingIntent: PendingIntent) : DeleteResult()
    object Failure : DeleteResult()
}
