package org.fossify.gallery.helpers

/**
 * UI-thread-only, last-result-wins handoff for a user-controlled ViewPager swipe.
 *
 * Heavy adapter/Fragment publications are held until the pager is idle. The gate
 * never authorizes media or delays capability revocation; callers clear it when
 * an access boundary changes and still check revisions before applying output.
 */
internal class ViewerSwipePublicationGate<T> {
    private var moving = false
    private var latest: T? = null

    val isMoving: Boolean get() = moving
    val hasPending: Boolean get() = latest != null

    fun deferIfMoving(value: T): Boolean {
        if (!moving) return false
        latest = value
        return true
    }

    fun onScrollMoving(isMoving: Boolean): T? {
        moving = isMoving
        if (moving) return null
        val ready = latest
        latest = null
        return ready
    }

    fun clear() {
        moving = false
        latest = null
    }
}
