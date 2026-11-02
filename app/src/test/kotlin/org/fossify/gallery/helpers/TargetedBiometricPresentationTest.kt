package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import android.widget.LinearLayout
import androidx.biometric.BiometricPrompt
import androidx.biometric.auth.AuthPromptCallback
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class TargetedBiometricPresentationTest {
    @Test fun mismatchRetriesCancelRestoresAndSuccessDeliversOnlyOnce() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val host = controller.get().apply { setTheme(R.style.OxygenClassicAlertDialog_Light) }
        val tab = View(host).apply { id = org.fossify.commons.R.id.biometric_lock_holder }
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(host).apply { id = org.fossify.commons.R.id.dialog_tab_view_pager })
            addView(tab, LinearLayout.LayoutParams(100, 100))
        }
        val dialog = OxygenClassicDialog.show(host, OxygenClassicDialog.builder(host, "")
            .setView(root).setNegativeButton("Cancel", null))
        ClassicBiometricPresentation.register(host, dialog)
        var starts = 0
        var successes = 0
        var failures = 0
        lateinit var callback: AuthPromptCallback
        val original = ClassicBiometricPresentation.startPrompt
        ClassicBiometricPresentation.startPrompt = { _, cb -> starts++; callback = cb }
        fun launch() = ClassicBiometricPresentation.show(host, { _, _ -> successes++ }, { failures++ }, 0, null, tab)
        try {
            launch(); launch()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, starts)
            assertEquals(0f, dialog.window!!.attributes.alpha, 0f)
            assertTrue(dialog.isShowing)
            callback.onAuthenticationFailed(host)
            assertEquals(0, successes)
            assertEquals(0, failures)
            assertTrue(ClassicBiometricPresentation.isHidden(dialog))
            callback.onAuthenticationError(host, BiometricPrompt.ERROR_USER_CANCELED, "Canceled")
            assertEquals(1f, dialog.window!!.attributes.alpha, 0f)
            assertFalse(ClassicBiometricPresentation.isHidden(dialog))
            assertTrue(dialog.isShowing)
            assertEquals(1, failures)
            launch()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val result = androidx.biometric.TestAuthenticationResultFactory.success()
            callback.onAuthenticationSucceeded(host, result)
            callback.onAuthenticationSucceeded(host, result)
            assertEquals(1, successes)
            assertEquals(1f, dialog.window!!.attributes.alpha, 0f)
        } finally {
            ClassicBiometricPresentation.startPrompt = original
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }
}
