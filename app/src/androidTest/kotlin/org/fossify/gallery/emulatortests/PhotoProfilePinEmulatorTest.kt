package org.fossify.gallery.emulatortests

import android.app.Instrumentation
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.extensions.authenticatePhotoPassword
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.configurePhotoPassword
import org.fossify.gallery.extensions.unlockPhotoProfile
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.PhotoProfile
import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.helpers.PhotoProfileRepository
import org.fossify.gallery.helpers.PhotoSource
import org.fossify.gallery.helpers.PhotoSourceType
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PIN-only UI authentication (no biometrics), with two truly protected source
 * folders. The folder LOCK records are seeded from fixture-specific PIN
 * hashes; the live capability is obtained EXCLUSIVELY via the real UI flow.
 * No code here writes source bindings, grants capabilities or mocks credentials.
 */
@RunWith(AndroidJUnit4::class)
class PhotoProfilePinEmulatorTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val device get() = UiDevice.getInstance(inst)

    private fun waitFor(label: String, deadline: Long = 20000L, ready: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + deadline
        while (SystemClock.elapsedRealtime() < until) {
            if (ready()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("PIN UI timeout waiting for " + label)
    }

    private fun button(id: String): androidx.test.uiautomator.UiObject2 =
        device.wait(Until.findObject(By.res(context.packageName, id)), 15000L)
            ?: throw AssertionError("Missing genuine PIN UI control " + id)

    private fun enterPin(value: String) {
        for (digit in value) button("pin_" + digit).click()
        button("pin_ok").click()
        Log.i("GalleryEmulatorPIN", "PIN challenge submitted (" + value.length + " digits); no PIN value logged")
    }

    private fun fixtureHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
    }

    private fun cancelDialog() {
        val label = context.getString(org.fossify.commons.R.string.cancel)
        val control = device.wait(Until.findObject(By.text(label)), 12000)
            ?: throw AssertionError("Could not find cancellation in security dialog")
        control.click()
    }

    @Test fun realPinSetupWrongCancelMultiLockBindingAndRevocation() {
        Assume.assumeTrue("PIN suite only",
            InstrumentationRegistry.getArguments().getString("suite") == "pin" &&
                InstrumentationRegistry.getArguments().getString("scale") == "smoke")
        PhotoProfileEmulatorTest.buildMediaFixtures()
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryCI")
        val protectedP = File(root, "P").canonicalPath
        val protectedP2 = File(root, "P2").canonicalPath
        val secondFolder = File(protectedP2)
        assertTrue(secondFolder.mkdirs() || secondFolder.isDirectory)
        File(root, "P/IMG_0.png").copyTo(File(secondFolder, "SECOND.png"), overwrite = true)

        val repo = PhotoProfileRepository.of(context)
        val profile = PhotoProfile(UUID.randomUUID().toString(), "PIN regression",
            listOf("A", "P", "P2").map { PhotoSource(PhotoSourceType.FOLDER, File(root, it).canonicalPath) })
        val config = context.config
        val priorCredential = config.photoCredentialSnapshot()
        Assume.assumeFalse("Do not touch a preconfigured credential", priorCredential.enabled)
        val originalP = config.isFolderProtected(protectedP)
        val originalP2 = config.isFolderProtected(protectedP2)
        Assume.assumeFalse("Fixture source already locked", originalP || originalP2)
        var host: MediaActivity? = null
        try {
            val intent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, PhotoProfileMediaLoader.PATH)
            }
            host = inst.startActivitySync(intent) as MediaActivity
            val activity = host
            val setupComplete = AtomicBoolean(false)
            inst.runOnMainSync {
                activity.configurePhotoPassword { credential ->
                    setupComplete.set(credential != null)
                }
            }
            val pinLabel = context.getString(org.fossify.commons.R.string.pin)
            val choosePin = device.wait(Until.findObject(By.text(pinLabel)), 12000L)
                ?: throw AssertionError("Actual PIN-vs-pattern choice not shown")
            choosePin.click()
            enterPin("1357")
            enterPin("1357")
            waitFor("photo PIN credential configured") { setupComplete.get() }
            val credential = config.photoCredentialSnapshot()
            assertTrue("Configured credential is not current", config.isPhotoCredentialCurrent(credential))
            assertEquals(PROTECTION_PIN, credential.type)
            assertEquals("Fixture PIN hash differs from SecurityDialog hash", fixtureHash("1357"), credential.hash)

            // Synthetic source setup, not access grant: creates two real locks
            // and forces the app to verify distinct PIN secrets.
            config.addFolderProtection(protectedP, credential.hash, PROTECTION_PIN)
            config.addFolderProtection(protectedP2, fixtureHash("2468"), PROTECTION_PIN)
            repo.save(profile)
            PhotoProfileAccessSessionManager.revokeAll("pin_ci_preflight")
            val blocked = PhotoProfileMediaLoader(context).load(profile.id)
                .filterIsInstance<Medium>().map { it.path }
            assertTrue("Public A media unexpectedly absent", blocked.any { it.contains("/A/") })
            assertFalse("P leaked before unlocking", blocked.any { it.contains("/P/") })
            assertFalse("P2 leaked before unlocking", blocked.any { it.contains("/P2/") })

            // Incorrect first PIN followed by cancel must never create a token.
            val badComplete = AtomicBoolean(false)
            inst.runOnMainSync { activity.unlockPhotoProfile(profile.id) { badComplete.set(true) } }
            enterPin("0000")
            cancelDialog()
            waitFor("cancel callback") { badComplete.get() }
            assertNull("Wrong PIN + cancel yielded a token",
                PhotoProfileAccessSessionManager.currentToken(context))

            val goodComplete = AtomicBoolean(false)
            inst.runOnMainSync { activity.unlockPhotoProfile(profile.id) { goodComplete.set(true) } }
            enterPin("1357")
            // The same PIN covers P. P2 has a separate credential; the
            // original app should request separate consent and a second PIN.
            val okLabel = context.getString(org.fossify.commons.R.string.ok)
            val bindPrompt = device.wait(Until.findObject(By.text(okLabel)), 20000)
                ?: throw AssertionError("Missing second-source PIN challenge consent")
            bindPrompt.click()
            enterPin("2468")
            waitFor("two-source authorization") { goodComplete.get() }
            val token = PhotoProfileAccessSessionManager.currentToken(context)
            assertNotNull("Valid PINs were not granted a photo capability", token)
            val unlocked = PhotoProfileMediaLoader(context, sessionId = token).load(profile.id)
                .filterIsInstance<Medium>().map { it.path }
            assertTrue("P absent after real PIN verification", unlocked.any { it.contains("/P/") })
            assertTrue("P2 absent after separate PIN verification", unlocked.any { it.contains("/P2/") })
            Log.i("GalleryEmulatorPIN", "AUTHORIZED sources=A,P,P2 count=" + unlocked.size)

            PhotoProfileAccessSessionManager.revokeAll("pin_ci_verify_revoke")
            val revoked = PhotoProfileMediaLoader(context).load(profile.id)
                .filterIsInstance<Medium>().map { it.path }
            assertFalse("Revoked P content leaked", revoked.any { it.contains("/P/") })
            assertFalse("Revoked P2 content leaked", revoked.any { it.contains("/P2/") })
            Log.i("GalleryEmulatorPIN", "REVOKED sources=P,P2 count=" + revoked.size)
        } finally {
            host?.runOnUiThread { host?.finish() }
            inst.waitForIdleSync()
            PhotoProfileAccessSessionManager.revokeAll("pin_ci_cleanup")
            repo.delete(profile.id)
            config.removeFolderProtection(protectedP)
            config.removeFolderProtection(protectedP2)
            config.clearPhotoCredential()
            secondFolder.deleteRecursively()
        }
    }
}
