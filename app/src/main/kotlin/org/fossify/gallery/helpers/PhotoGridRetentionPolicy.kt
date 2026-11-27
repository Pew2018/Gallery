package org.fossify.gallery.helpers

/**
 * Whether a Photo-profile Grid may discard its displayed adapter when the page stops.
 *
 * A genuine departure from Photos releases the rendered grid so the next visit
 * re-validates access and re-reads the sources. A child configuration page is not
 * a departure: it is launched from, and returns to, the same authorized grid. The
 * previous unconditional clear removed the adapter while the Grid window was
 * already covered, so the task snapshot used by the platform's cross-Activity
 * predictive-back preview contained only the page background. The Grid was then
 * rebuilt after the child page returned, which is also why the blank frame was
 * intermittent: it depended on whether the snapshot happened before or after the
 * clear. Keeping the grid for a covered child page makes the real thumbnails the
 * preview and avoids a needless rebuild on return.
 *
 * The Viewer is deliberately separate: it already retains the grid through
 * `photoViewerTransition` and has its own measured return target.
 */
internal object PhotoGridRetentionPolicy {
    fun clearsDisplayedGrid(
        isPhotoProfile: Boolean,
        viewerTransition: Boolean,
        childPageCovered: Boolean,
    ): Boolean = isPhotoProfile && !viewerTransition && !childPageCovered
}
