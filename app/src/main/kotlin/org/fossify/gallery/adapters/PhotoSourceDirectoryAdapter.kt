package org.fossify.gallery.adapters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import android.app.Activity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.R
import org.fossify.gallery.databinding.PhotoSourceItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Directory

/** A source row exposes selection only, never Gallery file operations. */
internal class PhotoSourceDirectoryAdapter(
    private val activity: Activity,
    var directories: List<Directory>,
    private val draft: PhotoSourceSelectionDraft,
    protectedPaths: Set<String>,
    private val selectionChanged: () -> Unit
) : RecyclerView.Adapter<PhotoSourceDirectoryAdapter.Holder>(), RecyclerViewFastScroller.OnPopupTextUpdate {
    class Holder(val binding: PhotoSourceItemBinding) : RecyclerView.ViewHolder(binding.root)
    private val boundHolders = HashMap<String, Holder>()
    private var protectedPaths = protectedPaths.toSet()
    override fun getItemCount() = directories.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(PhotoSourceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        ViewCompat.setAccessibilityDelegate(holder.binding.sourceCheckTarget, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.CheckBox::class.java.name
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        })
        return holder
    }

    fun updateDirectories(directories: List<Directory>, protectedPaths: Set<String> = this.protectedPaths) {
        val protectionChanged = this.protectedPaths != protectedPaths
        this.protectedPaths = protectedPaths.toSet()
        val next = directories.toList()
        val old = this.directories.toList()
        if (protectionChanged) {
            this.directories = next
            notifyDataSetChanged()
            return
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition].path == next[newItemPosition].path
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition] == next[newItemPosition]
        })
        this.directories = next
        diff.dispatchUpdatesTo(this)
    }

    fun refreshSelection(path: String) {
        val position = directories.indexOfFirst { it.path.trimEnd('/') == path.trimEnd('/') }
        if (position < 0) return
        val directory = directories[position]
        boundHolders[directory.path.trimEnd('/')]?.let { bindSelection(it, directory) }
        notifyItemChanged(position, SELECTION_PAYLOAD)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val directory = directories[position]
        boundHolders[directory.path.trimEnd('/')] = holder
        val config = activity.config
        val colors = photoDialogColors(activity)
        val background = colors.surface
        val text = colors.text
        val accent = colors.accent
        // Worker-computed canonical boundaries plus a fresh lexical check. Never
        // show a cached thumbnail while protection is unknown or has just changed.
        val locked = directory.path in protectedPaths ||
            hasProtectedFolderBoundary(directory.path, config::isFolderProtected) ||
            (directory.path == FAVORITES && config.isFavoritePasswordProtectionOn)
        with(holder.binding) {
            if (locked) clearThumbnail(this)
            sourceLock.visibility = if (locked) View.VISIBLE else View.GONE
            sourceLock.background = ColorDrawable(androidx.core.graphics.ColorUtils.blendARGB(background, text, .06f))
            sourceLock.setColorFilter(text)
            sourceName.text = directory.name
            sourceName.setTextColor(text)
            sourceStatus.text = if (locked) activity.getString(R.string.photo_source_locked)
                else if (directory.subfoldersMediaCount < 0) activity.getString(R.string.photo_source_not_indexed, directory.path)
                else activity.getString(R.string.photo_source_media_count, directory.subfoldersMediaCount)
            sourceStatus.setTextColor(colors.secondary)
            sourceStatus.alpha = 1f
            sourceCheckIcon.setImageDrawable(PhotoSourceCheckDrawable(
                activity.resources,
                OxygenClassicPalette.readableAccent(accent, Color.WHITE),
                if (Color.luminance(background) > .5) Color.DKGRAY else Color.LTGRAY
            ))
            sourceCheckTarget.background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 32)),
                ColorDrawable(Color.TRANSPARENT), ColorDrawable(Color.WHITE)
            )
            bindSelection(holder, directory)
            root.background = OxygenClassicDialog.ripple(accent, activity.resources.displayMetrics.density)
            root.contentDescription = directory.name + if (locked) ", " + activity.getString(R.string.photo_source_locked) else ""
            root.setOnClickListener { toggleSource(directory.path) }
            sourceCheckTarget.setOnClickListener { toggleSource(directory.path) }
            sourceThumbnail.visibility = View.VISIBLE
            sourceThumbnail.clearColorFilter()
            if (!locked) DirectoryThumbnailBinder.bind(
                activity, sourceThumbnail, directory.tmb, directory.getKey(), config.cropThumbnails,
                config.animateGifs, false,
                if (config.folderStyle == org.fossify.gallery.helpers.FOLDER_STYLE_SQUARE) ROUNDED_CORNERS_NONE else ROUNDED_CORNERS_BIG,
                colors.secondary
            ) { }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            bindSelection(holder, directories[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun bindSelection(holder: Holder, directory: Directory) = with(holder.binding) {
        val checked = draft.contains(directory.path)
        root.isSelected = checked
        sourceCheckTarget.isSelected = checked
        sourceCheckIcon.isActivated = checked
        sourceCheckIcon.drawable?.state = sourceCheckIcon.drawableState
        sourceCheckTarget.contentDescription = directory.name + ", " + activity.getString(
            if (checked) R.string.photo_source_deselect else R.string.photo_source_select
        )
    }

    private fun toggleSource(path: String) {
        if (!draft.canSelect(path)) return
        draft.toggle(path)
        val position = directories.indexOfFirst { it.path.trimEnd('/') == path.trimEnd('/') }
        if (position >= 0) {
            directories[position].let { directory ->
                boundHolders[directory.path.trimEnd('/')]?.let { bindSelection(it, directory) }
            }
            notifyItemChanged(position, SELECTION_PAYLOAD)
        }
        selectionChanged()
    }

    private companion object { const val SELECTION_PAYLOAD = "source-selection" }
    private fun clearThumbnail(binding: PhotoSourceItemBinding) {
        DirectoryThumbnailBinder.clear(activity, binding.sourceThumbnail, ROUNDED_CORNERS_NONE)
        binding.sourceThumbnail.visibility = View.VISIBLE
    }
    override fun onViewRecycled(holder: Holder) {
        boundHolders.entries.removeAll { it.value === holder }
        clearThumbnail(holder.binding)
        holder.binding.root.setOnClickListener(null)
        holder.binding.sourceCheckTarget.setOnClickListener(null)
        super.onViewRecycled(holder)
    }
    override fun onChange(position: Int): String = directories.getOrNull(position)?.name.orEmpty()
}
