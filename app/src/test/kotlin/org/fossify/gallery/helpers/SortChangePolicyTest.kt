package org.fossify.gallery.helpers

import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SortChangePolicyTest {
    @Test fun repeatedRandomSelectionStillRequestsANewOrder() {
        assertTrue(SortChangePolicy.shouldApply(SORT_BY_RANDOM, SORT_BY_RANDOM))
        assertTrue(SortChangePolicy.shouldApply(SORT_BY_RANDOM, SORT_BY_RANDOM or
            org.fossify.commons.helpers.SORT_DESCENDING))
    }

    @Test fun unchangedDeterministicSelectionDoesNotCreateAnUpdate() {
        assertFalse(SortChangePolicy.shouldApply(SORT_BY_NAME, SORT_BY_NAME))
        assertTrue(SortChangePolicy.shouldApply(SORT_BY_NAME, SORT_BY_RANDOM))
        assertTrue(SortChangePolicy.shouldApply(SORT_BY_RANDOM, SORT_BY_NAME))
    }
}
