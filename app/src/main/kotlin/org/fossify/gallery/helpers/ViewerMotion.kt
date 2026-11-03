package org.fossify.gallery.helpers

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup

/** One interruptible alpha transition for Activity chrome and photo/video overlays. */
internal object ViewerChromeMotion {
    private val generations = java.util.WeakHashMap<View, Long>()

    private fun supersede(view: View): Long {
        val generation = (generations[view] ?: 0L) + 1L
        generations[view] = generation
        view.animate().withEndAction(null).cancel()
        return generation
    }

    fun cancel(view: View) {
        supersede(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) cancel(view.getChildAt(index))
    }

    fun show(view: View, visible: Boolean, duration: Long = OxygenClassicMotion.VIEWER_CHROME_MS) {
        val generation = supersede(view)
        val alpha = if (visible) 1f else 0f
        if (!OxygenClassicMotion.enabled(view.context)) {
            view.alpha = alpha
            view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            return
        }
        if (visible) view.visibility = View.VISIBLE
        view.animate().alpha(alpha).setDuration(duration).setInterpolator(OxygenClassicMotion.easing())
            .withEndAction {
                if (generations[view] == generation) view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            }.start()
    }
}

/** Main-thread controller; delayed feedback never delays the media publication itself. */
internal class ViewerLoadingMotion(private val view: View) {
    private var wanted = false
    private var shownAt = 0L
    private var pending: Runnable? = null
    private var disposed = false
    private var generation = 0L

    fun setLoading(loading: Boolean) {
        if (disposed || wanted == loading) return
        val request = ++generation
        wanted = loading
        pending?.let(view::removeCallbacks)
        pending = null
        view.animate().withEndAction(null).cancel()
        if (!OxygenClassicMotion.enabled(view.context)) {
            view.alpha = if (loading) 1f else 0f
            view.visibility = if (loading) View.VISIBLE else View.GONE
            shownAt = if (loading) SystemClock.uptimeMillis() else 0L
            return
        }
        if (loading && view.visibility == View.VISIBLE) {
            view.animate().alpha(1f).setDuration(OxygenClassicMotion.VIEWER_LOADING_FADE_MS).start()
            return
        }
        val delay = if (loading) OxygenClassicMotion.VIEWER_LOADING_DELAY_MS else
            if (view.visibility == View.VISIBLE)
                (OxygenClassicMotion.VIEWER_LOADING_MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - shownAt)).coerceAtLeast(0L)
            else 0L
        val task = Runnable {
            pending = null
            if (disposed || request != generation || wanted != loading) return@Runnable
            if (loading) {
                shownAt = SystemClock.uptimeMillis()
                view.alpha = 0f
                view.visibility = View.VISIBLE
                GalleryPerfTrace.event("viewer_loading_shown")
            }
            view.animate().alpha(if (loading) 1f else 0f)
                .setDuration(OxygenClassicMotion.VIEWER_LOADING_FADE_MS)
                .setInterpolator(OxygenClassicMotion.easing()).withEndAction {
                    if (!loading && !wanted && request == generation && !disposed) {
                        view.visibility = View.GONE
                        GalleryPerfTrace.event("viewer_loading_hidden")
                    }
                }.start()
        }
        pending = task
        view.postDelayed(task, delay)
    }

    fun dispose() {
        generation++
        disposed = true
        pending?.let(view::removeCallbacks)
        pending = null
        view.animate().withEndAction(null).cancel()
        view.visibility = View.GONE
    }
}
