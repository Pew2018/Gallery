package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager

/**
 * Lightweight, OPT-IN capture of the window state that decides a predictive-back
 * preview. Nothing is logged unless Gallery diagnostics were enabled by the user,
 * so there is no cost or privacy impact in a normal build.
 *
 * This exists to answer one question on a real device: when a child page is scaled
 * by the system back gesture, which surface actually paints the area around it?
 * The fields below are exactly the ones needed to separate "parent Activity is
 * gone / not previewed / mis-sized" from "child window background / SystemUI scrim".
 * It records no media path, file name or user data.
 */
internal object PredictiveBackDiagnostics {
    private const val MODULE = "PredictiveBack"

    fun capture(activity: Activity, phase: String) {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        val window = activity.window
        val attributes = window.attributes
        val decor = window.decorView
        val content = activity.findViewById<android.view.View>(android.R.id.content)
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, MODULE, "window_state_fields", fields = buildMap {
            put("phase", phase)
            put("activity", activity.javaClass.simpleName)
            put("sdk", Build.VERSION.SDK_INT)
            put("taskId", activity.taskId)
            put("isFinishing", activity.isFinishing)
            put("isDestroyed", activity.isDestroyed)
            put("flags", "0x${Integer.toHexString(attributes.flags)}")
            put("softInput", attributes.softInputMode)
            put("windowAnimations", attributes.windowAnimations)
            put("alpha", attributes.alpha)
            put("dimAmount", attributes.dimAmount)
            put("translucent", isFlag(attributes.flags, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) ||
                isOpaqueFlag(attributes.flags))
            put("blurBehind", isFlag(attributes.flags, WindowManager.LayoutParams.FLAG_BLUR_BEHIND))
            put("windowBackgroundArgb", window.peekBackgroundArgb())
            put("decorBackgroundArgb", decor.background.peekArgb())
            put("contentBackgroundArgb", content?.background?.peekArgb())
            put("decorBounds", decor.bounds())
            put("contentBounds", content?.bounds())
            put("measuredWidth", decor.measuredWidth)
            put("measuredHeight", decor.measuredHeight)
            put("uiMode", activity.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        })
        val dispatcher = (activity as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, MODULE, "back_callbacks", fields = buildMap {
            put("phase", phase)
            put("activity", activity.javaClass.simpleName)
            put("hasEnabledCallbacks", dispatcher?.hasEnabledCallbacks() ?: false)
        })
    }

    private fun isFlag(flags: Int, flag: Int) = flags and flag != 0

    private fun isOpaqueFlag(flags: Int): Boolean {
        // FLAG_TRANSLUCENT_WINDOW is hidden on some APIs; translucency is inferred from
        // the theme on the device side, so report the status/nav flags that are public.
        return !isFlag(flags, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }

    private fun android.graphics.drawable.Drawable?.peekArgb(): String {
        val color = (this as? ColorDrawable)?.color ?: return "none"
        return String.format(java.util.Locale.US, "#%08X", color)
    }

    private fun android.view.Window.peekBackgroundArgb(): String = decorView.background.peekArgb()

    private fun android.view.View.bounds(): String =
        "[$left,$top,$right,$bottom]"

    /** Call from onCreate/onResume of ordinary pages that take part in the repro path. */
    fun captureCreated(activity: Activity) = capture(activity, "created")

    /** Call right before finish/finishAfterTransition to pair create and return state. */
    fun captureClosing(activity: Activity, reason: String) = capture(activity, "closing:$reason")
}
