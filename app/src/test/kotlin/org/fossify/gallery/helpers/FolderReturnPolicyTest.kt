package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderReturnPolicyTest {
    private fun allowed(
        public: Boolean = true,
        full: Boolean = true,
        settings: Boolean = true,
        store: Boolean = true,
        folder: Boolean = true,
        viewer: Boolean = true,
        age: Long = 3000
    ) = FolderReturnPolicy.canReuse(public, full, settings, store, folder, viewer, age)

    @Test fun onlyUnchangedCompletePublicAlbumCanReuse() {
        assertTrue(allowed())
        assertFalse(allowed(public = false))
        assertFalse(allowed(full = false))
        assertFalse(allowed(settings = false))
        assertFalse(allowed(store = false))
        assertFalse(allowed(folder = false))
        assertFalse(allowed(viewer = false))
        assertFalse(allowed(age = -1))
        assertFalse(allowed(age = 60001))
    }

    @Test fun operationsInvalidateCheckpoints() {
        val old = FolderContentRevision.current()
        FolderContentRevision.changed()
        assertTrue(FolderContentRevision.current() > old)
    }
}
