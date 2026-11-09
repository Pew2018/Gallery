package org.fossify.gallery.helpers

/** Intent contract for the optional, photo-only grid-to-viewer shared element. */
object PhotoViewerTransition {
    const val EXTRA_PATH = "org.fossify.gallery.extra.PHOTO_VIEWER_TRANSITION_PATH"
    const val ELEMENT_NAME = "gallery_photo_content"
    const val READY_TIMEOUT_MS = 1_200L
}
