package org.fossify.gallery.helpers

import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/**
 * The Photos snapshot the grid is currently showing, kept only for visual continuity
 * while the same scope is re-scanned.
 *
 * A refresh has three different sets and they are not interchangeable:
 *
 *  - **discovered**: what this scan has already enumerated and access-validated;
 *  - **displayed**: what may be shown right now, which is the baseline union the
 *    discovered rows (a partial batch may legitimately miss media that still exists);
 *  - **authoritative**: the COMPLETE result, which is the only completeness proof.
 *
 * A baseline row therefore never proves that a file exists. It is never written back
 * to Room, never injected into COMPLETE and never used to skip the disk scan. Its only
 * job is to stop a smaller partial batch from deleting visible thumbnails. Anything
 * that cannot be re-confirmed by this scan disappears when COMPLETE commits, which is
 * why a real deletion is still applied (the scan is what confirms it).
 *
 * Eligibility is deliberately stricter than "the adapter is not empty": a revision
 * change, an access-session change, a locked profile or an active search all reject
 * the baseline. Scrolling is not part of the decision - a revoked capability must stop
 * the old thumbnails even while the grid is moving.
 */
internal data class PhotoRefreshBaseline(
    val scopeKey: String,
    val profileId: String?,
    val revision: Long,
    val accessVersion: Long,
    val sorting: Int,
    /** The displayed snapshot was the authoritative COMPLETE result of this scope. */
    val complete: Boolean,
    val items: List<Medium>,
) {
    val size: Int get() = items.size

    /**
     * Display union for a partial stage. Newly validated rows replace retained rows
     * with the same path, so updated metadata and favourite/protection facts win; the
     * retained row only fills the gap until the scan reaches that path again.
     */
    fun merged(discovered: List<Medium>): PhotoBaselineMerge {
        if (items.isEmpty()) return PhotoBaselineMerge(discovered, retained = 0, added = discovered.size, updated = 0)
        val merged = LinkedHashMap<String, Medium>(items.size + discovered.size)
        items.forEach { merged[it.path] = it }
        var updated = 0
        var added = 0
        discovered.forEach { row ->
            val previous = merged.put(row.path, row)
            when {
                previous == null -> added++
                !MediaUpdatePolicy.sameContent(previous, row) -> updated++
            }
        }
        return PhotoBaselineMerge(merged.values.toList(),
            retained = items.size - (discovered.size - added), added = added, updated = updated)
    }

    /** Whether this baseline still describes the request that is about to run. */
    fun supports(request: PhotoProfileLoadContext): Boolean =
        scopeKey == request.scope.toString() && profileId == request.scope.profileId &&
            revision == request.revision && accessVersion == request.scope.accessVersion &&
            sorting == request.scope.sorting

    companion object {
        /**
         * Captures the displayed rows, or null when keeping them would be incorrect.
         * `displayed` must be the source list, never the filtered search results.
         */
        fun capture(
            scopeKey: String,
            profileId: String?,
            revision: Long,
            accessVersion: Long,
            sorting: Int,
            complete: Boolean,
            needsUnlock: Boolean,
            currentKey: String?,
            displayedKey: String?,
            currentRevision: Long,
            currentAccessVersion: Long,
            searchActive: Boolean,
            adapterReady: Boolean,
            displayed: List<ThumbnailItem>,
        ): PhotoRefreshBaseline? {
            val rejection = PhotoRefreshBaselinePolicy.rejection(
                currentKey = currentKey, displayedKey = displayedKey,
                currentRevision = currentRevision, displayedRevision = revision,
                currentAccessVersion = currentAccessVersion, displayedAccessVersion = accessVersion,
                needsUnlock = needsUnlock, displayedCount = displayed.count { it is Medium },
                searchActive = searchActive, adapterReady = adapterReady, sorting = sorting,
            )
            if (rejection != PhotoBaselineRejection.NONE || scopeKey.isEmpty()) return null
            val rows = displayed.filterIsInstance<Medium>()
            if (rows.isEmpty()) return null
            return PhotoRefreshBaseline(
                scopeKey = scopeKey,
                profileId = profileId, revision = revision, accessVersion = accessVersion,
                sorting = sorting, complete = complete,
                // The adapter may edit grid metadata in place; the worker must not.
                items = rows.map { it.copy() },
            )
        }
    }
}

internal data class PhotoBaselineMerge(
    val media: List<Medium>,
    val retained: Int,
    val added: Int,
    val updated: Int,
)

internal enum class PhotoBaselineRejection(val token: String) {
    NONE("none"),
    NO_DISPLAY("no_display"),
    ADAPTER_MISSING("adapter_missing"),
    SCOPE_CHANGED("scope_changed"),
    ACCESS_CHANGED("access_changed"),
    REVISION_CHANGED("revision_changed"),
    LOCKED("locked"),
    SEARCH_ACTIVE("search_active"),
    UNSTABLE_ORDER("unstable_order"),
}

internal object PhotoRefreshBaselinePolicy {
    /**
     * Why the currently displayed snapshot may not be reused as a refresh baseline.
     * Access is checked before the content revision so a revoked capability is never
     * reported as an ordinary content change.
     */
    fun rejection(
        currentKey: String?,
        displayedKey: String?,
        currentRevision: Long,
        displayedRevision: Long,
        currentAccessVersion: Long,
        displayedAccessVersion: Long,
        needsUnlock: Boolean,
        displayedCount: Int,
        searchActive: Boolean,
        adapterReady: Boolean,
        sorting: Int = 0,
    ): PhotoBaselineRejection = when {
        !adapterReady || displayedKey == null -> PhotoBaselineRejection.ADAPTER_MISSING
        displayedCount <= 0 -> PhotoBaselineRejection.NO_DISPLAY
        currentKey == null || currentKey != displayedKey -> PhotoBaselineRejection.SCOPE_CHANGED
        currentAccessVersion != displayedAccessVersion -> PhotoBaselineRejection.ACCESS_CHANGED
        needsUnlock -> PhotoBaselineRejection.LOCKED
        searchActive -> PhotoBaselineRejection.SEARCH_ACTIVE
        currentRevision != displayedRevision -> PhotoBaselineRejection.REVISION_CHANGED
        // A random order is regenerated per request and is not a stable displayed order.
        sorting and SORT_BY_RANDOM != 0 -> PhotoBaselineRejection.UNSTABLE_ORDER
        else -> PhotoBaselineRejection.NONE
    }
}
