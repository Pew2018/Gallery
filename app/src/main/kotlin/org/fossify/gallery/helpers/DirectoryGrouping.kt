package org.fossify.gallery.helpers

import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.DirectoryStats
import java.io.File

/** Aggregation visits each physical row once per ancestor, never a synthetic row's totals. */
internal object DirectoryGrouping {
    fun opensMedia(directory: Directory, grouped: Boolean) =
        !grouped || (directory.containsMediaFilesDirectly && directory.subfoldersCount <= 1)

    fun within(child: String, parent: String) = child == parent || child.startsWith("${parent.trimEnd('/')}/")

    fun fill(source: List<Directory>, makeParent: (String, Directory) -> Directory): ArrayList<Directory> {
        val nodes = source.associateByTo(linkedMapOf()) { it.path }
        var candidates = source.mapNotNull { File(it.path).parent }.toSet()
        while (candidates.isNotEmpty()) {
            val next = linkedSetOf<String>()
            val counts = hashMapOf<String, Int>()
            val firstChildren = hashMapOf<String, Directory>()
            for (node in nodes.values) {
                val parent = File(node.path).parent ?: continue
                counts[parent] = (counts[parent] ?: 0) + 1
                firstChildren.putIfAbsent(parent, node)
            }
            for (path in candidates) {
                if (path != "/" && path !in nodes && (counts[path] ?: 0) > 1) {
                    val child = firstChildren.getValue(path)
                    nodes[path] = makeParent(path, child)
                    File(path).parent?.let(next::add)
                }
            }
            candidates = next
        }
        return ArrayList(nodes.values)
    }

    fun visible(source: List<Directory>, prefix: String,
        makeParent: (String, Directory) -> Directory): ArrayList<Directory> {
        val nodes = source.associateByTo(linkedMapOf()) { it.path }
        if (prefix.isNotEmpty()) {
            // A multi-level child still provides a navigable immediate child at this level.
            source.forEach { child ->
                if (child.path != prefix && within(child.path, prefix)) {
                    val part = child.path.removePrefix("${prefix.trimEnd('/')}/").substringBefore('/')
                    val path = "${prefix.trimEnd('/')}/$part"
                    if (path !in nodes) nodes[path] = makeParent(path, child)
                }
            }
            return nodes.values.filterTo(ArrayList()) { File(it.path).parent == prefix }
        }
        return nodes.values.filterTo(ArrayList()) { node ->
            var parent = File(node.path).parent
            var visible = true
            while (parent != null) {
                if (parent in nodes) { visible = false; break }
                parent = File(parent).parent
            }
            visible
        }
    }

    fun aggregate(source: List<Directory>, cards: List<Directory>, descending: Boolean = false) {
        val physical = source.filter { it.containsMediaFilesDirectly }.distinctBy { it.path }
        val byPath = cards.associateBy { it.path }
        val members = hashMapOf<String, MutableList<Directory>>()
        physical.forEach { child ->
            var path: String? = child.path
            while (path != null) {
                if (path in byPath) members.getOrPut(path) { arrayListOf() }.add(child)
                path = File(path).parent
            }
        }
        // Snapshot all inputs before changing cards (a parent may also be a source row).
        val sums = members.mapValues { (_, rows) ->
            val first = rows.first().stats
            val scopeMatches = rows.all { it.stats.scope == first.scope }
            var mask = if (scopeMatches) DirectoryStats.ALL else 0
            rows.forEach { mask = mask and it.stats.known }
            DirectoryStats(
                count = rows.sumOf { it.mediaCnt }, bytes = rows.sumOf { it.size },
                minModified = rows.minOf { it.modifiedForSort(false) },
                maxModified = rows.maxOf { it.modifiedForSort(true) },
                minTaken = rows.minOf { it.takenForSort(false) },
                maxTaken = rows.maxOf { it.takenForSort(true) },
                types = rows.fold(0) { acc, row -> acc or row.types }, known = mask,
                takenFallbackCount = rows.sumOf { it.stats.takenFallbackCount },
                scope = if (scopeMatches) first.scope else "", scannedAt = rows.minOf { it.stats.scannedAt }
            )
        }
        cards.forEach { card ->
            val rows = members[card.path].orEmpty()
            val sum = sums[card.path] ?: return@forEach
            card.subfoldersCount = rows.size
            card.subfoldersMediaCount = sum.count
            card.mediaCnt = sum.count
            card.size = sum.bytes
            card.modified = if (descending) sum.maxModified else sum.minModified
            card.taken = if (descending) sum.maxTaken else sum.minTaken
            card.types = sum.types
            card.stats = sum
        }
    }
}
