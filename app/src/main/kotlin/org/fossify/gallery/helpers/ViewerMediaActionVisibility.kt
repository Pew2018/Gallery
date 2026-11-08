package org.fossify.gallery.helpers

import android.view.View
import org.fossify.gallery.models.Medium

/** Keeps image-only bottom actions bound to the currently selected Viewer medium. */
internal object ViewerMediaActionVisibility {
    fun apply(
        edit: View,
        resize: View,
        rotate: View,
        visibleActions: Int,
        medium: Medium?
    ) {
        val isImage = medium?.isImage() == true
        edit.visibility = if (
            visibleActions and BOTTOM_ACTION_EDIT != 0 && isImage && medium?.isSVG() == false
        ) View.VISIBLE else View.GONE
        resize.visibility = if (
            visibleActions and BOTTOM_ACTION_RESIZE != 0 && isImage
        ) View.VISIBLE else View.GONE
        rotate.visibility = if (
            visibleActions and BOTTOM_ACTION_ROTATE != 0 && isImage
        ) View.VISIBLE else View.GONE
    }
}
