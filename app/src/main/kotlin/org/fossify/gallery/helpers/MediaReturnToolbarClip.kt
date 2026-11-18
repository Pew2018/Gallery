package org.fossify.gallery.helpers

import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.view.View
import kotlin.math.ceil

/** Clips only the returning proxy's drawing. The framework still captures its full bounds. */
internal class MediaReturnToolbarClip(private val proxy: View) {
    private val original = proxy.clipBounds
    private val matrix = Matrix()
    private val point = FloatArray(2)
    private val location = IntArray(2)
    private val bounds = Rect()
    private var applied = false

    fun update(toolbarBottomOnScreen: Int?) {
        if (toolbarBottomOnScreen == null || !proxy.isAttachedToWindow) {
            restore()
            return
        }
        val localTop = localY(toolbarBottomOnScreen).takeIf(Float::isFinite) ?: run {
            restore()
            return
        }
        val top = ceil(localTop).toInt()
        if (top <= (original?.top ?: 0)) {
            restore()
            return
        }
        val bottom = original?.bottom ?: proxy.height
        val clippedTop = top.coerceAtMost(bottom)
        val left = original?.left ?: 0
        val right = original?.right ?: proxy.width
        if (!applied || (bounds.top != clippedTop || bounds.bottom != bottom || bounds.right != right)) {
            bounds.set(left, clippedTop, right, bottom)
            proxy.clipBounds = bounds
            applied = true
        }
    }

    private fun localY(screenY: Int): Float {
        matrix.reset()
        if (Build.VERSION.SDK_INT >= 29) {
            proxy.transformMatrixToGlobal(matrix)
            if (!matrix.invert(matrix)) return Float.NaN
            point[0] = 0f
            point[1] = screenY.toFloat()
            matrix.mapPoints(point)
            return point[1]
        }
        // The return transform has translation and scale only. getLocationOnScreen
        // supplies the transformed origin on older APIs.
        proxy.getLocationOnScreen(location)
        var view: View? = proxy
        while (view != null) {
            matrix.postConcat(view.matrix)
            view = view.parent as? View
        }
        point[0] = 0f
        point[1] = 1f
        matrix.mapVectors(point)
        return (screenY - location[1]) / point[1]
    }

    private fun restore() {
        if (!applied) return
        proxy.clipBounds = original
        applied = false
    }
}
