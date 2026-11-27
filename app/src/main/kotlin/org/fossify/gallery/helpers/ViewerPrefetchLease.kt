package org.fossify.gallery.helpers

import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget

/**
 * Owns exactly one Viewer return-prefetch request and, crucially, the *time* at which
 * its Glide target may be cleared (Phase 2.3 A).
 *
 * Glide 5.0.7's `SingleRequest.onResourceReady` / `onLoadFailed` set
 * `isCallingCallbacks = true` around the user's callbacks and `SingleRequest.clear()`
 * calls `assertNotCallingCallbacks()` before doing anything else. Calling
 * `Glide.with(...).clear(target)` synchronously from `CustomTarget.onResourceReady`
 * therefore throws `IllegalStateException`. The 2026-11-25 device log records five
 * crashes whose last complete stack ends in exactly that call chain.
 *
 * This class keeps the callback body strictly read/copy/schedule:
 *  1. reject a callback that does not belong to this lease (path/key alone cannot
 *     distinguish an old A request from a new A request after A -> B -> A),
 *  2. copy the pixels into the bounded reserve *before* anything can recycle them,
 *  3. capture resource diagnostics while the resource is still alive,
 *  4. publish a single terminal outcome to the caller,
 *  5. post the `clear(target)` to a scheduler that is independent of animation
 *     handlers, so it runs only after Glide's callback guard has been released and
 *     cannot be dropped by an unrelated `removeCallbacksAndMessages(null)`.
 *
 * Every instance has exactly one terminal state. Late callbacks from the same
 * target are counted but cannot mutate that state or touch a newer lease.
 */
internal class ViewerPrefetchLease(
    val generation: Long,
    val viewerSessionId: Long,
    val requestId: Long,
    val path: String,
    val key: Any,
    private val owner: Any,
    val target: CustomTarget<Drawable>,
    private val reserve: ViewerReturnPixelReserve,
    private val scheduler: (Runnable) -> Unit,
    private val clearer: (CustomTarget<Drawable>) -> Unit,
    private val onResolved: (lease: ViewerPrefetchLease, outcome: Outcome, stored: Boolean) -> Unit,
) {
    /** The single terminal outcome of this request. */
    enum class Outcome { READY, FAILED, CANCELLED }

    private var terminal: Outcome? = null

    val outcome: Outcome? get() = terminal
    val isSettled: Boolean get() = terminal != null

    /** True when the reserve accepted a defensive pixel copy. */
    var storedPixels: Boolean = false
        private set

    /** Resource fields captured inside the callback, before any deferred release. */
    var drawableType: String? = null
        private set
    var intrinsicSize: String? = null
        private set

    /** Why a cancellation happened; null unless [Outcome.CANCELLED]. */
    var cancelReason: String? = null
        private set

    /** Diagnostics: callbacks arriving after the terminal decision are ignored. */
    var staleCallbacks: Int = 0
        private set

    var releaseScheduled: Boolean = false
        private set
    var releaseExecuted: Boolean = false
        private set

    /**
     * Full request identity. `generation` changes for every request (including a
     * second request for the same path), `viewerSessionId` for every Viewer session,
     * and the target instance ties the lease to the one Glide request it owns.
     */
    fun matches(
        generation: Long,
        viewerSessionId: Long,
        path: String,
        key: Any,
        target: CustomTarget<Drawable>,
    ): Boolean = this.generation == generation &&
        this.viewerSessionId == viewerSessionId &&
        this.path == path &&
        this.key == key &&
        this.target === target

    /**
     * Called from `CustomTarget.onResourceReady`. The resource is still owned by
     * Glide here; copy it and record its fields, then defer the release.
     */
    fun ready(resource: Drawable) {
        if (terminal != null) {
            staleCallbacks++
            return
        }
        drawableType = resource.javaClass.simpleName
        intrinsicSize = "${resource.intrinsicWidth}x${resource.intrinsicHeight}"
        // The reserve makes its own copy; no reference to the Glide resource escapes.
        storedPixels = reserve.remember(key, resource, owner)
        settle(Outcome.READY, null)
    }

    /** Called from `CustomTarget.onLoadFailed`. */
    fun failed() {
        if (terminal != null) {
            staleCallbacks++
            return
        }
        settle(Outcome.FAILED, null)
    }

    /**
     * Called for page changes, supersede, Viewer exit and Activity teardown.
     * Even though these run on the main thread outside a Glide callback, the clear
     * is still posted: one uniform path cannot accidentally re-enter a callback
     * (e.g. a cancellation issued from inside another target's callback).
     */
    fun cancel(reason: String) {
        if (terminal != null) {
            staleCallbacks++
            return
        }
        settle(Outcome.CANCELLED, reason)
    }

    private fun settle(outcome: Outcome, reason: String?) {
        terminal = outcome
        cancelReason = reason
        // onResolved must never be able to strand the release: even a diagnostic
        // failure still has to hand the target back to Glide.
        try {
            onResolved(this, outcome, storedPixels)
        } finally {
            scheduleRelease()
        }
    }

    /**
     * Posts the only legal place a Glide request for this lease may be cleared.
     * The posted runnable clears *this* target only, and is idempotent.
     */
    fun scheduleRelease() {
        if (releaseScheduled) return
        releaseScheduled = true
        scheduler(Runnable {
            if (releaseExecuted) return@Runnable
            releaseExecuted = true
            clearer(target)
        })
    }
}
