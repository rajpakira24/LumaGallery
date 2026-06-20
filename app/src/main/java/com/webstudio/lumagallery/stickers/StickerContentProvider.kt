package com.webstudio.lumagallery.stickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.webstudio.lumagallery.BuildConfig
import com.webstudio.lumagallery.data.sticker.StickerPack
import com.webstudio.lumagallery.data.sticker.StickerPackRepository
import java.io.File

class StickerContentProvider : ContentProvider() {
    private lateinit var repo: StickerPackRepository

    private val authority get() = "${BuildConfig.APPLICATION_ID}.stickercontentprovider"
    private val matcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "metadata", METADATA)
            addURI(authority, "metadata/*", METADATA_SINGLE)
            addURI(authority, "stickers/*", STICKERS)
            addURI(authority, "stickers_asset/*/*", STICKERS_ASSET)
        }
    }

    override fun onCreate(): Boolean {
        repo = StickerPackRepository(context!!)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sortOrder: String?): Cursor? {
        return when (matcher.match(uri)) {
            METADATA -> metadataCursor(repo.loadPacks(), uri)
            METADATA_SINGLE -> metadataCursor(repo.loadPacks().filter { it.identifier == uri.lastPathSegment }, uri)
            STICKERS -> stickersCursor(uri)
            else -> null
        }
    }

    private fun metadataCursor(packs: List<StickerPack>, uri: Uri): Cursor {
        val cols = arrayOf(
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "avoid_cache", "animated_sticker_pack",
        )
        val c = MatrixCursor(cols)
        for (p in packs) {
            c.addRow(arrayOf<Any?>(
                p.identifier, p.name, p.publisher,
                p.trayFileName, "", "",
                "", "", "", "",
                "1", 0, 0,
            ))
        }
        c.setNotificationUri(context!!.contentResolver, uri)
        return c
    }

    private fun stickersCursor(uri: Uri): Cursor {
        val packId = uri.lastPathSegment
        val pack = repo.loadPacks().firstOrNull { it.identifier == packId }
        val cols = arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")
        val c = MatrixCursor(cols)
        pack?.stickers?.forEach { s ->
            c.addRow(arrayOf<Any?>(s.fileName, s.emojis.joinToString(","), ""))
        }
        c.setNotificationUri(context!!.contentResolver, uri)
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != STICKERS_ASSET) return null
        val segments = uri.pathSegments // [stickers_asset, <packId>, <fileName>]
        if (segments.size != 3) return null
        val file: File = repo.assetFile(segments[1], segments[2])
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        METADATA, METADATA_SINGLE -> "vnd.android.cursor.dir/vnd.${authority}.metadata"
        STICKERS -> "vnd.android.cursor.dir/vnd.${authority}.stickers"
        STICKERS_ASSET -> "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

    companion object {
        private const val METADATA = 1
        private const val METADATA_SINGLE = 2
        private const val STICKERS = 3
        private const val STICKERS_ASSET = 4
    }
}
