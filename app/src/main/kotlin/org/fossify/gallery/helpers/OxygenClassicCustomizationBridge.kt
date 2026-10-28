package org.fossify.gallery.helpers

import android.app.Activity
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.gallery.extensions.config

/**
 * Commons owns live, unsaved color previews. Do not re-theme its already inflated controls
 * or intercept save callbacks. Adapt only the toolbar when its Activity resumes.
 * Its in-page live preview intentionally remains Commons-owned until the next resume.
 */
object OxygenClassicCustomizationBridge {
    fun apply(activity: Activity) {
        if (activity !is CustomizationActivity) return
        val toolbar = activity.findViewById<MaterialToolbar>(
            org.fossify.commons.R.id.customization_toolbar
        ) ?: return
        OxygenToolbarStyle.applyToolbar(activity, toolbar)
        activity.findViewById<View>(org.fossify.commons.R.id.app_bar)
            ?.setBackgroundColor(OxygenThemeEngine.toolbarSurface(activity, activity.config))
        OxygenSystemBars.apply(activity)
    }
}
