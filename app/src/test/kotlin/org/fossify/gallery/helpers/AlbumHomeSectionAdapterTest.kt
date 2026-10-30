package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AlbumHomeSectionAdapterTest {
    @Test
    fun unchangedOtherAlbumCountDoesNotDispatchRedundantAdapterUpdates() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val adapter = AlbumOtherTitleAdapter(activity)
        var inserts = 0
        var changes = 0
        var removals = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { inserts++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { changes++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { removals++ }
        })

        adapter.submit(4)
        adapter.submit(4)
        adapter.submit(3)
        adapter.submit(0)

        assertEquals(1, inserts)
        assertEquals(1, changes)
        assertEquals(1, removals)
        assertTrue(adapter.hasStableIds())
        assertTrue(adapter.getItemId(0) != RecyclerView.NO_ID)
    }
}
