package org.fossify.gallery.helpers

/** Pure visibility rules shared by the cached and explicit-favorite paths. */
object FavoriteFolderRules {
    fun isExcluded(path: String, roots: Collection<String>): Boolean =
        roots.any { root ->
            val normalized = root.trimEnd('/')
            normalized.isNotEmpty() && (path == normalized || path.startsWith("$normalized/"))
        }

    /** Saved visibility preferences must still be gated by caller authentication. */
    fun shouldShow(hidden: Boolean, excluded: Boolean, showHidden: Boolean, showExcluded: Boolean): Boolean =
        (!hidden || showHidden) && (!excluded || showExcluded)
}
