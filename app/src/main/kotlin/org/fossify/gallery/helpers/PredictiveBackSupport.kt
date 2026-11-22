package org.fossify.gallery.helpers

import android.os.Build

/**
 * Where the platform, rather than the app, owns the page transition.
 *
 * Android 14 is the first release that animates a cross-Activity back itself. Before
 * that the app window animation is the only motion available, so the Oxygen Classic
 * page animation stays in charge there.
 *
 * Setting a window animation style on a page the platform wants to animate makes the
 * platform drop its own preview: the gesture then has nothing to show and the page
 * swaps in a single frame. Folder pages and standalone Viewer pages used to do exactly
 * that, which is why their return was a 33 ms cut with no revealed parent.
 */
internal object PredictiveBackSupport {
    val systemDrivesPageTransition: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
