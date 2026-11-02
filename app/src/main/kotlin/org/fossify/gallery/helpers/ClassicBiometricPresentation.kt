package org.fossify.gallery.helpers

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricPrompt
import androidx.biometric.auth.AuthPromptCallback
import androidx.biometric.auth.AuthPromptHost
import androidx.biometric.auth.Class2BiometricAuthPrompt
import androidx.fragment.app.FragmentActivity
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Only Commons' biometric tab uses this hook. Credential and Session code is unchanged. */
object ClassicBiometricPresentation {
    private val dialogs = WeakHashMap<Activity, WeakReference<AlertDialog>>()
    private val hidden = WeakHashMap<AlertDialog, Boolean>()
    fun isHidden(dialog: AlertDialog) = hidden[dialog] == true

    private val active = WeakHashMap<Activity, Boolean>()

    fun register(activity: Activity, dialog: AlertDialog) {
        if (dialog.findViewById<View>(org.fossify.commons.R.id.dialog_tab_view_pager) != null) {
            dialogs[activity] = WeakReference(dialog)
        }
    }

    internal var startPrompt: (FragmentActivity, AuthPromptCallback) -> Unit = { host, callback ->
        Class2BiometricAuthPrompt.Builder(
            host.getText(org.fossify.commons.R.string.authenticate),
            host.getText(org.fossify.commons.R.string.cancel)
        ).build().startAuthentication(AuthPromptHost(host), callback)
        Unit
    }

    @JvmStatic
    fun show(
        activity: Activity, success: ((String, Int) -> Unit)?, failure: (() -> Unit)?,
        @Suppress("UNUSED_PARAMETER") defaults: Int, @Suppress("UNUSED_PARAMETER") marker: Any?, tab: View
    ) {
        // initTab may auto-click before the dialog's first layout. Post until the
        // existing before-show hook has registered its window; do not dismiss it.
        if (active[activity] == true) return
        active[activity] = true
        tab.post {
            val host = activity as? FragmentActivity
            val dialog = dialogs[activity]?.get()
            if (host == null || activity.isFinishing || activity.isDestroyed || dialog?.isShowing != true ||
                dialog.findViewById<View>(org.fossify.commons.R.id.biometric_lock_holder) !== tab) {
                active.remove(activity)
                return@post
            }
            val window = dialog.window ?: run { active.remove(activity); return@post }
            val alpha = window.attributes.alpha
            val dim = window.attributes.dimAmount
            var terminal = false
            fun restore(): Boolean {
                if (terminal) return false
                terminal = true
                active.remove(activity)
                hidden.remove(dialog)
                window.attributes = window.attributes.apply { this.alpha = alpha; dimAmount = dim }
                return !activity.isFinishing && !activity.isDestroyed && dialog.isShowing
            }
            hidden[dialog] = true
            window.attributes = window.attributes.apply { this.alpha = 0f; dimAmount = 0f }
            try {
                startPrompt(host, object : AuthPromptCallback() {
                    override fun onAuthenticationSucceeded(activity: FragmentActivity?, result: BiometricPrompt.AuthenticationResult) {
                        if (restore()) success?.invoke("", PROTECTION_FINGERPRINT)
                    }
                    override fun onAuthenticationFailed(activity: FragmentActivity?) {
                        // A mismatch is retryable; keep the system prompt active.
                    }
                    override fun onAuthenticationError(activity: FragmentActivity?, errorCode: Int, errString: CharSequence) {
                        if (restore()) {
                            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                                host.toast(errString.toString())
                            }
                            failure?.invoke()
                        }
                    }
                })
            } catch (_: IllegalStateException) {
                if (restore()) failure?.invoke()
            }
        }
    }
}
