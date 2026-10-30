package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesReturnCoverTest {
    private lateinit var root: FrameLayout
    private lateinit var grid: RecyclerView
    private lateinit var cover: FavoritesReturnCover

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        root = FrameLayout(activity)
        activity.setContentView(root)
        grid = RecyclerView(activity)
        grid.layoutManager = LinearLayoutManager(activity)
        grid.setPadding(0, 0, 0, 40)
        grid.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 40
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = View(parent.context).apply { layoutParams = RecyclerView.LayoutParams(600, 100) }
                return object : RecyclerView.ViewHolder(view) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        root.addView(grid, FrameLayout.LayoutParams(600, 664).apply { topMargin = 56 })
        frame()
        cover = FavoritesReturnCover(activity)
        root.addView(cover, FrameLayout.LayoutParams(600, 800))
        cover.prepare(root, grid) { it >= 0 }
        cover.show(Color.WHITE, Color.BLACK, 0f)
        frame()
    }

    private fun frame() {
        val exact = View.MeasureSpec.EXACTLY
        root.measure(View.MeasureSpec.makeMeasureSpec(600, exact), View.MeasureSpec.makeMeasureSpec(800, exact))
        root.layout(0, 0, 600, 800)
        root.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun image(): Bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also {
        cover.draw(Canvas(it))
    }

    @Test fun clearedThumbnailAdapterStillHasAnOpaqueStructuredReturnFrame() {
        grid.adapter = null
        frame()
        val bitmap = image()
        assertEquals(Color.rgb(235, 235, 235), bitmap.getPixel(10, 100))
        assertEquals(255, Color.alpha(bitmap.getPixel(10, 500)))
        assertEquals(0, Color.alpha(bitmap.getPixel(10, 10))) // Toolbar remains untouched.
        assertEquals(0, Color.alpha(bitmap.getPixel(10, 750))) // Bottom bar remains untouched.
    }

    @Test fun darkThemeUsesDarkPlaceholdersInsteadOfAWhiteReturnFrame() {
        cover.show(Color.BLACK, Color.WHITE, 0f)
        assertEquals(Color.rgb(57, 57, 57), image().getPixel(10, 100))
    }

    @Test fun pauseCancelsAnOldFadeAndRestoresTheOpaqueSurface() {
        cover.reveal { true }
        cover.pause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertTrue(cover.covering)
        assertEquals(1f, cover.alpha, 0f)
        assertEquals(View.VISIBLE, cover.visibility)
    }

    @Test fun staleLoadCannotStartRevealingContent() {
        cover.reveal { false }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertTrue(cover.covering)
        assertEquals(1f, cover.alpha, 0f)
    }

    @Test fun loadingHasAFiniteRetryableTimeout() {
        var retried = false
        cover.armTimeout({ true }) { retried = true }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertTrue(cover.failed)
        cover.performClick()
        assertTrue(retried)
    }

    @Test fun staleTimeoutCannotReplaceTheCurrentSurfaceWithAnError() {
        cover.armTimeout({ false }) { fail("Stale retry") }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertFalse(cover.failed)
    }

    @Test fun coverBlocksOnlyTheMediaViewport() {
        val toolbar = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val media = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 100f, 0)
        assertFalse(cover.onTouchEvent(toolbar))
        assertTrue(cover.onTouchEvent(media))
        toolbar.recycle()
        media.recycle()
    }

    @Test fun dismissCancelsPendingTimeouts() {
        cover.armTimeout({ true }) { fail("Dismissed cover must not retry") }
        cover.dismiss()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertFalse(cover.covering)
        assertFalse(cover.failed)
        assertEquals(View.GONE, cover.visibility)
    }
}
