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
        assertEquals(160L, GalleryBottomBar.ROOT_ENTER_DURATION_MS)
        assertTrue(GalleryBottomBar.ROOT_FINISH_FALLBACK_MS >=
            GalleryBottomBar.FIRST_DRAW_LIMIT_MS + GalleryBottomBar.ROOT_ENTER_DURATION_MS)
    }

    @Test fun sixTransitionsRespectDestinationForColdCacheAndRapidEntry() {
        val flows = listOf(
            GallerySection.ALL to GallerySection.PHOTOS,
            GallerySection.FAVORITES to GallerySection.PHOTOS,
            GallerySection.PHOTOS to GallerySection.ALL,
            GallerySection.PHOTOS to GallerySection.FAVORITES,
            GallerySection.ALL to GallerySection.PHOTOS,
            GallerySection.PHOTOS to GallerySection.ALL
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
}
