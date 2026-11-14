package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.extensions.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class OxygenSystemBarsTest {
    @Test fun viewerStatusSurfaceUsesPageColorsAndLeavesFullscreenCanvasClear() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        try {
            OxygenSystemBars.apply(activity)
            val decor = activity.window.decorView as FrameLayout
            val underlay = decor.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            assertNotNull("status surface should exist before the viewer draws", underlay)
            assertEquals(
                OxygenThemeEngine.snapshot(activity, activity.config).statusBarBackground,
                activity.window.statusBarColor,
            )

            OxygenSystemBars.setStatusUnderlayVisible(activity, false)
            assertEquals(View.GONE, underlay.visibility)
            OxygenSystemBars.setStatusUnderlayVisible(activity, true)
            assertEquals(View.VISIBLE, underlay.visibility)

            OxygenSystemBars.apply(activity)
            assertSame(underlay, decor.findViewWithTag(OxygenSystemBars.STATUS_UNDERLAY))
        } finally {
            activity.finish()
        }
    }
}
