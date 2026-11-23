package org.fossify.gallery.helpers

/**
 * Lifecycle contract for Photos while a child configuration page covers it.
 *
 * Launching `PhotoProfileManageActivity` is not a departure from Photos: it is a
 * temporary cover over the same authorized profile. The page used to treat it as a
 * departure in `onPause`/`onStop` - it invalidated the media request and stopped the
 * scan worker - and then had nothing left to return to, so the return started a cold
 * full scan of the profile behind an empty page.
 *
 * These predicates are pure so the three return branches are pinned without a device:
 *
 *  1. same source, access unchanged, committed grid   -> keep adapter/scroll/identity
 *  2. same source, access unchanged, scan still active -> rejoin that scan
 *  3. source, access, filter or sorting changed        -> discard and reload
 *
 * Branch 3 must never be traded for a shorter visual gap: showing media from a
 * revoked source or a previous profile is worse than a themed loading state.
 */
internal object PhotoChildReturnPolicy {
    /**
     * The in-flight scan may outlive `onPause` while a child page covers Photos.
     *
     * `displayedKey`/`currentKey` are the same `PhotoProfileScope` string the grid
     * publishes against, so a filter, sorting, profile or access-session change that
     * happened before the cover was opened cannot be carried across it.
     */
    fun detachesScan(
        isPhotoProfile: Boolean,
        viewerTransition: Boolean,
        childPageCovered: Boolean,
        scanRunning: Boolean,
        displayedKey: String?,
        currentKey: String?,
    ): Boolean = isPhotoProfile && !viewerTransition && childPageCovered && scanRunning &&
        displayedKey != null && displayedKey == currentKey

    /**
     * Results that arrive while the page is covered are retained for the return
     * instead of being published to a window that is not drawing.
     */
    fun holdsResult(childPageCovered: Boolean, scanRetained: Boolean): Boolean =
        childPageCovered && scanRetained

    /**
     * Whether the return may rejoin the retained scan instead of starting a new one.
     *
     * Every term is an identity check: an outdated request, a moved scope key, a new
     * content revision, revoked access or a dead worker all force the reload branch.
     */
    fun rejoins(
        returnedFromChildPage: Boolean,
        requestCurrent: Boolean,
        keyUnchanged: Boolean,
        revisionCurrent: Boolean,
        accessCurrent: Boolean,
        taskAlive: Boolean,
    ): Boolean = returnedFromChildPage && requestCurrent && keyUnchanged &&
        revisionCurrent && accessCurrent && taskAlive

    /** A retained scan whose identity no longer matches must be stopped, not reused. */
    fun discards(
        requestCurrent: Boolean,
        keyUnchanged: Boolean,
        revisionCurrent: Boolean,
        accessCurrent: Boolean,
        taskAlive: Boolean,
    ): Boolean = !(requestCurrent && keyUnchanged && revisionCurrent && accessCurrent && taskAlive)
}
