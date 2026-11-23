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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * System bar ownership during a Viewer return.
 *
 * Two mechanisms used to write the same surfaces while the Viewer was leaving: the Viewer
 * blended its bars on the return clock, and the Grid it returns to re-applies its own bars
 * in `onResume` underneath the still-animating window. Whichever wrote last won, and
 * because the blend crosses the icon contrast threshold the polarity could flip more than
 * once - the reported "unnecessary brightness change" of the status bar.
 *
 * The contract asserted here:
 *  - while a committed return holds the bars, no progress frame writes anything;
 *  - the teardown path that restores the viewer chrome cannot snap the bars back to the
 *    viewer surface after the handover;
 *  - a cancelled return takes the bars back and restores the viewer appearance;
 *  - the entry/steady path still tracks progress, because there the Viewer *is* the
 *    destination and has to blend in.
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

        /** The surface actually painted behind the status icons on this API level. */
        fun statusSurface(): Int = if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor
        } else {
            val underlay = activity.window.decorView
                .findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            ((underlay?.background as? ColorDrawable)?.color) ?: Int.MIN_VALUE
        }

        fun darkStatusIcons(): Boolean =
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars

        fun progress(): Float = field("viewerSystemBarProgress") as Float

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
            fixture.establish(0f)
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
    fun aHeldReturnWritesNothingOnAnyProgressFrame() {
        val fixture = fixture()
        try {
            fixture.establish(1f)
            val viewerSurface = fixture.statusSurface()
            val icons = fixture.darkStatusIcons()

            fixture.set("viewerSystemBarHold", true)
            fixture.set("viewerSystemBarHoldProgress", 1f)
            listOf(0.9f, 0.6f, 0.4f, 0.15f, 0f).forEach { progress ->
                fixture.renderCanvas(progress)
                assertEquals("progress $progress must not rewrite the surface",
                    viewerSurface, fixture.statusSurface())
                assertEquals("progress $progress must not rewrite the icons",
                    icons, fixture.darkStatusIcons())
                assertEquals(1f, fixture.progress(), 0.0001f)
            }
        } finally {
            fixture.destroyQuietly()
        }
    }

    @Test
    @Config(sdk = [34, 36])
    fun theTeardownChromeRestoreCannotUndoTheHandover() {
        val fixture = fixture()
        try {
            fixture.establish(1f)
            fixture.set("viewerSystemBarHold", true)
            fixture.set("viewerSystemBarHoldProgress", 1f)
            fixture.renderCanvas(0f)
            val handedOver = fixture.statusSurface()

            // Releasing the return leases restores the black canvas and asks the bar
            // renderer for progress 1 again. That re-write was the flash at the end of the
            // exit; with the hold in force it has to be a no-op.
            fixture.renderCanvas(1f)
            assertEquals(handedOver, fixture.statusSurface())
            assertEquals(1f, fixture.progress(), 0.0001f)
        } finally {
            fixture.destroyQuietly()
        }
    }

    @Test
    @Config(sdk = [34, 36])
    fun aCancelledReturnTakesTheBarsBack() {
        val fixture = fixture()
        try {
            fixture.establish(1f)
            val viewerSurface = fixture.statusSurface()
            val icons = fixture.darkStatusIcons()

            // The gesture moved the bars, then the return was committed and cancelled.
            fixture.establish(0.5f)
            fixture.set("viewerSystemBarHold", true)
            fixture.set("viewerSystemBarHoldProgress", 0.5f)
            fixture.renderCanvas(0.5f)
            fixture.set("viewerSystemBarHold", false)
            fixture.establish(1f)

            assertEquals(viewerSurface, fixture.statusSurface())
            assertEquals(icons, fixture.darkStatusIcons())
        } finally {
            fixture.destroyQuietly()
        }
    }
}
