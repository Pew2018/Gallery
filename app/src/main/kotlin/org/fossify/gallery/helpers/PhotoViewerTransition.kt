package org.fossify.gallery.helpers

/** Intent and timing contract for a photo grid to full-screen shared element. */
object PhotoViewerTransition {
    const val EXTRA_PATH = "org.fossify.gallery.extra.PHOTO_VIEWER_TRANSITION_PATH"
    const val EXTRA_FADE_ONLY = "org.fossify.gallery.extra.PHOTO_VIEWER_FADE_ONLY"
    const val ELEMENT_NAME = "gallery_photo_content"
    const val DURATION_MS = 280L
    const val FADE_DURATION_MS = 170L
    const val READY_TIMEOUT_MS = 1_200L
    const val REQUEST_CODE = 9_071
    const val EXTRA_RETURN_PATH = "org.fossify.gallery.extra.MEDIA_RETURN_PATH"
}
