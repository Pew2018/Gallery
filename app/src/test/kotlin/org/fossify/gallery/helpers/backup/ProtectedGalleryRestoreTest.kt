package org.fossify.gallery.helpers.backup

import android.Manifest
import android.app.Application
import androidx.room.Room
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@RoboConfig(sdk = [28], application = Application::class)
class ProtectedGalleryRestoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: GalleryDatabase
    private lateinit var root: File
    private val mode get() = GalleryRestoreMode.REPLACE
    private val access get() = DeviceBackupPathAccess(context)
    @Before fun setup() {
        context.getSharedPrefs().edit().clear().commit()
        FavoriteAccessSessionManager.revokeAll()
        PhotoAuthentication.requests.cancelAll()
        GalleryRestoreJournal(context).delete()
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        root = Files.createTempDirectory("restore-protected").toFile()
        db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun cleanup() { db.close(); root.deleteRecursively(); GalleryRestoreJournal(context).delete(); FavoriteAccessSessionManager.revokeAll() }
    private fun file(name: String): String = File(root, name).apply { parentFile!!.mkdirs(); writeText(name) }.absolutePath
    private fun favorite(path: String) { db.FavoritesDao().insert(Favorite(null, path, File(path).name, File(path).parent.orEmpty())) }
    private fun paths() = db.FavoritesDao().getValidFavoritePaths().toSet()
    private fun service(fault: (String) -> Unit = {}) = GalleryRestoreService(context, db, access, fault = fault)
    private fun enable(type: Int = PROTECTION_PIN) {
        if (type == PROTECTION_FINGERPRINT) context.config.saveFavoriteBiometricCredential("fallback-fixture", PROTECTION_PIN)
        else context.config.saveFavoriteCredential("fixture-secret", type)
    }
    private fun proof() = org.fossify.gallery.helpers.Config.FavoriteCredentialProof(context.config.favoriteCredentialSnapshot(), FavoriteAccessSessionManager.currentGeneration())
    private fun authorize(service: GalleryRestoreService, plan: GalleryRestorePlan,
                          operation: GalleryRestoreOperation = GalleryRestoreOperation(), selected: GalleryRestoreMode = mode): Pair<GalleryRestorePlan, GalleryRestoreOperation> =
        service.authorize(plan, selected, proof(), operation) to operation

    @Test fun actualDeviceAccessSeparatesVirtualCredentialFromStorageAndIndependentFolderLocks() {
        val path = file("public/a.jpg")
        enable()
        val detail = access.inspectBatch(setOf(BackupPathRequest(path, false))).values.single()
        assertEquals(BackupPathStatus.AVAILABLE, detail.storageStatus)
        assertTrue(detail.requiresFavoriteCredential); assertTrue(detail.protectedRoots.isEmpty())
        assertEquals(BackupPathStatus.PROTECTED, detail.status())
        assertEquals(BackupPathStatus.AVAILABLE, detail.status(true))
        context.config.addFolderProtection(File(path).parent!!, "other-secret", PROTECTION_PATTERN)
        val locked = access.inspectBatch(setOf(BackupPathRequest(path, false))).values.single()
        assertEquals(setOf(File(path).parent!!), locked.protectedRoots)
        assertEquals(BackupPathStatus.PROTECTED, locked.status(true))
    }

    @Test fun noPasswordMergeReportsObservedAddsAndRepeatedBackupIsNoChange() {
        val a = file("a.jpg"); val b = file("b.jpg"); favorite(a)
        val service = service(); val snapshot = fullBackupFixture(context, listOf(a, b, b))
        val plan = service.preview(snapshot)
        assertEquals(1, plan.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
        val result = service.restore(plan, GalleryRestoreMode.MERGE)
        assertEquals(1, result.media.appliedAdded); assertEquals(0, result.media.appliedRemoved)
        assertEquals(1, result.media.duplicates); assertEquals(2, result.media.finalCount)
        assertEquals(setOf(a, b), paths())
        val repeat = service.restore(service.preview(snapshot), GalleryRestoreMode.MERGE)
        assertEquals(0, repeat.media.appliedAdded); assertEquals(GalleryRestoreCompletion.NO_CHANGES, repeat.completion)
    }

    @Test fun pinPatternAndBiometricCredentialProofsEnableReplannedMergeWithoutChangingSecrets() {
        for (type in listOf(PROTECTION_PIN, PROTECTION_PATTERN, PROTECTION_FINGERPRINT)) {
            db.FavoritesDao().clearFavorites(); enable(type)
            val a = file("type-$type/a.jpg")
            val before = context.config.favoriteCredentialSnapshot()
            val service = service(); val initial = service.preview(fullBackupFixture(context, listOf(a)))
            assertEquals(0, initial.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
            assertTrue(initial.requiresFavoriteAuthorization(GalleryRestoreMode.MERGE))
            assertThrows(RestoreAuthorizationRequired::class.java) { service.restore(initial, GalleryRestoreMode.MERGE) }
            val (formal, operation) = authorize(service, initial, selected = GalleryRestoreMode.MERGE)
            assertNotSame(initial, formal)
            assertEquals(0, initial.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
            assertEquals(1, formal.targets.getValue(GalleryRestoreMode.MERGE).mediaCount.added)
            val result = service.restore(formal, GalleryRestoreMode.MERGE, operation)
            assertEquals(setOf(a), paths()); assertEquals(1, result.media.appliedAdded)
            assertEquals(before, context.config.favoriteCredentialSnapshot())
            assertFalse(formal.authorization!!.current(context, formal, GalleryRestoreMode.MERGE))
            operation.close()
        }
    }

    @Test fun unauthorizedEmptyReplacementCannotClearFavoritesOrApplyOtherCategories() {
        val old = file("old.jpg"); favorite(old); enable()
        val service = service(); val snapshot = fullBackupFixture(context, legacy = mapOf(SHOW_ALL to false))
        val initial = service.preview(snapshot)
        val before = context.getSharedPrefs().all
        assertTrue(initial.requiresFavoriteAuthorization(mode))
        assertEquals(0, initial.targets.getValue(mode).mediaCount.removed)
        assertThrows(RestoreAuthorizationRequired::class.java) { service.restore(initial, mode) }
        assertEquals(setOf(old), paths()); assertEquals(before, context.getSharedPrefs().all)
        assertFalse(GalleryRestoreJournal(context).exists())
    }

    @Test fun authorizedEmptyReplacementRemovesOnlyAccessibleRelationsAndNeverDeletesFiles() {
        val public = file("public/a.jpg"); val secret = file("secret/b.jpg"); favorite(public); favorite(secret)
        context.config.addFolderProtection(File(secret).parent!!, "independent", PROTECTION_PIN); enable()
        val service = service(); val initial = service.preview(fullBackupFixture(context))
        val (formal, operation) = authorize(service, initial)
        assertEquals(1, formal.targets.getValue(mode).mediaCount.removed)
        val result = service.restore(formal, mode, operation)
        assertEquals(setOf(secret), paths()); assertEquals(1, result.media.appliedRemoved)
        assertEquals(1, result.media.retainedUnsafe); assertEquals(1, result.retainedMedia)
        assertTrue(File(public).exists()); assertTrue(File(secret).exists())
        assertEquals(GalleryRestoreCompletion.PARTIAL, result.completion)
    }

    @Test fun multipleNestedAndSymlinkedFolderLocksAreNeverAuthorizedByFavoritesProof() {
        val public = file("public/a.jpg"); val first = file("private/b.jpg"); val nested = file("private/nested/c.jpg")
        val second = file("other/d.jpg")
        context.config.addFolderProtection(File(first).parent!!, "first", PROTECTION_PIN)
        context.config.addFolderProtection(File(nested).parent!!, "second", PROTECTION_PATTERN)
        context.config.addFolderProtection(File(second).parent!!, "third", PROTECTION_PIN)
        val link = root.toPath().resolve("public/link.jpg")
        Files.createSymbolicLink(link, File(nested).toPath())
        enable()
        val service = service(); val snapshot = fullBackupFixture(context, listOf(public, first, nested, second, link.toString()))
        val initial = service.preview(snapshot)
        assertEquals(2, initial.accessDetails.getValue(BackupPathRequest(nested, false)).protectedRoots.size)
        val (formal, operation) = authorize(service, initial)
        val result = service.restore(formal, mode, operation)
        assertEquals(setOf(public), paths())
        assertEquals(4, result.media.skipped[RestoreSkipReason.SOURCE_PROTECTION])
        assertEquals(1, result.media.appliedAdded); assertEquals(GalleryRestoreCompletion.PARTIAL, result.completion)
    }

    @Test fun folderOnlyProtectionWithoutVirtualPasswordIsConservativelySkipped() {
        val secret = file("secret/a.jpg")
        context.config.addFolderProtection(File(secret).parent!!, "folder", PROTECTION_PATTERN)
        val service = service(); val plan = service.preview(fullBackupFixture(context, listOf(secret)))
        assertFalse(plan.requiresFavoriteAuthorization(mode))
        val result = service.restore(plan, mode)
        assertTrue(paths().isEmpty()); assertEquals(1, result.media.skipped[RestoreSkipReason.SOURCE_PROTECTION])
    }

    @Test fun missingOfflineSafAndMediaPermissionAreStorageReasonsNotFavoriteAuthorization() {
        val existing = file("present.jpg"); val missing = File(root, "missing.jpg").absolutePath
        enable()
        val requests = setOf(BackupPathRequest(missing, false), BackupPathRequest("/storage/FFFF-DEAD/a.jpg", false),
            BackupPathRequest("content://unknown.provider/document/123", false))
        val details = access.inspectBatch(requests)
        assertEquals(BackupPathStatus.MISSING, details.getValue(BackupPathRequest(missing, false)).storageStatus)
        assertEquals(BackupPathStatus.OFFLINE_STORAGE, details.getValue(BackupPathRequest("/storage/FFFF-DEAD/a.jpg", false)).status(true))
        assertEquals(BackupPathStatus.SAF_PERMISSION, details.getValue(BackupPathRequest("content://unknown.provider/document/123", false)).status(true))
        shadowOf(context).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertEquals(BackupPathStatus.MEDIA_PERMISSION, access.inspectBatch(setOf(BackupPathRequest(existing, false))).values.single().status(true))
    }

    @Test fun duplicateCrossCategoryPathsAreNotCountedAsRepeatedFailedMedia() {
        val missing = File(root, "missing.jpg").absolutePath
        val profile = backupProfile(path = missing).copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, missing)))
        val service = service(); val snapshot = fullBackupFixture(context, listOf(missing, missing),
            listOf(FavoriteBackupCodec.FolderRecord(missing, 1)), PhotoProfileState(profiles = listOf(profile), selectedId = profile.id))
        val result = service.restore(service.preview(snapshot), GalleryRestoreMode.MERGE)
        assertEquals(1, result.media.skippedCount); assertEquals(1, result.media.duplicates)
        assertEquals(1, result.unresolvedPaths); assertEquals(1, result.profiles.inaccessibleSourcesRetained)
        assertEquals(1, db.FavoriteFoldersDao().getAll().size)
    }

    @Test fun permissionChangeAfterProofInvalidatesFormalPlanWithoutWrites() {
        val a = file("a.jpg"); enable(); val service = service()
        val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(a))))
        shadowOf(context).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val error = assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(formal, mode, operation) }
        assertEquals(RestoreStaleReason.ACCESS, error.reason); assertTrue(paths().isEmpty())
    }

    @Test fun fileIdentityChangeAfterProofInvalidatesPlanEvenIfReadableStatusStaysAvailable() {
        val a = file("a.jpg"); enable(); val service = service()
        val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(a))))
        File(a).writeText("different length and identity evidence")
        assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(formal, mode, operation) }
        assertTrue(paths().isEmpty())
    }

    @Test fun credentialGenerationDatabaseAndNewFolderBoundaryChangesInvalidateProof() {
        val path = file("a.jpg")
        for (change in listOf("credential", "generation", "database", "folder")) {
            context.getSharedPrefs().edit().clear().commit(); db.FavoritesDao().clearFavorites(); enable()
            val service = service(); val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(path))))
            when (change) {
                "credential" -> context.config.saveFavoriteCredential("changed", PROTECTION_PATTERN)
                "generation" -> FavoriteAccessSessionManager.revokeAll()
                "database" -> favorite(file("concurrent.jpg"))
                "folder" -> context.config.addFolderProtection(root.absolutePath, "new-lock", PROTECTION_PIN)
            }
            assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(formal, mode, operation) }
            assertFalse(path in paths()); operation.close()
        }
    }

    @Test fun cancelledDestroyedOrDifferentOperationAndDifferentModeCannotReuseAuthorization() {
        val path = file("a.jpg"); enable()
        for (case in listOf("cancel", "owner", "operation", "mode", "backup")) {
            var alive = true
            val operation = GalleryRestoreOperation { alive }
            val service = service(); val plan = service.preview(fullBackupFixture(context, listOf(path)))
            val (formal, _) = authorize(service, plan, operation)
            when (case) { "cancel" -> operation.close(); "owner" -> alive = false }
            val candidate = if (case == "backup") formal.copy(incoming = fullBackupFixture(context, listOf(file("other.jpg")))) else formal
            val runMode = if (case == "mode") GalleryRestoreMode.MERGE else mode
            assertThrows(StaleGalleryRestorePlan::class.java) {
                service.restore(candidate, runMode, if (case == "operation") GalleryRestoreOperation() else operation)
            }
            assertTrue(paths().isEmpty()); assertFalse(GalleryRestoreJournal(context).exists()); operation.close()
        }
    }

    @Test fun nullOrOldProofCannotAuthorizeAndLegacyProtectionDoesNotMigrate() {
        val path = file("a.jpg"); enable(); val service = service(); val initial = service.preview(fullBackupFixture(context, listOf(path)))
        assertThrows(RestoreAuthorizationRequired::class.java) { service.authorize(initial, mode, null, GalleryRestoreOperation()) }
        val old = proof(); context.config.saveFavoriteCredential("changed", PROTECTION_PIN)
        val newer = service.preview(initial.incoming)
        assertThrows(RestoreAuthorizationRequired::class.java) { service.authorize(newer, mode, old, GalleryRestoreOperation()) }
        context.config.addFolderProtection(FAVORITES, "legacy", PROTECTION_PATTERN)
        val before = context.getSharedPrefs().all
        val legacy = service.preview(initial.incoming)
        val error = assertThrows(RestoreAuthorizationRequired::class.java) { service.authorize(legacy, mode, proof(), GalleryRestoreOperation()) }
        assertEquals(RestoreAuthorizationReason.LEGACY_MIGRATION, error.reason)
        assertEquals(before, context.getSharedPrefs().all); assertTrue(paths().isEmpty())
    }

    @Test fun lastMomentAccessChangeRollsBackPreferencesAndRelationsWithTypedCause() {
        val path = file("a.jpg"); enable()
        val service = service { if (it == "after_preferences") File(path).delete() }
        val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(path), legacy = mapOf(SHOW_ALL to false))))
        val before = context.getSharedPrefs().all
        val failure = assertThrows(GalleryRestoreFailure::class.java) { service.restore(formal, mode, operation) }
        assertTrue(failure.compensationSucceeded); assertTrue(failure.cause is StaleGalleryRestorePlan)
        assertTrue(paths().isEmpty()); assertEquals(before, context.getSharedPrefs().all)
        assertFalse(GalleryRestoreJournal(context).exists())
    }

    @Test fun cancellationInsideTransactionRollsBackAndCannotBeCountedAsSuccess() {
        val path = file("a.jpg"); enable(); val operation = GalleryRestoreOperation()
        val service = service { if (it == "after_room_changes") operation.close() }
        val (formal, _) = authorize(service, service.preview(fullBackupFixture(context, listOf(path))), operation)
        val failure = assertThrows(GalleryRestoreFailure::class.java) { service.restore(formal, mode, operation) }
        assertTrue(failure.compensationSucceeded); assertTrue(paths().isEmpty())
        assertFalse(GalleryRestoreJournal(context).exists())
    }

    @Test fun onlyAuthorizedCachedFlagsAreRepairedAndNoChangeDoesNotRefreshMedia() {
        val a = file("a.jpg"); val secret = file("secret/b.jpg"); favorite(a); favorite(secret)
        db.MediumDao().insert(Medium().apply { path = a; isFavorite = false })
        db.MediumDao().insert(Medium().apply { path = secret; isFavorite = false })
        context.config.addFolderProtection(File(secret).parent!!, "independent", PROTECTION_PIN)
        enable()
        val service = service(); val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(a, secret))))
        val result = service.restore(formal, mode, operation)
        assertEquals(1, result.media.cacheRepaired)
        assertTrue(db.MediumDao().getCachedMediaFromFiles(listOf(a)).single().isFavorite)
        assertFalse(db.MediumDao().getCachedMediaFromFiles(listOf(secret)).single().isFavorite)
        val revision = PhotoContentRevision.current()
        val (next, op2) = authorize(service, service.preview(formal.incoming))
        val repeat = service.restore(next, mode, op2)
        assertEquals(0, repeat.media.cacheRepaired); assertEquals(revision, PhotoContentRevision.current())
    }

    @Test fun operationGatePreventsRepeatedConfirmationAndLateProofAfterCancellation() {
        val op = GalleryRestoreOperation()
        assertTrue(op.advance()); assertFalse(op.advance())
        op.awaitConfirmation(); assertTrue(op.advance()); op.close()
        assertFalse(op.advance()); assertFalse(op.isActive())
        val a = file("a.jpg"); enable(); val service = service()
        assertThrows(StaleGalleryRestorePlan::class.java) { service.authorize(service.preview(fullBackupFixture(context, listOf(a))), mode, proof(), op) }
    }

    @Test fun observedPartialReplacementMatchesTheDatabaseAndProducesReviewableEvidence() {
        val keep = file("keep.jpg"); val remove = file("remove.jpg"); val secret = file("secret/old.jpg")
        val add = file("add.jpg"); val blocked = file("secret/new.jpg"); val missing = File(root, "missing.jpg").absolutePath
        listOf(keep, remove, secret).forEach(::favorite)
        context.config.addFolderProtection(File(secret).parent!!, "source-only", PROTECTION_PIN); enable()
        val snapshot = fullBackupFixture(context, listOf(keep, add, blocked, missing, add))
        val service = service(); val initial = service.preview(snapshot); val (formal, op) = authorize(service, initial)
        val planned = formal.targets.getValue(mode).mediaCount
        val result = service.restore(formal, mode, op)
        assertEquals(1, planned.added); assertEquals(1, planned.removed)
        assertEquals(1, result.media.appliedAdded); assertEquals(1, result.media.appliedRemoved)
        assertEquals(1, result.media.skipped[RestoreSkipReason.SOURCE_PROTECTION])
        assertEquals(1, result.media.skipped[RestoreSkipReason.MISSING])
        assertEquals(setOf(keep, add, secret), paths()); assertEquals(3, result.media.finalCount)
        val evidence = com.google.gson.JsonObject().apply {
            addProperty("scenario", "authorized-partial-replace-real-files-room")
            addProperty("backupRows", snapshot.mediaTotal); addProperty("backupUnique", snapshot.mediaFavorites.distinct().size)
            addProperty("before", 3); addProperty("plannedAdded", planned.added); addProperty("plannedRemoved", planned.removed)
            addProperty("appliedAdded", result.media.appliedAdded); addProperty("appliedRemoved", result.media.appliedRemoved)
            addProperty("sourceProtectedSkipped", result.media.skipped[RestoreSkipReason.SOURCE_PROTECTION])
            addProperty("missingSkipped", result.media.skipped[RestoreSkipReason.MISSING])
            addProperty("retainedUnsafe", result.media.retainedUnsafe); addProperty("after", paths().size)
        }
        val directory = File("build/reports/restore-reliability").apply { mkdirs() }
        File(directory, "observed-partial-replace.json").writeText(evidence.toString())
    }
    private class SimulatedDeath : Error("simulated interruption")

    @Test fun interruptedAuthorizedRestoreNeverPersistsProofAndCannotReuseItAfterRecovery() {
        val a = file("a.jpg"); enable()
        for (point in listOf("after_preferences", "after_room_commit")) {
            db.FavoritesDao().clearFavorites()
            val service = service { if (it == point) throw SimulatedDeath() }
            val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(a), legacy = mapOf(SHOW_ALL to false))))
            assertThrows(SimulatedDeath::class.java) { service.restore(formal, mode, operation) }
            val checkpoint = File(context.filesDir, "gallery-restore-checkpoint.json").readText()
            assertFalse(checkpoint.contains("fixture-secret")); assertFalse(checkpoint.contains(operation.id))
            assertFalse(checkpoint.contains("credentialVersion")); assertFalse(checkpoint.contains("authorization"))
            assertFalse(formal.authorization!!.current(context, formal, mode))
            assertThrows(PendingGalleryRestore::class.java) { service().snapshot() }
            val recovery = service().recoverPending()
            assertEquals(if (point == "after_preferences") GalleryRecoveryResult.REVERTED else GalleryRecoveryResult.FINISHED, recovery)
            assertEquals(if (point == "after_preferences") emptySet<String>() else setOf(a), paths())
            assertFalse(GalleryRestoreJournal(context).exists()); operation.close()
        }
    }

    @Test fun checkpointFileCreationFailureHappensBeforeRelationsAndSettingsWrites() {
        val a = file("a.jpg")
        // Block both old (.bak) and new (.new) AtomicFile layouts with nonempty directories.
        val blocked = listOf("", ".bak", ".new").map { File(context.filesDir, "gallery-restore-checkpoint.json$it") }
        val service = service { if (it == "before_checkpoint") blocked.forEach {
            it.mkdirs(); File(it, "block-write").writeText("simulated storage obstruction")
        } }
        val plan = service.preview(fullBackupFixture(context, listOf(a)))
        val before = context.getSharedPrefs().all
        try {
            assertThrows(GalleryRestoreCheckpointFailure::class.java) { service.restore(plan, mode) }
            assertTrue(paths().isEmpty()); assertEquals(before, context.getSharedPrefs().all)
        } finally { blocked.forEach { it.deleteRecursively() } }
    }

    @Test fun folderOnlyInterruptedCommitDoesNotRefreshMediaOnRecovery() {
        val revision = PhotoContentRevision.current()
        val service = service { if (it == "after_room_commit") throw SimulatedDeath() }
        val plan = service.preview(fullBackupFixture(context, folders = listOf(FavoriteBackupCodec.FolderRecord(root.absolutePath, 1))))
        assertThrows(SimulatedDeath::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertFalse(GalleryRestoreJournal(context).read().mediaChanged)
        assertEquals(GalleryRecoveryResult.FINISHED, service().recoverPending())
        assertEquals(revision, PhotoContentRevision.current())
    }

    @Test fun actualFolderProfileAndSettingsChangesHaveSeparateObservedCounts() {
        val folder = File(root, "folder").apply { mkdirs() }
        db.FavoriteFoldersDao().insert(org.fossify.gallery.models.FavoriteFolder(folder.absolutePath, 7))
        val oldProfile = backupProfile(path = root.absolutePath).copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, root.absolutePath)))
        PhotoProfileRepository.of(context).save(oldProfile)
        val profile = oldProfile.copy(id = "70619f70-d47d-4bc1-83bf-c42a9f45d011", name = "New")
        val snapshot = fullBackupFixture(context, folders = listOf(FavoriteBackupCodec.FolderRecord(folder.absolutePath, 90)),
            profiles = PhotoProfileState(profiles = listOf(profile), selectedId = profile.id), legacy = mapOf(SHOW_ALL to false))
        val service = service(); val result = service.restore(service.preview(snapshot), mode)
        assertEquals(0, result.folders.appliedAdded); assertEquals(1, result.folders.existing)
        assertEquals(1, result.folders.dateConflicts); assertEquals(90L, db.FavoriteFoldersDao().getAll().single().addedAt)
        assertEquals(1, result.profiles.appliedAdded); assertEquals(1, result.profiles.appliedRemoved)
        assertEquals(profile, PhotoProfileRepository.of(context).state().current)
        assertTrue(result.settings.changed > 0); assertFalse(context.config.showAll)
    }

    @Test fun credentialChangeAtFinalCommitIsNotUndoneBySettingsCompensation() {
        val a = file("a.jpg"); enable()
        val service = service { if (it == "after_preferences") context.config.saveFavoriteCredential("new-user-secret", PROTECTION_PATTERN) }
        val (formal, operation) = authorize(service, service.preview(fullBackupFixture(context, listOf(a), legacy = mapOf(SHOW_ALL to false))))
        val failure = assertThrows(GalleryRestoreFailure::class.java) { service.restore(formal, mode, operation) }
        assertTrue(failure.compensationSucceeded); assertTrue(paths().isEmpty())
        assertEquals("new-user-secret", context.config.favoriteCredentialSnapshot().hash)
        assertEquals(PROTECTION_PATTERN, context.config.favoriteCredentialSnapshot().type)
        assertFalse(GalleryRestoreJournal(context).exists())
    }

    @Test fun unchangedConfirmedPlanIsSingleUseEvenWhenItWritesNothing() {
        val service = service(); val snapshot = fullBackupFixture(context)
        service.restore(service.preview(snapshot), GalleryRestoreMode.MERGE)
        val plan = service.preview(snapshot)
        val revision = PhotoContentRevision.current()
        assertEquals(GalleryRestoreCompletion.NO_CHANGES, service.restore(plan, GalleryRestoreMode.MERGE).completion)
        assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertEquals(revision, PhotoContentRevision.current())
    }

    @Test fun originalV1CheckpointWithoutRefreshHintRemainsReadable() {
        val journal = GalleryRestoreJournal(context)
        journal.write(GalleryRestoreCheckpoint("0".repeat(64), "1".repeat(64), emptyMap(), emptyMap()))
        val file = File(context.filesDir, "gallery-restore-checkpoint.json")
        val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        json.remove("mediaChanged"); file.writeText(json.toString())
        assertTrue(journal.read().mediaChanged)
    }

    @Test fun storageInspectorCannotAccidentallyBypassTheConfiguredVirtualGate() {
        val a = file("a.jpg"); enable()
        val service = GalleryRestoreService(context, db, BackupPathAccess { _, _ -> BackupPathStatus.AVAILABLE })
        val plan = service.preview(fullBackupFixture(context, listOf(a)))
        assertTrue(plan.requiresFavoriteAuthorization(mode))
        assertThrows(RestoreAuthorizationRequired::class.java) { service.restore(plan, mode) }
        assertTrue(paths().isEmpty())
    }

    @Test fun unrelatedPreferenceChangeOnNoWriteFastPathCannotBeReportedAsAnAppliedSetting() {
        val snapshot = fullBackupFixture(context)
        service().restore(service().preview(snapshot), GalleryRestoreMode.MERGE)
        var changeDuringCheck = false
        val inspector = BackupPathAccess { _, _ ->
            if (changeDuringCheck) {
                changeDuringCheck = false
                context.getSharedPrefs().edit().putInt("independent_preference", 9).commit()
            }
            BackupPathStatus.AVAILABLE
        }
        val folder = File(root, "folder").apply { mkdirs() }
        db.FavoriteFoldersDao().insert(org.fossify.gallery.models.FavoriteFolder(folder.absolutePath, 7))
        val incoming = fullBackupFixture(context, folders = listOf(FavoriteBackupCodec.FolderRecord(folder.absolutePath, 7)))
        val service = GalleryRestoreService(context, db, inspector)
        val plan = service.preview(incoming)
        assertTrue(plan.targets.getValue(GalleryRestoreMode.MERGE).preferenceChanges.isEmpty())
        val revision = PhotoContentRevision.current()
        changeDuringCheck = true
        assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(plan, GalleryRestoreMode.MERGE) }
        assertEquals(9, context.getSharedPrefs().getInt("independent_preference", 0))
        assertEquals(revision, PhotoContentRevision.current()); assertFalse(GalleryRestoreJournal(context).exists())
    }

    @Test fun authenticatedReplanExcludesOnlyRetryCountersNotOtherPreferences() {
        val a = file("a.jpg"); enable(); val service = service()
        val initial = service.preview(fullBackupFixture(context, listOf(a)))
        context.getSharedPrefs().edit().putBoolean(SHOW_ALL, !context.config.showAll).commit()
        assertThrows(StaleGalleryRestorePlan::class.java) { service.authorize(initial, mode, proof(), GalleryRestoreOperation()) }
        assertTrue(paths().isEmpty()); assertFalse(GalleryRestoreJournal(context).exists())
    }

    @Test fun committedProfileConflictCountMatchesCaseInsensitiveUuidConflictRules() {
        val original = backupProfile(path = root.absolutePath).copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, root.absolutePath)))
        PhotoProfileRepository.of(context).save(original)
        val collision = original.copy(id = original.id.uppercase(java.util.Locale.ROOT), name = "conflicting imported name")
        val incoming = fullBackupFixture(context, profiles = PhotoProfileState(profiles = listOf(collision), selectedId = collision.id))
        val service = service(); val plan = service.preview(incoming)
        assertEquals(1, plan.targets.getValue(GalleryRestoreMode.MERGE).profileCount.conflicts)
        val result = service.restore(plan, GalleryRestoreMode.MERGE)
        assertEquals(1, result.profiles.conflicts); assertEquals(1, result.profiles.appliedAdded)
        assertEquals(2, PhotoProfileRepository.of(context).state().profiles.size)
        assertEquals(0, result.profiles.appliedRemoved)
    }

}
