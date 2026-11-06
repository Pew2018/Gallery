package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Build
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

    @Suppress("DEPRECATION")
    fun suppressWindowAnimation(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        activity.overridePendingTransition(0, 0)
    }
}
