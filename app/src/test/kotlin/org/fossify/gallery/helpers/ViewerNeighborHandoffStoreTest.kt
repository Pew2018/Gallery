package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ViewerNeighborHandoffStoreTest {
    private fun medium(path: String) = Medium(null, path.substringAfterLast('/'), path, "/gallery", 1, 1, 1, 1, 0, false, 0, 0)

    @Test fun handoffIsRandomSingleUseAndBoundToSourceAndSelectedPath() {
        val selected = "/gallery/selected.jpg"
        val window = ViewerNeighborWindow(
            selected, ViewerSourceType.FOLDER, "/gallery",
            listOf(medium("/gallery/previous.jpg"), medium(selected)), true
        )
        val id = ViewerNeighborHandoffStore.offer(window)!!
        assertNotEquals(selected, id)
        assertNull(ViewerNeighborHandoffStore.take(id, selected, ViewerSourceType.FAVORITES, "favorites"))
        assertNull(ViewerNeighborHandoffStore.take(id, selected, ViewerSourceType.FOLDER, "/gallery"))
    }

    @Test fun handoffReturnsCopiedBoundedMetadataOnlyOnce() {
        val selected = "/gallery/selected.jpg"
        val original = medium(selected)
        val id = ViewerNeighborHandoffStore.offer(
            ViewerNeighborWindow(selected, ViewerSourceType.PHOTO_PROFILE, "profile-id", listOf(original), false)
        )!!
        original.name = "changed.jpg"

        val taken = ViewerNeighborHandoffStore.take(id, selected, ViewerSourceType.PHOTO_PROFILE, "profile-id")!!
        assertEquals("selected.jpg", taken.items.single().name)
        assertNotSame(original, taken.items.single())
        assertTrue(taken.items.size <= ViewerNeighborWindow.MAX_ITEMS)
        assertNull(ViewerNeighborHandoffStore.take(id, selected, ViewerSourceType.PHOTO_PROFILE, "profile-id"))
        ViewerNeighborHandoffStore.clear(id)
    }
}
