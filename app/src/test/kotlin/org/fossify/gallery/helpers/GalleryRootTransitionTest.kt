package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class GalleryRootTransitionTest {
    @Test fun coldRootWaitsForContentButHasABoundedDeadline() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            activity.intent.putExtra("org.fossify.gallery.ROOT_TRANSITION", true)
            val root = CoordinatorLayout(activity)
            activity.setContentView(root)
            GalleryBottomBar.attach(activity, root, GalleryBottomBar.Tab.ALL) {}
            assertTrue(root.viewTreeObserver.dispatchOnPreDraw())
            ShadowSystemClock.advanceBy(Duration.ofMillis(GalleryBottomBar.FIRST_DRAW_LIMIT_MS + 1))
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
            assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun resolvedEmptyAlbumAndOrdinaryLaunchDoNotWait() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            val ordinary = CoordinatorLayout(activity)
            activity.setContentView(ordinary)
            GalleryBottomBar.attach(activity, ordinary, GalleryBottomBar.Tab.ALL) {}
            assertFalse(ordinary.viewTreeObserver.dispatchOnPreDraw())
            activity.intent.putExtra("org.fossify.gallery.ROOT_TRANSITION", true)
            val empty = CoordinatorLayout(activity)
            empty.addView(TextView(activity).apply { id = R.id.directories_empty_placeholder; visibility = View.VISIBLE })
            activity.setContentView(empty)
            GalleryBottomBar.attach(activity, empty, GalleryBottomBar.Tab.FAVORITES) {}
            assertFalse(empty.viewTreeObserver.dispatchOnPreDraw())
        } finally { controller.pause().stop().destroy() }
    }
}
