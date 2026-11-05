package org.fossify.gallery.helpers

import org.fossify.commons.helpers.SORT_BY_RANDOM

/** Random is an operation as well as a stored mode, so confirming it again reshuffles. */
internal object SortChangePolicy {
    fun shouldApply(oldSorting: Int, newSorting: Int): Boolean =
        oldSorting != newSorting || newSorting and SORT_BY_RANDOM != 0
}
