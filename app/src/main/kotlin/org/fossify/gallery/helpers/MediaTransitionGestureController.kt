package org.fossify.gallery.helpers

import kotlin.math.abs

/** Decides when a single-pointer vertical drag owns photo-viewer input. */
internal class MediaTransitionGestureController(
    private val touchSlop: Float,
    private val dismissDistance: Float,
    private val dismissVelocity: Float = 1_200f,
    private val verticalBias: Float = 1.2f,
) {
    data class Frame(val progress: Float, val deltaX: Float, val deltaY: Float, val began: Boolean)

    enum class Release { DISMISS, RESTORE, IGNORE }

    private var pointerId = INVALID_POINTER
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var rejected = false
    private var progress = 0f
    private var baseProgress = 0f
    private var allowUpwardStart = false

    fun down(pointerId: Int, x: Float, y: Float, eligible: Boolean, baseProgress: Float = 0f) {
        reset()
        if (!eligible) {
            rejected = true
            return
        }
        this.pointerId = pointerId
        startX = x
        startY = y
        this.baseProgress = baseProgress.coerceIn(0f, 1f)
        progress = this.baseProgress
        allowUpwardStart = this.baseProgress > 0f
    }

    fun move(pointerId: Int, x: Float, y: Float): Frame? {
        if (rejected || pointerId != this.pointerId || this.pointerId == INVALID_POINTER) return null
        val dx = x - startX
        val dy = y - startY
        val began: Boolean
        if (!dragging) {
            if (abs(dx) > touchSlop && abs(dx) > abs(dy) * verticalBias) {
                rejected = true
                return null
            }
            if (abs(dy) <= touchSlop || abs(dy) <= abs(dx) * verticalBias) return null
            if (dy < 0f && !allowUpwardStart) return null
            dragging = true
            began = true
        } else {
            began = false
        }
        progress = (baseProgress + dy / dismissDistance).coerceIn(0f, 1f)
        return Frame(progress, dx, dy, began)
    }

    fun pointerChanged(): Release {
        val wasDragging = dragging
        reset()
        return if (wasDragging) Release.RESTORE else Release.IGNORE
    }

    fun release(pointerId: Int, velocityY: Float): Release {
        if (pointerId != this.pointerId || this.pointerId == INVALID_POINTER) return Release.IGNORE
        val wasDragging = dragging
        val shouldDismiss = wasDragging && (progress >= DISMISS_PROGRESS || velocityY >= dismissVelocity)
        reset()
        return when {
            shouldDismiss -> Release.DISMISS
            wasDragging -> Release.RESTORE
            else -> Release.IGNORE
        }
    }

    fun cancel(): Release {
        val wasDragging = dragging
        reset()
        return if (wasDragging) Release.RESTORE else Release.IGNORE
    }

    fun isDragging() = dragging

    private fun reset() {
        pointerId = INVALID_POINTER
        dragging = false
        rejected = false
        progress = 0f
        baseProgress = 0f
        allowUpwardStart = false
    }

    private companion object {
        const val INVALID_POINTER = -1
        const val DISMISS_PROGRESS = 0.35f
    }
}
