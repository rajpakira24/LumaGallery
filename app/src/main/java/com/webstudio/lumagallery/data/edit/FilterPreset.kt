package com.webstudio.lumagallery.data.edit

import android.graphics.ColorMatrix

enum class FilterPreset(
    val displayName: String,
    private val build: () -> ColorMatrix
) {
    Original("Original", { ColorMatrix() }),

    BlackAndWhite("B&W", { ColorMatrix().apply { setSaturation(0f) } }),

    Sepia("Sepia", {
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }),

    Vivid("Vivid", {
        ColorMatrix().apply {
            setSaturation(1.6f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, -10f,
                        0f, 1.1f, 0f, 0f, -10f,
                        0f, 0f, 1.1f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }),

    Cool("Cool", {
        ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }),

    Warm("Warm", {
        ColorMatrix(
            floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.05f, 0f, 0f, 0f,
                0f, 0f, 0.85f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }),

    Vintage("Vintage", {
        ColorMatrix(
            floatArrayOf(
                0.8f, 0.2f, 0.1f, 0f, 20f,
                0.1f, 0.85f, 0.1f, 0f, 10f,
                0.1f, 0.2f, 0.7f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }),

    Fade("Fade", {
        ColorMatrix().apply {
            setSaturation(0.7f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0f, 0f, 0f, 18f,
                        0f, 0.95f, 0f, 0f, 18f,
                        0f, 0f, 0.95f, 0f, 18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    });

    fun matrix(): ColorMatrix = build()
}
