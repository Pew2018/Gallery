package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OxygenSystemBarsTest {
    @Test
    @Config(sdk = [29])
    fun legacyViewerUsesNativeBarColorsAndUpdatesIconContrast() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val window = activity.window
            val colors = OxygenThemeEngine.snapshot(activity, activity.config).copy(
                statusBarBackground = Color.WHITE, navigationBarBackground = Color.WHITE)
            val bars = WindowCompat.getInsetsController(window, window.decorView)

            OxygenSystemBars.applyViewer(activity, colors, 0f, true)
            assertEquals(Color.WHITE, window.statusBarColor)
            assertEquals(true, bars.isAppearanceLightStatusBars)
            assertEquals(true, bars.isAppearanceLightNavigationBars)

            OxygenSystemBars.applyViewer(activity, colors, 1f, true)
            assertEquals(activity.getColor(R.color.viewer_status_surface), window.statusBarColor)
            assertEquals(Color.BLACK, window.navigationBarColor)
            assertEquals(false, bars.isAppearanceLightStatusBars)
            assertEquals(false, bars.isAppearanceLightNavigationBars)
            assertNull(window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [29, 36])
    fun viewerBarColorsStayOpaqueAtFractionalProgress() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val window = activity.window
            val colors = OxygenThemeEngine.snapshot(activity, activity.config).copy(
                statusBarBackground = Color.WHITE, navigationBarBackground = Color.WHITE)
            // Float interpolation followed by truncation can turn alpha 255 into 254 here.
            OxygenSystemBars.applyViewer(activity, colors, 0.008f, true)
            val statusColor = if (android.os.Build.VERSION.SDK_INT < 35) {
                assertEquals(Color.rgb(252, 252, 252), window.navigationBarColor)
                window.statusBarColor
            } else {
                val underlay = window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
                (underlay.background as ColorDrawable).color
            }
            assertEquals(Color.rgb(253, 253, 253), statusColor)
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(true, bars.isAppearanceLightStatusBars)
            assertEquals(true, bars.isAppearanceLightNavigationBars)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [35, 36])
    fun edgeToEdgeReusesOneStatusSurfaceAndPreservesInsetsThroughImmersion() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val decor = activity.window.decorView as FrameLayout
            val theme = OxygenThemeEngine.snapshot(activity, activity.config)
            val light = theme.copy(statusBarBackground = Color.WHITE)
            val dark = theme.copy(statusBarBackground = Color.DKGRAY)
            val types = WindowInsetsCompat.Type.statusBars()
            val insets = WindowInsetsCompat.Builder()
                .setInsetsIgnoringVisibility(types, Insets.of(0, 44, 0, 0))
                .setVisible(types, false)
                .build()

            OxygenSystemBars.applyViewer(activity, light, 0f, true)
            val underlay = decor.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            assertEquals(insets, ViewCompat.dispatchApplyWindowInsets(underlay, insets))
            assertEquals(44, underlay.layoutParams.height)
            assertEquals(Color.WHITE, (underlay.background as ColorDrawable).color)

            OxygenSystemBars.applyViewer(activity, light, 0.5f, false)
            assertEquals(View.GONE, underlay.visibility)
            assertEquals(44, underlay.layoutParams.height)
            assertEquals(ColorUtils.blendARGB(Color.WHITE, activity.getColor(R.color.viewer_status_surface), 0.5f),
                (underlay.background as ColorDrawable).color)

            OxygenSystemBars.applyViewer(activity, dark, 0f, true)
            assertEquals(View.VISIBLE, underlay.visibility)
            assertEquals(Color.DKGRAY, (underlay.background as ColorDrawable).color)
            assertSame(underlay, decor.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
