package org.fossify.gallery.helpers

import android.app.Activity
import android.content.ContextWrapper
import android.content.res.ColorStateList
import androidx.core.widget.TextViewCompat
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor

/** Public, version-pinned build hooks. No reflection or access to Commons private state. */
object ClassicCommonsHooks {
    @JvmStatic
    fun prepareBuilder(builder: AlertDialog.Builder): AlertDialog.Builder {
        if (builder is com.google.android.material.dialog.MaterialAlertDialogBuilder) {
            // The final classic window has no transparent Material inset. Keep the
            // builder's public outside-touch region consistent with that window.
            builder.setBackgroundInsetStart(0).setBackgroundInsetTop(0)
                .setBackgroundInsetEnd(0).setBackgroundInsetBottom(0)
        }
        return builder
    }

    private fun findActivity(start: android.content.Context): Activity? {
        var context = start
        while (context is ContextWrapper && context !is Activity) {
            val base = context.baseContext
            if (base === context) return null
            context = base
        }
        return context as? Activity
    }

    /** Replaces Gallery Builder.show() calls, without recursively invoking Builder.show(). */
    @JvmStatic
    fun showClassicBuilder(builder: AlertDialog.Builder): AlertDialog {
        val dialog = prepareBuilder(builder).create()
        val activity = findActivity(dialog.context)
        if (activity != null) beforeDialogShow(activity, dialog, null)
        dialog.show()
        if (activity != null) {
            OxygenClassicDialog.apply(activity, dialog, dialog.window!!.decorView)
            onDialogShown(activity, dialog, null)
        }
        return dialog
    }

    @JvmStatic
    fun beforeDialogShow(activity: Activity, dialog: AlertDialog, content: View?) {
        // Public Dialog.create() initializes AlertController before WindowManager.addView.
        // show() subsequently reuses that initialized content and its original callbacks.
        dialog.create()
        OxygenClassicDialog.prepareWindow(activity, dialog)
        installFirstDrawGuard(activity, dialog)
    }

    @JvmStatic
    fun onDialogShown(activity: Activity, dialog: AlertDialog, content: View?) {
        installFirstDrawGuard(activity, dialog).markDirty()
    }

    private fun installFirstDrawGuard(activity: Activity, dialog: AlertDialog): DialogFrameObserver {
        val decor = requireNotNull(dialog.window).decorView
        val existing = decor.getTag(org.fossify.gallery.R.id.classic_dialog_observer) as? DialogFrameObserver
        if (existing != null) return existing
        return DialogFrameObserver(activity, dialog, decor).also {
            decor.setTag(org.fossify.gallery.R.id.classic_dialog_observer, it)
            it.attach()
        }
    }

    private class DialogFrameObserver(
        private val activity: Activity,
        private val dialog: AlertDialog,
        private val decor: View
    ) : ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnGlobalLayoutListener,
        View.OnAttachStateChangeListener, LifecycleEventObserver, View.OnLayoutChangeListener {
        private val host = activity.window.decorView
        private var dirty = true
        private var firstFrameReleased = false
        private var geometryPending = false
        private var cleaned = false

        fun attach() {
            decor.viewTreeObserver.addOnPreDrawListener(this)
            decor.viewTreeObserver.addOnGlobalLayoutListener(this)
            decor.addOnAttachStateChangeListener(this)
            host.addOnLayoutChangeListener(this)
            dialog.lifecycle.addObserver(this)
        }

        fun markDirty() { dirty = true }
        override fun onGlobalLayout() { markDirty() }

        override fun onPreDraw(): Boolean {
            if (activity.isFinishing || activity.isDestroyed) {
                cleanup()
                return true
            }
            if (!dialog.isShowing || cleaned) return true
            val result = if (dirty) {
                dirty = false
                OxygenClassicDialog.apply(activity, dialog, decor)
            } else OxygenClassicDialog.StyleResult()
            val resized = geometryPending
            geometryPending = false
            if ((!firstFrameReleased || resized) &&
                (resized || result.windowChanged || result.layoutRequested || decor.isLayoutRequested)) {
                // Cancel this unready draw, not the window's normal entrance animation.
                // The next traversal may draw as soon as all requested layout is complete.
                decor.requestLayout()
                decor.invalidate()
                return false
            }
            firstFrameReleased = true
            return true
        }

        override fun onLayoutChange(
            view: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
        ) {
            if (cleaned) return
            if (activity.isFinishing || activity.isDestroyed) {
                cleanup()
                return
            }
            if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return
            // This is the activity window, not dialog content/global layout. IME height
            // changes resolve the same IME-independent width and therefore do not resize it.
            val update = OxygenClassicDialog.prepareWindow(activity, dialog)
            if (update.geometryChanged && dialog.isShowing) geometryPending = true
            markDirty()
        }

        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = cleanup()
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) cleanup()
        }

        private fun cleanup() {
            if (cleaned) return
            cleaned = true
            if (decor.viewTreeObserver.isAlive) {
                decor.viewTreeObserver.removeOnPreDrawListener(this)
                decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
            decor.removeOnAttachStateChangeListener(this)
            host.removeOnLayoutChangeListener(this)
            decor.setTag(org.fossify.gallery.R.id.classic_dialog_observer, null)
            dialog.lifecycle.removeObserver(this)
        }
    }

    @JvmStatic
    fun positionBreadcrumbRoot(view: ViewGroup, scrollX: Int) {
        val items = view.getChildAt(0) as? ViewGroup ?: return
        val root = items.getChildAt(0) ?: return
        val offset = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            minOf(0, scrollX + view.width - view.paddingRight - items.left - root.right)
        } else {
            maxOf(0, scrollX + view.paddingLeft - items.left - root.left)
        }
        root.translationX = offset.toFloat()
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
        val text = if (light) Color.rgb(33, 33, 33) else Color.WHITE
        val accent = OxygenClassicPalette.readableAccent(view.context.getProperPrimaryColor(), surface)
        val textColors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(accent, text))
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
                setTextColor(textColors)
                TextViewCompat.setCompoundDrawableTintList(this, textColors)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        }
    }
}
