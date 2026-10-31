package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class PhotoContentRevisionTest {
    @Test fun readingDoesNotDirtyAndMutationDoes() {
        val first = PhotoContentRevision.current()
        assertEquals(first, PhotoContentRevision.current())
        assertTrue(PhotoContentRevision.changed() > first)
        assertEquals(first + 1, PhotoContentRevision.current())
    }
}
