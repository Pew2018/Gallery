package org.fossify.gallery.helpers

/**
 * Semantic names for the photo shared-proxy rejection codes.
 *
 * The photo renderer rejected proxy creation with bare integers, which the
 * 2026-11-25 device log could only report as `viewer_transition_photo_unavailable
 * count=4`. The integers stay in the logcat event for continuity with the Phase 2 /
 * 2.1 dashboards; every rejection now also carries its name (and the state that
 * produced it) into the Viewer return diagnostics, so a fallback can be attributed
 * to a legitimate zoom/rotation/geometry state instead of being filed under the
 * undifferentiated `viewer_no_proxy`.
 */
internal object ViewerProxyRejection {
    const val SELECTION_UNAVAILABLE = "SELECTION_UNAVAILABLE"
    const val RENDERER_NOT_READY = "RENDERER_NOT_READY"
    const val ROTATION_ACTIVE = "ROTATION_ACTIVE"
    const val NOT_AT_FIT = "NOT_AT_FIT"
    const val GIF_RENDERER = "GIF_RENDERER"
    const val DRAWABLE_UNAVAILABLE = "DRAWABLE_UNAVAILABLE"
    const val GEOMETRY_UNAVAILABLE = "GEOMETRY_UNAVAILABLE"
    const val SUBSAMPLING_ZOOMED = "SUBSAMPLING_ZOOMED"
    const val SUBSAMPLING_GEOMETRY_UNAVAILABLE = "SUBSAMPLING_GEOMETRY_UNAVAILABLE"
    const val PROXY_PREPARE_REJECTED = "PROXY_PREPARE_REJECTED"

    /** The bare integers the photo renderer has always logged. */
    fun legacyCode(name: String): Int = when (name) {
        SELECTION_UNAVAILABLE -> 1
        RENDERER_NOT_READY -> 2
        ROTATION_ACTIVE -> 3
        NOT_AT_FIT -> 4
        GIF_RENDERER -> 5
        DRAWABLE_UNAVAILABLE -> 6
        GEOMETRY_UNAVAILABLE -> 7
        SUBSAMPLING_ZOOMED -> 8
        SUBSAMPLING_GEOMETRY_UNAVAILABLE -> 9
        else -> 0
    }

    fun name(legacyCode: Int): String = when (legacyCode) {
        1 -> SELECTION_UNAVAILABLE
        2 -> RENDERER_NOT_READY
        3 -> ROTATION_ACTIVE
        4 -> NOT_AT_FIT
        5 -> GIF_RENDERER
        6 -> DRAWABLE_UNAVAILABLE
        7 -> GEOMETRY_UNAVAILABLE
        8 -> SUBSAMPLING_ZOOMED
        9 -> SUBSAMPLING_GEOMETRY_UNAVAILABLE
        else -> PROXY_PREPARE_REJECTED
    }
}
