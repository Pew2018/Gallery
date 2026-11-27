package org.fossify.gallery.helpers

import android.app.Activity
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.gallery.R

/**
 * Wraps Commons' action callback instead of replacing selection behavior.
 * Commons keeps selection keys, select-all interaction, and file operations.
 */
object OxygenSelectionMode {
    private val pendingPresentation = java.util.WeakHashMap<Activity, Boolean>()

    /** AppCompat calls these boundaries after installing its CAB fade. Complete
     * only our managed CAB presentation, preserving its callbacks and cleanup. */
    @android.annotation.SuppressLint("RestrictedApi")
    internal fun completePresentation(activity: Activity, entering: Boolean) {
        if (pendingPresentation[activity] != entering) return
        pendingPresentation.remove(activity)
        val bar = activity.findViewById<androidx.appcompat.widget.ActionBarContextView>(
            androidx.appcompat.R.id.action_mode_bar)
        // Cancels only this CAB's opacity animation, never Activity/Viewer motion.
        // Cancel delivers an existing end listener synchronously if it has started.
        bar?.animate()?.cancel()
        if (entering && bar != null) {
            bar.alpha = 1f
            bar.visibility = View.VISIBLE
            OxygenToolbarStyle.selectionVisibility(activity, true)
        } else {
            // A pending animator may be cancelled before its end listener runs.
            // killMode is idempotent and releases the same Commons custom/menu views.
            bar?.killMode()
            bar?.visibility = View.GONE
            OxygenToolbarStyle.selectionVisibility(activity, false)
        }
        androidx.core.view.ViewCompat.requestApplyInsets(activity.window.decorView)
    }

    fun wrap(activity: Activity, delegate: MyActionModeCallback): MyActionModeCallback =
        object : MyActionModeCallback() {
            private var active = false
            private var layoutView: View? = null
            private var layoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

            private fun clearLayoutListener() {
                layoutListener?.let { listener ->
                    layoutView?.viewTreeObserver?.takeIf { it.isAlive }
                        ?.removeOnGlobalLayoutListener(listener)
                }
                layoutView = null
                layoutListener = null
            }
            override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
                val created = delegate.onCreateActionMode(mode, menu)
                isSelectable = delegate.isSelectable
                if (!created) return false
                active = true
                pendingPresentation[activity] = true
                style(mode)
                // Register after Commons' own one-shot global-layout tint callback.
                // This runs once after attachment, with no delayed polling/repeated listener.
                mode.customView?.let { view ->
                    layoutView = view
                    val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                        clearLayoutListener()
                        if (active && !activity.isFinishing && !activity.isDestroyed) style(mode)
                    }
                    layoutListener = listener
                    view.viewTreeObserver.addOnGlobalLayoutListener(listener)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = delegate.onPrepareActionMode(mode, menu)
                if (active) style(mode)
                return result
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                delegate.onActionItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode) {
                active = false
                clearLayoutListener()
                delegate.onDestroyActionMode(mode)
                pendingPresentation[activity] = false
                isSelectable = delegate.isSelectable
                // Blend back to the live theme. A saved accent is read again here and
                // is never overwritten by the selection chrome.
                OxygenSelectionChrome.move(activity, 0f)
                OxygenToolbarStyle.refresh(activity)
                OxygenSystemBars.apply(activity)
            }

            // Commons owns this AppCompat CAB; only adapt its existing content-height hook.
            @android.annotation.SuppressLint("RestrictedApi")
            private fun style(mode: ActionMode) {
                val title = mode.customView as? TextView
                title?.textSize = 20f
                SelectionCountFormatter.selectedCount(title?.text)?.let { count ->
                    title?.text = activity.getString(R.string.oxygen_selected_count, count)
                }
                val bar = contextBar(title)
                    ?: activity.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
                if (bar is androidx.appcompat.widget.ActionBarContextView) {
                    bar.contentHeight = OxygenToolbarStyle.selectionContentHeight(activity)
                }
                // Count changes call style again. The same target does not restart the blend.
                OxygenSelectionChrome.attach(activity, mode)
                OxygenSelectionChrome.move(activity, 1f)
            }

            private fun contextBar(view: View?): View? {
                var parent = view
                while (parent != null) {
                    if (parent is androidx.appcompat.widget.ActionBarContextView) return parent
                    parent = parent.parent as? View
                }
                return null
            }
        }
}
