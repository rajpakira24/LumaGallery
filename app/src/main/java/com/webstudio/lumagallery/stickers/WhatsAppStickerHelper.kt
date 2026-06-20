package com.webstudio.lumagallery.stickers

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.webstudio.lumagallery.BuildConfig
import com.webstudio.lumagallery.data.sticker.StickerPack

object WhatsAppStickerHelper {
    private const val AUTHORITY_SUFFIX = ".stickercontentprovider"
    private const val ADD_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

    /** WhatsApp requires 3..30 stickers. Returns null if ok, else an error message. */
    fun validate(pack: StickerPack): String? = when {
        pack.stickers.size < 3 -> "Add ${3 - pack.stickers.size} more sticker(s) — WhatsApp needs at least 3."
        pack.stickers.size > 30 -> "Too many stickers (max 30)."
        else -> null
    }

    fun addToWhatsApp(activity: Activity, pack: StickerPack) {
        validate(pack)?.let { Toast.makeText(activity, it, Toast.LENGTH_LONG).show(); return }
        val intent = Intent(ADD_ACTION).apply {
            putExtra("sticker_pack_id", pack.identifier)
            putExtra("sticker_pack_authority", "${BuildConfig.APPLICATION_ID}$AUTHORITY_SUFFIX")
            putExtra("sticker_pack_name", pack.name)
        }
        try {
            activity.startActivityForResult(intent, 200)
        } catch (e: Exception) {
            Toast.makeText(activity, "WhatsApp not installed or too old.", Toast.LENGTH_LONG).show()
        }
    }
}
