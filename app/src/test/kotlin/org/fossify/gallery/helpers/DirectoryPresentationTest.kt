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
}
