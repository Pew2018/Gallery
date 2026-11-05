package org.fossify.gallery.helpers

/** Empty results from a progressing scan are provisional, not a final no-match state. */
internal object MediaSearchPublicationPolicy {
    fun showNoResults(rowsEmpty: Boolean, sourceComplete: Boolean): Boolean = rowsEmpty && sourceComplete
}
