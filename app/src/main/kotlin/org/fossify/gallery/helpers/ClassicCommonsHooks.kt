package org.fossify.gallery.helpers

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor

/** Public, version-pinned build hooks. No reflection or access to Commons private state. */
object ClassicCommonsHooks {
    @JvmStatic
    fun onStandaloneDialogShown(dialog: AlertDialog) {
        var context = dialog.context
        while (context is ContextWrapper && context !is Activity) {
            val base = context.baseContext
            if (base === context) return
            context = base
        }
        (context as? Activity)?.let { onDialogShown(it, dialog, dialog.window?.decorView ?: return) }
    }

    @JvmStatic
    fun onDialogShown(activity: Activity, dialog: AlertDialog, content: View) {
        val decor = dialog.window?.decorView ?: return
        if (activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) {
            dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (decor.getTag(org.fossify.gallery.R.id.classic_dialog_observer) != null) return
        val listener = object : ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnGlobalLayoutListener,
            View.OnAttachStateChangeListener {
            private var dirty = true
            private var firstDraw = true
            override fun onGlobalLayout() { dirty = true }
            override fun onPreDraw(): Boolean {
                if (dirty && dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) {
                    dirty = false
                    if (firstDraw) {
                        decor.setTag(org.fossify.gallery.R.id.classic_dialog_palette, null)
                        firstDraw = false
                    }
                    OxygenClassicDialog.apply(activity, dialog, decor)
                }
                return true
            }
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                if (v.viewTreeObserver.isAlive) {
                    v.viewTreeObserver.removeOnPreDrawListener(this)
                    v.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
                v.removeOnAttachStateChangeListener(this)
                v.setTag(org.fossify.gallery.R.id.classic_dialog_observer, null)
            }
        }
        decor.setTag(org.fossify.gallery.R.id.classic_dialog_observer, listener)
        decor.viewTreeObserver.addOnPreDrawListener(listener)
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        decor.addOnAttachStateChangeListener(listener)
        // The first pre-draw runs after Commons' background, font and callback passes.
        // It also catches asynchronously appended property rows without polling or posting.
    }

    @JvmStatic
    fun hasBreadcrumbItems(view: ViewGroup): Boolean =
        ((view.getChildAt(0) as? ViewGroup)?.childCount ?: 0) > 0

    @JvmStatic
    fun breadcrumbRootLeft(view: ViewGroup): Int =
        (view.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.left ?: 0

    @JvmStatic
    fun clearBreadcrumbPresses(view: ViewGroup) {
        val items = view.getChildAt(0) as? ViewGroup ?: return
        for (index in 0 until items.childCount) {
            items.getChildAt(index).apply {
                cancelPendingInputEvents()
                isPressed = false
                background?.jumpToCurrentState()
            }
        }
    }

    @JvmStatic
    fun styleBreadcrumbs(view: ViewGroup) {
        val items = view.getChildAt(0) as? ViewGroup ?: return
        val light = ColorUtils.calculateLuminance(view.context.getProperBackgroundColor()) >= 0.5
        val surface = if (light) Color.WHITE else Color.rgb(48, 48, 48)
        for (index in 0 until items.childCount) {
            val item = items.getChildAt(index)
            // A new instance per item prevents shared bounds/hotspot/pressed state.
            item.background = OxygenClassicDialog.ripple(
                if (light) Color.BLACK else Color.WHITE,
                view.resources.displayMetrics.density,
                if (index == 0) surface else Color.TRANSPARENT
            )
            // Keep Commons' root elevation and opaque fill: it overlays scrolled items.
            // Its old accent stroke and pill radius are purely decorative.
            (item as? android.widget.TextView)?.apply {
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        }
    }
}
