package org.fossify.gallery.helpers

/** Intent and timing contract for a folder card to its media page. */
object AlbumPageTransition {
    const val EXTRA_ACTIVE = "org.fossify.gallery.extra.ALBUM_TRANSITION_ACTIVE"
    const val EXTRA_COVER_PATH = "org.fossify.gallery.extra.ALBUM_TRANSITION_COVER_PATH"
    const val ELEMENT_NAME = "gallery_album_cover"
    const val DURATION_MS = OxygenClassicMotion.MEDIA_SHARED_ELEMENT_MS
    const val FADE_DURATION_MS = OxygenClassicMotion.ALBUM_CONTENT_ENTER_MS
    const val READY_TIMEOUT_MS = 450L
}
