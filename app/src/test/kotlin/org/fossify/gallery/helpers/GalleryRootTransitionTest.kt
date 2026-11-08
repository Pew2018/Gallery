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
    @Test fun legacyRootAnimationResourcesCannotFadeEitherWindow() {
        val context = RuntimeEnvironment.getApplication()
        val enter = AnimationUtils.loadAnimation(context, R.anim.oxygen_classic_root_enter) as AlphaAnimation
        val exit = AnimationUtils.loadAnimation(context, R.anim.oxygen_classic_root_exit) as AlphaAnimation
        assertEquals(0L, enter.duration)
        assertEquals(0L, exit.duration)
        val transform = android.view.animation.Transformation()
        enter.getTransformation(0L, transform)
        assertEquals(1f, transform.alpha)
        exit.getTransformation(0L, transform)
        assertEquals(1f, transform.alpha)
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
        GalleryBottomBar.coordinateFirstDraw(destination, root, frameState = { "loading" })

        root.viewTreeObserver.dispatchOnDraw()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1f, loading.alpha)
        val expected = OxygenThemeEngine.snapshot(destination, destination.config).pageBackground
        assertEquals(expected, (root.background as ColorDrawable).color)
        assertEquals(expected, (destination.window.decorView.background as ColorDrawable).color)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
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
        target.setContentView(root)
        var loadingFrames = 0
        GalleryBottomBar.coordinateFirstDraw(target, root, loadingFrame = { loadingFrames++ })
        clock.idleFor(GalleryBottomBar.FIRST_DRAW_LIMIT_MS, TimeUnit.MILLISECONDS)
        assertEquals(1, loadingFrames)
        assertFalse(source.isFinishing)
        GalleryBottomBar.dispose(target)
        root.viewTreeObserver.dispatchOnDraw()
        clock.idleFor(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS, TimeUnit.MILLISECONDS)
        assertFalse("disposed destination never committed a frame", source.isFinishing)
        assertEquals(0, GalleryBottomBar.pendingRootCount)
        GalleryBottomBar.dispose(source)
        target.finish()
        source.finish()
    }

    @Test fun frameArrivingAfterWatchdogStillRetiresItsOwnSource() {
        val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val intent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, intent, finishCurrent = true)
        val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
        val root = CoordinatorLayout(target)
        target.setContentView(root)
        GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { "content" })
        try {
            clock.idleFor(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS, TimeUnit.MILLISECONDS)
            assertFalse(source.isFinishing)
            assertEquals(0, GalleryBottomBar.pendingRootCount)
            root.viewTreeObserver.dispatchOnDraw()
            clock.idleFor(32, TimeUnit.MILLISECONDS)
            assertTrue(source.isFinishing)
        } finally {
            GalleryBottomBar.dispose(source)
            GalleryBottomBar.dispose(target)
            source.finish()
            target.finish()
        }
    }

    class TransitionRecordingActivity : android.app.Activity() {
        val transitions = mutableListOf<Pair<Int, Int>>()
        @Suppress("DEPRECATION")
        override fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {
            transitions.add(enterAnim to exitAnim)
            super.overridePendingTransition(enterAnim, exitAnim)
        }
    }

    @Test fun startAndFinishNeverRequestWindowFadesAtEitherAnimationScale() {
        val expected = 0 to 0
        for (scale in listOf(0f, 1f)) {
            val source = Robolectric.buildActivity(TransitionRecordingActivity::class.java).setup().get()
            val resolver = source.contentResolver
            val priorScale = android.provider.Settings.Global.getFloat(resolver,
                android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, scale)
            try {
                GalleryBottomBar.open(source, Intent(source, android.app.Activity::class.java), finishCurrent = true)
                assertEquals(listOf(expected), source.transitions)
                val targetIntent = Shadows.shadowOf(source).nextStartedActivity
                val target = Robolectric.buildActivity(android.app.Activity::class.java, targetIntent).setup().get()
                val root = CoordinatorLayout(target)
                target.setContentView(root)
                GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { "content" })
                root.viewTreeObserver.dispatchOnDraw()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
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
    @Test fun blankRootCannotRetireSourceAndLoadingOrEmptyFrameCan() {
        for (state in listOf("content", "loading", "empty_or_error")) {
            val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            val intent = Intent(source, android.app.Activity::class.java)
            GalleryBottomBar.open(source, intent, finishCurrent = true)
            val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
            val root = CoordinatorLayout(target)
            target.setContentView(root)
            var published: String? = null
            GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { published })
            val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
            assertTrue("blank traversal must be cancelled", root.viewTreeObserver.dispatchOnPreDraw())
            root.viewTreeObserver.dispatchOnDraw()
            clock.idleFor(32, TimeUnit.MILLISECONDS)
            assertFalse("empty root must not acknowledge navigation", source.isFinishing)
            published = state
            assertFalse("valid traversal may draw", root.viewTreeObserver.dispatchOnPreDraw())
            root.viewTreeObserver.dispatchOnDraw()
            clock.idleFor(32, TimeUnit.MILLISECONDS)
            assertTrue("valid $state frame retires source", source.isFinishing)
            GalleryBottomBar.dispose(source)
            GalleryBottomBar.dispose(target)
            target.finish()
        }
    }

    @Test fun runtimeSurfaceRestoresAfterThemeResumeForAllBackgroundModes() {
        for (mode in listOf("light", "dark", "custom")) {
            val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            source.config.oxygenThemeActivated = mode != "custom"
            source.config.oxygenAppearanceMode = mode
            if (mode == "custom") source.config.backgroundColor = android.graphics.Color.rgb(58, 44, 70)
            val intent = Intent(source, android.app.Activity::class.java)
            GalleryBottomBar.open(source, intent, finishCurrent = true)
            val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
            val root = CoordinatorLayout(target)
            target.setContentView(root)
            GalleryBottomBar.prepareRootSurface(target, root)
            GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { "loading" })
            target.window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.MAGENTA))
            target.window.setWindowAnimations(R.style.OxygenClassicPageWindow)
            GalleryBottomBar.restoreRootWindow(target)
            assertEquals(0, target.window.attributes.windowAnimations)
            assertEquals(OxygenThemeEngine.snapshot(target, target.config).pageBackground,
                (target.window.decorView.background as ColorDrawable).color)
            GalleryBottomBar.dispose(source)
            GalleryBottomBar.dispose(target)
            source.finish()
            target.finish()
        }
    }

    @Test fun obsoleteLateFrameCannotFinishSourceAfterNewerRequestIsDisposed() {
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        fun target(): Pair<android.app.Activity, CoordinatorLayout> {
            val intent = Intent(source, android.app.Activity::class.java)
            GalleryBottomBar.open(source, intent, finishCurrent = true)
            val activity = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
            val root = CoordinatorLayout(activity)
            activity.setContentView(root)
            GalleryBottomBar.coordinateFirstDraw(activity, root, frameState = { "loading" })
            return activity to root
        }
        val (old, oldRoot) = target()
        val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
        clock.idleFor(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS, TimeUnit.MILLISECONDS)
        val (newer, _) = target()
        GalleryBottomBar.dispose(newer)
        oldRoot.viewTreeObserver.dispatchOnDraw()
        clock.idleFor(32, TimeUnit.MILLISECONDS)
        assertFalse("old acknowledgement must stay obsolete even after newer teardown", source.isFinishing)
        GalleryBottomBar.dispose(old)
        GalleryBottomBar.dispose(source)
        newer.finish()
        old.finish()
        source.finish()
    }

    @Test fun rootThemeDisablesInheritedDarkPreviewWithoutMakingWindowTranslucent() {
        val context = RuntimeEnvironment.getApplication()
        val theme = context.resources.newTheme().apply { applyStyle(R.style.OxygenClassicRootTheme, true) }
        val value = android.util.TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowDisablePreview, value, true))
        assertNotEquals(0, value.data)
        assertTrue(theme.resolveAttribute(android.R.attr.windowIsTranslucent, value, true))
        assertEquals(0, value.data)
    }

    @Test @Config(sdk = [36])
    fun rootWindowContractSurvivesMediaMotionInstallAndFirstDrawCoordinationOnAndroid16() {
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val intent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, intent, finishCurrent = true)
        val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
        val root = CoordinatorLayout(target)
        target.setContentView(root)
        GalleryBottomBar.prepareRootSurface(target, root)
        GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { "loading" })
        OxygenClassicActivityMotion.install(target)
        assertTrue(GalleryBottomBar.isRootTransition(target))
        assertEquals(0, target.window.attributes.windowAnimations)
        assertEquals(1f, target.window.attributes.alpha)
        GalleryBottomBar.dispose(source)
        GalleryBottomBar.dispose(target)
        source.finish()
        target.finish()
    }

}
