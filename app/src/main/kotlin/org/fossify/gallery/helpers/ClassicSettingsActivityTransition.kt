package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View

/** Only explicit navigation transfers a frame. Recreation never creates a handoff. */
object ClassicSettingsActivityTransition {
    const val EXTRA_SESSION = "classic_settings_transition_session"
    private val handler = Handler(Looper.getMainLooper())
    private data class Page(
        val destination: String,
        val snapshot: ClassicSettingsPageTransition.Snapshot,
        val title: CharSequence,
        val forward: Boolean
    )
    private var pending: Page? = null
    private val expire = Runnable { clear() }

    fun stage(destination: String, viewport: View, title: CharSequence, forward: Boolean, enabled: Boolean) {
        clear()
        if (!enabled) return
        val snapshot = ClassicSettingsPageTransition.capture(viewport) ?: return
        pending = Page(destination, snapshot, title.toString(), forward)
        // Bound bitmap ownership even if start/finish is interrupted before a receiver exists.
        handler.postDelayed(expire, 5000L)
    }

    fun clear() {
        handler.removeCallbacks(expire)
        pending?.snapshot?.dispose()
        pending = null
    }

    fun receive(destination: String, viewport: View, content: View, enabled: Boolean,
                setTitle: (CharSequence) -> Unit, targetTitle: CharSequence): Boolean {
        val page = pending?.takeIf { it.destination == destination } ?: return false
        pending = null
        handler.removeCallbacks(expire)
        setTitle(page.title)
        ClassicSettingsPageTransition.run(
            container = viewport,
            incoming = content,
            incomingDirection = ClassicSettingsPageTransition.direction(page.forward, viewport),
            animationsEnabled = enabled,
            prepareTarget = {},
            onTitleCommit = { setTitle(targetTitle) },
            snapshot = page.snapshot
        )
        return true
    }

    /**
     * Hides the classic entrance animation only where the in-page snapshot fade is
     * actually played (below Android 14).
     *
     * From Android 14 the platform owns both the entrance and the cross-Activity back,
     * and [PredictiveBackSupport.systemDrivesPageTransition] disables the snapshot
     * fade. A zero [Activity.overrideActivityTransition] here would be permanent and
     * would still own the OPEN slot when a later, ordinary return re-enters this page.
     */
    fun suppressWindowAnimation(activity: Activity) {
        if (PredictiveBackSupport.systemDrivesPageTransition) {
            PredictiveBackSupport.clearPageTransitionOverrides(activity)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}
