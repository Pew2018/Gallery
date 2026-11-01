package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class FavoriteViewerCatalogStoreTest {
    @Test fun randomHandoffIdIsOneUseAndDoesNotCarryAuthorization() {
        val context = RuntimeEnvironment.getApplication()
        val media = arrayListOf(Medium(null, "a.jpg", "/Camera/a.jpg", "/Camera", 1, 1, 1,
            org.fossify.commons.helpers.TYPE_IMAGES, 0, true, 0, 1))
        val id = FavoriteViewerCatalogStore.prepare(context, media, setOf("/Camera/a.jpg")) { true }
        assertNotNull(id)
        media.first().name = "changed-after-handoff"

        assertNull(FavoriteViewerCatalogStore.take(context, "forged-id"))
        val catalog = FavoriteViewerCatalogStore.take(context, id)
        assertEquals("a.jpg", catalog?.items?.single()?.name)
        assertEquals(setOf("/Camera/a.jpg"), catalog?.favoritePaths)
        assertNull(FavoriteViewerCatalogStore.take(context, id))
    }
}
