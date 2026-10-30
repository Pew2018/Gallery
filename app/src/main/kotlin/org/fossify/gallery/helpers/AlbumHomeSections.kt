package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Directory

/**
 * A single full-span row in the main RecyclerView.
 *
 * Collapsed pinned albums are a horizontal, recycled strip. Expanded albums are
 * NOT rendered in a nested grid: MainActivity inserts their normal
 * DirectoryAdapter directly into the parent ConcatAdapter so column counts,
 * pinch zoom and vertical recycling continue to work.
 */
class AlbumPinnedHeaderAdapter(
    private val context: Context,
    private val createStripAdapter: (MyRecyclerView, ArrayList<Directory>, Int) -> DirectoryAdapter,
    private val onToggle: () -> Unit
) : RecyclerView.Adapter<AlbumPinnedHeaderAdapter.Holder>() {
    private var albums = arrayListOf<Directory>()
    private var expanded = false
    private var columns = 3

    private fun dp(value: Int) = (context.resources.displayMetrics.density * value + 0.5f).toInt()

    fun submit(items: List<Directory>, isExpanded: Boolean, columnCount: Int) {
        val oldHasItems = albums.isNotEmpty()
        val oldItems = albums
        val oldExpanded = expanded
        val oldColumns = columns
        albums = ArrayList(items)
        expanded = isExpanded
        columns = columnCount.coerceAtLeast(1)
        if (oldHasItems != albums.isNotEmpty()) {
            if (albums.isEmpty()) notifyItemRemoved(0) else notifyItemInserted(0)
        } else if (albums.isNotEmpty() && (oldItems != albums || oldExpanded != expanded || oldColumns != columns)) {
            notifyItemChanged(0)
        }
    }

    override fun getItemCount() = if (albums.isEmpty()) 0 else 1

    class Holder(
        val container: LinearLayout,
        val title: TextView,
        val button: TextView,
        val strip: MyRecyclerView
    ) : RecyclerView.ViewHolder(container) {
        var stripWidth = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        val title = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        val button = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, 0, 0)
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onToggle() }
        }
        heading.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        column.addView(heading)
        val strip = MyRecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(dp(12), 0, dp(12), 0)
            clipToPadding = false
        }
        column.addView(strip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
        ))
        return Holder(column, title, button, strip)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val theme = OxygenThemeEngine.snapshot(context, (context as android.app.Activity).config)
        holder.title.setTextColor(theme.secondaryText)
        holder.button.setTextColor(theme.interactiveAccent)
        holder.title.text = "${context.getString(R.string.album_home_pinned)} · ${albums.size}"
        holder.button.text = if (expanded) "${context.getString(R.string.album_home_collapse)} ▲" else "${context.getString(R.string.album_home_expand)} ▼"
        holder.button.contentDescription = context.getString(if (expanded) R.string.album_home_collapse_description else R.string.album_home_expand_description)
        holder.strip.visibility = if (expanded) View.GONE else View.VISIBLE
        if (!expanded) {
            // Keep the strip reasonably compact even for a 1- or 2-column home grid.
            val available = (holder.container.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels) - dp(32)
            val width = (available / columns.coerceAtLeast(3)).coerceAtLeast(dp(72))
            holder.strip.layoutParams = (holder.strip.layoutParams as LinearLayout.LayoutParams).apply {
                // Cover + caption (name/count) + margins, not just the square.
                height = width + dp(52)
            }
            val current = holder.strip.adapter as? DirectoryAdapter
            if (current == null || holder.stripWidth != width) {
                holder.strip.adapter = createStripAdapter(holder.strip, ArrayList(albums), width)
                holder.stripWidth = width
            } else {
                current.updateDirs(ArrayList(albums))
            }
        }
    }
}

/** A compact, full-width label preceding the ordinary album grid. */
class AlbumOtherTitleAdapter(private val context: Context) :
    RecyclerView.Adapter<AlbumOtherTitleAdapter.Holder>() {
    private var count = 0
    private fun dp(v: Int) = (context.resources.displayMetrics.density * v + 0.5f).toInt()

    fun submit(newCount: Int) {
        val oldVisible = count > 0
        count = newCount
        if (oldVisible != (count > 0)) {
            if (count == 0) notifyItemRemoved(0) else notifyItemInserted(0)
        } else if (count > 0) notifyItemChanged(0)
    }

    override fun getItemCount() = if (count > 0) 1 else 0
    class Holder(val label: TextView) : RecyclerView.ViewHolder(label)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(TextView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            )
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val theme = OxygenThemeEngine.snapshot(context, (context as android.app.Activity).config)
        holder.label.setTextColor(theme.secondaryText)
        holder.label.text = "${context.getString(R.string.album_home_other)} · $count"
    }
}
