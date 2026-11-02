package org.fossify.gallery.helpers.backup

import android.Manifest
import android.app.Application
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.PROTECTION_PIN
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
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.io.File
import java.util.concurrent.Executor

/** Simulates OS callbacks only. Does not claim physical biometric hardware validation. */
@RunWith(RobolectricTestRunner::class)
@RoboConfig(sdk = [28], application = Application::class,
    shadows = [RestoreBiometricCallbackTest.Hardware::class, RestoreBiometricCallbackTest.Prompt::class])
class RestoreBiometricCallbackTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        context.getSharedPrefs().edit().clear().commit()
        PhotoAuthentication.requests.cancelAll(); FavoriteAccessSessionManager.revokeAll(); GalleryRestoreJournal(context).delete()
        shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        Prompt.count = 0; Prompt.callback = null
    }
    @After fun cleanup() { PhotoAuthentication.requests.cancelAll(); Prompt.callback = null; GalleryRestoreJournal(context).delete() }
    @Implements(value = Reprint::class, isInAndroidSdk = false)
    class Hardware {
        companion object { @JvmStatic @Implementation fun isHardwarePresent() = true }
    }
    @Implements(value = BiometricPrompt::class, isInAndroidSdk = false)
    class Prompt {
        @Implementation fun __constructor__(host: FragmentActivity, executor: Executor, listener: BiometricPrompt.AuthenticationCallback) {
            callback = listener
        }
        @Implementation fun authenticate(info: BiometricPrompt.PromptInfo) { count++ }
        companion object {
            var count = 0
            var callback: BiometricPrompt.AuthenticationCallback? = null
        }
    }

    @Test fun cancelAndRetryMismatchDoNotAuthorizeOrWriteAndDuplicateRequestIsRejected() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        try {
            activity.config.saveFavoriteBiometricCredential("fallback", PROTECTION_PIN)
            val before = context.getSharedPrefs().all
            var calls = 0
            var proof: org.fossify.gallery.helpers.Config.FavoriteCredentialProof? = null
            assertTrue(activity.authenticateFavoritePassword(allowLegacyMigration = false) { calls++; proof = it })
            assertEquals(1, Prompt.count)
            assertFalse(activity.authenticateFavoritePassword(allowLegacyMigration = false) { fail("duplicate") })
            val callback = Prompt.callback!!
            callback.onAuthenticationFailed()
            assertEquals(0, calls); assertNull(proof)
            callback.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "cancelled")
            callback.onAuthenticationSucceeded(androidx.biometric.TestAuthenticationResultFactory.success())
            assertEquals(1, calls); assertNull(proof); assertEquals(before, context.getSharedPrefs().all)
        } finally { controller.destroy() }
    }

    @Test fun successfulSystemCallbackProducesProofForOneReconfirmedRestoreAndExpiresAfterUse() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        val db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).allowMainThreadQueries().build()
        val file = File(context.cacheDir, "restore-biometric-source.jpg").apply { writeText("source") }
        try {
            activity.config.saveFavoriteBiometricCredential("fallback", PROTECTION_PIN)
            val service = GalleryRestoreService(context, db)
            val initial = service.preview(fullBackupFixture(context, listOf(file.absolutePath)))
            var proof: org.fossify.gallery.helpers.Config.FavoriteCredentialProof? = null
            activity.authenticateFavoritePassword(allowLegacyMigration = false) { proof = it }
            assertEquals(1, Prompt.count)
            val callback = Prompt.callback!!
            callback.onAuthenticationSucceeded(androidx.biometric.TestAuthenticationResultFactory.success())
            assertNotNull(proof)
            val operation = GalleryRestoreOperation()
            val formal = service.authorize(initial, GalleryRestoreMode.MERGE, proof, operation)
            assertEquals(0L, db.FavoritesDao().getFavoritesCount())
            val result = service.restore(formal, GalleryRestoreMode.MERGE, operation)
            assertEquals(1, result.media.appliedAdded)
            assertEquals(listOf(file.absolutePath), db.FavoritesDao().getValidFavoritePaths())
            assertFalse(formal.authorization!!.current(context, formal, GalleryRestoreMode.MERGE))
            assertThrows(StaleGalleryRestorePlan::class.java) { service.restore(formal, GalleryRestoreMode.MERGE, operation) }
        } finally { db.close(); file.delete(); controller.destroy() }
    }
}
