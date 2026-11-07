package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRefreshPolicyTest {
    @Test fun refreshRemainsDisabledWhileSearchIsOpenEvenWithAnEmptyQuery() {
        assertFalse(SearchRefreshPolicy.isRefreshEnabled(searchOpen = true, query = ""))
        assertFalse(SearchRefreshPolicy.isRefreshEnabled(searchOpen = true, query = "photo"))
    }

    @Test fun refreshReturnsOnlyAfterSearchIsClosedAndCleared() {
        assertFalse(SearchRefreshPolicy.isRefreshEnabled(searchOpen = false, query = "photo"))
        assertTrue(SearchRefreshPolicy.isRefreshEnabled(searchOpen = false, query = ""))
    }
}
