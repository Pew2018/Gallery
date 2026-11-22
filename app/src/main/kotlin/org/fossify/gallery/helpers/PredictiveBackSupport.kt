package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Where the platform, rather than the app, owns the page transition.
 *
 * Android 14 is the first release that animates a cross-Activity back itself. Before
 * that the app window animation is the only motion available, so the Oxygen Classic
 * page animation stays in charge there.
 *
 * On Android 14+ the page window still carries a window animation style: a non-zero
 * android:activityCloseExitAnimation is what makes WindowManager offer the *custom*
 * CLOSE branch to the Shell (BackNavigationController.isCustomizeExitAnimation ->
 * BackNavigationInfo.setWindowAnimations -> CustomAnimationLoader). That branch keeps
 * the entering parent on a full-screen base rect and only applies the small CLOSE
 * enter animation, so the real previous page fills the screen instead of exposing the
 * Shell backdrop around a shrunken parent, which is what the default branch does.
 * A zero window animation silently selects that default branch instead.
 */
internal object PredictiveBackSupport {
    val systemDrivesPageTransition: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * Frees both runtime override slots of a page.
     *
     * [Activity.overrideActivityTransition] is not a one-shot call: the override stays
     * attached to the Activity for the rest of its life and wins over the window's
     * theme animation. A zero animation installed for one root handoff or one staged
     * snapshot therefore still applies much later, when this same page is the source
     * of an ordinary back. The documented way to remove it is
     * [Activity.clearOverrideActivityTransition], not another zero override.
     *
     * A normal page never needs a runtime override: the system draws both the preview
     * and the committed transition when predictive back is active.
     */
    fun clearPageTransitionOverrides(activity: Activity) {
        if (!systemDrivesPageTransition) return
        activity.clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN)
        activity.clearOverrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)
    }

    /**
     * Opens an ordinary child page.
     *
     * Zero-animation [ActivityOptions] only exist to hide a second in-page snapshot
     * fade below Android 14. From Android 14 that fade is disabled and the platform
     * owns the open/return pair, so a custom animation bundle would only add a
     * third-party animation on top of the system one. The root tab handoff is a
     * separate, deliberate zero-animation case and does not go through here.
     */
    fun startPage(activity: Activity, intent: Intent, legacyOptions: Bundle?) {
        if (systemDrivesPageTransition) activity.startActivity(intent)
        else activity.startActivity(intent, legacyOptions)
    }
}
