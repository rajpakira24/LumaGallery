package com.webstudio.lumagallery.ui.util

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

class DragSelectState {
    var isDragging by mutableStateOf(false)
    var dragStartIndex by mutableStateOf(-1)
    var dragCurrentIndex by mutableStateOf(-1)
    val itemBounds = mutableStateMapOf<Int, Rect>()

    fun startDrag(index: Int) {
        isDragging = true
        dragStartIndex = index
        dragCurrentIndex = index
    }

    fun updateDrag(index: Int) {
        if (isDragging) dragCurrentIndex = index
    }

    fun endDrag() {
        isDragging = false
    }

    fun selectedRange(): IntRange {
        if (dragStartIndex < 0 || dragCurrentIndex < 0) return IntRange.EMPTY
        return minOf(dragStartIndex, dragCurrentIndex)..maxOf(dragStartIndex, dragCurrentIndex)
    }

    fun findIndexAt(x: Float, y: Float): Int =
        itemBounds.entries.firstOrNull { (_, rect) -> rect.contains(Offset(x, y)) }?.key ?: -1
}
