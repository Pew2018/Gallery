package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchPublicationPolicyTest {
    @Test fun partialEmptySearchRemainsProvisionalUntilCompletion() {
        assertFalse(MediaSearchPublicationPolicy.showNoResults(rowsEmpty = true, sourceComplete = false))
        assertTrue(MediaSearchPublicationPolicy.showNoResults(rowsEmpty = true, sourceComplete = true))
        assertFalse(MediaSearchPublicationPolicy.showNoResults(rowsEmpty = false, sourceComplete = true))
    }
}
