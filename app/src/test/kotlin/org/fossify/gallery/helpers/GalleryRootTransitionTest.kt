package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.drawable.ColorDrawable
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Root-navigation contracts; frame-perfect rendering still needs a real device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryRootTransitionTest {
    @Test fun contentProofBeatsArbitraryRecyclerChildrenAndTimeoutIsBounded() {
        assertFalse(GalleryBottomBar.mayDrawRoot(false, true, false, 0L))
        assertFalse(GalleryBottomBar.mayDrawRoot(false, false, true, 120L))
        assertTrue(GalleryBottomBar.mayDrawRoot(true, true, false, 25L))
        assertTrue(GalleryBottomBar.mayDrawRoot(true, false, true, 35L))
        assertFalse(GalleryBottomBar.mayDrawRoot(true, false, false, 249L))
        assertTrue(GalleryBottomBar.mayDrawRoot(false, false, false, 250L))
        assertEquals(0L, GalleryBottomBar.ROOT_ENTER_DURATION_MS)
        assertTrue(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS >=
            GalleryBottomBar.FIRST_DRAW_LIMIT_MS + GalleryBottomBar.ROOT_ENTER_DURATION_MS)
    }

    @Test fun sixTransitionsRespectDestinationForColdCacheAndRapidEntry() {
        val flows = listOf(
            GallerySection.ALL to GallerySection.FAVORITES,
            GallerySection.FAVORITES to GallerySection.ALL,
            GallerySection.ALL to GallerySection.PHOTOS,
            GallerySection.PHOTOS to GallerySection.ALL,
            GallerySection.FAVORITES to GallerySection.PHOTOS,
            GallerySection.PHOTOS to GallerySection.FAVORITES
        )
        for (allFiles in listOf(false, true)) for (cacheHit in listOf(false, true)) {
            flows.forEachIndexed { index, (from, to) ->
                val destination = GalleryNavigation.bottomDestination(to, allFiles)
                assertEquals("path $index from $from", to, destination.section)
                assertEquals(to, destination.bottomSelection)
                if (to == GallerySection.ALL) assertEquals(allFiles, destination.isAllFiles)
                if (cacheHit) {
                    assertTrue(GalleryBottomBar.mayDrawRoot(true, true, false, 0L))
                } else {
                    assertFalse(GalleryBottomBar.mayDrawRoot(false, false, false, 0L))
                    assertTrue(GalleryBottomBar.mayDrawRoot(false, false, false, 250L))
                }
            }
        }
    }

    @Test fun destinationWindowAndLayoutUseOpaqueClassicThemeBeforeContent() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.config.oxygenThemeActivated = true
        activity.config.oxygenAppearanceMode = "dark"
        val parent = CoordinatorLayout(activity)
        activity.intent.putExtra("org.fossify.gallery.ROOT_TRANSITION", true)
        activity.setContentView(parent)
        GalleryBottomBar.prepareRootSurface(activity, parent)
        val expected = OxygenThemeEngine.snapshot(activity, activity.config).pageBackground
        assertEquals(expected, (parent.background as ColorDrawable).color)
        // Window background should be opaque even when media has not published rows.
        assertEquals(expected, (activity.window.decorView.background as ColorDrawable).color)
        activity.finish()
    }

    @Test fun sourceWaitsForActualDrawAcrossAllSixDestinationsAndEmptyOrFailedLoads() {
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val paths = listOf(false to GallerySection.PHOTOS, false to GallerySection.PHOTOS,
            false to GallerySection.ALL, false to GallerySection.FAVORITES,
            true to GallerySection.PHOTOS, true to GallerySection.ALL)
        for ((allFiles, section) in paths) for (state in listOf(
            GalleryBottomBar.RootContent.EMPTY, GalleryBottomBar.RootContent.FAILED,
            GalleryBottomBar.RootContent.AUTHORIZATION)) {
            val sourceController = Robolectric.buildActivity(android.app.Activity::class.java).setup()
            val source = sourceController.get()
            val destination = GalleryNavigation.bottomDestination(section, allFiles)
            assertEquals(section, destination.bottomSelection)
            GalleryBottomBar.open(source, android.content.Intent(source, android.app.Activity::class.java), true)
            val intent = org.robolectric.Shadows.shadowOf(source).nextStartedActivity
            sourceController.pause().stop()
            val targetController = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup()
            val target = targetController.get()
            val root = CoordinatorLayout(target)
            val empty = android.widget.TextView(target).apply {
                id = org.fossify.gallery.R.id.media_empty_text_placeholder
                visibility = android.view.View.VISIBLE
            }
            val grid = androidx.recyclerview.widget.RecyclerView(target).apply {
                id = org.fossify.gallery.R.id.media_grid
            }
            root.addView(grid)
            root.addView(empty)
            GalleryBottomBar.prepareRootSurface(target, root)
            GalleryBottomBar.coordinateFirstDraw(target, root)
            assertTrue(root.viewTreeObserver.dispatchOnPreDraw())
            GalleryBottomBar.markRootContentReady(target, state)
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
            clock.idleFor(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertFalse("pre-draw is not a drawn frame", source.isFinishing)
            root.viewTreeObserver.dispatchOnDraw()
            clock.idle()
            assertTrue("draw acknowledgement finishes without another animation timer", source.isFinishing)
            assertEquals(0, GalleryBottomBar.pendingRootCount)
            GalleryBottomBar.dispose(target)
            GalleryBottomBar.dispose(source)
            targetController.pause().stop().destroy()
            sourceController.destroy()
        }
    }

    @Test fun timeoutRevealsThemedLoadingAndTeardownCancelsAcknowledgement() {
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        GalleryBottomBar.open(source, android.content.Intent(source, android.app.Activity::class.java), true)
        GalleryBottomBar.open(source, android.content.Intent(source, android.app.Activity::class.java), true)
        assertEquals(1, GalleryBottomBar.pendingRootCount)
        val intent = org.robolectric.Shadows.shadowOf(source).nextStartedActivity
        val controller = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup()
        val target = controller.get()
        val root = CoordinatorLayout(target)
        GalleryBottomBar.prepareRootSurface(target, root)
        var loadingFrames = 0
        GalleryBottomBar.coordinateFirstDraw(target, root) { loadingFrames++ }
        assertTrue(root.viewTreeObserver.dispatchOnPreDraw())
        clock.idleFor(GalleryBottomBar.FIRST_DRAW_LIMIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
        assertEquals(1, loadingFrames)
        assertEquals(255, android.graphics.Color.alpha((root.background as ColorDrawable).color))
        GalleryBottomBar.dispose(target)
        root.viewTreeObserver.dispatchOnDraw()
        clock.idleFor(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue(source.isFinishing)
        assertEquals(0, GalleryBottomBar.pendingRootCount)
        assertEquals(1, loadingFrames)
        GalleryBottomBar.dispose(source)
        controller.pause().stop().destroy()
    }

    @Test fun disabledAnimationsStillWaitForDrawButDoNotDelaySourceFinish() {
        val source = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val resolver = source.contentResolver
        val prior = android.provider.Settings.Global.getFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        android.provider.Settings.Global.putFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            GalleryBottomBar.open(source, android.content.Intent(source, android.app.Activity::class.java), true)
            assertFalse(source.isFinishing)
            val intent = org.robolectric.Shadows.shadowOf(source).nextStartedActivity
            val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
            val root = CoordinatorLayout(target)
            GalleryBottomBar.coordinateFirstDraw(target, root)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                GalleryBottomBar.FIRST_DRAW_LIMIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
            assertFalse(source.isFinishing)
            root.viewTreeObserver.dispatchOnDraw()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(source.isFinishing)
            assertEquals(0, GalleryBottomBar.pendingRootCount)
            GalleryBottomBar.dispose(target)
            target.finish()
        } finally {
            GalleryBottomBar.dispose(source)
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }


    @Test fun committedContentNeedsLayoutButDoesNotWaitForImageDecoding() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.intent.putExtra("org.fossify.gallery.ROOT_TRANSITION", true)
        val root = CoordinatorLayout(activity)
        val grid = androidx.recyclerview.widget.RecyclerView(activity).apply {
            id = org.fossify.gallery.R.id.media_grid
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
            adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun getItemCount() = 1
                override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int) =
                    object : androidx.recyclerview.widget.RecyclerView.ViewHolder(android.widget.ImageView(parent.context).apply {
                        layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(100, 100)
                    }) {}
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) = Unit
            }
        }
        root.addView(grid)
        GalleryBottomBar.coordinateFirstDraw(activity, root)
        GalleryBottomBar.markRootContentReady(activity)
        assertTrue("adapter itemCount alone cannot release the frame", root.viewTreeObserver.dispatchOnPreDraw())
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 800)
        assertTrue(grid.childCount > 0)
        assertNull((grid.getChildAt(0) as android.widget.ImageView).drawable)
        assertFalse("layout is ready even though thumbnail decode is pending", root.viewTreeObserver.dispatchOnPreDraw())
        root.viewTreeObserver.dispatchOnDraw()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        GalleryBottomBar.dispose(activity)
        activity.finish()
    }

    class TransitionRecordingActivity : android.app.Activity() {
        val transitions = mutableListOf<Pair<Int, Int>>()
        @Suppress("DEPRECATION")
        override fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {
            transitions.add(enterAnim to exitAnim)
            super.overridePendingTransition(enterAnim, exitAnim)
        }
    }

    @Test fun rootStartAndDeferredFinishNeverRequestASecondWindowAnimation() {
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        for (scale in listOf(0f, 1f)) {
            val source = Robolectric.buildActivity(TransitionRecordingActivity::class.java).setup().get()
            android.provider.Settings.Global.putFloat(source.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, scale)
            GalleryBottomBar.open(source, android.content.Intent(source, android.app.Activity::class.java), true)
            assertEquals(listOf(0 to 0), source.transitions)
            val intent = org.robolectric.Shadows.shadowOf(source).nextStartedActivity
            val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
            val root = CoordinatorLayout(target)
            GalleryBottomBar.coordinateFirstDraw(target, root)
            clock.idleFor(GalleryBottomBar.FIRST_DRAW_LIMIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
            assertFalse(source.isFinishing)
            root.viewTreeObserver.dispatchOnDraw()
            clock.idle()
            assertTrue(source.isFinishing)
            assertEquals(listOf(0 to 0, 0 to 0), source.transitions)
            GalleryBottomBar.dispose(source)
            GalleryBottomBar.dispose(target)
            target.finish()
        }
    }

}
