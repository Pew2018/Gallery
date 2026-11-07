package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

/** Root-window contracts; actual Surface blending still needs device capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryRootTransitionTest {
    @Test fun rootWindowAnimationsArePairedShortAlphaCrossFade() {
        val context = RuntimeEnvironment.getApplication()
        val enter = AnimationUtils.loadAnimation(context, R.anim.oxygen_classic_root_enter) as AlphaAnimation
        val exit = AnimationUtils.loadAnimation(context, R.anim.oxygen_classic_root_exit) as AlphaAnimation
        assertEquals(GalleryBottomBar.ROOT_ENTER_DURATION_MS, enter.duration)
        assertEquals(GalleryBottomBar.ROOT_ENTER_DURATION_MS, exit.duration)
    }

    @Test fun allSectionPairsKeepDestinationSelectionIndependentOfWindowMotion() {
        val flows = listOf(
            GallerySection.ALL to GallerySection.FAVORITES,
            GallerySection.FAVORITES to GallerySection.ALL,
            GallerySection.ALL to GallerySection.PHOTOS,
            GallerySection.PHOTOS to GallerySection.ALL,
            GallerySection.FAVORITES to GallerySection.PHOTOS,
            GallerySection.PHOTOS to GallerySection.FAVORITES
        )
        for (allFiles in listOf(false, true)) flows.forEach { (from, to) ->
            val destination = GalleryNavigation.bottomDestination(to, allFiles)
            assertEquals(to, destination.section)
            assertEquals(to, destination.bottomSelection)
            if (to == GallerySection.ALL) assertEquals(allFiles, destination.isAllFiles)
            assertNotEquals(from, to)
        }
    }

    @Test fun destinationKeepsOpaqueClassicThemeAndDrawsLoadingFrameWithoutWaitingForRows() {
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        source.config.oxygenThemeActivated = true
        source.config.oxygenAppearanceMode = "dark"
        val targetIntent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, targetIntent, finishCurrent = true)
        val destination = Robolectric.buildActivity(android.app.Activity::class.java,
            Shadows.shadowOf(source).nextStartedActivity).setup().get()
        val root = CoordinatorLayout(destination)
        val loading = android.widget.TextView(destination).apply { text = "Loading" }
        root.addView(loading)
        destination.setContentView(root)
        GalleryBottomBar.prepareRootSurface(destination, root)
        GalleryBottomBar.coordinateFirstDraw(destination, root)

        assertTrue("initial loading frame must not wait for adapter rows", root.viewTreeObserver.dispatchOnPreDraw())
        root.viewTreeObserver.dispatchOnDraw()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1f, loading.alpha)
        val expected = OxygenThemeEngine.snapshot(destination, destination.config).pageBackground
        assertEquals(expected, (root.background as ColorDrawable).color)
        assertEquals(expected, (destination.window.decorView.background as ColorDrawable).color)
        assertFalse("source is held only through the remainder of the 180ms window transition", source.isFinishing)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            GalleryBottomBar.ROOT_ENTER_DURATION_MS, TimeUnit.MILLISECONDS)
        assertTrue(source.isFinishing)
        GalleryBottomBar.dispose(destination)
        GalleryBottomBar.dispose(source)
        destination.finish()
        source.finish()
    }

    @Test fun noDrawStartupUsesBoundedFallbackAndDisposalCancelsFrameCallbacks() {
        val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        GalleryBottomBar.open(source, Intent(source, android.app.Activity::class.java), finishCurrent = true)
        val targetIntent = Shadows.shadowOf(source).nextStartedActivity
        val target = Robolectric.buildActivity(android.app.Activity::class.java, targetIntent).setup().get()
        val root = CoordinatorLayout(target)
        var loadingFrames = 0
        GalleryBottomBar.coordinateFirstDraw(target, root) { loadingFrames++ }
        clock.idleFor(GalleryBottomBar.FIRST_DRAW_LIMIT_MS, TimeUnit.MILLISECONDS)
        assertEquals(1, loadingFrames)
        assertFalse(source.isFinishing)
        GalleryBottomBar.dispose(target)
        root.viewTreeObserver.dispatchOnDraw()
        clock.idleFor(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS, TimeUnit.MILLISECONDS)
        assertTrue(source.isFinishing)
        assertEquals(0, GalleryBottomBar.pendingRootCount)
        GalleryBottomBar.dispose(source)
        target.finish()
        source.finish()
    }

    class TransitionRecordingActivity : android.app.Activity() {
        val transitions = mutableListOf<Pair<Int, Int>>()
        @Suppress("DEPRECATION")
        override fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {
            transitions.add(enterAnim to exitAnim)
            super.overridePendingTransition(enterAnim, exitAnim)
        }
    }

    @Test fun startRequestsWindowCrossFadeAndDoesNotAddASecondFinishTransition() {
        val expected = R.anim.oxygen_classic_root_enter to R.anim.oxygen_classic_root_exit
        for (scale in listOf(0f, 1f)) {
            val source = Robolectric.buildActivity(TransitionRecordingActivity::class.java).setup().get()
            val resolver = source.contentResolver
            val priorScale = android.provider.Settings.Global.getFloat(resolver,
                android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, scale)
            try {
                assertEquals(scale > 0f, GalleryBottomBar.windowMotionEnabled(source))
                GalleryBottomBar.open(source, Intent(source, android.app.Activity::class.java), finishCurrent = true)
                assertEquals(listOf(expected), source.transitions)
                val targetIntent = Shadows.shadowOf(source).nextStartedActivity
                val target = Robolectric.buildActivity(android.app.Activity::class.java, targetIntent).setup().get()
                val root = CoordinatorLayout(target)
                target.setContentView(root)
                GalleryBottomBar.coordinateFirstDraw(target, root)
                assertTrue(root.viewTreeObserver.dispatchOnPreDraw())
                root.viewTreeObserver.dispatchOnDraw()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if (scale > 0f) {
                    assertFalse("source remains only for the visible window motion", source.isFinishing)
                    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                        GalleryBottomBar.ROOT_ENTER_DURATION_MS, TimeUnit.MILLISECONDS)
                }
                assertTrue(source.isFinishing)
                assertEquals(listOf(expected, 0 to 0), source.transitions)
                GalleryBottomBar.dispose(source)
                GalleryBottomBar.dispose(target)
                target.finish()
            } finally {
                GalleryBottomBar.dispose(source)
                android.provider.Settings.Global.putFloat(resolver,
                    android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, priorScale)
            }
        }
    }
}
