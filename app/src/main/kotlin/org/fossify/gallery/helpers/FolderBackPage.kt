package org.fossify.gallery.helpers

/**
 * A page whose back returns to the album grid, i.e. the only page that owns the
 * folder-specific post-commit CLOSE curve.
 *
 * The marker exists so the choice of window style is a property of the page rather
 * than a class check inside [OxygenClassicActivityMotion]: the folder exit is the only
 * one where the entering parent is guaranteed to be static, and the settings / theme /
 * custom-photo-profile returns must keep the shared `OxygenClassicPageBackWindow` pair
 * untouched.
 */
interface FolderBackPage {
    /** False for pages hosted by the folder grid that must keep the shared page motion. */
    val usesFolderBackMotion: Boolean
}
