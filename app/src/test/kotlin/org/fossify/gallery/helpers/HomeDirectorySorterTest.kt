package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getDirsToShow
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.models.Directory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HomeDirectorySorterTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun directory(path: String, size: Long = 0, count: Int = 0, modified: Long = 0, taken: Long = 0) =
        Directory().apply {
            this.path = path
            name = path.substringAfterLast('/')
            this.size = size
            mediaCnt = count
            this.modified = modified
            this.taken = taken
        }

    private fun options(
        sorting: Int,
        custom: String = "",
        pinned: Set<String> = emptySet(),
        random: MutableMap<String, Long> = HashMap(),
        showRecycleBin: Boolean = false,
        recycleBinLast: Boolean = false
) = HomeDirectorySorter.Options(
        sorting, custom, pinned, showRecycleBin, recycleBinLast, random, Locale.US
    )

    @Test fun computesOneNormalizedKeyPerDirectoryAndNaturallySortsNamesAndPaths() {
        val names = listOf(directory("/root/Album 10"), directory("/root/Album 2"), directory("/root/Album 1"))
        var prepared = 0
        val result = HomeDirectorySorter.sort(names, options(SORT_BY_NAME or SORT_USE_NUMERIC_VALUE)) {
            prepared++
        }
        assertEquals(names.size, prepared)
        assertEquals(listOf("/root/Album 1", "/root/Album 2", "/root/Album 10"), result.directories.map { it.path })

        val paths = listOf(directory("/Root/album 10"), directory("/root/Album 2"))
        assertEquals(listOf("/root/Album 2", "/Root/album 10"),
            HomeDirectorySorter.sort(paths, options(SORT_BY_PATH or SORT_USE_NUMERIC_VALUE)).directories.map { it.path })
        assertEquals(0, HomeDirectorySorter.sort(emptyList(), options(SORT_BY_NAME)).directories.size)
        assertEquals(listOf("/single"), HomeDirectorySorter.sort(listOf(directory("/single")), options(SORT_BY_NAME)).directories.map { it.path })
    }

    @Test fun sortsSizeCountAndDatesInBothDirectionsWithDeterministicTies() {
        val rows = listOf(
            directory("/z", size = 7, count = 2, modified = 4, taken = 8),
            directory("/b", size = 7, count = 1, modified = 3, taken = 8),
            directory("/a", size = 2, count = 1, modified = 3, taken = 1)
        )
        val cases = listOf(
            SORT_BY_SIZE to listOf("/a", "/b", "/z"),
            SORT_BY_SIZE or SORT_DESCENDING to listOf("/b", "/z", "/a"),
            SORT_BY_COUNT to listOf("/a", "/b", "/z"),
            SORT_BY_COUNT or SORT_DESCENDING to listOf("/z", "/a", "/b"),
            SORT_BY_DATE_MODIFIED to listOf("/a", "/b", "/z"),
            SORT_BY_DATE_MODIFIED or SORT_DESCENDING to listOf("/z", "/a", "/b"),
            SORT_BY_DATE_TAKEN to listOf("/a", "/b", "/z"),
            SORT_BY_DATE_TAKEN or SORT_DESCENDING to listOf("/b", "/z", "/a")
        )
        cases.forEach { (mode, expected) ->
            val actual = HomeDirectorySorter.sort(rows.reversed(), options(mode)).directories.map { it.path }
            assertEquals("mode=$mode", expected, actual)
        }
    }

    @Test fun preservesCustomAndRandomOrderAndAppliesPinnedPriority() {
        val rows = listOf(directory("/a"), directory("/b"), directory("/c"))
        val custom = HomeDirectorySorter.sort(
            rows, options(SORT_BY_CUSTOM, custom = "/c|||/a|||/missing", pinned = setOf("/a"))
        ).directories.map { it.path }
        assertEquals(listOf("/a", "/c", "/b"), custom)

        val ranks = mutableMapOf("/a" to 3L, "/b" to 1L, "/c" to 2L)
        val randomOptions = options(SORT_BY_RANDOM, pinned = setOf("/c"), random = ranks)
        val random = HomeDirectorySorter.sort(rows, randomOptions).directories.map { it.path }
        assertEquals(listOf("/c", "/b", "/a"), random)
        assertEquals(random, HomeDirectorySorter.sort(rows.reversed(), randomOptions).directories.map { it.path })
        assertTrue(ranks.keys.containsAll(rows.map { it.path }))
    }

    @Test fun tiesDoNotDependOnInputOrderAndGroupedParentsUseTheSameSorter() {
        val ties = listOf(directory("/root/Z"), directory("/root/a"), directory("/root/B"))
        val expected = listOf("/root/a", "/root/B", "/root/Z")
        assertEquals(expected, HomeDirectorySorter.sort(ties, options(SORT_BY_COUNT)).directories.map { it.path })
        assertEquals(expected, HomeDirectorySorter.sort(ties.reversed(), options(SORT_BY_COUNT)).directories.map { it.path })

        context.config.groupDirectSubfolders = true
        val children = arrayListOf(
            directory("/root/Z/one", count = 1), directory("/root/Z/two", count = 1),
            directory("/root/a/one", count = 1), directory("/root/a/two", count = 1)
        )
        val grouped = context.getDirsToShow(
            children, children, "/root", sortDirs = true, groupDirectSubfolders = true,
            directorySorter = { HomeDirectorySorter.sort(it, options(SORT_BY_NAME)).directories }
        )
        assertEquals(listOf("/root/a", "/root/Z"), grouped.map { it.path })
    }

    @Test fun searchKeepsPrefixMatchesFirstAndStableOrderWithinEachBucket() {
        val rows = listOf(directory("/root/Alpha match"), directory("/root/Beta Alpha"), directory("/root/Alpine match"))
        assertEquals(
            listOf("Alpha match", "Alpine match", "Beta Alpha"),
            HomeDirectorySorter.filterForSearch(rows, "al").map { it.name }
        )
        assertEquals(rows.map { it.path }, HomeDirectorySorter.filterForSearch(rows, "").map { it.path })
        assertTrue(HomeDirectorySorter.filterForSearch(rows, "missing").isEmpty())
    }

    @Test fun syntheticSorterComparisonReportsBaselineAndPreparedSortStages() {
        context.config.pinnedFolders = hashSetOf()
        val modes = listOf(
            "name-natural" to (SORT_BY_NAME or SORT_USE_NUMERIC_VALUE),
            "path-natural" to (SORT_BY_PATH or SORT_USE_NUMERIC_VALUE),
            "date" to SORT_BY_DATE_MODIFIED,
            "size" to SORT_BY_SIZE,
            "count" to SORT_BY_COUNT,
            "random" to SORT_BY_RANDOM,
            "custom" to SORT_BY_CUSTOM
        )
        for (count in listOf(100, 500, 1_000, 5_000)) {
            val rows = (0 until count).map { index ->
                directory("/synthetic/Album ${index.toString().padStart(5, '0')}",
                    size = (count - index).toLong(), count = index % 37,
                    modified = (count - index).toLong(), taken = index.toLong())
            }
            for ((label, mode) in modes) {
                context.config.directorySorting = mode
                context.config.customFoldersOrder = rows.asReversed().joinToString("|||") { it.path }
                val ranks = rows.associate { it.path to (it.path.hashCode().toLong()) }.toMutableMap()
                val baseline = ArrayList<Long>()
                val keyPreparation = ArrayList<Long>()
                val optimizedSort = ArrayList<Long>()
                repeat(5) {
                    var started = System.nanoTime()
                    context.getSortedDirectories(ArrayList(rows), ranks)
                    baseline += System.nanoTime() - started
                    val result = HomeDirectorySorter.sort(rows, options(mode, custom = context.config.customFoldersOrder, random = ranks))
                    keyPreparation += result.keyPreparationNanos
                    optimizedSort += result.sortingNanos
                }
                fun median(values: List<Long>) = values.sorted()[values.size / 2] / 1_000
                println("HomeDirectorySorterBenchmark count=$count mode=$label baseline_sort=${median(baseline)}us " +
                    "key_prepare=${median(keyPreparation)}us optimized_sort=${median(optimizedSort)}us")
            }
        }
    }
}
