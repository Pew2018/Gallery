package org.fossify.gallery.helpers

import android.app.Activity
import org.fossify.gallery.R

/** Installs the shared Activity Window transition contract for folder and Viewer layers. */
object OxygenClassicActivityMotion {
    fun install(activity: Activity) {
        activity.window.setWindowAnimations(R.style.OxygenClassicPageWindow)
    }

    fun applyClose(activity: Activity) {
        activity.overridePendingTransition(
            R.anim.oxygen_classic_page_close_enter,
            R.anim.oxygen_classic_page_close_exit
        )
    }
}
