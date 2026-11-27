package org.fossify.gallery.helpers

internal enum class SnapshotDecision(val token: String) {
    PUBLISH("publish"),
    SUPPRESS_COMPLETE_BASELINE("covered_by_complete_grid"),
    SUPPRESS_SHRINK("would_remove_displayed_media"),
    SUPPRESS_GROWTH("displayed_list_still_has_headroom"),
}

/**
 * Whether a worker stage has to reach the Adapter at all.
 *
 * Scanning, sorting and validation are bounded by what the user can see, so the
 * decision is about display need - not about how many times the worker advanced.
 * The device log published twelve large FILESYSTEM snapshots of the same 10 968 media
 * in ten seconds; every one of them ended in a full REPLACE of the grid.
 *
 * Rules, in priority order:
 *
 *  1. COMPLETE always commits. A delayed final result is the one outcome that must
 *     never be traded for fewer diffs.
 *  2. An empty grid publishes immediately; the first slice is what the tap waits for.
 *  3. A complete displayed snapshot is not replaced by scan progress at all. The grid
 *     is already the whole scope, so only the authoritative scan can change it.
 *  4. A snapshot that would drop media the grid already shows is never admitted.
 *  5. The user near the end of a partial grid gets the new items (browsing must keep
 *     working - merging may not swallow the scroll).
 *  6. Otherwise intermediate snapshots are merged until the list has grown enough to
 *     be worth a visible replacement.
 */
internal object PhotoSnapshotAdmission {
    /** One grid screen of items: below this the user is about to run out of content. */
    const val HEADROOM_ITEMS = 24L
    /** Smallest growth worth replacing a partial grid for. */
    const val MIN_GROWTH = 120L

    fun decide(
        stage: PhotoLoadStage,
        displayedCount: Int,
        mergedCount: Int,
        baselineComplete: Boolean,
        browseHeadroom: Int,
    ): SnapshotDecision {
        if (stage == PhotoLoadStage.COMPLETE) return SnapshotDecision.PUBLISH
        if (displayedCount <= 0) return SnapshotDecision.PUBLISH
        if (baselineComplete) return SnapshotDecision.SUPPRESS_COMPLETE_BASELINE
        if (mergedCount < displayedCount) return SnapshotDecision.SUPPRESS_SHRINK
        if (browseHeadroom <= HEADROOM_ITEMS) return SnapshotDecision.PUBLISH
        val growth = mergedCount - displayedCount
        val needed = maxOf(displayedCount.toLong(), MIN_GROWTH)
        return if (growth >= needed) SnapshotDecision.PUBLISH else SnapshotDecision.SUPPRESS_GROWTH
    }
}
