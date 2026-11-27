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
            if (to == GallerySection.ALL) assertFalse(destination.isAllFiles)
            assertNotEquals(from, to)
        }
    }

    @Test fun destinationKeepsOpaqueClassicThemeAndBoundsItsLoadingFrameInsteadOfWaitingForRows() {
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
        GalleryBottomBar.coordinateFirstDraw(destination, root, frameState = { GalleryBottomBar.RootFrame.LOADING })

        root.viewTreeObserver.dispatchOnDraw()
        val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
        clock.idle()
        assertEquals(1f, loading.alpha)
        val expected = OxygenThemeEngine.snapshot(destination, destination.config).pageBackground
        assertEquals(expected, (root.background as ColorDrawable).color)
        assertEquals(expected, (destination.window.decorView.background as ColorDrawable).color)
        // A loading-only frame is not content evidence: the source survives the frame
        // so a late first publish is not covered by a retired window.
        clock.idleFor(32, TimeUnit.MILLISECONDS)
        assertFalse(source.isFinishing)
        // The wait is bounded; the stable loading frame is eventually accepted.
        clock.idleFor(GalleryBottomBar.LOADING_HANDOFF_LIMIT_MS, TimeUnit.MILLISECONDS)
        assertTrue(source.isFinishing)
        GalleryBottomBar.dispose(destination)
        GalleryBottomBar.dispose(source)
        destination.finish()
        source.finish()
    }

    @Test fun attachingRootNavigationKeepsCachedGridVisibleFromItsFirstFrame() {
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val intent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, intent, finishCurrent = true)
        val destination = Robolectric.buildActivity(android.app.Activity::class.java,
            Shadows.shadowOf(source).nextStartedActivity).setup().get()
        val root = CoordinatorLayout(destination)
        val grid = androidx.recyclerview.widget.RecyclerView(destination).apply {
            id = R.id.directories_grid
            alpha = 1f
        }
        root.addView(grid, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT))
        destination.setContentView(root)

        GalleryBottomBar.attach(destination, root, GalleryBottomBar.Tab.ALL,
            frameState = { GalleryBottomBar.RootFrame.CONTENT }, navigate = {})

        assertEquals("cached root content stays visible immediately", 1f, grid.alpha)
        root.viewTreeObserver.dispatchOnPreDraw()
        root.viewTreeObserver.dispatchOnDraw()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
        assertEquals("root attach must not schedule a second full-grid fade", 1f, grid.alpha)

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
        GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { GalleryBottomBar.RootFrame.CONTENT })
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
                GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { GalleryBottomBar.RootFrame.CONTENT })
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
    @Test fun blankRootCannotRetireSourceAndContentOrEmptyFrameCan() {
        for (state in listOf(GalleryBottomBar.RootFrame.CONTENT, GalleryBottomBar.RootFrame.EMPTY,
                GalleryBottomBar.RootFrame.ERROR)) {
            val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
            val intent = Intent(source, android.app.Activity::class.java)
            GalleryBottomBar.open(source, intent, finishCurrent = true)
            val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
            val root = CoordinatorLayout(target)
            target.setContentView(root)
            var published: GalleryBottomBar.RootFrame? = null
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

    @Test fun stableLoadingFrameCannotRetireSourceBeforeItsDeadline() {
        val (source, target, root) = pendingRootWithFrame(GalleryBottomBar.RootFrame.LOADING)
        try {
            val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
            root.viewTreeObserver.dispatchOnPreDraw()
            root.viewTreeObserver.dispatchOnDraw()
            clock.idleFor(32, TimeUnit.MILLISECONDS)
            assertFalse("loading alone must not retire the source", source.isFinishing)
            clock.idleFor(GalleryBottomBar.LOADING_HANDOFF_LIMIT_MS - 64, TimeUnit.MILLISECONDS)
            assertFalse("the deadline must be the only loading commit", source.isFinishing)
            clock.idleFor(128, TimeUnit.MILLISECONDS)
            assertTrue("a stable loading frame is eventually accepted", source.isFinishing)
        } finally {
            GalleryBottomBar.dispose(source)
            GalleryBottomBar.dispose(target)
            target.finish()
        }
    }

    @Test fun contentDrawnBeforeTheDeadlineCommitsWithoutWaitingForIt() {
        val (source, target, root) = pendingRootWithFrame(GalleryBottomBar.RootFrame.LOADING)
        try {
            val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
            root.viewTreeObserver.dispatchOnPreDraw()
            root.viewTreeObserver.dispatchOnDraw()
            clock.idleFor(120, TimeUnit.MILLISECONDS)
            publishedFrame = GalleryBottomBar.RootFrame.CONTENT
            root.viewTreeObserver.dispatchOnPreDraw()
            root.viewTreeObserver.dispatchOnDraw()
            clock.idleFor(32, TimeUnit.MILLISECONDS)
            assertTrue("content frame commits immediately", source.isFinishing)
            assertTrue(120 + 32 < GalleryBottomBar.LOADING_HANDOFF_LIMIT_MS)
        } finally {
            GalleryBottomBar.dispose(source)
            GalleryBottomBar.dispose(target)
            target.finish()
        }
    }

    private var publishedFrame: GalleryBottomBar.RootFrame? = null

    private fun pendingRootWithFrame(initial: GalleryBottomBar.RootFrame): Triple<android.app.Activity, android.app.Activity, CoordinatorLayout> {
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val intent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, intent, finishCurrent = true)
        val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
        val root = CoordinatorLayout(target)
        target.setContentView(root)
        publishedFrame = initial
        GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { publishedFrame })
        return Triple(source, target, root)
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
            GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { GalleryBottomBar.RootFrame.LOADING })
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
            GalleryBottomBar.coordinateFirstDraw(activity, root, frameState = { GalleryBottomBar.RootFrame.LOADING })
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

    @Test fun rootThemeKeepsWindowOpaqueWithoutSuppressingPlatformPreview() {
        val context = RuntimeEnvironment.getApplication()
        val theme = context.resources.newTheme().apply { applyStyle(R.style.OxygenClassicRootTheme, true) }
        val value = android.util.TypedValue()
        // Root pages must stay opaque so the platform can reveal the parent page
        // behind them. windowDisablePreview is no longer forced on: leaving it unset
        // lets the system build its launch-behind predictive-back preview of the
        // parent instead of falling back to a black reveal.
        assertTrue(theme.resolveAttribute(android.R.attr.windowIsTranslucent, value, true))
        assertEquals(0, value.data)
        val preview = android.util.TypedValue()
        if (theme.resolveAttribute(android.R.attr.windowDisablePreview, preview, true)) {
            assertEquals(0, preview.data)
        }
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
        GalleryBottomBar.coordinateFirstDraw(target, root, frameState = { GalleryBottomBar.RootFrame.LOADING })
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
