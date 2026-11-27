package org.fossify.gallery.helpers

/**
 * App-observable side of the predictive-back ownership contract (Phase 2.3 D7).
 *
 * Android 16 (targetSdk 36) enables the system's back-to-home / cross-activity
 * animations by default. Per the official guidance an app yields ownership simply
 * by disabling its `OnBackPressedCallback`s; an enabled callback swallows the
 * gesture and removes the system preview. This object records only facts the app
 * can observe through public APIs. Platform-internal facts (whether the Shell is
 * currently running a predictive animation, gesture direction, system progress)
 * are deliberately absent - claiming them would be fabrication.
 *
 * Pure and host-testable; it never registers another back callback, which would
 * itself change the ownership being diagnosed.
 */
internal object AppBackOwnershipDiagnostics {

    fun fields(
        activityId: Int,
        taskId: Int,
        isTaskRoot: Boolean,
        section: String?,
        searchOpen: Boolean,
        actionModeActive: Boolean,
        imeVisible: Boolean,
        appBackCallbackEnabled: Boolean,
        inAppBackSteps: Int,
        navigationToken: String?,
        windowAnimations: Int,
        lifecycle: String,
        rootRetiring: Boolean,
    ): Map<String, Any?> = mapOf(
        "activityId" to activityId,
        "taskId" to taskId,
        "isTaskRoot" to isTaskRoot,
        "bottomSection" to section,
        "searchOpen" to searchOpen,
        "actionModeActive" to actionModeActive,
        "imeVisible" to imeVisible,
        "appBackCallbackEnabled" to appBackCallbackEnabled,
        "inAppBackSteps" to inAppBackSteps,
        "navigationToken" to navigationToken,
        "windowAnimations" to windowAnimations,
        "lifecycle" to lifecycle,
        "rootRetiring" to rootRetiring,
        // The following are intentionally NOT recorded: they are Shell-internal.
        "platformOwnsBack" to (!appBackCallbackEnabled && inAppBackSteps == 0),
    )
}
