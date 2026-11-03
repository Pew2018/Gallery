package org.fossify.gallery.helpers

import java.io.File
import java.util.concurrent.CancellationException

internal data class FolderDiscoveryResult(
    val folders: Set<String>,
    val unreadablePaths: Set<String>,
    val truncated: Boolean = false
) {
    val complete get() = unreadablePaths.isEmpty() && !truncated
}

/** Bounded breadth-first discovery. A policy skip is intentional; an unreadable source is not empty. */
internal object FolderDiskDiscovery {
    fun find(
        roots: Collection<String>, allowed: (String) -> Boolean, cancelled: () -> Boolean,
        maxFolders: Int = 10_000,
        listFiles: (File) -> Array<File>? = { it.listFiles() }
    ): FolderDiscoveryResult {
        require(maxFolders > 0)
        val result = linkedSetOf<String>()
        val unreadable = linkedSetOf<String>()
        val visited = hashSetOf<String>()
        fun checkCancellation() { if (cancelled()) throw CancellationException("Directory discovery retired") }
        for (root in roots.distinct()) {
            checkCancellation()
            if (!allowed(root)) continue
            val start = File(root)
            val boundary = try { start.canonicalPath } catch (_: Exception) { unreadable.add(root); continue }
            if (!allowed(boundary)) continue
            if (!start.isDirectory) { unreadable.add(root); continue }
            val queue = java.util.ArrayDeque<File>().apply { add(start) }
            while (queue.isNotEmpty()) {
                checkCancellation()
                val folder = queue.removeFirst()
                val canonical = try { folder.canonicalPath } catch (_: Exception) { unreadable.add(folder.path); continue }
                if (canonical != boundary && !canonical.startsWith("${boundary.trimEnd('/')}/")) continue
                // Check both names: a symlink must not bypass a protected canonical path.
                if (!allowed(folder.absolutePath) || !allowed(canonical) || !visited.add(canonical)) continue
                if (result.size >= maxFolders) {
                    checkCancellation()
                    return FolderDiscoveryResult(result, unreadable, truncated = true)
                }
                result.add(folder.absolutePath)
                val children = try { listFiles(folder) } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    null
                }
                if (children == null) { unreadable.add(folder.path); continue }
                for (child in children) {
                    checkCancellation()
                    if (allowed(child.absolutePath) && child.isDirectory) {
                        // Bound the frontier as well as visited directories.
                        if (queue.size + result.size >= maxFolders) {
                            checkCancellation()
                            return FolderDiscoveryResult(result, unreadable, truncated = true)
                        }
                        queue.add(child)
                    }
                }
            }
        }
        checkCancellation()
        return FolderDiscoveryResult(result, unreadable)
    }
}
