package org.fossify.gallery.helpers

/** Evidence for the displayed rows, not a claim that the source scan is complete.
 * Activity sourceVersion and search/sort generations additionally guard every adapter submit.
 */
internal data class FavoriteInteractiveSnapshot(
    val request: Long,
    val relationshipRevision: Long,
    val relationships: Set<String>,
    val validation: FavoriteAccessSessionManager.ValidatedPaths
) {
    fun isCurrent(displayedRequest: Long, token: String?, revision: Long): Boolean =
        request == displayedRequest && validation.token == token && relationshipRevision == revision &&
            FavoriteAccessSessionManager.isPublicationCurrent(validation)
}
