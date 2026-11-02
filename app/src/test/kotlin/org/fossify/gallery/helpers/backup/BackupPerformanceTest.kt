package org.fossify.gallery.helpers.backup

import android.Manifest
import android.app.Application
import androidx.room.Room
import com.google.gson.GsonBuilder
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Favorite
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Reproducible host measurements, NOT Android device latency or UI performance assertions.
 * No time thresholds: correctness is asserted; raw nanosecond observations are uploaded by CI.
 * The fixed inspector isolates planner/Room costs. Real host filesystem checks are separate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupPerformanceTest {
    @Test fun measureThreeScalesWithoutChangingProductionAuthorizationOrTransactions() {
        val executor = Executors.newSingleThreadExecutor()
        try { executor.submit { measure() }.get(15, TimeUnit.MINUTES) } finally { executor.shutdownNow() }
    }

    private fun measure() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val rows = arrayListOf<Map<String, Any>>()
        val root = Files.createTempDirectory("backup-perf").toFile()
        fun <T> sample(size: Int, cycle: Int, phase: String, action: () -> T): T {
            val start = System.nanoTime()
            return action().also { rows.add(linkedMapOf("media" to size, "cycle" to cycle, "phase" to phase,
                "nanoseconds" to System.nanoTime() - start, "mainThread" to (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()))) }
        }
        try {
            for ((size, folderCount, profileCount) in listOf(Triple(100, 10, 3), Triple(1000, 50, 10), Triple(10000, 200, 30))) {
                val folderPaths = (0 until folderCount).map { File(root, "$size/folder-$it").apply { mkdirs() }.absolutePath }
                val paths = (0 until size).map { File(folderPaths[it % folderCount], "image-$it.jpg").apply { writeText("fixture") }.absolutePath }
                val profiles = (0 until profileCount).map {
                    backupProfile(UUID.nameUUIDFromBytes("profile-$it".toByteArray()).toString(), "Profile $it", folderPaths[it % folderCount])
                }
                for (cycle in 0 until 5) {
                    context.getSharedPrefs().edit().clear().commit()
                    GalleryRestoreJournal(context).delete()
                    val db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).allowMainThreadQueries().build()
                    try {
                        // The same half-existing dataset and authorization state on each cycle.
                        db.runInTransaction { paths.take(size / 2).forEach { db.FavoritesDao().insert(Favorite(null, it, File(it).name, File(it).parent!!)) } }
                        val snapshot = fullBackupFixture(context, paths, folderPaths.mapIndexed { index, path -> FavoriteBackupCodec.FolderRecord(path, index.toLong()) },
                            PhotoProfileState(profiles = profiles, selectedId = profiles.first().id), mapOf("oxygen_classic_appearance" to "dark"))
                        val zip = File(root, "$size.zip")
                        sample(size, cycle, "zip_export_including_validation") { GalleryBackupZipCodec.encode(snapshot, zip) }
                        sample(size, cycle, "zip_inspect_including_crc_sha") { GalleryBackupZipCodec.inspect(zip) }
                        val decoded = sample(size, cycle, "zip_decode_including_inspect") { GalleryBackupZipCodec.decode(zip.inputStream(), context.cacheDir) }
                        assertEquals(snapshot, decoded)
                        sample(size, cycle, "sha256_favorites_only") { BackupDigest.sha256(FavoriteBackupCodec.encodeMediaPaths(paths).toByteArray()) }
                        sample(size, cycle, "filesystem_metadata_only") { paths.forEach { assertTrue(File(it).isFile && File(it).canRead()) } }
                        sample(size, cycle, "protection_snapshot_only") { FavoriteAccessSessionManager.captureSnapshot(paths.toSet(), context::favoriteProtection) }
                        val requests = paths.mapTo(linkedSetOf()) { BackupPathRequest(it, false) }
                        sample(size, cycle, "device_batch_including_protection") { assertTrue(DeviceBackupPathAccess(context).inspectBatch(requests).values.all { it.status() == BackupPathStatus.AVAILABLE }) }
                        val local = sample(size, cycle, "collector_snapshot") { GalleryBackupCollector(context, db).capture() }
                        val access = BackupPathAccess { _, _ -> BackupPathStatus.AVAILABLE }
                        val plan = sample(size, cycle, "planner_fixed_access") { GalleryRestorePlanner(context, access).plan(decoded, local) }
                        assertEquals(size / 2, plan.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
                        // Compare the former List membership formula and Set membership on identical targets.
                        // Include Set construction in its timing; production reuses the Set it already creates.
                        for (mode in GalleryRestoreMode.entries) {
                            val target = plan.targets.getValue(mode)
                            val old = local.snapshot.mediaFavorites.toSet()
                            val reference = sample(size, cycle, "removed_count_list_reference_$mode") { old.count { it !in target.media } }
                            val indexed = sample(size, cycle, "removed_count_set_including_build_$mode") {
                                val index = target.media.toSet()
                                old.count { it !in index }
                            }
                            assertEquals(reference, indexed)
                            assertEquals(reference, target.mediaCount.removed)
                        }
                        var preferenceNanos = 0L
                        var relationStart = 0L
                        var relationNanos = 0L
                        val service = GalleryRestoreService(context, db, access, writePreferences = { values ->
                            val start = System.nanoTime()
                            BackupPreferences.commit(context.getSharedPrefs(), values)
                            preferenceNanos += System.nanoTime() - start
                        }, fault = { phase ->
                            if (phase == "before_room_changes") relationStart = System.nanoTime()
                            if (phase == "after_room_changes") relationNanos += System.nanoTime() - relationStart
                        })
                        val preview = sample(size, cycle, "service_preview_fixed_access") { service.preview(decoded) }
                        val result = sample(size, cycle, "service_restore_fixed_access") { service.restore(preview, GalleryRestoreMode.MERGE) }
                        assertEquals(size / 2, result.media.appliedAdded)
                        rows.add(mapOf("media" to size, "cycle" to cycle, "phase" to "preferences_commit_nested", "nanoseconds" to preferenceNanos, "mainThread" to false))
                        rows.add(mapOf("media" to size, "cycle" to cycle, "phase" to "room_relation_changes_nested_excludes_commit", "nanoseconds" to relationNanos, "mainThread" to false))
                        val repeated = sample(size, cycle, "repeat_preview_fixed_access") { service.preview(decoded) }
                        assertEquals(0, repeated.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
                        val repeatResult = sample(size, cycle, "repeat_restore_fixed_access") { service.restore(repeated, GalleryRestoreMode.MERGE) }
                        assertEquals(0, repeatResult.media.appliedAdded)
                        assertEquals(size.toLong(), db.FavoritesDao().getFavoritesCount())
                        assertFalse(GalleryRestoreJournal(context).exists())
                    } finally { db.close() }
                }
            }
        } finally {
            root.deleteRecursively()
            val directory = File("build/reports/backup-performance").apply { mkdirs() }
            File(directory, "measurements.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
                "environment" to mapOf("kind" to "Robolectric SDK28, host JVM", "java" to System.getProperty("java.version"), "os" to System.getProperty("os.name"),
                    "processors" to Runtime.getRuntime().availableProcessors(), "maxHeapBytes" to Runtime.getRuntime().maxMemory(), "cycle0" to "first pass per size; not a process cold start", "cycle1to4" to "warm host caches; fresh in-memory Room",
                    "authorization" to "password disabled; deterministic available inspector in service; actual host metadata/protection measured separately",
                    "limitations" to "nested phases overlap; SHA sample only favorites; no device SAF, isolated service Room commit, UI sync or frame latency measurements"), "samples" to rows)))
            File(directory, "measurements.csv").writeText("media,cycle,phase,nanoseconds,mainThread\n" + rows.joinToString("\n") {
                "${it["media"]},${it["cycle"]},${it["phase"]},${it["nanoseconds"]},${it["mainThread"]}"
            })
            assertTrue(rows.none { it["mainThread"] == true })
        }
    }
}
