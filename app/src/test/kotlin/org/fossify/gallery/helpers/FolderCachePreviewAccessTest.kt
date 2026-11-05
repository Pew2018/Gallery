package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.extensions.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, manifest = Config.NONE,
    shadows = [PhotoProfileMediaLoadingTest.ScopedEnvironment::class])
class FolderCachePreviewAccessTest {
    @Test fun scopedStorageWithoutPersistedGrantIsNotFullFolderCacheAuthorization() {
        val context = RuntimeEnvironment.getApplication()
        val access = FolderCachePreviewAccess.capture(context, "/storage/emulated/0/Pictures")
        assertFalse(access())
    }

    @Test fun sortingDoesNotInvalidateVisibilityButFiltersDo() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPrefs().edit().clear().commit()
        val original = FolderCachePreviewAccess.configuration(context, "/folder")
        context.config.saveCustomSorting("/folder", org.fossify.commons.helpers.SORT_BY_SIZE)
        assertEquals(original, FolderCachePreviewAccess.configuration(context, "/folder"))
        context.config.filterMedia = TYPE_VIDEOS
        assertNotEquals(original, FolderCachePreviewAccess.configuration(context, "/folder"))
    }
}
