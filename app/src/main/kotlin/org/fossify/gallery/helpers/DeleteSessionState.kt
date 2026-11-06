package org.fossify.gallery.helpers

/** In-memory deletion choices shared by every page during one app usage session. */
internal object DeleteSessionState {
    var skipConfirmation: Boolean = false
    var skipRecycleBin: Boolean = false

    fun reset() {
        skipConfirmation = false
        skipRecycleBin = false
    }
}

/**
 * Tracks created Activities, not started/resumed ones: Home, permission prompts and
 * external editors must not end a deletion session. A new session starts only after
 * all Gallery Activities were destroyed, except for configuration recreation.
 * Application lifecycle callbacks invoke this on the main thread.
 */
internal class DeleteSessionLifecycle {
    private val activities = mutableSetOf<Any>()
    private var recreating = false

    fun onCreated(activity: Any) {
        if (activities.isEmpty() && !recreating) DeleteSessionState.reset()
        recreating = false
        activities.add(activity)
    }

    fun onDestroyed(activity: Any, changingConfigurations: Boolean) {
        if (activities.remove(activity) && activities.isEmpty()) {
            recreating = changingConfigurations
            if (!recreating) DeleteSessionState.reset()
        }
    }
}
