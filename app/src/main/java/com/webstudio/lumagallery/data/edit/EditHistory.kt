package com.webstudio.lumagallery.data.edit

import android.graphics.Bitmap

/**
 * Bounded undo/redo stack of bitmap snapshots. When the stack overflows, the oldest
 * snapshot is evicted and recycled to bound memory (~10 MB per 2048-px snapshot).
 *
 * Not thread-safe — callers should mutate from a single coroutine.
 */
class EditHistory(private val maxSize: Int = 6) {

    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Record [previous] as the prior state before a new commit. Clears the redo stack. */
    fun pushUndo(previous: Bitmap) {
        if (undo.size >= maxSize) undo.removeLast().safeRecycle()
        undo.addFirst(previous)
        while (redo.isNotEmpty()) redo.removeFirst().safeRecycle()
    }

    /**
     * Walks one step back. Caller passes the [current] bitmap (which becomes the top of redo),
     * receives the previous bitmap back; returns null when no undo available.
     */
    fun popUndo(current: Bitmap): Bitmap? {
        val prev = undo.removeFirstOrNull() ?: return null
        if (redo.size >= maxSize) redo.removeLast().safeRecycle()
        redo.addFirst(current)
        return prev
    }

    /** Inverse of [popUndo]. */
    fun popRedo(current: Bitmap): Bitmap? {
        val next = redo.removeFirstOrNull() ?: return null
        if (undo.size >= maxSize) undo.removeLast().safeRecycle()
        undo.addFirst(current)
        return next
    }

    fun clear() {
        while (undo.isNotEmpty()) undo.removeFirst().safeRecycle()
        while (redo.isNotEmpty()) redo.removeFirst().safeRecycle()
    }

    private fun Bitmap.safeRecycle() {
        if (!isRecycled) recycle()
    }
}
