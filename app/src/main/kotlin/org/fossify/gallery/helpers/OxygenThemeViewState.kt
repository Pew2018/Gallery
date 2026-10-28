package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MySearchMenu

/** Preserve navigation UI across the single theme-triggered recreation, including async lists. */
class OxygenThemeViewState {
    private val cleanup = ArrayList<() -> Unit>()

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), action)
    }

    fun save(activity: Activity, out: Bundle) {
        val state = Bundle()
        walk(activity.findViewById(android.R.id.content)) { view ->
            if (view.id != View.NO_ID) {
                when (view) {
                    is MySearchMenu -> state.putBundle("search_${view.id}", Bundle().apply {
                        putString("query", view.getCurrentQuery())
                        putBoolean("open", view.isSearchOpen)
                    })
                    is RecyclerView -> state.putParcelable("list_${view.id}",
                        view.layoutManager?.onSaveInstanceState())
                }
            }
        }
        out.putBundle("classic_theme_view_state", state)
    }

    @Suppress("DEPRECATION")
    fun restore(activity: Activity, saved: Bundle?) {
        val state = saved?.getBundle("classic_theme_view_state") ?: return
        walk(activity.findViewById(android.R.id.content)) { view ->
            when (view) {
                is MySearchMenu -> state.getBundle("search_${view.id}")?.let { search ->
                    // Commons installs its focus listener via post during setupMenu().
                    val restore = Runnable {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            val query = search.getString("query").orEmpty()
                            if (view.getCurrentQuery() != query) view.binding.topToolbarSearch.setText(query)
                            if (search.getBoolean("open")) view.focusView()
                        }
                    }
                    view.post(restore)
                    cleanup.add { view.removeCallbacks(restore) }
                }
                is RecyclerView -> state.getParcelable<Parcelable>("list_${view.id}")?.let { position ->
                    restoreList(view, position)
                }
            }
        }
    }

    private fun restoreList(view: RecyclerView, position: Parcelable) {
        val adapter = view.adapter
        if (adapter != null && adapter.itemCount > 0) {
            view.layoutManager?.onRestoreInstanceState(position)
        } else if (adapter != null) {
            var registered = true
            val observer = object : RecyclerView.AdapterDataObserver() {
                private fun restoreIfReady() {
                    if (!registered || adapter.itemCount == 0) return
                    registered = false
                    adapter.unregisterAdapterDataObserver(this)
                    view.layoutManager?.onRestoreInstanceState(position)
                }
                override fun onChanged() = restoreIfReady()
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = restoreIfReady()
            }
            adapter.registerAdapterDataObserver(observer)
            cleanup.add {
                if (registered) {
                    registered = false
                    adapter.unregisterAdapterDataObserver(observer)
                }
            }
        } else {
            val listener = object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewDetachedFromWindow(view: View) = Unit
                override fun onChildViewAttachedToWindow(child: View) {
                    view.removeOnChildAttachStateChangeListener(this)
                    val restore = Runnable { view.layoutManager?.onRestoreInstanceState(position) }
                    view.post(restore)
                    cleanup.add { view.removeCallbacks(restore) }
                }
            }
            view.addOnChildAttachStateChangeListener(listener)
            cleanup.add { view.removeOnChildAttachStateChangeListener(listener) }
        }
    }

    fun clear() {
        cleanup.forEach { it() }
        cleanup.clear()
    }
}
