package org.fossify.gallery.helpers

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.DialogFragment
import androidx.appcompat.widget.ListPopupWindow
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R

/** DIALOG_OVERSCROLL_EFFECT = NONE. An irreversible policy for this View's lifetime.
 * Mark content before parenting/show; mark decor at the window boundary as well.
 * Does not replace hierarchy, touch, scroll, refresh, or adapter listeners.
 */
object DialogOverscrollPolicy {
    fun isForbidden(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.getTag(R.id.dialog_no_edge_glow) == true) return true
            current = current.parent as? View
        }
        return false
    }

    fun protect(root: View) {
        disableTree(root)
        if (root.getTag(R.id.dialog_overscroll_observer) != null) return
        // One observer per declared root, not per child. No global strong references.
        Observer(root).also {
            root.setTag(R.id.dialog_overscroll_observer, it)
            root.addOnAttachStateChangeListener(it)
            it.observe()
        }
    }

    private fun disableTree(view: View) {
        view.setTag(R.id.dialog_no_edge_glow, true)
        if (view is RecyclerView) OxygenClassicEdgeGlow.remove(view)
        (view as? ClassicGlowHost)?.stopEdgeGlow()
        view.overScrollMode = View.OVER_SCROLL_NEVER
        if (view is ViewGroup) for (i in 0 until view.childCount) disableTree(view.getChildAt(i))
    }

    private class Observer(private val root: View) : View.OnAttachStateChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener {
        private var tree: ViewTreeObserver? = null
        fun observe() {
            // The outermost declared root owns traversal. Nested picker/security
            // roots keep their reattach guard but need no duplicate layout scan.
            if ((root.parent as? View)?.let(::isForbidden) == true) { unobserve(); return }
            val next = root.viewTreeObserver
            if (tree === next) return
            unobserve()
            // A floating observer is merged into the window observer on attach.
            next.removeOnGlobalLayoutListener(this)
            next.addOnGlobalLayoutListener(this)
            tree = next
        }
        private fun unobserve() {
            val current = root.viewTreeObserver
            if (current !== tree && current.isAlive) current.removeOnGlobalLayoutListener(this)
            tree?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            tree = null
        }
        override fun onGlobalLayout() {
            if ((root.parent as? View)?.let(::isForbidden) == true) unobserve() else disableTree(root)
        }
        override fun onViewAttachedToWindow(v: View) { disableTree(root); observe() }
        override fun onViewDetachedFromWindow(v: View) { unobserve() }
    }

    /** Public presentation boundaries also cover plain Dialog and DialogFragment. */
    @JvmStatic fun showPlatformBuilder(builder: android.app.AlertDialog.Builder): android.app.AlertDialog {
        val dialog = builder.create()
        protectDialog(dialog)
        dialog.show()
        return dialog
    }
    @JvmStatic fun protectDialog(dialog: Dialog) {
        dialog.create()
        dialog.window?.decorView?.let(::protect)
    }
    @JvmStatic fun protectFragment(fragment: DialogFragment) {
        fragment.dialog?.let(::protectDialog)
    }
    @JvmStatic fun protectPopup(popup: ListPopupWindow) {
        popup.listView?.let(::protect)
    }
}

/** Custom page renderers must also honor dialog policy before drawing. */
interface ClassicGlowHost { fun stopEdgeGlow() }
