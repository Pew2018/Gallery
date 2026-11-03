package org.fossify.gallery.helpers

/** A selected folder is an explicit media source, even under a .nomedia ancestor.
 * Dot paths and excluded roots remain hidden; authorization is always checked separately.
 * An explicit source does not grant this exception to unrelated Favorites entries.
 */
internal object PhotoProfileVisibilityPolicy {
    fun visible(folder: String, explicitFolder: Boolean, excluded: Collection<String>, noMedia: List<String>): Boolean =
        folder.startsWith('/') && !folder.contains("/.") &&
            !FavoriteFolderRules.isExcluded(folder, excluded) &&
            (explicitFolder || (!FavoriteFolderRules.hasNoMediaAncestor(folder) &&
                noMedia.none { folder == it || folder.startsWith("$it/") }))
}
