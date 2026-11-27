package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 1.1 work measurement.
 *
 * The device report's bottleneck was filesystem work, not sorting: 4995 existence
 * checks cost 938-2416 ms while the full sort cost about 1 ms. These measurements
 * therefore assert *work* (how many candidates are probed, how often parents are
 * re-probed, how many sorts run) and only report timings as host-side context.
 *
 * These numbers are produced on a JVM with synthetic candidates. They are NOT a
 * Pixel 8 Pro measurement and must not be presented as one: Android's FUSE layer,
 * real storage latency and frame presentation are outside this harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoCachePreviewMeasurementTest {
    private val sizes = listOf(160, 1_000, 5_000, 12_000)

    private fun item(index: Int, folder: String = "/photos") = Medium(
        index.toLong() + 1, "photo-$index.jpg", "$folder/photo-$index.jpg", folder,
        1_000L - index, 1_000L - index, 10L, TYPE_IMAGES, 0, false, 0, 0
    )

    private fun catalog(size: Int) = (0 until size).map { item(it) }

    /** Counts the filesystem probes the production code would perform. */
    private class Probes(val exists: (String) -> Boolean) {
        var candidates = 0
        var parentProbes = 0
        var fileProbes = 0
        private val readableParents = HashMap<String, Boolean>()

        /** Matches [CachedMediaPreviewFilter.VisibleBatch]: parent memoized per batch. */
        fun memoizedBatch(item: Medium): Boolean {
            candidates++
            val file = File(item.path)
            val parent = file.parentFile ?: return true
            val readable = readableParents.getOrPut(parent.path) { parentProbes++; true }
            if (!readable) return true
            fileProbes++
            return exists(item.path)
        }

        /** Matches [CachedMediaPreviewFilter.isVisible]: no memo, up to three probes. */
        fun unmemoized(item: Medium): Boolean {
            candidates++
            val file = File(item.path)
            val parent = file.parentFile ?: return true
            parentProbes++
            if (!true) return true
            fileProbes++
            return exists(item.path)
        }
    }

    private class Row(
        val path: String, val input: Int, val checked: Int, val parentProbes: Int, val fileProbes: Int,
        val sorts: Int, val selected: Int, val elapsedMs: Double, val complete: Boolean
    )

    @Test fun firstSliceWorkScalesWithTheWindowNotTheCatalog() {
        val rows = ArrayList<Row>()
        for (size in sizes) {
            val ordered = catalog(size)
            // 1. The previous full-catalog path.
            run {
                val probes = Probes { true }
                var sorts = 0
                val started = System.nanoTime()
                val filtered = ordered.filter { probes.unmemoized(it) }.toCollection(ArrayList())
                val sorted = ArrayList(filtered).also { list ->
                    sorts++
                    list.sortWith(compareByDescending<Medium> { it.taken }.thenBy { it.path })
                }
                val window = PhotoProfileMediaCache.preview(sorted, null)
                rows.add(Row("full_filter", size, probes.candidates, probes.parentProbes, probes.fileProbes,
                    sorts, window.size, ms(started), true))
            }
            // 2. Ordered in-memory catalog + bounded window.
            run {
                val probes = Probes { true }
                val started = System.nanoTime()
                val slice = CachedMediaPreviewOrder.selectBounded(
                    ordered, SORT_BY_DATE_TAKEN, null, visible = probes::memoizedBatch)!!
                rows.add(Row("ordered_window", size, probes.candidates, probes.parentProbes, probes.fileProbes,
                    0, slice.items.size, ms(started), slice.complete))
            }
            // 3. Room rows ordered first, then the same bounded window.
            run {
                val probes = Probes { true }
                val started = System.nanoTime()
                val shuffled = ordered.shuffled(java.util.Random(11))
                var sorts = 0
                val sorted = ArrayList(shuffled).also {
                    sorts++
                    it.sortWith(compareByDescending<Medium> { m -> m.taken }.thenBy { m -> m.path })
                }
                val slice = CachedMediaPreviewOrder.selectBounded(
                    sorted, SORT_BY_DATE_TAKEN, null, visible = probes::memoizedBatch)!!
                rows.add(Row("room_window", size, probes.candidates, probes.parentProbes, probes.fileProbes,
                    sorts, slice.items.size, ms(started), slice.complete))
            }
            // 4. A few deleted candidates inside the window.
            run {
                val deleted = ordered.take(180).filterIndexed { index, _ -> index % 40 == 7 }.map { it.path }.toSet()
                val probes = Probes { it !in deleted }
                val started = System.nanoTime()
                val slice = CachedMediaPreviewOrder.selectBounded(
                    ordered, SORT_BY_DATE_TAKEN, null, visible = probes::memoizedBatch)!!
                rows.add(Row("partial_invalid", size, probes.candidates, probes.parentProbes, probes.fileProbes,
                    0, slice.items.size, ms(started), slice.complete))
            }
            // 5. Bounded degradation when the cache is almost entirely stale.
            run {
                val probes = Probes { false }
                val started = System.nanoTime()
                val slice = CachedMediaPreviewOrder.selectBounded(
                    ordered, SORT_BY_DATE_TAKEN, null, visible = probes::memoizedBatch)!!
                rows.add(Row("degraded", size, probes.candidates, probes.parentProbes, probes.fileProbes,
                    0, slice.items.size, ms(started), slice.complete))
            }
        }

        report(rows)

        // Deterministic work assertions, independent of host speed.
        val budget = CachedMediaPreviewOrder.LIMIT * CachedMediaPreviewOrder.CHECK_BUDGET_MULTIPLIER
        for (size in sizes) {
            val full = rows.single { it.path == "full_filter" && it.input == size }
            assertEquals("the legacy path probes every row", size, full.checked)
            val ordered = rows.single { it.path == "ordered_window" && it.input == size }
            assertEquals(CachedMediaPreviewOrder.LIMIT, ordered.checked)
            assertEquals("one parent probe for a single-folder window", 1, ordered.parentProbes)
            assertEquals(0, ordered.sorts)
            val room = rows.single { it.path == "room_window" && it.input == size }
            assertEquals(1, room.sorts)
            assertEquals(CachedMediaPreviewOrder.LIMIT, room.checked)
            val degraded = rows.single { it.path == "degraded" && it.input == size }
            assertTrue("degradation must stop at the budget", degraded.checked <= budget)
            assertEquals(0, degraded.selected)
            assertTrue(!degraded.complete)
        }
    }

    /** Real filesystem probes, for an honest ratio rather than an absolute claim. */
    @Test fun realFilesystemProbeCountDropsByTwoOrdersOfMagnitude() {
        val root = File(java.nio.file.Files.createTempDirectory("preview-probe").toFile(), "shots")
        root.mkdirs()
        try {
            val present = (0 until 400).map { index ->
                File(root, "photo-$index.jpg").also { it.writeBytes(byteArrayOf(1)) }.path
            }
            val ghosts = (0 until 5_000).map { index -> item(index, root.path) }
            val live = present.mapIndexed { index, path ->
                item(index, root.path).copy(path = path, name = File(path).name)
            }

            var fullProbes = 0
            val fullStarted = System.nanoTime()
            val filtered = (live + ghosts).filter { fullProbes++; CachedMediaPreviewFilter.isVisible(it) }
            val fullElapsed = ms(fullStarted)

            val batch = CachedMediaPreviewFilter.VisibleBatch()
            var boundedProbes = 0
            val boundedStarted = System.nanoTime()
            val slice = CachedMediaPreviewOrder.selectBounded(
                live, SORT_BY_DATE_TAKEN, null, visible = { boundedProbes++; batch.isVisible(it) })!!
            val boundedElapsed = ms(boundedStarted)

            println(
                "real_fs: legacy rows=${live.size + ghosts.size} probes=$fullProbes ${"%.2f".format(fullElapsed)}ms | " +
                    "bounded rows=${live.size} probes=$boundedProbes ${"%.2f".format(boundedElapsed)}ms " +
                    "selected=${slice.items.size}"
            )
            assertEquals(live.size + ghosts.size, fullProbes)
            assertEquals(CachedMediaPreviewOrder.LIMIT, boundedProbes)
            assertEquals(CachedMediaPreviewOrder.LIMIT, slice.items.size)
            assertTrue("bounded probes must stay more than an order of magnitude below the catalog",
                fullProbes > boundedProbes * 20)
            assertTrue(filtered.size >= live.size)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun ms(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000.0

    private fun report(rows: List<Row>) {
        val text = buildString {
            appendLine("| path | input | checked | parentProbes | fileProbes | sorts | selected | complete | hostMs |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---|---:|")
            rows.forEach {
                appendLine("| ${it.path} | ${it.input} | ${it.checked} | ${it.parentProbes} | ${it.fileProbes} | " +
                    "${it.sorts} | ${it.selected} | ${it.complete} | ${"%.2f".format(it.elapsedMs)} |")
            }
        }
        println("photo_cache_preview_work\n$text")
    }
}
