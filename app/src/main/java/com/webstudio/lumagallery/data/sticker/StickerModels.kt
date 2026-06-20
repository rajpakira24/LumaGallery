package com.webstudio.lumagallery.data.sticker

import kotlinx.serialization.Serializable

@Serializable
data class Sticker(val fileName: String, val emojis: List<String> = listOf("✨"))

@Serializable
data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String = "LumaGallery",
    val trayFileName: String = "tray.webp",
    val stickers: List<Sticker> = emptyList(),
)
