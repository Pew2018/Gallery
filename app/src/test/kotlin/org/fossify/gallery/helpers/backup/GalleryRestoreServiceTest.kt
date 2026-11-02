package org.fossify.gallery.helpers.backup

import android.app.Application
import androidx.room.Room
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GalleryRestoreServiceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: GalleryDatabase
    private val available = BackupPathAccess { _, _ -> BackupPathStatus.AVAILABLE }
    @Before fun setup() {
        context.getSharedPrefs().edit().clear().commit()
        GalleryRestoreJournal(context).delete()
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close(); GalleryRestoreJournal(context).delete() }
    private fun service(access: BackupPathAccess = available) = GalleryRestoreService(context, db, access)
    private fun favorite(path: String) = Favorite(null, path, path.substringAfterLast('/'), path.substringBeforeLast('/'))
    private fun restore(snapshot: GalleryBackupSnapshot, mode: GalleryRestoreMode = GalleryRestoreMode.MERGE) =
        service().let { it.restore(it.preview(snapshot), mode) }
    private class SimulatedDeath : Error("simulated process termination")

    @Test fun previewAndCancellationHaveNoSideEffects() {
        db.FavoritesDao().insert(favorite("/old.jpg"))
        val prefs = context.getSharedPrefs().all
        val revision = PhotoContentRevision.current()
        val plan = service().preview(fullBackupFixture(context, listOf("/new.jpg")))
        assertEquals(1, plan.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
        assertEquals(listOf("/old.jpg"), db.FavoritesDao().getValidFavoritePaths())
        assertEquals(prefs, context.getSharedPrefs().all); assertEquals(revision, PhotoContentRevision.current())
        assertFalse(GalleryRestoreJournal(context).exists())
    }
    @Test fun mergeKeepsExistingDatesAndIndependentRelationsAndRepairsCachedFlags() {
        db.FavoritesDao().insert(favorite("/old.jpg"))
        db.FavoriteFoldersDao().insert(FavoriteFolder("/offline", 7))
        db.MediumDao().insert(Medium().apply { path = "/new.jpg" })
        val snapshot = fullBackupFixture(context, listOf("/new.jpg", "/old.jpg", "/new.jpg"),
            listOf(FavoriteBackupCodec.FolderRecord("/offline", 90), FavoriteBackupCodec.FolderRecord("/new-folder", 42)))
        val result = restore(snapshot)
        assertEquals(1, result.target.mediaCount.added)
        assertEquals(setOf("/new.jpg", "/old.jpg"), db.FavoritesDao().getValidFavoritePaths().toSet())
        assertEquals(7L, db.FavoriteFoldersDao().getAll().first { it.path == "/offline" }.addedAt)
        assertTrue(db.MediumDao().getCachedMediaFromFiles(listOf("/new.jpg")).single().isFavorite)
        assertEquals(0, restore(snapshot).target.mediaCount.added)
    }
    @Test fun replaceUsesTargetDatesAndClearsOnlyRelationshipFlagsNeverMediaRows() {
        db.FavoritesDao().insert(favorite("/old.jpg")); db.FavoriteFoldersDao().insert(FavoriteFolder("/offline", 7))
        db.MediumDao().insert(Medium().apply { path = "/old.jpg"; isFavorite = true })
        val snapshot = fullBackupFixture(context, listOf("/new.jpg"), listOf(FavoriteBackupCodec.FolderRecord("/offline", 99)))
        val result = restore(snapshot, GalleryRestoreMode.REPLACE)
        assertEquals(1, result.target.mediaCount.removed)
        assertEquals(listOf("/new.jpg"), db.FavoritesDao().getValidFavoritePaths())
        assertEquals(99L, db.FavoriteFoldersDao().getAll().single().addedAt)
        assertFalse(db.MediumDao().getCachedMediaFromFiles(listOf("/old.jpg")).single().isFavorite)
    }
    @Test fun unsafeAndUnavailableMediaAreNotAddedOrRemovedEvenInReplace() {
        db.FavoritesDao().insert(favorite("/private.jpg")); db.FavoritesDao().insert(favorite("/missing.jpg")); db.FavoritesDao().insert(favorite("/remove.jpg"))
        val access = BackupPathAccess { path, _ -> when (path) {
            "/private.jpg" -> BackupPathStatus.PROTECTED
            "/missing.jpg", "/incoming-missing.jpg" -> BackupPathStatus.MISSING
            else -> BackupPathStatus.AVAILABLE
        } }
        val service = service(access)
        val plan = service.preview(fullBackupFixture(context, listOf("/incoming-missing.jpg", "/new.jpg")))
        assertEquals(1, plan.targets.getValue(GalleryRestoreMode.REPLACE).mediaCount.removed)
        service.restore(plan, GalleryRestoreMode.REPLACE)
        assertEquals(setOf("/private.jpg", "/missing.jpg", "/new.jpg"), db.FavoritesDao().getValidFavoritePaths().toSet())
    }
    @Test fun emptyTargetCanExplicitlyReplaceAccessibleFavoritesWithoutDeletingMedia() {
        db.FavoritesDao().insert(favorite("/old.jpg"))
        db.MediumDao().insert(Medium().apply { path = "/old.jpg"; isFavorite = true })
        val snapshot = fullBackupFixture(context)
        assertEquals(0, restore(snapshot).target.mediaCount.removed)
        assertEquals(1, restore(snapshot, GalleryRestoreMode.REPLACE).target.mediaCount.removed)
        assertEquals(0L, db.FavoritesDao().getFavoritesCount()); assertEquals(1, db.MediumDao().getAllMedia().size)
    }
    @Test fun offlineFoldersAndProfileSourcesArePreservedAndBindingsNotImported() {
        val prefs = context.getSharedPrefs()
        prefs.edit().putString("photo_source_bindings", "{\"local\":\"fingerprint\"}").putString("photo_protect_password_hash", "local-secret").commit()
        val profile = backupProfile()
        val snapshot = fullBackupFixture(context, folders = listOf(FavoriteBackupCodec.FolderRecord("/storage/OFFLINE/Folder", 8)),
            profiles = PhotoProfileState(profiles = listOf(profile), selectedId = profile.id))
        val service = service(BackupPathAccess { _, _ -> BackupPathStatus.OFFLINE_STORAGE })
        service.restore(service.preview(snapshot), GalleryRestoreMode.MERGE)
        assertEquals(8L, db.FavoriteFoldersDao().getAll().single().addedAt)
        assertEquals(profile, PhotoProfileRepository.of(context).state().current)
        assertEquals("local-secret", prefs.getString("photo_protect_password_hash", null))
        assertEquals("{\"local\":\"fingerprint\"}", prefs.getString("photo_source_bindings", null))
    }
    @Test fun mergeDeduplicatesNewUuidEquivalentContentAndKeepsCurrentSelection() {
        val local = backupProfile(name = "本地")
        PhotoProfileRepository.of(context).save(local)
        val equivalent = local.copy(id = "70619f70-d47d-4bc1-83bf-c42a9f45d011")
        val snapshot = fullBackupFixture(context, profiles = PhotoProfileState(profiles = listOf(equivalent), selectedId = equivalent.id))
        val before = PhotoContentRevision.current()
        restore(snapshot); restore(snapshot)
        assertEquals(listOf(local), PhotoProfileRepository.of(context).state().profiles)
        assertEquals(local.id, PhotoProfileRepository.of(context).state().selectedId)
        assertEquals(before, PhotoContentRevision.current())
    }
    @Test fun replaceRestoresUuidSelectionAndExistingEditingStillWorks() {
        PhotoProfileRepository.of(context).save(backupProfile(name = "旧"))
        val incoming = backupProfile("70619f70-d47d-4bc1-83bf-c42a9f45d011", "新")
        val snapshot = fullBackupFixture(context, profiles = PhotoProfileState(profiles = listOf(incoming), selectedId = incoming.id))
        restore(snapshot, GalleryRestoreMode.REPLACE)
        val store = PhotoProfileRepository.of(context)
        assertEquals(incoming.id, store.state().selectedId)
        store.updateExisting(incoming.copy(name = "编辑后")); assertEquals("编辑后", store.state().current!!.name)
        store.select(incoming.id); store.delete(incoming.id); assertTrue(store.state().profiles.isEmpty())
    }
    @Test fun settingsMergeRetainsUnspecifiedExtensionsAndReplaceResetsOnlyAllowlist() {
        val prefs = context.getSharedPrefs()
        prefs.edit().putString("oxygen_classic_appearance", "dark").putInt("sort_folder_/local", 1025)
            .putString("photo_source_bindings", "keep").putInt("app_run_count", 42).commit()
        val snapshot = fullBackupFixture(context, oxygen = mapOf("oxygen_bottom_nav_order" to "PHOTOS,FAVORITES,ALL"))
        restore(snapshot)
        assertEquals("dark", prefs.getString("oxygen_classic_appearance", null)); assertTrue(prefs.contains("sort_folder_/local"))
        restore(snapshot, GalleryRestoreMode.REPLACE)
        assertFalse(prefs.contains("oxygen_classic_appearance")); assertFalse(prefs.contains("sort_folder_/local"))
        assertEquals("PHOTOS,FAVORITES,ALL", prefs.getString("oxygen_bottom_nav_order", null))
        assertEquals("keep", prefs.getString("photo_source_bindings", null)); assertEquals(42, prefs.getInt("app_run_count", 0))
    }
    @Test fun directionalColumnsAndPathPreferencesOverrideLegacyWireColumns() {
        val oxygen = OxygenSettingsBackupCodec.columns.mapIndexed { index, key -> key to index + 1 }.toMap() +
            mapOf("sort_folder_/pictures/旅行" to 1025, "group_folder_/pictures/旅行" to 2, "view_type_folder_/pictures/旅行" to 3)
        restore(fullBackupFixture(context, oxygen = oxygen), GalleryRestoreMode.REPLACE)
        val prefs = context.getSharedPrefs()
        OxygenSettingsBackupCodec.columns.forEachIndexed { index, key -> assertEquals(index + 1, prefs.getInt(key, -1)) }
        assertEquals(3, org.fossify.gallery.helpers.Config(context).getFolderViewType("/Pictures/旅行"))
    }
    @Test fun themeRestorationPreservesImportedLegacySnapshotAndAppliesIdempotently() {
        val oxygen = mapOf("oxygen_classic_theme_activated" to true, "oxygen_classic_theme_preset" to "oxygen_blue",
            "oxygen_classic_appearance" to "dark", "oxygen_legacy_text" to -16777216, "oxygen_legacy_background" to -1,
            "oxygen_legacy_primary" to -65536, "oxygen_legacy_accent" to -65536, "oxygen_legacy_system" to true)
        restore(fullBackupFixture(context, oxygen = oxygen), GalleryRestoreMode.REPLACE)
        val config = org.fossify.gallery.helpers.Config(context)
        val before = context.getSharedPrefs().all
        OxygenThemeEngine.apply(context, config)
        assertEquals(before, context.getSharedPrefs().all)
        config.oxygenThemeActivated = false
        assertEquals(-1, config.backgroundColor); assertEquals(-65536, config.primaryColor); assertTrue(config.isSystemThemeEnabled)
    }
    @Test fun changedLocalPreferencesAfterPreviewBlockRestore() {
        val service = service(); val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg")))
        context.getSharedPrefs().edit().putBoolean("show_all", false).commit()
        assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(plan, GalleryRestoreMode.REPLACE) }
        assertEquals(0L, db.FavoritesDao().getFavoritesCount()); assertFalse(GalleryRestoreJournal(context).exists())
    }
    @Test fun changedDatabaseAfterPreviewBlocksRestore() {
        val service = service(); val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg")))
        db.FavoritesDao().insert(favorite("/new-local.jpg"))
        assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(plan, GalleryRestoreMode.REPLACE) }
        assertEquals(listOf("/new-local.jpg"), db.FavoritesDao().getValidFavoritePaths())
    }
    @Test fun changedPermissionOrProtectionAfterPreviewBlocksRestore() {
        var status = BackupPathStatus.AVAILABLE
        val service = service(BackupPathAccess { _, _ -> status })
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg")))
        status = BackupPathStatus.PROTECTED
        assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertEquals(0L, db.FavoritesDao().getFavoritesCount())
    }
    @Test fun roomFailureRollsBackBothRelationshipTablesAndDoesNotTouchPreferences() {
        val service = GalleryRestoreService(context, db, available, fault = { if (it == "after_room_changes") error("SQLite full") })
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg"), listOf(FavoriteBackupCodec.FolderRecord("/new-folder", 1))))
        val before = context.getSharedPrefs().all
        val error = assertThrows(GalleryRestoreFailure::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertTrue(error.compensationSucceeded); assertEquals(before, context.getSharedPrefs().all)
        assertEquals(0L, db.FavoritesDao().getFavoritesCount()); assertTrue(db.FavoriteFoldersDao().getAll().isEmpty())
    }
    @Test fun failedPreferencesCommitIsCompensatedAfterRoomRollback() {
        var writes = 0
        val service = GalleryRestoreService(context, db, available, writePreferences = {
            BackupPreferences.commit(context.getSharedPrefs(), it)
            if (++writes == 1) throw IOException("simulated disk failure after preference memory update")
        })
        val before = context.getSharedPrefs().all
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg"), legacy = mapOf(SHOW_ALL to false)))
        val error = assertThrows(GalleryRestoreFailure::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertTrue(error.compensationSucceeded); assertEquals(before, context.getSharedPrefs().all)
        assertEquals(0L, db.FavoritesDao().getFavoritesCount()); assertFalse(GalleryRestoreJournal(context).exists())
    }
    @Test fun failedCompensationKeepsCheckpointAndDoesNotReportSuccess() {
        var writes = 0
        val service = GalleryRestoreService(context, db, available, writePreferences = {
            if (++writes == 1) BackupPreferences.commit(context.getSharedPrefs(), it)
            throw IOException("persistent disk failure")
        })
        val before = context.getSharedPrefs().all
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg"), legacy = mapOf(SHOW_ALL to false)))
        val error = assertThrows(GalleryRestoreFailure::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertFalse(error.compensationSucceeded); assertTrue(GalleryRestoreJournal(context).exists())
        assertThrows(PendingGalleryRestore::class.java) { service().preview(fullBackupFixture(context)) }
        assertEquals(GalleryRecoveryResult.REVERTED, service().recoverPending())
        assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun processDeathBeforeRoomCommitRecoversPreviousPreferences() {
        val service = GalleryRestoreService(context, db, available, fault = { if (it == "after_preferences") throw SimulatedDeath() })
        val before = context.getSharedPrefs().all
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg"), legacy = mapOf(SHOW_ALL to false)))
        assertThrows(SimulatedDeath::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertEquals(0L, db.FavoritesDao().getFavoritesCount()); assertTrue(GalleryRestoreJournal(context).exists())
        assertEquals(GalleryRecoveryResult.REVERTED, service().recoverPending()); assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun processDeathAfterRoomCommitFinishesConfirmedRestore() {
        val service = GalleryRestoreService(context, db, available, fault = { if (it == "after_room_commit") throw SimulatedDeath() })
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg"), legacy = mapOf(SHOW_ALL to false)))
        assertThrows(SimulatedDeath::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertEquals(listOf("/new.jpg"), db.FavoritesDao().getValidFavoritePaths())
        assertEquals(GalleryRecoveryResult.FINISHED, service().recoverPending())
        assertFalse(context.getSharedPrefs().getBoolean(SHOW_ALL, true)); assertFalse(GalleryRestoreJournal(context).exists())
    }
    @Test fun ambiguousPreferenceOnlyInterruptionRevertsRatherThanSilentlyCompleting() {
        val service = GalleryRestoreService(context, db, available, fault = { if (it == "after_preferences") throw SimulatedDeath() })
        val before = context.getSharedPrefs().all
        val plan = service.preview(fullBackupFixture(context, legacy = mapOf(SHOW_ALL to false)))
        assertThrows(SimulatedDeath::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertEquals(GalleryRecoveryResult.REVERTED, service().recoverPending()); assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun ambiguousRecoveryAfterIndependentChangesDoesNotOverwriteNewData() {
        val service = GalleryRestoreService(context, db, available, fault = { if (it == "after_preferences") throw SimulatedDeath() })
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg")))
        assertThrows(SimulatedDeath::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        db.FavoritesDao().insert(favorite("/independent.jpg"))
        assertThrows(IllegalStateException::class.java) { service().recoverPending() }
        assertEquals(listOf("/independent.jpg"), db.FavoritesDao().getValidFavoritePaths()); assertTrue(GalleryRestoreJournal(context).exists())
    }
    @Test fun concurrentRestoresSerializeAndSecondStalePlanCannotOverwriteFirst() {
        val service = service()
        val a = service.preview(fullBackupFixture(context, listOf("/a.jpg")))
        val b = service.preview(fullBackupFixture(context, listOf("/b.jpg")))
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<String>())
        val threads = listOf(a, b).map { plan -> Thread {
            try { service.restore(plan, GalleryRestoreMode.REPLACE); outcomes.add("success") }
            catch (_: StaleGalleryRestorePlan) { outcomes.add("stale") }
        }.apply { start() } }
        threads.forEach { it.join(20000); assertFalse(it.isAlive) }
        assertEquals(listOf("stale", "success"), outcomes.sorted()); assertEquals(1L, db.FavoritesDao().getFavoritesCount())
    }
    @Test fun checkpointStorageFailureOccursBeforeAnyUserDataWrites() {
        val service = GalleryRestoreService(context, db, available, fault = { if (it == "before_checkpoint") throw IOException("ENOSPC") })
        val plan = service.preview(fullBackupFixture(context, listOf("/new.jpg")))
        val before = context.getSharedPrefs().all
        assertThrows(IOException::class.java) { service.restore(plan, GalleryRestoreMode.REPLACE) }
        assertEquals(before, context.getSharedPrefs().all); assertEquals(0L, db.FavoritesDao().getFavoritesCount())
        assertFalse(GalleryRestoreJournal(context).exists())
    }
    @Test fun favoriteFolderOnlyRestoreDoesNotInvalidatePhotoMediaCaches() {
        val revision = PhotoContentRevision.current()
        restore(fullBackupFixture(context, folders = listOf(FavoriteBackupCodec.FolderRecord("/offline", 1))))
        assertEquals(revision, PhotoContentRevision.current())
    }
    @Test fun collectorExportsNoSecurityValuesAndReadsRelationshipsWithoutMutatingState() {
        context.getSharedPrefs().edit().putString("photo_source_bindings", "local-only").putString("photo_protect_password_hash", "secret").commit()
        db.FavoritesDao().insert(favorite("/中文.JPG"))
        val before = context.getSharedPrefs().all
        val snapshot = service().snapshot()
        assertEquals(listOf("/中文.JPG"), snapshot.mediaFavorites)
        assertFalse(snapshot.legacySettings.contains("secret"))
        assertFalse(OxygenSettingsBackupCodec.encode(snapshot.oxygenSettings).contains("local-only"))
        assertEquals(before, context.getSharedPrefs().all)
    }
    @Test fun checkpointRejectsDeviceCredentialKeysBeforeWriting() {
        val hash = "0".repeat(64)
        assertThrows(IllegalArgumentException::class.java) {
            GalleryRestoreJournal(context).write(GalleryRestoreCheckpoint(hash, hash,
                mapOf("photo_source_bindings" to "local"), mapOf("photo_source_bindings" to "injected")))
        }
        assertFalse(GalleryRestoreJournal(context).exists())
    }
    @Test fun restoreCannotEnableProtectedVisibilityOrRemoveProtectedExcludedRoots() {
        val config = org.fossify.gallery.helpers.Config(context)
        config.isHiddenPasswordProtectionOn = true
        config.isExcludedPasswordProtectionOn = true
        config.addExcludedFolders(setOf("/private"))
        val snapshot = fullBackupFixture(context, legacy = mapOf(SHOW_HIDDEN_MEDIA to true, EXCLUDED_FOLDERS to ""),
            oxygen = mapOf("oxygen_favorite_show_hidden" to true, "oxygen_favorite_show_excluded" to true))
        val result = restore(snapshot, GalleryRestoreMode.REPLACE)
        assertEquals(4, result.target.retainedSecuritySettings.size)
        assertFalse(config.showHiddenMedia); assertFalse(config.favoriteShowHidden); assertFalse(config.favoriteShowExcluded)
        assertTrue("/private" in config.excludedFolders)
    }
}
