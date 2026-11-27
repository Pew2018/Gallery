package org.fossify.gallery.helpers

import android.app.Activity
import android.app.ActivityManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * Lightweight, OPT-IN capture of the window state that decides a predictive-back
 * preview. Nothing is logged unless Gallery diagnostics were enabled by the user,
 * so there is no cost or privacy impact in a normal build.
 *
 * This exists to answer one question on a real device: when a child page is scaled
 * by the system back gesture, which surface actually paints the area around it?
 * The fields below separate the layers the earlier revision conflated:
 *
 *  - theme colorBackground / windowBackground (what Android 16 resolves into the
 *    Activity TaskDescription in onApplyThemeResource);
 *  - the resolved windowIsTranslucent attribute (what disqualifies a window from
 *    the CROSS_ACTIVITY back animation - the old flag-based heuristic recorded
 *    opaque windows as translucent);
 *  - the actual TaskDescription backgroundColor the server reports back;
 *  - the decor/content backgrounds the window actually paints with.
 *
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
        val themeWindowBackground = resolveThemeDrawableAttr(activity, android.R.attr.windowBackground)
        val themeColorBackground = resolveThemeColorAttr(activity, android.R.attr.colorBackground)
        val themeWindowIsTranslucent = resolveThemeBooleanAttr(activity, android.R.attr.windowIsTranslucent)
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, MODULE, "window_state_fields", fields = buildMap {
            put("phase", phase)
            put("activity", activity.javaClass.simpleName)
            put("instance", System.identityHashCode(activity).toString(16))
            put("sdk", Build.VERSION.SDK_INT)
            put("taskId", activity.taskId)
            put("isFinishing", activity.isFinishing)
            put("isDestroyed", activity.isDestroyed)
            put("flags", "0x${Integer.toHexString(attributes.flags)}")
            put("softInput", attributes.softInputMode)
            put("windowAnimations", attributes.windowAnimations)
            put("alpha", attributes.alpha)
            put("dimAmount", attributes.dimAmount)
            // Resolved theme attribute - the actual CROSS_ACTIVITY disqualifier.
            // FLAG_TRANSLUCENT_STATUS/NAVIGATION are bar cosmetics and say nothing
            // about whether the window occludes its parent; do not infer from them.
            put("themeWindowIsTranslucent", themeWindowIsTranslucent)
            put("flagTranslucentStatus", isFlag(attributes.flags, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS))
            put("flagTranslucentNavigation", isFlag(attributes.flags, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION))
            put("blurBehind", isFlag(attributes.flags, WindowManager.LayoutParams.FLAG_BLUR_BEHIND))
            put("themeColorBackgroundArgb", themeColorBackground)
            put("themeWindowBackground", themeWindowBackground)
            put("decorBackgroundArgb", decor.background.peekArgb())
            put("contentBackgroundArgb", content?.background.peekArgb())
            put("decorBounds", decor.bounds())
            put("contentBounds", content?.bounds())
            put("measuredWidth", decor.measuredWidth)
            put("measuredHeight", decor.measuredHeight)
            put("systemDark", (activity.applicationContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
            put("uiMode", activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            put("taskDescriptionBackgroundArgb", readTaskDescriptionBackground(activity))
        })
        val dispatcher = (activity as? ComponentActivity)?.onBackPressedDispatcher
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, MODULE, "back_callbacks", fields = buildMap {
            put("phase", phase)
            put("activity", activity.javaClass.simpleName)
            // Only reports whether a default-priority callback is enabled; it says
            // nothing about which BackNavigation type the server selected.
            put("hasEnabledCallbacks", dispatcher?.hasEnabledCallbacks() ?: false)
        })
    }

    private fun isFlag(flags: Int, flag: Int) = flags and flag != 0

    private fun android.graphics.drawable.Drawable?.peekArgb(): String {
        val color = (this as? ColorDrawable)?.color ?: return "none"
        return String.format(java.util.Locale.US, "#%08X", color)
    }

    private fun resolveThemeDrawableAttr(activity: Activity, attr: Int): String {
        val typed = themeAttr(activity, attr) ?: return "none"
        return when {
            typed.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typed.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT ->
                String.format(java.util.Locale.US, "#%08X", typed.data)
            else -> "drawable/${typed.resourceId}"
        }
    }

    private fun resolveThemeColorAttr(activity: Activity, attr: Int): String {
        val typed = themeAttr(activity, attr) ?: return "none"
        return if (typed.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
            typed.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            String.format(java.util.Locale.US, "#%08X", typed.data)
        } else "non-color/${typed.type}"
    }

    private fun themeAttr(activity: Activity, attr: Int): android.util.TypedValue? {
        val typed = android.util.TypedValue()
        val found = activity.theme.resolveAttribute(attr, typed, true)
        return if (found) typed else null
    }

    private fun resolveThemeBooleanAttr(activity: Activity, attr: Int): Boolean {
        val typed = themeAttr(activity, attr) ?: return false
        return typed.type == android.util.TypedValue.TYPE_INT_BOOLEAN && typed.data != 0
    }

    /** Read back the server-side merged task background for this activity's task. */
    private fun readTaskDescriptionBackground(activity: Activity): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unavailable"
        return try {
            val manager = activity.getSystemService(Activity.ACTIVITY_SERVICE) as? ActivityManager ?: return "unavailable"
            val task = manager.appTasks.firstOrNull { appTask ->
                try {
                    appTask.taskInfo?.taskId == activity.taskId
                } catch (_: SecurityException) {
                    false
                }
            } ?: return "not-found"
            val description = task.taskInfo?.taskDescription
                ?: return "no-description"
            val color = description.backgroundColor
            if (color == 0) "unset" else String.format(java.util.Locale.US, "#%08X", color)
        } catch (_: Exception) {
            "error"
        }
    }

    private fun android.view.View.bounds(): String =
        "[$left,$top,$right,$bottom]"

    /**
     * Call when a page has its content and final theme. Settings captures after
     * setContentView: the created sample must reflect the same configuration the
     * back gesture will see, not the pre-inflation defaults.
     */
    fun captureCreated(activity: Activity) = capture(activity, "created")

    /** Call right before a deliberate finish to pair create and return state. */
    fun captureClosing(activity: Activity, reason: String) = capture(activity, "closing:$reason")
}
