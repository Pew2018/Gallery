package org.fossify.gallery.helpers

import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** UI-thread arbiter for one viewer visit. The grid owns its own UI endpoint and leases. */
internal class MediaTransitionSessionCoordinator(
    private val observer: ((event: String, token: Token, detail: String) -> Unit)? = null,
) {
    enum class State {
        PREPARING, ENTRY_CAPTURED, ENTERING, ENTRY_FALLBACK, VIEWING, INTERACTIVE_RETURN,
        RETURN_PREPARING, RETURNING, COMPLETE, CANCELLED, FALLBACK, CLEANED
    }

    enum class Resource {
        SOURCE, SOURCE_NAME, TARGET, TARGET_NAME, PUBLICATION, PROXY, RENDERER_HANDOFF, CHROME, INPUT, CALLBACKS,
        TARGET_PROBE, GRID_PREVIEW, RETURN_TOOLBAR_CLIP, FIRST_FRAME, TARGET_DRAW, TIMEOUT, INTERACTIVE_ANIMATOR, WINDOW_POSTPONE
    }

    interface GridPreview {
        fun show(token: Token, path: String)
        fun release(token: Token)
        fun entryFinished(token: Token) = Unit
        fun photoReturnTarget(token: Token, path: String): StaticPhotoReturnTarget? = null
        fun toolbarBottomOnScreen(token: Token): Int? = null
        fun photoTargetIsCropped(token: Token, path: String): Boolean = false
    }

    data class Token(val sessionId: String, val generation: Long, val operationId: Long)

    private fun token(operationId: Long) = Token(id, generation, operationId)

    private val generation = generations.incrementAndGet()
    private val id = UUID.randomUUID().toString()
    val entry = token(1)
    val entryGate = PhotoTransitionCompletionGate()
    var state = State.PREPARING
        private set
    var outcome: State? = null
        private set
    var returnOperation: Token? = null
        private set
    var gridAttached = false
        private set
    var viewerAttached = false
        private set
    var resultAccepted = false
        private set
    private var expectedResult = entry
    private var reenterAccepted = false
    var viewerMotionOutcome: State? = null
        private set
    private var operation = 1L
    private var leaseCount = 0
    private val current get() = returnOperation ?: entry

    init { emit("session_created", entry, state.name) }

    private var gridPreview: WeakReference<GridPreview>? = null

    private var staticPhotoSource: StaticPhotoSource? = null
    private var staticPhotoSourceConfirmed = false

    fun publishStaticPhotoSource(token: Token, source: StaticPhotoSource) {
        if (valid(token, State.PREPARING) && staticPhotoSource == null) staticPhotoSource = source
    }

    fun readStaticPhotoSource(token: Token, requireConfirmed: Boolean = false): StaticPhotoSource? =
        staticPhotoSource.takeIf { token == entry && entryActive && (!requireConfirmed || staticPhotoSourceConfirmed) }

    fun confirmStaticPhotoSource(token: Token, actual: StaticPhotoSource?) {
        if (token != entry || !entryActive) return
        val offered = staticPhotoSource
        if (actual == null || offered == null || actual.adapterVersion < offered.adapterVersion || !offered.near(actual)) {
            staticPhotoSource = null
            staticPhotoSourceConfirmed = false
        } else {
            // Progressive publication may advance the adapter without rebinding or moving the tapped pixels.
            staticPhotoSource = actual
            staticPhotoSourceConfirmed = true
        }
    }

    fun attachGrid(preview: GridPreview? = null) {
        gridAttached = true
        gridPreview = preview?.let { WeakReference(it) }
    }

    fun detachGrid() {
        returnOperation?.let(::releaseGridPreview)
        gridPreview = null
        gridAttached = false
    }

    fun showGridPreview(token: Token, path: String) {
        if (valid(token, State.INTERACTIVE_RETURN)) gridPreview?.get()?.show(token, path)
    }

    fun gridToolbarBottomOnScreen(token: Token): Int? {
        if (!valid(token, State.INTERACTIVE_RETURN, State.RETURN_PREPARING, State.RETURNING)) return null
        return gridPreview?.get()?.toolbarBottomOnScreen(token)
    }

    fun photoTargetIsCropped(token: Token, path: String): Boolean =
        valid(token, State.RETURN_PREPARING, State.RETURNING) &&
            gridPreview?.get()?.photoTargetIsCropped(token, path) == true

    fun photoReturnTarget(token: Token, path: String): StaticPhotoReturnTarget? =
        if (valid(token, State.RETURNING)) gridPreview?.get()?.photoReturnTarget(token, path) else null

    fun gridPreviewEvent(token: Token, detail: String) {
        emit("grid_interactive_preview", token, detail)
    }

    fun releaseGridPreview(token: Token) {
        if (token == returnOperation) gridPreview?.get()?.release(token)
    }

    fun attachViewer(): Boolean {
        if (outcome != null || viewerAttached || state != State.PREPARING) return false
        viewerAttached = true
        return true
    }

    val entryActive get() = state in ENTRY_STATES

    fun capture(token: Token) = update(token, State.ENTRY_CAPTURED, State.PREPARING)
    fun beginMotion(token: Token) = update(token, State.ENTERING, State.ENTRY_CAPTURED)

    fun entryFallback(token: Token, reason: String): Boolean {
        if (!update(token, State.ENTRY_FALLBACK, State.PREPARING, State.ENTRY_CAPTURED, State.ENTERING)) return false
        emit("transition_fallback", token, reason)
        return true
    }

    fun entryFinished(token: Token, withoutSharedEntry: Boolean = false) =
        if (withoutSharedEntry) update(token, State.VIEWING, State.PREPARING)
        else update(token, State.VIEWING, State.ENTERING, State.ENTRY_FALLBACK)

    fun beginGesture(): Token? {
        if (state != State.VIEWING) return null
        return token(++operation).also {
            returnOperation = it
            change(State.INTERACTIVE_RETURN, it)
        }
    }

    fun cancelGesture(token: Token): Boolean {
        if (!update(token, State.VIEWING, State.INTERACTIVE_RETURN)) return false
        releaseGridPreview(token)
        returnOperation = null
        return true
    }

    fun beginReturn(): Token? {
        if (state != State.VIEWING && state != State.INTERACTIVE_RETURN) return null
        return (returnOperation ?: token(++operation)).also {
            returnOperation = it
            expectedResult = it
            change(State.RETURN_PREPARING, it)
        }
    }

    fun isReturning(token: Token) = returnOperation == token &&
        (state == State.RETURN_PREPARING || state == State.RETURNING)

    fun acceptReenter(token: Token): Boolean {
        if (reenterAccepted || resultAccepted || !isReturning(token)) return rejected(token, "reenter")
        reenterAccepted = true
        return true
    }

    fun finishViewerMotion(token: Token, cancelled: Boolean): Boolean {
        if (!isReturning(token) || viewerMotionOutcome != null) return rejected(token, "viewer_motion_end")
        viewerMotionOutcome = if (cancelled) State.CANCELLED else State.COMPLETE
        return true
    }

    /**
     * After a cancelled shared-element return that leaves the Viewer open, drop the
     * return lease and restore VIEWING so a later back can close normally.
     */
    fun abortCancelledReturn(token: Token): Boolean {
        if (outcome != null || returnOperation != token || viewerMotionOutcome != State.CANCELLED) {
            return rejected(token, "abort_cancelled_return")
        }
        if (state != State.RETURN_PREPARING && state != State.RETURNING) {
            return rejected(token, "abort_cancelled_return")
        }
        releaseGridPreview(token)
        returnOperation = null
        viewerMotionOutcome = null
        change(State.VIEWING, token)
        return true
    }

    fun targetReady(token: Token) = update(token, State.RETURNING, State.RETURN_PREPARING)

    fun acceptResult(token: Token): Boolean {
        // Android can deliver the result after the platform animation or a local fallback.
        if (resultAccepted || expectedResult != token ||
            (token == entry && outcome != State.CANCELLED) || (outcome == null && !isReturning(token))) {
            return rejected(token, "result")
        }
        resultAccepted = true
        return true
    }

    fun acceptFrameworkCancellation(): Boolean {
        // A launch that fails before the Viewer takes its ticket can produce RESULT_CANCELED
        // without an Intent. Once claimed, the Viewer always installs a stamped result.
        if (viewerAttached || resultAccepted || state != State.PREPARING) return false
        cancel("framework_cancelled")
        return acceptResult(entry)
    }

    fun terminate(token: Token, terminal: State, reason: String): Boolean {
        val permitted = when (terminal) {
            State.COMPLETE -> state == State.RETURNING
            State.FALLBACK -> state == State.RETURN_PREPARING || state == State.RETURNING
            State.CANCELLED -> true
            else -> false
        }
        if (outcome != null || current != token || !permitted) return rejected(token, "terminal")
        entryGate.cancel()
        outcome = terminal
        change(terminal, token)
        releaseGridPreview(token)
        if (terminal == State.FALLBACK) emit("transition_fallback", token, reason)
        emit("transition_terminal", token, "${terminal.name}:$reason")
        return true
    }

    fun cancel(reason: String) = terminate(current, State.CANCELLED, reason)

    fun clean(): Boolean {
        if (outcome == null || leaseCount != 0 || state == State.CLEANED) return false
        change(State.CLEANED, current)
        emit("transition_cleanup_completed", current, requireNotNull(outcome).name)
        return true
    }

    fun leases() = Leases()

    /** Activity-owned cleanup actions. The coordinator only counts and admits leases. */
    inner class Leases {
        private val owned = linkedMapOf<Resource, Pair<Token, () -> Unit>>()

        fun acquire(token: Token, resource: Resource, release: () -> Unit): Boolean {
            val active = if (token == entry) entryActive else state == State.INTERACTIVE_RETURN || isReturning(token)
            if (!active || current != token || resource in owned) return rejected(token, resource.name)
            owned[resource] = token to release
            leaseCount++
            emit("transition_lease_acquired", token, resource.name)
            return true
        }

        fun release(token: Token, resource: Resource): Boolean {
            if (owned[resource]?.first != token) return false
            val (_, action) = requireNotNull(owned.remove(resource))
            try {
                action()
            } finally {
                leaseCount--
                emit("transition_lease_released", token, resource.name)
            }
            return true
        }

        fun releaseAll() {
            owned.entries.toList().asReversed().forEach { (kind, lease) -> release(lease.first, kind) }
        }
    }

    private fun update(token: Token, to: State, vararg from: State): Boolean {
        if (!valid(token, *from)) return rejected(token, to.name)
        change(to, token)
        return true
    }

    private fun valid(token: Token, vararg from: State) = outcome == null && token == current && state in from

    private fun change(to: State, token: Token) {
        val from = state
        val wasEntry = entryActive
        state = to
        if (wasEntry && !entryActive) gridPreview?.get()?.entryFinished(entry)
        if (!entryActive) {
            staticPhotoSource = null
            staticPhotoSourceConfirmed = false
        }
        if (observer != null) emit("transition_state_changed", token, "${from.name}->${to.name}")
    }

    private fun rejected(token: Token, detail: String): Boolean {
        emit("stale_transition_callback_ignored", token, detail)
        return false
    }

    private fun emit(event: String, token: Token, detail: String) {
        observer?.invoke(event, token, detail)
    }

    private companion object {
        val generations = AtomicLong()
        val ENTRY_STATES = setOf(State.PREPARING, State.ENTRY_CAPTURED, State.ENTERING, State.ENTRY_FALLBACK)
    }
}
