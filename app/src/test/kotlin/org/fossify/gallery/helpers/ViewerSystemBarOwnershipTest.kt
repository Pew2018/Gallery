package org.fossify.gallery.helpers

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.ActivityMediumBinding
import org.fossify.gallery.extensions.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * System bar ownership during a Viewer return, on a real window.
 *
 * Two mechanisms used to write the same surfaces while the Viewer was leaving: the Viewer
 * blended its bars on the return clock, and the Grid it returns to re-applies its own bars
 * in `onResume` underneath the still-animating window. Whichever wrote last won, and
 * because the blend crosses the icon contrast threshold the polarity could flip more than
 * once - the reported "unnecessary brightness change" of the status bar.
 *
 * Phase 2 changed the contract twice, so this file asserts the new one:
 *  - a committed return keeps a single writer, but it no longer freezes the surface at the
 *    release blend: it continues to the page colour on the return remainder, which is what
 *    removes the mid-exit jump;
 *  - a page-level `OxygenSystemBars.apply` on a Viewer window replays the Viewer state
 *    instead of repainting the page, so the entry can no longer flip the icons
 *    `LIGHT -> DARK -> LIGHT`.
 *
 * Robolectric does not composite Shell surfaces or SystemUI, so this covers the app-side
 * write protocol only; the visual result still needs device acceptance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ViewerSystemBarOwnershipTest {
    private val path = "/storage/emulated/0/Gallery/ownership.jpg"

    private class Fixture(val controller: ActivityController<ViewPagerActivity>) {
        val activity = controller.get()

        fun field(name: String): Any? =
            activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

        fun set(name: String, value: Any?) =
            activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)

        fun call(name: String, vararg args: Any?): Any? =
            activity.javaClass.declaredMethods
                .single { it.name == name && it.parameterCount == args.size }
                .apply { isAccessible = true }
                .invoke(activity, *args)

        fun renderCanvas(progress: Float, scrim: Float = progress) =
            call("renderSharedViewerCanvas", progress, scrim)

        /** Force the next render to write, so the assertion does not depend on call order. */
        fun establish(progress: Float) {
            set("viewerSystemBarProgress", -1f)
            renderCanvas(progress)
        }

        private fun choreography() = field("viewerBars") as ViewerSystemBarChoreography

        /** Commits a return at the blend that is currently on screen. */
        fun commitReturn(): Long {
            val token = choreography().beginReturn(field("viewerSystemBarProgress") as Float)
            set("viewerBarToken", token)
            return token
        }

        /** One frame of the returning clock, through the Activity's own write path. */
        fun settleReturn(remaining: Float) {
            val token = field("viewerBarToken") as Long
            choreography().returnBarProgress(token, remaining)?.let { call("writeViewerBarProgress", it) }
        }

        fun terminateReturn(cancelled: Boolean) {
            val token = field("viewerBarToken") as Long
            choreography().terminal(token, cancelled)?.let { call("writeViewerBarProgress", it) }
            // Matches onSharedReturnTerminal: a cancelled return hands the surfaces back.
            if (cancelled) choreography().settleViewer()
        }

        fun owner(): ViewerBarOwner = choreography().owner

        /** The surface actually painted behind the status icons on this API level. */
        fun statusSurface(): Int = if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor
        } else {
            val underlay = activity.window.decorView
                .findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            ((underlay?.background as? ColorDrawable)?.color) ?: Int.MIN_VALUE
        }

        fun navigationSurface(): Int = if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor
        } else {
            val underlay = activity.window.decorView
                .findViewWithTag<View>(OxygenSystemBars.NAV_UNDERLAY)
            ((underlay?.background as? ColorDrawable)?.color) ?: Int.MIN_VALUE
        }

        fun darkStatusIcons(): Boolean =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars

        fun darkNavigationIcons(): Boolean =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightNavigationBars

        fun entryProgress(progress: Float) {
            choreography().startEntry()
            establish(progress)
        }

        /**
         * Robolectric's FragmentActivity refuses a second pause when the content view was
         * replaced before onCreate ran; the assertions have already completed by then.
         */
        fun destroyQuietly() {
            runCatching { controller.pause() }
            runCatching { controller.stop() }
            runCatching { controller.destroy() }
        }
    }

    private fun fixture(): Fixture {
        val controller = Robolectric.buildActivity(ViewPagerActivity::class.java)
        val activity = controller.get()
        activity.intent.putExtra(PhotoViewerTransition.EXTRA_PATH, path)
        val binding = ActivityMediumBinding.inflate(activity.layoutInflater)
        activity.window.setContentView(binding.root)
        controller.visible()
        BaseViewerActivity::class.java.getDeclaredMethod("refreshViewerSystemBars")
            .apply { isAccessible = true }.invoke(activity)
        return Fixture(controller)
    }

    @Test
    @Config(sdk = [34, 36])
    fun theViewerStillBlendsItsBarsWhileItIsTheDestination() {
        val fixture = fixture()
        try {
            val pageSurface = OxygenThemeEngine.snapshot(fixture.activity, fixture.activity.config)
                .statusBarBackground
            fixture.entryProgress(0f)
            assertEquals(pageSurface, fixture.statusSurface())

            fixture.establish(1f)
            assertEquals(fixture.activity.getColor(R.color.viewer_status_surface),
                fixture.statusSurface())
            assertFalse("the near-black surface needs light icons", fixture.darkStatusIcons())
        } finally {
            fixture.destroyQuietly()
        }
    }

    @Test
    @Config(sdk = [34, 36])
    fun aCommittedReturnContinuesFromTheReleasedFrameInsteadOfFreezing() {
        val fixture = fixture()
        try {
            // Release the predictive gesture at the progress the device log recorded.
            fixture.entryProgress(1f)
            fixture.establish(0.49627578f)
            val released = fixture.statusSurface()
            fixture.commitReturn()
            assertEquals(ViewerBarOwner.RETURN_WAIT_TARGET, fixture.owner())

            // Waiting for the Grid target: the release frame is held on purpose.
            assertEquals(released, fixture.statusSurface())

            // The returning clock owns the surface from here and walks it to the page
            // colour; nothing is parked on an arbitrary mid blend.
            fixture.settleReturn(0.5f)
            fixture.settleReturn(0f)
            val pageSurface = OxygenThemeEngine.snapshot(fixture.activity, fixture.activity.config)
                .statusBarBackground
            assertEquals(pageSurface, fixture.statusSurface())
        } finally {
            fixture.destroyQuietly()
        }
    }

    @Test
    @Config(sdk = [34, 36])
    fun theTeardownChromeRestoreCannotUndoTheHandover() {
        val fixture = fixture()
        try {
            fixture.entryProgress(1f)
            fixture.commitReturn()
            fixture.settleReturn(0f)
            fixture.terminateReturn(cancelled = false)
            val handedOver = fixture.statusSurface()

            // Releasing the return leases restores the black canvas and asks the bar
            // renderer for progress 1 again. That re-write was the flash at the end of the
            // exit; after the handover it has to be a no-op.
            fixture.renderCanvas(1f)
            assertEquals(handedOver, fixture.statusSurface())
            assertEquals(ViewerBarOwner.PAGE_HANDOFF, fixture.owner())
        } finally {
            fixture.destroyQuietly()
        }
    }

    @Test
    @Config(sdk = [34, 36])
    fun aCancelledReturnTakesTheBarsBack() {
        val fixture = fixture()
        try {
            fixture.entryProgress(1f)
            val viewerSurface = fixture.statusSurface()
            val icons = fixture.darkStatusIcons()

            fixture.establish(0.5f)
            fixture.commitReturn()
            fixture.settleReturn(0.5f)
            fixture.terminateReturn(cancelled = true)
            fixture.establish(1f)

            assertEquals(viewerSurface, fixture.statusSurface())
            assertEquals(icons, fixture.darkStatusIcons())
            assertEquals(ViewerBarOwner.VIEWER, fixture.owner())
        } finally {
            fixture.destroyQuietly()
        }
    }

    /**
     * The entry regression from the exported log: one monotonic entry produced
     * `LIGHT -> DARK -> LIGHT`. `BaseViewerActivity.onResume` blends (progress 0), then
     * `SimpleActivity.onPostResume` repaints the page polarity on the same window, then
     * the next entry frame blends again. A page write on a Viewer-owned window must be a
     * replay of the Viewer state, not a page repaint.
     */
    @Test
    @Config(sdk = [34, 36])
    fun aPageBarWriteCannotRepaintAViewerOwnedWindow() {
        val fixture = fixture()
        try {
            fixture.entryProgress(0f)
            val initialSurface = fixture.statusSurface()
            fixture.establish(0.010795873f)
            val blendedSurface = fixture.statusSurface()
            val blendedIcons = fixture.darkStatusIcons()
            assertTrue("the entry blend must actually move the surface", blendedSurface != initialSurface)

            OxygenSystemBars.apply(fixture.activity)
            assertEquals("a page write must not repaint the Viewer's surface",
                blendedSurface, fixture.statusSurface())
            assertEquals("a page write must not flip the Viewer's icons",
                blendedIcons, fixture.darkStatusIcons())
        } finally {
            fixture.destroyQuietly()
        }
    }

    @Test
    @Config(sdk = [35, 36])
    fun enforcedEdgeToEdgePaintsANavigationSurfaceForTheViewer() {
        val fixture = fixture()
        try {
            fixture.entryProgress(1f)
            assertEquals(android.graphics.Color.BLACK, fixture.navigationSurface())
            assertFalse(fixture.darkNavigationIcons())

            fixture.establish(0f)
            val pageNavigation = OxygenThemeEngine.snapshot(fixture.activity, fixture.activity.config)
                .navigationBarBackground
            assertEquals(pageNavigation, fixture.navigationSurface())
        } finally {
            fixture.destroyQuietly()
        }
    }
}
