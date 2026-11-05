package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getDirsToShow
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.models.Directory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DirectoryPresentationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun directory(path: String, size: Long = 0, count: Int = 0) = Directory().apply {
        this.path = path
        name = path.substringAfterLast('/')
        this.size = size
        mediaCnt = count
        sortValue = "stale value from another sorting mode"
    }

    @Test fun changingSortUsesTheSelectedFieldsWithoutMutatingCachedKeys() {
        val a = directory("/root/Album 10", size = 100, count = 1).apply { modified = 30; taken = 50 }
        val b = directory("/root/Album 2", size = 20, count = 3).apply { modified = 40; taken = 10 }
        val source = arrayListOf(a, b)
        val config = context.config
        config.pinnedFolders = hashSetOf()
        for ((sorting, expected) in listOf(
            SORT_BY_NAME or SORT_USE_NUMERIC_VALUE to listOf(b.path, a.path),
            SORT_BY_PATH to listOf(a.path, b.path),
            SORT_BY_SIZE to listOf(b.path, a.path),
            SORT_BY_COUNT to listOf(a.path, b.path),
            SORT_BY_DATE_MODIFIED to listOf(a.path, b.path),
            SORT_BY_DATE_TAKEN to listOf(b.path, a.path),
            SORT_BY_SIZE or SORT_DESCENDING to listOf(a.path, b.path)
        )) {
            config.directorySorting = sorting
            assertEquals(expected, context.getSortedDirectories(source).map { it.path })
        }
        assertEquals("stale value from another sorting mode", a.sortValue)
        assertSame(a, source.first())
    }

    @Test fun intermediateBatchesKeepExistingPathsAndAppendOnlyNewCards() {
        val a = directory("/a")
        val b = directory("/b")
        val c = directory("/c")
        val d = directory("/d")
        val first = DirectoryPresentation.retainOrder(listOf(c, b, a), listOf(a, b))
        assertEquals(listOf("/a", "/b", "/c"), first.map { it.path })
        val second = DirectoryPresentation.retainOrder(listOf(d, c, a), first)
        assertEquals(listOf("/a", "/c", "/d"), second.map { it.path })
    }

    @Test fun randomOrderSurvivesRepeatedAndGrowingGroupedSubmissions() {
        val ranks = HashMap<String, Long>()
        context.config.directorySorting = SORT_BY_RANDOM
        val initial = (0..20).map { directory("/root/$it") }
        val first = context.getSortedDirectories(ArrayList(initial), ranks).map { it.path }
        repeat(10) { assertEquals(first, context.getSortedDirectories(ArrayList(initial.reversed()), ranks).map { it.path }) }
        val expanded = context.getSortedDirectories(ArrayList(initial + directory("/new")), ranks)
        assertEquals(first, expanded.filter { it.path != "/new" }.map { it.path })
    }

    @Test fun groupingCannotChangeAnAlreadyPublishedSnapshot() {
        context.config.groupDirectSubfolders = true
        context.config.directorySorting = SORT_BY_COUNT
        val a = directory("/root/shared/a", count = 2).apply { subfoldersMediaCount = 99 }
        val b = directory("/root/shared/b", count = 3)
        val source = arrayListOf(a, b)
        val before = source.map { it.snapshot() }
        val result = context.getDirsToShow(source, source, "", sortDirs = false)
        assertEquals(before, source)
        assertEquals(99, a.subfoldersMediaCount)
        assertTrue(result.any { it.path == "/root/shared" })
        assertEquals(5, result.first { it.path == "/root/shared" }.subfoldersMediaCount)
    }

    @Test fun snapshotIncludesIndependentGroupingAndPresentationStatus() {
        val source = directory("/root/a").apply { folderStatuses = "locked"; subfoldersMediaCount = 4 }
        val snapshot = source.snapshot()
        source.subfoldersMediaCount = 7
        source.folderStatuses = "hidden"
        assertEquals(4, snapshot.subfoldersMediaCount)
        assertEquals("locked", snapshot.folderStatuses)
        assertFalse(DirectoryPresentation.sameContents(listOf(source), listOf(snapshot)))
        assertFalse(DirectoryPresentation.sameContents(listOf(snapshot), listOf(snapshot.snapshot().apply {
            folderStatuses = "hidden"
        })))
    }

    @Test fun customOrderAndPinnedPrioritySurviveOrdinarySortTieChanges() {
        val dirs = arrayListOf(directory("/root/a"), directory("/root/b"), directory("/root/c"))
        context.config.directorySorting = SORT_BY_CUSTOM
        context.config.customFoldersOrder = "/root/c|||/root/a|||/root/b"
        context.config.pinnedFolders = hashSetOf("/root/b")
        repeat(8) {
            assertEquals(listOf("/root/b", "/root/c", "/root/a"),
                context.getSortedDirectories(ArrayList(dirs.shuffled())).map { it.path })
        }
        context.config.directorySorting = SORT_BY_COUNT or SORT_DESCENDING
        assertEquals(listOf("/root/b", "/root/a", "/root/c"), context.getSortedDirectories(dirs).map { it.path })
    }

    @Test fun sameSizedCatalogWithDifferentMembersIsNotAnIdenticalPresentation() {
        val before = listOf(directory("/root/a"), directory("/root/b"))
        val after = listOf(directory("/root/a"), directory("/root/c"))
        assertEquals(before.size, after.size)
        assertFalse(DirectoryPresentation.sameContents(before, after))
    }

    @Test fun twentyChangedCardsRetainEveryPathAndCurrentSortOrder() {
        context.config.pinnedFolders = hashSetOf()
        context.config.directorySorting = SORT_BY_NAME or SORT_USE_NUMERIC_VALUE
        val initial = (1..20).map { directory("/root/Album ${it.toString().padStart(2, '0')}") }
        var published = initial.map { it.snapshot() }
        // Model the production scan's eight-changed-card publication cadence.
        for (index in initial.indices) {
            initial[index].mediaCnt = 20 - index
            if ((index + 1) % 8 == 0 || index == initial.lastIndex) {
                val next = context.getSortedDirectories(ArrayList(initial.map { it.snapshot() }))
                assertEquals(initial.map { it.path }.sorted(), next.map { it.path })
                published = next.map { it.snapshot() }
            }
        }
        assertEquals(20, published.size)
        assertEquals(initial.map { it.path }.toSet(), published.map { it.path }.toSet())
    }

    @Test fun presentationPreparationBenchmarkReportsSyntheticDirectoryScales() {
        context.config.pinnedFolders = hashSetOf()
        val randomRanks = HashMap<String, Long>()
        val modes = listOf(
            "name" to (SORT_BY_NAME or SORT_USE_NUMERIC_VALUE),
            "date" to SORT_BY_DATE_MODIFIED,
            "size" to SORT_BY_SIZE,
            "random" to SORT_BY_RANDOM
        )
        for (size in listOf(100, 500, 1_000, 5_000)) {
            val source = (0 until size).map { index ->
                directory("/synthetic/Album ${index.toString().padStart(5, '0')}",
                    size = (size - index).toLong(), count = index % 37).apply {
                    modified = (size - index).toLong()
                    taken = index.toLong()
                    folderStatuses = if (index % 13 == 0) "excluded" else ""
                }
            }
            val samples = ArrayList<String>()
            for ((name, mode) in modes) {
                context.config.directorySorting = mode
                val runs = ArrayList<Long>()
                repeat(3) {
                    val started = System.nanoTime()
                    val sorted = context.getSortedDirectories(ArrayList(source), randomRanks)
                    val deduped = sorted.distinctBy { it.path }
                    val visible = context.getDirsToShow(deduped, deduped, "", randomRanks, sortDirs = false)
                    // Use the same case-insensitive name predicate as MainActivity's search preparation.
                    visible.filter { it.name.contains("Album 00", true) }
                    runs += (System.nanoTime() - started) / 1_000
                }
                samples += "$name=${runs.sorted()[1]}us"
            }
            println("AlbumPresentationSynthetic rows=$size medianOf3(sort+dedupe+visibility+search) ${samples.joinToString()}")
        }
    }

}
