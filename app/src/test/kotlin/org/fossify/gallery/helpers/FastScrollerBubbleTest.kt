package org.fossify.gallery.helpers

import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.gallery.R
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class FastScrollerBubbleTest {
    @Test fun trackAndThumbShareOneVerticalSessionAndCancelStalePopupHides() {
        exerciseScroller(vertical = true)
    }

    @Test fun horizontalTrackJumpUsesVisibleRowsAndCancelsOnDetach() {
        exerciseScroller(vertical = false)
    }

    private fun exerciseScroller(vertical: Boolean) {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        controller.setup()
        val scroller = RecyclerViewFastScroller(activity)
        val recycler = RecyclerView(activity)
        val orientation = if (vertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL
        val manager = LinearLayoutManager(activity, orientation, false)
        recycler.layoutManager = manager
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>(), RecyclerViewFastScroller.OnPopupTextUpdate {
            override fun onChange(position: Int) = "visible-$position"
            override fun getItemCount() = 80
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return object : RecyclerView.ViewHolder(View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(56, 56)
                }) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        scroller.addView(recycler, 0, ViewGroup.LayoutParams(-1, -1))
        scroller.attachFastScrollerToRecyclerView(recycler)
        scroller.setScrollVertically(vertical)
        val thumb = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
        val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
        val popup = scroller.popupTextView
        val states = mutableListOf<Boolean>()
        OxygenFastScroller.installGalleryTouchHandling(scroller, recycler) { states += it }
        activity.setContentView(scroller)
        controller.visible()
        val main = Shadows.shadowOf(Looper.getMainLooper())
        main.idle()
        scroller.measure(View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        scroller.layout(0, 0, 440, 600)
        OxygenFastScroller.setRangeAvailable(scroller, true)
        popup.scaleX = 0f
        popup.scaleY = 0f
        popup.alpha = 0f
        assertEquals(View.INVISIBLE, popup.visibility)

        fun touch(view: View, action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
            try {
                assertTrue(view.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }

        touch(thumb, MotionEvent.ACTION_DOWN, thumb.width / 2f, thumb.height / 2f)
        assertEquals(listOf(true), states)
        assertEquals(View.VISIBLE, popup.visibility)
        assertEquals(1f, popup.scaleX, 0f)
        assertEquals(1f, popup.scaleY, 0f)
        assertEquals(1f, popup.alpha, 0f)
        assertEquals("visible-0", popup.text.toString())
        OxygenFastScroller.installGalleryTouchHandling(scroller, recycler) { error("existing drag replaced") }
        touch(thumb, MotionEvent.ACTION_MOVE, if (vertical) 12f else 200f, if (vertical) 230f else 12f)
        assertTrue(manager.findFirstVisibleItemPosition() > 0)
        assertEquals("visible-${manager.findFirstVisibleItemPosition()}", popup.text.toString())
        touch(thumb, MotionEvent.ACTION_UP, 12f, 12f)
        main.idleFor(400, TimeUnit.MILLISECONDS)
        assertEquals(View.VISIBLE, popup.visibility)

        // Dispatch directly to the track beyond the thumb's current position.
        val x = if (vertical) 4f else track.width - 8f
        val y = if (vertical) track.height - 8f else 4f
        touch(track, MotionEvent.ACTION_DOWN, x, y)
        main.idleFor(400, TimeUnit.MILLISECONDS)
        assertEquals(listOf(true, false, true), states)
        assertEquals(View.VISIBLE, popup.visibility)
        assertEquals("visible-${manager.findFirstVisibleItemPosition()}", popup.text.toString())
        if (vertical) {
            touch(track, MotionEvent.ACTION_UP, x, y)
            main.idleFor(651, TimeUnit.MILLISECONDS)
        } else {
            OxygenFastScroller.disposeGalleryTouchHandling(scroller)
        }
        assertEquals(listOf(true, false, true, false), states)
        assertEquals(View.INVISIBLE, popup.visibility)
        OxygenFastScroller.setRangeAvailable(scroller, true)
        assertEquals(View.INVISIBLE, popup.visibility)
        controller.pause().stop().destroy()
    }

    @Test
    fun sectionUsesFirstFollowingMedia() {
        val items = listOf(section("2024"), medium(), medium())

        assertEquals(1, FastScrollerBubble.findMediaIndex(items, 0))
        assertEquals(2, FastScrollerBubble.findMediaIndex(items, 2))
    }

    @Test
    fun trailingOrConsecutiveSectionsUseNearestPreviousMedia() {
        val items = listOf(medium(), section("empty"), section("trailing"))

        assertEquals(0, FastScrollerBubble.findMediaIndex(items, 1))
        assertEquals(0, FastScrollerBubble.findMediaIndex(items, 2))
    }

    @Test
    fun emptyListsAndInvalidPositionsHaveNoMedia() {
        val items: List<ThumbnailItem> = listOf(section("empty"))

        assertEquals(null, FastScrollerBubble.findMediaIndex(emptyList(), 0))
        assertEquals(null, FastScrollerBubble.findMediaIndex(items, -1))
        assertEquals(null, FastScrollerBubble.findMediaIndex(items, 1))
        assertEquals(null, FastScrollerBubble.findMediaIndex(items, 0))
    }

    @Test
    fun zeroDateFallsBackAndHistoricalNegativeDatesRemainValid() {
        var formatterCalled = false
        val zero = FastScrollerBubble.dateOrFallback(0L, "photo.jpg") {
            formatterCalled = true
            "1970"
        }
        assertEquals("photo.jpg", zero)
        assertFalse(formatterCalled)

        val historical = FastScrollerBubble.dateOrFallback(-315619200000L, "photo.jpg") {
            formatterCalled = true
            "1960"
        }
        assertEquals("1960", historical)
        assertTrue(formatterCalled)
        assertEquals("photo.jpg", FastScrollerBubble.dateOrFallback(1L, "photo.jpg") { "" })
        assertEquals("photo.jpg", FastScrollerBubble.dateOrFallback(1L, "photo.jpg") {
            throw IllegalArgumentException("invalid date")
        })
        assertEquals("2026", FastScrollerBubble.dateOrFallback(1L, "photo.jpg") { "2026" })
    }

    @Test
    fun blankNameUsesFileNameFromPathThenUnknownLabel() {
        assertEquals("photo.jpg", FastScrollerBubble.displayName("", "/DCIM/photo.jpg", "Unknown"))
        assertEquals("Unknown", FastScrollerBubble.displayName("", "/", "Unknown"))
    }

    private fun medium() = Medium()
    private fun section(title: String) = ThumbnailSection(title)
}
