package org.fossify.gallery.helpers

import java.io.File

/** Folder visibility is independent from the persisted favorite relationship. */
object FavoriteFolderRules {
    /** Lexical normalization: never resolve symlinks or silently fold case on Android storage. */
    fun normalizedPath(path: String): String {
        if (path.isBlank()) return ""
        if ("://" in path) return path.trimEnd('/')
        return File(path).normalize().path.trimEnd('/').ifEmpty { "/" }
    }

    fun isExcluded(path: String, roots: Collection<String>): Boolean {
        val normalized = normalizedPath(path)
        if (normalized.isEmpty()) return false
        return roots.any { root ->
            val parent = normalizedPath(root)
            parent.isNotEmpty() &&
                (normalized == parent || normalized.startsWith(if (parent == "/") "/" else "$parent/"))
        }
    }

    fun isDotHidden(path: String): Boolean =
        normalizedPath(path).split('/').any { it.length > 1 && it.startsWith('.') }

    /** A .nomedia marker in the folder or any ancestor hides its media from MediaStore. */
    fun hasNoMediaAncestor(path: String): Boolean {
        val normalized = normalizedPath(path)
        if (!normalized.startsWith('/')) return false
        return generateSequence(File(normalized)) { it.parentFile }
            .any { folder -> File(folder, ".nomedia").isFile }
    }

    /** Caller must supply effective preferences *after* authentication gating. */
    fun shouldShow(hidden: Boolean, excluded: Boolean, showHidden: Boolean, showExcluded: Boolean): Boolean =
        (!hidden || showHidden) && (!excluded || showExcluded)
}
