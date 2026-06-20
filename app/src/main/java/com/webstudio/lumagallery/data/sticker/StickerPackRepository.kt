package com.webstudio.lumagallery.data.sticker

import android.content.Context
import android.graphics.Bitmap
import com.webstudio.lumagallery.data.edit.BitmapEngine
import com.webstudio.lumagallery.data.edit.StickerIO
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class StickerPackRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val indexFile get() = File(context.filesDir, "sticker_packs.json")
    private fun packDir(packId: String) = File(File(context.filesDir, "stickers"), packId)

    @Synchronized
    fun loadPacks(): List<StickerPack> =
        if (indexFile.exists()) runCatching { json.decodeFromString<List<StickerPack>>(indexFile.readText()) }.getOrDefault(emptyList())
        else emptyList()

    @Synchronized
    private fun savePacks(packs: List<StickerPack>) { indexFile.writeText(json.encodeToString(packs)) }

    @Synchronized
    fun createPack(name: String): StickerPack {
        val pack = StickerPack(identifier = UUID.randomUUID().toString(), name = name.take(128).ifBlank { "My Pack" })
        savePacks(loadPacks() + pack)
        packDir(pack.identifier).mkdirs()
        return pack
    }

    @Synchronized
    fun renamePack(packId: String, newName: String) {
        savePacks(loadPacks().map { if (it.identifier == packId) it.copy(name = newName.take(128)) else it })
    }

    @Synchronized
    fun deletePack(packId: String) {
        packDir(packId).deleteRecursively()
        savePacks(loadPacks().filterNot { it.identifier == packId })
    }

    /** Square-pads [bitmap] to 512, encodes WebP, stores it, makes it the tray if first. */
    @Synchronized
    fun addSticker(packId: String, bitmap: Bitmap, emojis: List<String> = listOf("✨")) {
        val dir = packDir(packId).apply { mkdirs() }
        val square = BitmapEngine.squarePad(bitmap, StickerIO.STICKER_SIZE)
        val fileName = "${UUID.randomUUID()}.webp"
        StickerIO.writeWebp(File(dir, fileName), square, StickerIO.STICKER_MAX_BYTES)

        val packs = loadPacks().toMutableList()
        val idx = packs.indexOfFirst { it.identifier == packId }
        if (idx < 0) return
        var pack = packs[idx]
        // first sticker -> also write tray icon
        if (pack.stickers.isEmpty()) {
            val tray = BitmapEngine.squarePad(bitmap, StickerIO.TRAY_SIZE)
            StickerIO.writeWebp(File(dir, pack.trayFileName), tray, StickerIO.TRAY_MAX_BYTES)
        }
        pack = pack.copy(stickers = pack.stickers + Sticker(fileName, emojis))
        packs[idx] = pack
        savePacks(packs)
    }

    @Synchronized
    fun removeSticker(packId: String, fileName: String) {
        File(packDir(packId), fileName).delete()
        savePacks(loadPacks().map {
            if (it.identifier == packId) it.copy(stickers = it.stickers.filterNot { s -> s.fileName == fileName }) else it
        })
    }

    /** File backing a stored asset (sticker webp or tray) — used by the ContentProvider. */
    fun assetFile(packId: String, fileName: String): File = File(packDir(packId), fileName)
}
