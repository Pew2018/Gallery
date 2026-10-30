package org.fossify.gallery.helpers

/** Shared by the entry point and viewer so a visible action always has playable media. */
internal object PhotoPlaybackPolicy {
    fun canPlay(type: Int, videos: Boolean, gifs: Boolean): Boolean =
        type and (TYPE_IMAGES or TYPE_PORTRAITS) != 0 ||
            (videos && type and TYPE_VIDEOS != 0) || (gifs && type and TYPE_GIFS != 0)

    fun toolbarScope(hasPublishedContent: Boolean): ToolbarQuickScope =
        if (hasPublishedContent) ToolbarQuickScope.FOLDER else ToolbarQuickScope.HOME
}
