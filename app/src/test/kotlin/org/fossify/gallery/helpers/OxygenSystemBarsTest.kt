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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OxygenSystemBarsTest {
    /**
     * The toolbar colour the Pixel 8 Pro run recorded at `progress=0.0`
     * (`backgroundColor=-2996452`). White icons are the page's declared polarity, but
     * black icons win its contrast by 0.12 - i.e. inside the latch's ambiguity band - so
     * a fresh latch without the page's declaration resolved the opening frame to DARK and
     * the icons had to flip back at `progress=0.30`.
     */
    private val recordedToolbar = 0xFFD2471C.toInt()

    private fun viewerTokens(activity: AppCompatActivity) =
        OxygenThemeEngine.snapshot(activity, activity.config).copy(
            statusBarBackground = recordedToolbar,
            lightStatusBarIcons = true,
            navigationBarBackground = 0xFFFAFAFA.toInt(),
            darkNavigationBarIcons = true,
        )

    @Test
    @Config(sdk = [29])
    fun legacyViewerUsesNativeBarColorsAndUpdatesIconContrast() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val window = activity.window
            val colors = OxygenThemeEngine.snapshot(activity, activity.config).copy(
                statusBarBackground = Color.WHITE, navigationBarBackground = Color.WHITE,
                lightStatusBarIcons = false, darkNavigationBarIcons = true)
            val bars = WindowCompat.getInsetsController(window, window.decorView)

            OxygenSystemBars.applyViewer(activity, colors, 0f, true)
            assertEquals(Color.WHITE, window.statusBarColor)
            assertEquals("a white page bar declares dark icons", true, bars.isAppearanceLightStatusBars)
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
    @Config(sdk = [29, 36])
    fun repeatedViewerBarStateDoesNotRewriteUnchangedWindowProperties() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val colors = viewerTokens(activity)
            // Establish a state that is distinct from the fully immersive endpoint.
            OxygenSystemBars.applyViewer(activity, colors, 1f, true)
            assertTrue("moving to the page colour must write",
                OxygenSystemBars.applyViewer(activity, colors, 0f, true))
            assertFalse("an identical frame must not touch the window",
                OxygenSystemBars.applyViewer(activity, colors, 0f, true))
            assertTrue("a changed progress must write",
                OxygenSystemBars.applyViewer(activity, colors, 0.5f, true))
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                assertTrue("a changed status-surface visibility must write",
                    OxygenSystemBars.applyViewer(activity, colors, 0.5f, false))
            }
            assertFalse(OxygenSystemBars.applyViewer(activity, colors, 0.5f, false))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /**
     * A01/A03: the entry starts and the return ends on the page's own polarity.
     *
     * This is the failure the 2026-11-24 log recorded six times out of six: the opening
     * frame chose `DARK` against `progress=0.0` while the page declares light icons.
     */
    @Test
    @Config(sdk = [29, 36])
    fun bothPageEndpointsInheritTheDeclaredPolarity() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val colors = viewerTokens(activity)
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

            OxygenSystemBars.applyViewer(activity, colors, 0f, true)
            assertEquals("entry progress 0 is the page", false, bars.isAppearanceLightStatusBars)
            assertEquals("entry progress 0 navigation is the page", true,
                bars.isAppearanceLightNavigationBars)

            OxygenSystemBars.applyViewer(activity, colors, 1f, true)
            assertEquals("the viewer surface needs light icons", false, bars.isAppearanceLightStatusBars)
            assertEquals("the viewer navigation surface is black", false,
                bars.isAppearanceLightNavigationBars)

            // The return clock walks back down to 0: the last frame must already be the page.
            listOf(0.75f, 0.5f, 0.25f, 0.05f, 0f).forEach {
                OxygenSystemBars.applyViewer(activity, colors, it, true)
            }
            assertEquals(false, bars.isAppearanceLightStatusBars)
            assertEquals(true, bars.isAppearanceLightNavigationBars)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /**
     * A02: the stable viewer endpoint follows the surface it actually paints, not the
     * page declaration. A theme whose viewer surface is light cannot keep light icons.
     */
    @Test
    @Config(sdk = [29, 36])
    fun stableViewerEndpointFollowsTheViewerSurface() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val colors = viewerTokens(activity)
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            OxygenSystemBars.applyViewer(activity, colors, 0f, true)
            OxygenSystemBars.applyViewer(activity, colors, 1f, true)
            // viewer_status_surface is near-black on a dark page and stays the darker of the
            // two candidate icon colours; whatever it resolves to must equal the contrast
            // answer for the colour the window just painted.
            val surface = if (android.os.Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                activity.window.statusBarColor
            } else {
                val underlay = activity.window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
                (underlay.background as ColorDrawable).color
            }
            assertEquals(SystemBarIconContrast.useDarkIcons(surface, previous = null),
                bars.isAppearanceLightStatusBars)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /** A04: the endpoint rule is a declaration, so it holds for every accent and theme. */
    @Test
    @Config(sdk = [29, 36])
    fun everyAccentAndThemeKeepsItsDeclaredEndpoint() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val base = OxygenThemeEngine.snapshot(activity, activity.config)
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            listOf(
                0xFFD2471C.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt(),
                0xFFF9A825.toInt(), 0xFF212121.toInt(), 0xFFFAFAFA.toInt(),
            ).forEach { accent ->
                val surface = OxygenThemeColors.toolbarBackground(accent)
                listOf(true, false).forEach { darkTheme ->
                    val colors = base.copy(
                        dark = darkTheme, statusBarBackground = surface, lightStatusBarIcons = true,
                        navigationBarBackground = if (darkTheme) 0xFF212121.toInt() else 0xFFFAFAFA.toInt(),
                        darkNavigationBarIcons = !darkTheme)
                    OxygenSystemBars.applyViewer(activity, colors, 0f, true)
                    assertEquals("accent=$accent dark=$darkTheme status", false,
                        bars.isAppearanceLightStatusBars)
                    assertEquals("accent=$accent dark=$darkTheme navigation", !darkTheme,
                        bars.isAppearanceLightNavigationBars)
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /**
     * A05: the middle of the entry blend keeps the hysteresis, so a progress that wobbles
     * inside the ambiguous band cannot add a second reversal.
     */
    @Test
    @Config(sdk = [29, 36])
    fun theAmbiguousBandOfTheEntryBlendDoesNotAddAReversal() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val colors = viewerTokens(activity)
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            val viewerSurface = activity.getColor(R.color.viewer_status_surface)
            OxygenSystemBars.applyViewer(activity, colors, 0f, true)
            assertEquals(false, bars.isAppearanceLightStatusBars)
            val seen = ArrayList<Boolean>()
            listOf(0.30f, 0.32f, 0.30f, 0.34f, 0.31f, 0.30f).forEach {
                OxygenSystemBars.applyViewer(activity, colors, it, true)
                seen += bars.isAppearanceLightStatusBars
            }
            assertEquals("the blend toolbar -> $viewerSurface is white icons on both ends",
                listOf(false, false, false, false, false, false), seen)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /** A12: the two bars are separate surfaces and can be hidden independently. */
    @Test
    @Config(sdk = [35, 36])
    fun statusAndNavigationBandsHideIndependently() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val decor = activity.window.decorView as FrameLayout
            val colors = viewerTokens(activity)

            OxygenSystemBars.applyViewer(activity, colors, 1f, true, true)
            val status = decor.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            val navigation = decor.findViewWithTag<View>(OxygenSystemBars.NAV_UNDERLAY)
            assertEquals(View.VISIBLE, status.visibility)
            assertEquals(View.VISIBLE, navigation.visibility)

            OxygenSystemBars.applyViewer(activity, colors, 1f, false, true)
            assertEquals(View.GONE, status.visibility)
            assertEquals("hiding the status band must not remove the navigation band",
                View.VISIBLE, navigation.visibility)

            OxygenSystemBars.applyViewer(activity, colors, 1f, true, false)
            assertEquals(View.VISIBLE, status.visibility)
            assertEquals(View.GONE, navigation.visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    /**
     * A07/A08/A10: the endpoint rule composes with the existing ownership clock. The
     * release frame is held while the Grid target is missing, the settled frames keep
     * moving towards the page, and the last one lands on the page's declared polarity.
     */
    @Test
    @Config(sdk = [29, 36])
    fun aCommittedReturnReachesThePagePolarityThroughTheOwnershipClock() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            val colors = viewerTokens(activity)
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            val choreography = ViewerSystemBarChoreography()
            choreography.startEntry()
            OxygenSystemBars.applyViewer(activity, colors, 0f, true)
            assertEquals(false, bars.isAppearanceLightStatusBars)
            choreography.settleViewer()
            OxygenSystemBars.applyViewer(activity, colors, 1f, true)

            val token = choreography.beginReturn(0.46f)
            OxygenSystemBars.applyViewer(activity, colors, choreography.returnBarProgress(token, 1f)!!, true)
            assertEquals("the release frame is held", false, bars.isAppearanceLightStatusBars)
            listOf(0.8f, 0.6f, 0.4f, 0.2f, 0.05f, 0f).forEach { remaining ->
                val progress = choreography.returnBarProgress(token, remaining)!!
                OxygenSystemBars.applyViewer(activity, colors, progress, true)
            }
            assertEquals("the final returned frame shows the page's icons", false,
                bars.isAppearanceLightStatusBars)
            assertEquals("and the page's navigation icons", true,
                bars.isAppearanceLightNavigationBars)
            // The last settling frame already wrote the endpoint; the terminal is a
            // no-op write and hands ownership to the page.
            assertNull(choreography.terminal(token, cancelled = false))
            assertEquals(ViewerBarOwner.PAGE_HANDOFF, choreography.owner)
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
