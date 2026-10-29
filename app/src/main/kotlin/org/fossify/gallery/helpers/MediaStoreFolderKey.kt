package org.fossify.gallery.helpers

/**
 * MediaStore's directory index must preserve path case. Lowercasing keys can
 * merge separate folders on case-sensitive storage (and is locale-sensitive).
 * Normalize only lexical segments and trailing separators.
 */
internal object MediaStoreFolderKey {
    fun of(path: String): String = FavoriteFolderRules.normalizedPath(path)
}
