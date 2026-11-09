package org.fossify.gallery.helpers

/** Intent and timing contract for a folder card to its media page. */
object AlbumPageTransition {
    const val EXTRA_ACTIVE = "org.fossify.gallery.extra.ALBUM_TRANSITION_ACTIVE"
    const val EXTRA_COVER_PATH = "org.fossify.gallery.extra.ALBUM_TRANSITION_COVER_PATH"
    const val ELEMENT_NAME = "gallery_album_cover"
    const val DURATION_MS = 300L
    const val FADE_DURATION_MS = 170L
    const val READY_TIMEOUT_MS = 1_200L
}
