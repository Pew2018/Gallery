package org.fossify.gallery.helpers

import java.io.File

/** Discover new child albums only below known, visible, authorized roots. Never follow escaping symlinks. */
internal object FolderDiskDiscovery {
    fun find(roots: Collection<String>, allowed: (String) -> Boolean, cancelled: () -> Boolean): Set<String> {
        val result = linkedSetOf<String>()
        val visited = hashSetOf<String>()
        for (root in roots) {
            if (cancelled()) break
            val start = File(root)
            if (!start.isDirectory) continue
            val boundary = runCatching { start.canonicalPath }.getOrNull() ?: continue
            val queue = java.util.ArrayDeque<File>().apply { add(start) }
            while (queue.isNotEmpty() && !cancelled()) {
                val folder = queue.removeFirst()
                val canonical = runCatching { folder.canonicalPath }.getOrNull() ?: continue
                if (canonical != boundary && !canonical.startsWith("$boundary/")) continue
                if (!allowed(folder.absolutePath) || !visited.add(canonical)) continue
                result.add(folder.absolutePath)
                // An inaccessible child is a partial discovery, not a global crash.
                // Never recurse into a symlink escaping the canonical root.
                runCatching { folder.listFiles() }.getOrNull()?.forEach {
                    if (!cancelled() && it.isDirectory) queue.add(it)
                }
            }
        }
        return result
    }
}
