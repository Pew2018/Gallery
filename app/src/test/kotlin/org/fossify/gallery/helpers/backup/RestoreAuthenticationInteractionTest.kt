package org.fossify.gallery.helpers.backup

import android.Manifest
import android.app.Application
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.room.Room
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.commons.views.PatternTab
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.authenticateFavoritePassword
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.security.MessageDigest
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@RoboConfig(sdk = [28], application = Application::class)
class RestoreAuthenticationInteractionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        context.getSharedPrefs().edit().clear().commit()
        PhotoAuthentication.requests.cancelAll(); FavoriteAccessSessionManager.revokeAll()
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        GalleryRestoreJournal(context).delete()
    }
    @After fun cleanup() {
        ShadowDialog.getShownDialogs().toList().forEach { it.dismiss() }
        PhotoAuthentication.requests.cancelAll(); GalleryRestoreJournal(context).delete()
    }
    private fun measure(dialog: AlertDialog) {
        val root = dialog.window!!.decorView
        val d = root.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((340 * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((800 * d).toInt(), View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun sha1(pin: String) = MessageDigest.getInstance("SHA-1").digest(pin.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun enterPin(dialog: AlertDialog, digits: String) {
        digits.forEach { digit -> dialog.findViewById<View>(context.resources.getIdentifier("pin_$digit", "id", context.packageName))!!.performClick() }
        dialog.findViewById<View>(R.id.pin_ok)!!.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
    }

    @Test fun realPinVerificationRejectsWrongInputThenReplansAfterCommonsRetryCounterReset() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        val db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).allowMainThreadQueries().build()
        val file = File(context.cacheDir, "restore-pin-source.jpg").apply { writeText("source") }
        try {
            activity.config.saveFavoriteCredential(sha1("1234"), PROTECTION_PIN)
            val credential = activity.config.favoriteCredentialSnapshot()
            val service = GalleryRestoreService(context, db)
            val initial = service.preview(fullBackupFixture(context, listOf(file.absolutePath)))
            var proof: org.fossify.gallery.helpers.Config.FavoriteCredentialProof? = null
            var calls = 0
            assertTrue(activity.authenticateFavoritePassword(allowLegacyMigration = false) { proof = it; calls++ })
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            measure(dialog)
            enterPin(dialog, "9999")
            assertEquals(0, calls); assertNull(proof); assertEquals(0L, db.FavoritesDao().getFavoritesCount())
            enterPin(dialog, "1234")
            assertEquals(1, calls); assertNotNull(proof)
            assertEquals(credential, activity.config.favoriteCredentialSnapshot())
            assertNotEquals(initial.localToken, GalleryBackupCollector(context, db).capture().token)
            val operation = GalleryRestoreOperation()
            val formal = service.authorize(initial, GalleryRestoreMode.MERGE, proof, operation)
            val result = service.restore(formal, GalleryRestoreMode.MERGE, operation)
            assertEquals(1, result.media.appliedAdded)
            assertEquals(listOf(file.absolutePath), db.FavoritesDao().getValidFavoritePaths())
        } finally { db.close(); file.delete(); controller.destroy() }
    }

    @Test fun actualPatternVerifierDoesNotIssueProofForMismatchAndCancelDoesNotAuthorize() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        try {
            activity.config.saveFavoriteCredential("correct-pattern-hash", PROTECTION_PATTERN)
            val credential = activity.config.favoriteCredentialSnapshot()
            var calls = 0
            var proof: org.fossify.gallery.helpers.Config.FavoriteCredentialProof? = null
            activity.authenticateFavoritePassword(allowLegacyMigration = false) { calls++; proof = it }
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            measure(dialog)
            val tab = dialog.findViewById<PatternTab>(R.id.pattern_lock_holder)!!
            val receive = PatternTab::class.java.getDeclaredMethod("receivedHash", String::class.java).apply { isAccessible = true }
            // Exercise Commons' actual hash-comparison boundary; gesture encoding remains unchanged.
            receive.invoke(tab, "incorrect-pattern-hash")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            assertEquals(0, calls); assertNull(proof)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, calls); assertNull(proof)
            assertEquals(credential, activity.config.favoriteCredentialSnapshot())
            activity.authenticateFavoritePassword(allowLegacyMigration = false) { calls++; proof = it }
            val next = ShadowDialog.getLatestDialog() as AlertDialog
            measure(next)
            receive.invoke(next.findViewById<PatternTab>(R.id.pattern_lock_holder)!!, "correct-pattern-hash")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            assertEquals(2, calls); assertNotNull(proof)
        } finally { controller.destroy() }
    }

    @Test fun restoreAuthenticationNeverConfiguresDisabledOrMigratesLegacyCredentials() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        try {
            for (legacy in listOf(false, true)) {
                if (legacy) activity.config.addFolderProtection(FAVORITES, "legacy-secret", PROTECTION_PIN)
                val before = context.getSharedPrefs().all
                val shown = ShadowDialog.getShownDialogs().size
                var calls = 0
                assertTrue(activity.authenticateFavoritePassword(allowLegacyMigration = false) { assertNull(it); calls++ })
                assertEquals(1, calls); assertEquals(before, context.getSharedPrefs().all)
                assertEquals(shown, ShadowDialog.getShownDialogs().size)
            }
        } finally { controller.destroy() }
    }

    @Test fun duplicateAuthenticationRequestsDoNotOpenTwoWindowsAndOwnerDestructionClosesLease() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        activity.config.saveFavoriteCredential(sha1("1234"), PROTECTION_PIN)
        val operation = GalleryRestoreOperation { !activity.isDestroyed && !activity.isFinishing }
        val field = SettingsActivity::class.java.getDeclaredField("restoreOperation").apply { isAccessible = true }
        field.set(activity, operation)
        var calls = 0
        assertTrue(activity.authenticateFavoritePassword(allowLegacyMigration = false) { calls++ })
        val first = ShadowDialog.getLatestDialog()
        assertFalse(activity.authenticateFavoritePassword(allowLegacyMigration = false) { fail("duplicate request") })
        assertSame(first, ShadowDialog.getLatestDialog())
        controller.destroy()
        assertFalse(operation.isActive()); assertEquals(0, calls)
        val owner = Any()
        val request = PhotoAuthentication.requests.begin(owner, PhotoProfileAccessSessionManager.currentGeneration())
        assertNotNull(request)
        PhotoAuthentication.requests.cancelAll()
    }
}
