package com.webstudio.lumagallery.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Gallery : Screen("gallery")

    object CategoryDetail : Screen("category_detail/{categoryName}") {
        fun createRoute(categoryName: String): String {
            // URL encode the category name to handle special characters like /
            val encoded = Uri.encode(categoryName)
            return "category_detail/$encoded"
        }
    }

    object PhotoDetail : Screen("photo_detail/{photoId}") {
        fun createRoute(photoId: Long) = "photo_detail/$photoId"
    }

    object RecycleBin : Screen("recycle_bin")

    object HiddenCollection : Screen("hidden_collection")

    object EditPhoto : Screen("edit_photo/{photoId}") {
        fun createRoute(photoId: Long) = "edit_photo/$photoId"
    }

    object StickerPacks : Screen("sticker_packs")
}