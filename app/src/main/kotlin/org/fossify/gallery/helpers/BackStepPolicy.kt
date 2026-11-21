package org.fossify.gallery.helpers

/**
 * Decides whether the visible page still owns an in-app back step.
 *
 * A page that owns a step keeps the back callback enabled so it can animate the
 * step itself. A page that does not must leave the gesture to the system: an
 * enabled callback swallows the gesture and suppresses the predictive exit
 * preview, which is why a bottom bar root may never keep one.
 */
internal object BackStepPolicy {
    /**
     * Albums root: inline search closes first, then a grouped subfolder climbs one
     * level. Reaching the tab root itself is not a back step — it leaves the app.
     */
    fun albumsOwnsBackStep(searchOpen: Boolean, groupedSubfolders: Boolean, openedSubfolders: Int): Boolean =
        searchOpen || (groupedSubfolders && openedSubfolders > 1)

    /**
     * Folder, Photos and Favorites roots only close inline search. A folder is a
     * separate Activity, so its parent is a normal page return, not a back step of
     * this page.
     */
    fun mediaOwnsBackStep(searchOpen: Boolean): Boolean = searchOpen

    /**
     * Settings owns a step while a category page is open; the hub hands the gesture
     * back to the system so the return to the launching page is previewed there.
     */
    fun settingsOwnsBackStep(categoryOpen: Boolean): Boolean = categoryOpen
}
