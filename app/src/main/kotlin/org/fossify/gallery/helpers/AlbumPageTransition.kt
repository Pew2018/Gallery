package org.fossify.gallery.helpers

/** Marker for the optional album-cover to media-grid Activity transition. */
object AlbumPageTransition {
    const val EXTRA_ACTIVE = "org.fossify.gallery.extra.ALBUM_PAGE_TRANSITION"
    const val EXTRA_COVER_PATH = "org.fossify.gallery.extra.ALBUM_COVER_PATH"
    const val ELEMENT_NAME = "gallery_album_content"
    const val READY_TIMEOUT_MS = 1_200L
    const val DURATION_MS = 260L
}
