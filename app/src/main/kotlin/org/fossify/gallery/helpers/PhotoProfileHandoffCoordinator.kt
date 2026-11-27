package org.fossify.gallery.helpers

/**
 * The single Photos configuration-switch transaction (Phase 2.3 B).
 *
 * A profile selection is observed by three independent entry points: the
 * SharedPreferences listener in the parent Activity (fired synchronously by
 * `PhotoProfileRepository.select()` while the Manage page still covers the grid),
 * the child page's Activity result, and `onResume`. Treating each as a fresh
 * "leave Photos, clear everything, reload" event produced a 150-207 ms window with
 * no adapter at all - the reported white flash.
 *
 * This coordinator separates the two decisions that were previously conflated:
 *
 *  1. *Validity*: access, protection, source directory, filter and sorting must be
 *     unchanged before any old pixel may survive. A revoked source or credential
 *     change always takes the immediate-clear branch; no visual goal overrides it.
 *  2. *Publication*: for a pure profile switch while a child page covers the grid,
 *     the old adapter stays attached until the target's first result is ready, so
 *     `publishMediaSnapshot` replaces its content in one commit instead of showing
 *     a blank adapter between the clear and the reload.
 *
 * The class is pure and deterministic so the branches can be pinned on the host.
 */
internal class PhotoProfileHandoffCoordinator {

    /** Suggested state semantics; the Activity owns the actual resources. */
    enum class Phase {
        CURRENT_GRID,
        CONFIGURATION_COVERED,
        TARGET_SCOPE_VALIDATED,
        TARGET_PREVIEW_PREPARING,
        TARGET_PREVIEW_READY,
        GRID_COMMITTED,
        COMPLETE,
    }

    data class Decision(
        val phase: Phase,
        /** Keep the current adapter/pixels until the target's first commit. */
        val deferDisplayClear: Boolean,
        /** Start the target scope's load now, while the child page still covers us. */
        val startTargetLoad: Boolean,
        /** Another entry point asked to reload an already-owned transaction. */
        val mergeReloadEntry: Boolean,
        val reason: String,
    )

    private var phase: Phase = Phase.CURRENT_GRID
    private var targetKey: String? = null
    private var generation: Long = 0L
    private var mergedEntries: Int = 0

    val currentPhase: Phase get() = phase
    val currentTargetKey: String? get() = targetKey
    val currentGeneration: Long get() = generation
    val mergedEntryCount: Int get() = mergedEntries
    val inFlight: Boolean get() = targetKey != null && phase != Phase.COMPLETE

    /** The Manage/Edit child page covers this grid; it is not a departure from Photos. */
    fun childPageCovered() {
        if (phase == Phase.CURRENT_GRID) phase = Phase.CONFIGURATION_COVERED
    }

    /**
     * A media-source preference changed. Only a pure, still-authorized profile
     * switch under a covering child page may defer the destructive clear.
     */
    fun onScopeChanged(
        covered: Boolean,
        viewerTransition: Boolean,
        accessUnchanged: Boolean,
        targetKey: String,
        displayedKey: String?,
    ): Decision {
        if (!covered || viewerTransition || !accessUnchanged || targetKey == displayedKey ||
            targetKey == "null"
        ) {
            return Decision(phase, false, false, false, "immediate_clear_required")
        }
        generation++
        this.targetKey = targetKey
        phase = Phase.TARGET_PREVIEW_PREPARING
        return Decision(phase, deferDisplayClear = true, startTargetLoad = true,
            mergeReloadEntry = false, reason = "target_preparing")
    }

    /**
     * Activity result / onResume entry for the same target is merged, not executed
     * a second time. A request for a different scope is left to normal admission.
     */
    fun onReloadEntry(source: String, currentKey: String?, targetRunning: Boolean): Decision {
        if (inFlight && currentKey == targetKey && targetRunning) {
            mergedEntries++
            return Decision(phase, true, false, true, "merged_$source")
        }
        return Decision(phase, false, false, false, "independent")
    }

    /** The first target-scope result is being published into the existing adapter. */
    fun onPreviewCommitted(key: String) {
        if (inFlight && key == targetKey) phase = Phase.TARGET_PREVIEW_READY
    }

    fun onGridCommitted(key: String) {
        if (inFlight && key == targetKey && phase != Phase.COMPLETE) phase = Phase.GRID_COMMITTED
    }

    fun complete() {
        phase = Phase.COMPLETE
    }

    /** A new Viewer or a genuine departure retires the transaction. */
    fun reset() {
        phase = Phase.CURRENT_GRID
        targetKey = null
        mergedEntries = 0
    }
}
