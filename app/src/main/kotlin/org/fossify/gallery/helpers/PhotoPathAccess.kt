package org.fossify.gallery.helpers

import java.io.File

/** Request-local path/protection evidence, without the Favorites identity/stat snapshot. */
internal object PhotoPathAccess {
    data class Entry(val canonicalPath: String, val locks: Map<String, FavoriteAccessSessionManager.Protection>)

    fun capture(
        paths: Collection<String>,
        protection: (String) -> FavoriteAccessSessionManager.Protection?,
        cancelled: () -> Boolean = { false }
    ): Map<String, Entry> {
        val protections = HashMap<String, FavoriteAccessSessionManager.Protection?>()
        val ancestors = HashMap<String, Map<String, FavoriteAccessSessionManager.Protection>>()
        fun locks(parent: File?): Map<String, FavoriteAccessSessionManager.Protection> {
            if (parent == null) return emptyMap()
            return ancestors.getOrPut(parent.path) {
                val found = LinkedHashMap<String, FavoriteAccessSessionManager.Protection>()
                var folder: File? = parent
                while (folder != null) {
                    val path = folder.path
                    if (!protections.containsKey(path)) protections[path] = protection(path)
                    protections[path]?.let { found[path] = it }
                    folder = folder.parentFile
                }
                found
            }
        }
        val result = LinkedHashMap<String, Entry>()
        paths.forEach { path ->
            if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException("Photo path check cancelled")
            val raw = File(path)
            require(raw.isAbsolute)
            // Resolve each file, not just the directory: file symlinks may cross a lock boundary.
            val canonical = raw.canonicalPath
            result[path] = Entry(canonical, locks(raw.absoluteFile.parentFile) + locks(File(canonical).parentFile))
        }
        return result
    }
}
