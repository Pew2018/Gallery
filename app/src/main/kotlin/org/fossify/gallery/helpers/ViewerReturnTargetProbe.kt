package org.fossify.gallery.helpers

/**
 * Why a Grid thumbnail can or cannot serve as the measured destination of an
 * app-owned Viewer exit.
 *
 * The old probe collapsed every failure into `no_target`. The Viewer covers the
 * Grid Activity, so "the ImageView is currently shown by its window" is NOT the
 * same question as "a legal screen rectangle and the same pixels still exist".
 * These reasons keep the two apart and make the covered-parent case explicit.
 */
internal enum class ViewerReturnTargetReason {
    READY,
    NO_POSITION,
    GRID_UNSETTLED,
    HOLDER_UNAVAILABLE,
    PIXELS_UNAVAILABLE,
    BOUNDS_UNAVAILABLE,
    PARENT_WINDOW_HIDDEN,
    TARGET_NOT_VISIBLE,
    IDENTITY_CHANGED,
    SCOPE_CHANGED,
}

/**
 * Facts gathered from the live Grid. Geometry and identity are deliberately
 * separate: a covered parent can still supply a valid rectangle.
 */
internal data class ViewerReturnTargetEvidence(
    val scopeMatches: Boolean,
    val hasPosition: Boolean,
    val gridSettled: Boolean,
    val holderBound: Boolean,
    val pixelsBound: Boolean,
    val identityMatches: Boolean,
    val attached: Boolean,
    val boundsUsable: Boolean,
    val parentWindowVisible: Boolean,
    val targetVisible: Boolean,
    val geometryUsable: Boolean,
)

/**
 * The result of probing one Grid target.
 *
 * [accepted] means the caller may use this geometry for a return visual. A
 * covered parent is accepted because the Viewer has not changed the Grid's
 * layout; the WindowManager simply stopped composing it. [reason] stays
 * [ViewerReturnTargetReason.PARENT_WINDOW_HIDDEN] so diagnostics never claim the
 * thumbnail was visible.
 */
internal data class ViewerReturnTargetDecision(
    val accepted: Boolean,
    val reason: ViewerReturnTargetReason,
    val geometryFromCoveredParent: Boolean,
)

/** Pure decision table, exercised by JVM tests without a RecyclerView. */
internal object ViewerReturnTargetProbe {
    fun decide(evidence: ViewerReturnTargetEvidence): ViewerReturnTargetDecision {
        fun reject(reason: ViewerReturnTargetReason) = ViewerReturnTargetDecision(false, reason, false)
        if (!evidence.scopeMatches) return reject(ViewerReturnTargetReason.SCOPE_CHANGED)
        if (!evidence.hasPosition) return reject(ViewerReturnTargetReason.NO_POSITION)
        if (!evidence.gridSettled) return reject(ViewerReturnTargetReason.GRID_UNSETTLED)
        if (!evidence.holderBound) return reject(ViewerReturnTargetReason.HOLDER_UNAVAILABLE)
        if (!evidence.identityMatches) return reject(ViewerReturnTargetReason.IDENTITY_CHANGED)
        if (!evidence.pixelsBound) return reject(ViewerReturnTargetReason.PIXELS_UNAVAILABLE)
        if (!evidence.attached || !evidence.boundsUsable || !evidence.geometryUsable) {
            return reject(ViewerReturnTargetReason.BOUNDS_UNAVAILABLE)
        }
        if (!evidence.parentWindowVisible) {
            // The Grid Activity was stopped by the Viewer. Its layout and pixels
            // are unchanged, so the measured rectangle is still a legal target.
            return ViewerReturnTargetDecision(
                accepted = true,
                reason = ViewerReturnTargetReason.PARENT_WINDOW_HIDDEN,
                geometryFromCoveredParent = true,
            )
        }
        if (!evidence.targetVisible) return reject(ViewerReturnTargetReason.TARGET_NOT_VISIBLE)
        return ViewerReturnTargetDecision(true, ViewerReturnTargetReason.READY, false)
    }
}

/**
 * Facts for the anchor frozen when the Viewer was launched.
 *
 * The anchor is only a fallback for the covered-parent case. It must never be
 * used blindly: a switched page, a deleted/rebound item, a re-sorted adapter, a
 * changed scope or a changed file version all invalidate it.
 */
internal data class ViewerCachedReturnAnchorEvidence(
    val launchMatches: Boolean,
    val scopeMatches: Boolean,
    val pathStillSelected: Boolean,
    val adapterVersionMatches: Boolean,
    val mediaIdentityMatches: Boolean,
    val fileVersionMatches: Boolean,
    val windowGeometryMatches: Boolean,
    val drawableAvailable: Boolean,
)

internal enum class ViewerCachedReturnAnchorReason {
    READY,
    LAUNCH_CHANGED,
    SCOPE_CHANGED,
    IDENTITY_CHANGED,
    BOUNDS_UNAVAILABLE,
    PIXELS_UNAVAILABLE,
}

internal data class ViewerCachedReturnAnchorDecision(
    val accepted: Boolean,
    val reason: ViewerCachedReturnAnchorReason,
)

internal object ViewerCachedReturnAnchorPolicy {
    fun decide(evidence: ViewerCachedReturnAnchorEvidence): ViewerCachedReturnAnchorDecision {
        fun reject(reason: ViewerCachedReturnAnchorReason) =
            ViewerCachedReturnAnchorDecision(false, reason)
        if (!evidence.launchMatches) return reject(ViewerCachedReturnAnchorReason.LAUNCH_CHANGED)
        if (!evidence.scopeMatches || !evidence.pathStillSelected) {
            return reject(ViewerCachedReturnAnchorReason.SCOPE_CHANGED)
        }
        if (!evidence.adapterVersionMatches || !evidence.mediaIdentityMatches ||
            !evidence.fileVersionMatches) {
            return reject(ViewerCachedReturnAnchorReason.IDENTITY_CHANGED)
        }
        if (!evidence.windowGeometryMatches) return reject(ViewerCachedReturnAnchorReason.BOUNDS_UNAVAILABLE)
        if (!evidence.drawableAvailable) return reject(ViewerCachedReturnAnchorReason.PIXELS_UNAVAILABLE)
        return ViewerCachedReturnAnchorDecision(true, ViewerCachedReturnAnchorReason.READY)
    }
}
