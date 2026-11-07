package org.fossify.gallery.helpers

/** Pull-to-refresh is reserved for the unfiltered, collapsed list state. */
object SearchRefreshPolicy {
    fun isRefreshEnabled(searchOpen: Boolean, query: String): Boolean =
        !searchOpen && query.isEmpty()
}
