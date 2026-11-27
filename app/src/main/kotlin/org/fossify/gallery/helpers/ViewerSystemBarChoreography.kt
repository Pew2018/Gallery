package org.fossify.gallery.helpers

/**
 * Explicit ownership of the Viewer window's status and navigation surfaces.
 *
 * The Viewer used to freeze its bar appearance for the whole committed return
 * (`viewerSystemBarHold`). That removed the write race, but it froze the surface at
 * whatever blend progress the finger released on - the exported diagnostics recorded
 * `heldProgress=0.49627578`, `0.9895514`, `0.41481012`, ... - and the band then jumped
 * to the Grid's colour when the window was removed. Ownership and *visual progress*
 * were the same boolean, which is what this class separates.
 *
 * Phases:
 *  - [ViewerBarOwner.PAGE] the Viewer window no longer owns anything (window is gone).
 *  - [ViewerBarOwner.VIEWER_ENTERING] the entry clock blends page colour -> viewer
 *    surface; only the entry frames may write.
 *  - [ViewerBarOwner.VIEWER] steady browsing at the viewer surface.
 *  - [ViewerBarOwner.INTERACTIVE_RETURN] a drag / predictive gesture drives the blend.
 *  - [ViewerBarOwner.RETURN_WAIT_TARGET] a return is committed but the Grid has not
 *    produced a drawable target yet. The release frame is held *on purpose*: nothing
 *    is moving on screen, so the bars must not move either.
 *  - [ViewerBarOwner.RETURN_SETTLING] the return clock is running. The bars continue
 *    from the held frame to the page surface on the same remainder the scrim uses, so
 *    the handover is a continuation instead of a jump.
 *  - [ViewerBarOwner.PAGE_HANDOFF] the final page surface has been written once; no
 *    later frame may write again.
 *  - [ViewerBarOwner.CANCEL_RESTORE] a cancelled return hands the surfaces back to the
 *    Viewer at its steady appearance.
 *
 * Every return owns a monotonically increasing token. A terminal or progress frame
 * from an older operation (a superseded gesture, a lifecycle callback that arrives
 * after a new return started) is rejected instead of overwriting the current owner.
 *
 * Pure Kotlin so the contract is testable without Robolectric.
 */
internal enum class ViewerBarOwner {
    PAGE,
    VIEWER_ENTERING,
    VIEWER,
    INTERACTIVE_RETURN,
    RETURN_WAIT_TARGET,
    RETURN_SETTLING,
    PAGE_HANDOFF,
    CANCEL_RESTORE,
}

internal class ViewerSystemBarChoreography {
    var owner: ViewerBarOwner = ViewerBarOwner.VIEWER
        private set

    /** Token of the return that currently owns the surfaces; 0 when none is running. */
    var returnToken: Long = 0L
        private set

    /** Blend progress this window last considered to be on screen. */
    var settledProgress: Float = 1f
        private set

    /** Progress the committed return was released on. */
    var releaseProgress: Float = 1f
        private set

    fun startEntry() {
        returnToken++
        owner = ViewerBarOwner.VIEWER_ENTERING
        settledProgress = 0f
    }

    fun settleViewer() {
        if (owner == ViewerBarOwner.VIEWER_ENTERING ||
            owner == ViewerBarOwner.CANCEL_RESTORE ||
            owner == ViewerBarOwner.INTERACTIVE_RETURN) {
            owner = ViewerBarOwner.VIEWER
            settledProgress = 1f
        }
    }

    /** A drag or predictive gesture took the bars; it may write from now on. */
    fun startInteractive() {
        if (owner == ViewerBarOwner.VIEWER || owner == ViewerBarOwner.VIEWER_ENTERING) {
            owner = ViewerBarOwner.INTERACTIVE_RETURN
        }
    }

    /** A cancelled gesture put the surface back at the viewer appearance. */
    fun cancelInteractive() {
        if (owner == ViewerBarOwner.INTERACTIVE_RETURN) {
            owner = ViewerBarOwner.VIEWER
            settledProgress = 1f
        }
    }

    /**
     * Commits a return at [progress] (the blend actually on screen when the user let
     * go) and takes the bars away from every other writer. Returns the return token
     * that later frames have to present.
     */
    fun beginReturn(progress: Float): Long {
        returnToken++
        releaseProgress = progress.coerceIn(0f, 1f)
        settledProgress = releaseProgress
        owner = ViewerBarOwner.RETURN_WAIT_TARGET
        return returnToken
    }

    /**
     * Bar progress for a plain Viewer canvas frame (entry, gesture, cancel restore).
     * Null means "this frame must not write": either the window is already handed over
     * or the committed return's own layers are the only legal writer.
     */
    fun canvasBarProgress(progress: Float): Float? = when (owner) {
        ViewerBarOwner.PAGE,
        ViewerBarOwner.PAGE_HANDOFF,
        ViewerBarOwner.RETURN_WAIT_TARGET,
        ViewerBarOwner.RETURN_SETTLING -> null
        else -> progress.coerceIn(0f, 1f)
    }

    /**
     * Bar progress for a shared-element return frame. [remaining] is the same 1 -> 0
     * remainder the scrim and the chrome use, so the surface reaches the page colour
     * exactly on the frame the Viewer content is gone.
     */
    fun returnBarProgress(token: Long, remaining: Float): Float? {
        if (token != returnToken) return null
        if (owner != ViewerBarOwner.RETURN_WAIT_TARGET && owner != ViewerBarOwner.RETURN_SETTLING) return null
        owner = ViewerBarOwner.RETURN_SETTLING
        val value = releaseProgress * remaining.coerceIn(0f, 1f)
        settledProgress = value
        return value
    }

    /**
     * Terminal frame of a return. A cancelled return gives the Viewer its appearance
     * back; a committed one writes the page surface at most once and then stays silent
     * for the rest of the window's life.
     */
    fun terminal(token: Long, cancelled: Boolean): Float? {
        if (token != returnToken) return null
        if (cancelled) {
            owner = ViewerBarOwner.CANCEL_RESTORE
            settledProgress = 1f
            return 1f
        }
        val value = if (settledProgress != 0f) 0f else null
        owner = ViewerBarOwner.PAGE_HANDOFF
        settledProgress = 0f
        return value
    }

    /** The window is going away without a returning transition (fade fallback). */
    fun releaseToPage() {
        returnToken++
        owner = ViewerBarOwner.PAGE_HANDOFF
        settledProgress = 0f
    }

    /** True while a return owns the surfaces, for diagnostics. */
    fun isReturning() = owner == ViewerBarOwner.RETURN_WAIT_TARGET || owner == ViewerBarOwner.RETURN_SETTLING
}
