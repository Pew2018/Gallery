package org.fossify.gallery.adapters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.squareup.picasso.Picasso
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import android.app.Activity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.R
import org.fossify.gallery.databinding.PhotoSourceItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.loadImage
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Directory

/** A source has navigation and selection actions, never Gallery file operations. */
internal class PhotoSourceDirectoryAdapter(
    private val activity: Activity,
    var directories: List<Directory>,
    private val draft: PhotoSourceSelectionDraft,
    private val protectedPaths: Set<String>,
    private val selectionChanged: () -> Unit,
    private val navigate: (Directory) -> Unit
) : RecyclerView.Adapter<PhotoSourceDirectoryAdapter.Holder>(), RecyclerViewFastScroller.OnPopupTextUpdate {
    class Holder(val binding: PhotoSourceItemBinding) : RecyclerView.ViewHolder(binding.root)
    override fun getItemCount() = directories.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        PhotoSourceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    fun updateDirectories(directories: List<Directory>) {
        this.directories = directories
        notifyDataSetChanged()
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val directory = directories[position]
        val config = activity.config
        val background = activity.getProperBackgroundColor()
        val text = activity.getProperTextColor()
        val accent = OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), background)
        // Worker-computed canonical boundaries plus a fresh lexical check. Never
        // show a cached thumbnail while protection is unknown or has just changed.
        val locked = directory.path in protectedPaths ||
            hasProtectedFolderBoundary(directory.path, config::isFolderProtected) ||
            (directory.path == FAVORITES && config.isFavoritePasswordProtectionOn)
        with(holder.binding) {
            clearThumbnail(this)
            sourceLock.visibility = if (locked) View.VISIBLE else View.GONE
            sourceLock.background = ColorDrawable(background)
            sourceLock.setColorFilter(text)
            sourceName.text = directory.name
            sourceName.setTextColor(text)
            sourceStatus.text = if (locked) activity.getString(R.string.photo_source_locked)
                else activity.getString(R.string.photo_source_media_count, directory.subfoldersMediaCount)
            sourceStatus.setTextColor(text)
            sourceStatus.alpha = 0.65f
            sourceCheck.setOnCheckedChangeListener(null)
            sourceCheck.buttonTintList = null
            sourceCheck.buttonDrawable = PhotoSourceCheckDrawable(activity.resources, accent, if (Color.luminance(background) > .5) Color.DKGRAY else Color.LTGRAY)
            sourceCheck.background = OxygenClassicDialog.ripple(accent, activity.resources.displayMetrics.density)
            sourceCheck.isChecked = draft.contains(directory.path)
            sourceCheck.contentDescription = directory.name + ", " + activity.getString(
                if (sourceCheck.isChecked) R.string.photo_source_deselect else R.string.photo_source_select)
            sourceCheck.setOnCheckedChangeListener { _, _ ->
                draft.toggle(directory.path)
                selectionChanged()
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
            }
            root.background = OxygenClassicDialog.ripple(accent, activity.resources.displayMetrics.density)
            root.contentDescription = directory.name + if (locked) ", " + activity.getString(R.string.photo_source_locked) else ""
            root.setOnClickListener { navigate(directory) }
            if (!locked && directory.tmb.isNotBlank()) {
                sourceThumbnail.visibility = View.VISIBLE
                sourceThumbnail.tag = directory.tmb
                val type = when {
                    directory.tmb.isVideoFast() -> TYPE_VIDEOS
                    directory.tmb.isGif() -> TYPE_GIFS
                    directory.tmb.isRawFast() -> TYPE_RAWS
                    directory.tmb.isSvg() -> TYPE_SVGS
                    else -> TYPE_IMAGES
                }
                activity.loadImage(type, directory.tmb, sourceThumbnail, false, false, true,
                    ROUNDED_CORNERS_NONE, directory.getKey())
            }
        }
    }
    private fun clearThumbnail(binding: PhotoSourceItemBinding) {
        Picasso.get().cancelRequest(binding.sourceThumbnail)
        Glide.with(activity).clear(binding.sourceThumbnail)
        binding.sourceThumbnail.tag = null
        binding.sourceThumbnail.setImageDrawable(ColorDrawable(activity.getProperBackgroundColor()))
        binding.sourceThumbnail.visibility = View.VISIBLE
    }
    override fun onViewRecycled(holder: Holder) {
        clearThumbnail(holder.binding)
        holder.binding.sourceCheck.setOnCheckedChangeListener(null)
        holder.binding.root.setOnClickListener(null)
        super.onViewRecycled(holder)
    }
    override fun onChange(position: Int): String = directories.getOrNull(position)?.name.orEmpty()
}
