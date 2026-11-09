package org.fossify.gallery.helpers

import android.app.Application
import android.widget.FrameLayout
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DirectoryPageHeroTransitionTest {
    @Test fun itemSnapshotKeepsItsPositionAndHasABoundedBitmap() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val container = FrameLayout(activity)
        val card = View(activity)
        container.addView(card, FrameLayout.LayoutParams(500, 500))
        activity.setContentView(container)
        container.layout(0, 0, 1080, 2400)
        card.layout(120, 360, 620, 860)

        val snapshot = DirectoryPageHeroTransition.capture(card, container)
        assertNotNull(snapshot)
        snapshot!!
        assertTrue(maxOf(snapshot.bitmap.width, snapshot.bitmap.height) <= 720)
        assertEquals(120f, snapshot.startBounds.left, 0f)
        assertEquals(360f, snapshot.startBounds.top, 0f)
        snapshot.dispose()
        activity.finish()
    }

    @Test fun pageSnapshotUsesTheSameAllocationLimit() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val container = FrameLayout(activity)
        activity.setContentView(container)
        container.layout(0, 0, 1080, 2400)

        val snapshot = DirectoryPageHeroTransition.capturePage(container)
        assertNotNull(snapshot)
        snapshot!!
        assertTrue(maxOf(snapshot.bitmap.width, snapshot.bitmap.height) <= 720)
        snapshot.dispose()
        activity.finish()
    }
}
