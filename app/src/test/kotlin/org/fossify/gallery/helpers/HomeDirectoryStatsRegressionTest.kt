package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.DirectoryStats
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HomeDirectoryStatsRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        context.getSharedPrefs().edit().clear().commit()
        context.config.filterMedia = TYPE_IMAGES
        context.config.pinnedFolders = hashSetOf()
        context.config.showRecycleBinAtFolders = false
    }
    private fun card(path: String, dates: List<Long>, size: Long = 3): Directory {
        val media = dates.mapIndexed { i, date -> Medium().apply {
            this.path = "$path/$i.jpg"; modified = date; taken = date; this.size = size; type = TYPE_IMAGES
        } }
        val stats = DirectoryStats.calculate(media, "test", DirectoryStats.ALL)
        return Directory().apply {
            this.path = path; name = path.substringAfterLast('/'); mediaCnt = stats.count
            this.size = stats.bytes; modified = stats.maxModified; taken = stats.maxTaken; this.stats = stats
        }
    }
    @Test fun directionSwitchesUseBothExtremaWithoutRebuildingStats() {
        val a = card("/A", listOf(1, 100))
        val b = card("/B", listOf(50, 60))
        val original = a.stats
        for (field in listOf(SORT_BY_DATE_MODIFIED, SORT_BY_DATE_TAKEN)) {
            for (direction in listOf(SORT_DESCENDING, 0, SORT_DESCENDING, 0)) {
                context.config.directorySorting = field or direction
                assertEquals(listOf("/A", "/B"), context.getSortedDirectories(arrayListOf(b, a)).map { it.path })
            }
        }
        assertSame(original, a.stats)
    }
    @Test fun dateBubblesUseTheSelectedExtremumJustLikeTheComparator() {
        val row = card("/A", listOf(86_400_000L, 17_280_000_000L))
        for (descending in listOf(false, true)) {
            val flag = if (descending) SORT_DESCENDING else 0
            val date = if (descending) row.stats.maxModified else row.stats.minModified
            assertEquals(date.formatDate(context, "yyyy-MM-dd", null),
                row.getBubbleText(SORT_BY_DATE_MODIFIED or flag, context, "yyyy-MM-dd", null))
            assertEquals(date.formatDate(context), row.getBubbleText(SORT_BY_DATE_TAKEN or flag, context))
        }
    }

    @Test fun groupWithDirectMembersUsesOneRangeForAllSortFields() {
        val a = card("/root/A", listOf(1))
        val child = card("/root/A/child", List(100) { 1000 })
        val b = card("/root/B", listOf(500, 500))
        val rows = arrayListOf(a, child, b)
        for (field in listOf(SORT_BY_COUNT, SORT_BY_SIZE, SORT_BY_DATE_MODIFIED, SORT_BY_DATE_TAKEN)) {
            context.config.directorySorting = field or SORT_DESCENDING
            val cards = context.getDirsToShow(rows, rows, "/root", groupDirectSubfolders = true)
            assertEquals(listOf(a.path, b.path), cards.map { it.path })
            val grouped = cards.first()
            assertEquals(101, grouped.mediaCnt)
            assertEquals(grouped.mediaCnt, grouped.subfoldersMediaCount)
            assertEquals(303L, grouped.size)
            assertEquals(1L, grouped.stats.minModified)
            assertEquals(1000L, grouped.modified)
            assertEquals(1000L, grouped.taken)
        }
        assertEquals(1, a.mediaCnt)
    }
    @Test fun syntheticParentsNestedChildrenAndCurrentEntryDoNotDoubleCount() {
        val rows = arrayListOf(card("/root/A/one", listOf(1)), card("/root/A/two/deep", listOf(10, 20)),
            card("/root/B", listOf(5)))
        context.config.directorySorting = SORT_BY_COUNT or SORT_DESCENDING
        val cards = context.getDirsToShow(rows, rows, "/root", groupDirectSubfolders = true)
        val a = cards.first { it.path == "/root/A" }
        assertEquals(3, a.mediaCnt)
        assertEquals(9L, a.size)
        assertEquals(20L, a.modified)
        assertFalse(a.containsMediaFilesDirectly)
        assertFalse(DirectoryGrouping.opensMedia(a, true))
        val nested = context.getDirsToShow(rows, rows, "/root/A", groupDirectSubfolders = true)
        assertEquals(setOf("/root/A/one", "/root/A/two"), nested.map { it.path }.toSet())
        val direct = card("/root/A", listOf(9))
        val withDirect = ArrayList(rows + direct)
        val entered = context.getDirsToShow(withDirect, withDirect, "/root/A", groupDirectSubfolders = true)
        val current = entered.first { it.path == direct.path }
        assertEquals(1, current.mediaCnt)
        assertEquals(1, current.subfoldersCount)
        assertEquals(1, current.subfoldersMediaCount)
        assertTrue(DirectoryGrouping.opensMedia(current, true))
        val empty = card("/root/A", emptyList()).apply { containsMediaFilesDirectly = false }
        val withoutDirect = ArrayList(rows + empty)
        assertFalse(context.getDirsToShow(withoutDirect, withoutDirect, "/root/A", groupDirectSubfolders = true)
            .any { it.path == empty.path })
    }
    @Test fun identitiesRespectCaseAndDirectoryBoundaries() {
        assertNotEquals("/Pictures/Travel".getDistinctPath(), "/Pictures/travel".getDistinctPath())
        val rows = arrayListOf(card("/Pictures/Travel", listOf(1)), card("/Pictures/travel", listOf(2)),
            card("/Pictures/TravelExtra", listOf(3)))
        val grouped = context.getDirsToShow(rows, rows, "/Pictures", groupDirectSubfolders = true)
        assertEquals(3, grouped.size)
        assertTrue(grouped.all { it.mediaCnt == 1 })
        assertFalse(DirectoryGrouping.within("/AB/child", "/A"))
        assertTrue(DirectoryGrouping.within("/A/child", "/A"))
    }
    @Test fun unknownAndTrueZeroAreDifferentAndScopesCannotMerge() {
        val known = card("/A", listOf(1, 100)).stats
        val partial = DirectoryStats.calculate(listOf(Medium()), known.scope, DirectoryStats.COUNT)
        val merged = DirectoryStats.retainUnknown(partial, known)
        assertEquals(known.bytes, merged.bytes)
        assertEquals(known.minTaken, merged.minTaken)
        assertFalse(merged.has(DirectoryStats.SIZE))
        assertFalse(merged.has(DirectoryStats.TAKEN))
        assertEquals(0L, DirectoryStats.retainUnknown(partial.copy(scope = "different"), known).bytes)
        val zero = DirectoryStats.calculate(emptyList(), "test", DirectoryStats.ALL)
        assertTrue(zero.has(DirectoryStats.ALL))
        assertEquals(0L, zero.bytes)
        assertFalse(DirectoryStats().has(DirectoryStats.ALL))
    }
    @Test fun confirmedSdcardSymlinkAliasStillDeduplicatesWithoutFoldingCase() {
        val root = java.nio.file.Files.createTempDirectory("directory-alias").toFile()
        try {
            val storage = java.io.File(root, "storage/emulated/0").apply { mkdirs() }
            val alias = java.io.File(root, "sdcard")
            java.nio.file.Files.createSymbolicLink(alias.toPath(), storage.toPath())
            assertEquals(java.io.File(storage, "Pictures/Travel").path.getDistinctPath(),
                java.io.File(alias, "Pictures/Travel").path.getDistinctPath())
            assertTrue(java.io.File(storage, "Pictures/Travel").path.isThisOrParentIncluded(mutableSetOf(alias.path)))
            assertTrue(java.io.File(alias, "Pictures/Travel").path.isThisOrParentExcluded(mutableSetOf(storage.path)))
            assertFalse("/Pictures/travel/one.jpg".isThisOrParentIncluded(mutableSetOf("/Pictures/Travel")))
            assertFalse("/Pictures/TravelExtra".isThisOrParentExcluded(mutableSetOf("/Pictures/Travel")))
            assertNotEquals(java.io.File(alias, "Pictures/Travel").path.getDistinctPath(),
                java.io.File(alias, "Pictures/travel").path.getDistinctPath())
        } finally { root.deleteRecursively() }
    }

    @Test fun customCoverBypassesGroupingAndDefaultCoverKeepsInternalGroupOrder() {
        val root = java.nio.file.Files.createTempDirectory("directory-covers").toFile()
        try {
            val a = java.io.File(root, "a.jpg").apply { writeBytes(byteArrayOf(1)) }
            val b = java.io.File(root, "b.jpg").apply { writeBytes(byteArrayOf(2)) }
            val rows = arrayListOf(Medium().apply { path = a.path; name = a.name; type = TYPE_IMAGES; taken = 1; gridPosition = 7 },
                Medium().apply { path = b.path; name = b.name; type = TYPE_IMAGES; taken = 100000000; gridPosition = 8 })
            context.config.saveFolderGrouping(root.path, GROUP_BY_DATE_TAKEN_DAILY or GROUP_DESCENDING)
            val custom = arrayListOf(org.fossify.gallery.models.AlbumCover(root.path, a.path))
            assertFalse(context.directoryCoverNeedsMediaOrder(root.path, custom))
            assertTrue(context.directoryCoverNeedsMediaOrder(root.path, emptyList()))
            val card = context.createDirectoryFromMedia(root.path, rows, custom, "hidden", mutableSetOf(), true, arrayListOf())
            assertEquals(a.path, card.tmb)
            assertEquals(listOf(7, 8), rows.map { it.gridPosition })
            val expected = MediaFetcher(context).groupMedia(ArrayList(rows.map { it.copy() }), root.path)
                .filterIsInstance<Medium>().first().path
            val default = context.createDirectoryFromMedia(root.path, rows, arrayListOf(), "hidden", mutableSetOf(), true, arrayListOf())
            assertEquals(expected, default.tmb)
        } finally { root.deleteRecursively() }
    }

    @Test fun takenFallbackIsExplicitAndStatisticsDoNotDependOnMediaOrder() {
        val rows = listOf(Medium().apply { modified = 100; taken = 100; size = 7; takenIsFallback = true },
            Medium().apply { modified = 1; taken = 5; size = 2 })
        val forward = DirectoryStats.calculate(rows, "test", DirectoryStats.ALL, 1)
        assertEquals(forward, DirectoryStats.calculate(rows.reversed(), "test", DirectoryStats.ALL, 1))
        assertEquals(1, forward.takenFallbackCount)
    }
}
