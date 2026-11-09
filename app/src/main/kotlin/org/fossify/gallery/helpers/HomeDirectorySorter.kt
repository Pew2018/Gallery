package org.fossify.gallery.helpers

import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.gallery.models.Directory
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Sorting for the MainActivity album grid only; picker and media views keep their own paths. */
internal object HomeDirectorySorter {
    data class Options(
        val sorting: Int,
        val customFoldersOrder: String,
        val pinnedFolders: Set<String>,
        val showRecycleBin: Boolean,
        val showRecycleBinLast: Boolean,
        val randomOrder: MutableMap<String, Long>,
        val locale: Locale = Locale.getDefault()
    )

    data class Result(
        val directories: ArrayList<Directory>,
        val keyPreparationNanos: Long,
        val sortingNanos: Long
    )

    fun sort(
        source: List<Directory>,
        options: Options,
        onKeyPrepared: (Directory) -> Unit = {}
    ): Result {
        val started = System.nanoTime()
        val directories = ArrayList(source)
        if (options.sorting and SORT_BY_RANDOM != 0) {
            directories.forEach { directory ->
                checkCancellation()
                options.randomOrder.getOrPut(directory.path) { Random.nextLong() }
            }
            directories.sortWith(compareBy<Directory> { options.randomOrder[it.path] }
                .thenBy { it.path })
            return Result(prioritize(directories, options), 0L, System.nanoTime() - started)
        }

        if (options.sorting and SORT_BY_CUSTOM != 0) {
            val remaining = directories.associateByTo(LinkedHashMap()) { it.path }
            val ordered = ArrayList<Directory>(directories.size)
            options.customFoldersOrder.split("|||").forEach { path ->
                checkCancellation()
                remaining.remove(path)?.let(ordered::add)
            }
            ordered.addAll(remaining.values)
            return Result(prioritize(ordered, options), 0L, System.nanoTime() - started)
        }

        val numeric = options.sorting and SORT_USE_NUMERIC_VALUE != 0
        val descending = options.sorting and SORT_DESCENDING != 0
        val keyStarted = System.nanoTime()
        val keyed = directories.mapIndexed { index, directory ->
            checkCancellation()
            val textKey = when {
                options.sorting and SORT_BY_PATH != 0 -> directory.path.lowercase(options.locale)
                options.sorting and SORT_BY_NAME != 0 -> directory.name.normalizeString().lowercase(options.locale)
                else -> null
            }
            val pathKey = FavoriteFolderRules.normalizedPath(directory.path).lowercase(Locale.ROOT)
            onKeyPrepared(directory)
            PreparedDirectory(directory, index, textKey, pathKey)
        }.toMutableList()
        val keyPreparationNanos = System.nanoTime() - keyStarted
        val comparator = if (numeric && keyed.any { it.textKey != null }) AlphanumericComparator() else null
        val sortStarted = System.nanoTime()
        keyed.sortWith { first, second ->
            checkCancellation()
            val primary = when {
                first.textKey != null && second.textKey != null && comparator != null ->
                    comparator.compare(first.textKey, second.textKey)
                first.textKey != null && second.textKey != null -> first.textKey.compareTo(second.textKey)
                options.sorting and SORT_BY_SIZE != 0 -> first.directory.size.compareTo(second.directory.size)
                options.sorting and SORT_BY_COUNT != 0 -> first.directory.mediaCnt.compareTo(second.directory.mediaCnt)
                options.sorting and SORT_BY_DATE_MODIFIED != 0 -> first.directory.modifiedForSort(descending).compareTo(second.directory.modifiedForSort(descending))
                else -> first.directory.takenForSort(descending).compareTo(second.directory.takenForSort(descending))
            }
            val orderedPrimary = if (descending) primary.compareTo(0).let { -it } else primary.compareTo(0)
            if (orderedPrimary != 0) orderedPrimary else {
                val pathOrder = first.pathKey.compareTo(second.pathKey)
                if (pathOrder != 0) pathOrder else {
                    val exactPathOrder = first.directory.path.compareTo(second.directory.path)
                    if (exactPathOrder != 0) exactPathOrder else first.originalIndex.compareTo(second.originalIndex)
                }
            }
        }
        val sorted = ArrayList(keyed.map { it.directory })
        val result = prioritize(sorted, options)
        return Result(result, keyPreparationNanos, System.nanoTime() - sortStarted)
    }

    fun filterForSearch(source: List<Directory>, query: String): ArrayList<Directory> {
        checkCancellation()
        if (query.isEmpty()) return ArrayList(source)
        return source.withIndex()
            .filter { checkCancellation(); it.value.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<IndexedValue<Directory>> { !it.value.name.startsWith(query, ignoreCase = true) }
                .thenBy { it.index })
            .mapTo(ArrayList()) { it.value }
    }

    private data class PreparedDirectory(
        val directory: Directory,
        val originalIndex: Int,
        val textKey: String?,
        val pathKey: String
    )

    private fun prioritize(source: ArrayList<Directory>, options: Options): ArrayList<Directory> {
        val favorites = source.filter { it.path == FAVORITES }
        val recycleBin = if (options.showRecycleBin) source.filter { it.isRecycleBin() } else emptyList()
        val pinned = source.filter {
            it.path != FAVORITES && (!options.showRecycleBin || !it.isRecycleBin()) && it.path in options.pinnedFolders
        }
        val regular = source.filter {
            it.path != FAVORITES && (!options.showRecycleBin || !it.isRecycleBin()) && it.path !in options.pinnedFolders
        }
        return ArrayList<Directory>(source.size).apply {
            addAll(favorites)
            if (options.showRecycleBin && !options.showRecycleBinLast) addAll(recycleBin)
            addAll(pinned)
            addAll(regular)
            if (options.showRecycleBin && options.showRecycleBinLast) addAll(recycleBin)
        }
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }
}
