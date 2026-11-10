package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.RelativeLayout
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, application = Application::class)
class MediaSharedSurfaceTest {
    @Test fun fitUsesTheAvailableCanvasAndNeverChangesAspectRatio() {
        assertEquals(RectF(0f, 300f, 400f, 500f), MediaTransitionGeometry.fit(400, 800, 800, 400))
        assertEquals(RectF(100f, 0f, 300f, 800f), MediaTransitionGeometry.fit(400, 800, 200, 800))
        assertEquals(RectF(0f, 200f, 400f, 600f), MediaTransitionGeometry.fit(400, 800, 1000, 1000))
        assertEquals(RectF(0f, 350f, 400f, 450f), MediaTransitionGeometry.fit(400, 800, 1600, 400))
        assertNull(MediaTransitionGeometry.fit(0, 800, 800, 400))
        assertNull(MediaTransitionGeometry.fit(400, 800, 0, 0))
    }

    @Test fun proxySharesOnlyContentAndRestoresRenderersOnEveryExit() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val parent = RelativeLayout(controller.get())
            controller.get().setContentView(parent)
            val renderer = View(controller.get()).apply { alpha = .7f; setBackgroundColor(Color.BLACK) }
            parent.addView(renderer)
            val surface = MediaSharedSurface(parent)
            val image = surface.prepare(ColorDrawable(Color.RED), RectF(0f, 300f, 400f, 500f), renderer)!!
            assertEquals(400, image.width)
            assertEquals(200, image.height)
            assertEquals(300, image.top)
            assertNull(image.background)
            assertEquals(0f, renderer.alpha)
            assertSame(image, surface.prepare(ColorDrawable(Color.BLUE), RectF(0f, 0f, 20f, 20f), renderer))
            surface.clear()
            surface.clear()
            assertEquals(.7f, renderer.alpha)
            assertEquals(1, parent.childCount)
        } finally { controller.pause().stop().destroy() }
    }
}
