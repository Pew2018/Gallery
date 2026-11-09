package org.fossify.gallery.helpers

import android.app.Activity
import org.fossify.gallery.R

/** Installs the shared Activity Window transition contract for folder and Viewer layers. */
object OxygenClassicActivityMotion {
    fun install(activity: Activity) {
        if (GalleryBottomBar.isRootTransition(activity)) {
            GalleryBottomBar.restoreRootWindow(activity)
        } else activity.window.setWindowAnimations(R.style.OxygenClassicPageWindow)
    }

    fun applyClose(activity: Activity) {
        if (GalleryBottomBar.isRetiringRoot(activity)) {
            GalleryBottomBar.restoreRootWindow(activity)
            activity.overridePendingTransition(0, 0)
            return
        }
        activity.overridePendingTransition(
            R.anim.oxygen_classic_page_close_enter,
            R.anim.oxygen_classic_page_close_exit
        )
    }
}
