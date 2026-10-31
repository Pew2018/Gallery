package org.fossify.gallery.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ItemPhotoProfileManageBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.OxygenThemeColors
import org.fossify.gallery.helpers.OxygenThemeEngine

internal data class PhotoProfileManageRow(
    val id: String,
    val name: String,
    val sourceCount: Int,
    val isCurrent: Boolean
)

internal class PhotoProfileManageAdapter(
    private val onProfileClick: (String) -> Unit,
    private val onMoreClick: (String, android.view.View) -> Unit
) : ListAdapter<PhotoProfileManageRow, PhotoProfileManageAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemPhotoProfileManageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPhotoProfileManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val context = holder.binding.root.context
        val colors = OxygenThemeEngine.snapshot(context, context.config)
        with(holder.binding) {
            root.setBackgroundColor(colors.pageBackground)
            profileManageRow.background = OxygenClassicDialog.ripple(
                colors.interactiveAccent, context.resources.displayMetrics.density
            )
            profileManageName.text = row.name
            profileManageName.setTextColor(colors.primaryText)
            profileManageSourceCount.text = context.getString(R.string.photo_profiles_source_count, row.sourceCount)
            profileManageSourceCount.setTextColor(colors.secondaryText)
            profileManageCurrent.apply {
                visibility = if (row.isCurrent) android.view.View.VISIBLE else android.view.View.GONE
                setImageResource(org.fossify.commons.R.drawable.ic_check_vector)
                imageTintList = ColorStateList.valueOf(colors.interactiveAccent)
                contentDescription = context.getString(R.string.photo_profiles_current_accessibility)
            }
            profileManageMore.apply {
                setImageResource(R.drawable.photo_profile_more)
                imageTintList = ColorStateList.valueOf(colors.primaryText)
                contentDescription = context.getString(R.string.photo_profiles_more_actions_for, row.name)
                background = OxygenClassicDialog.ripple(colors.interactiveAccent, context.resources.displayMetrics.density)
                setOnClickListener { onMoreClick(row.id, this) }
            }
            profileManageDivider.setBackgroundColor(
                OxygenThemeColors.blend(colors.pageBackground, colors.primaryText, 0.10)
            )
            profileManageRow.contentDescription = context.getString(
                if (row.isCurrent) R.string.photo_profiles_row_current_accessibility
                else R.string.photo_profiles_row_accessibility,
                row.name,
                row.sourceCount
            )
            profileManageRow.setOnClickListener { onProfileClick(row.id) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PhotoProfileManageRow>() {
            override fun areItemsTheSame(oldItem: PhotoProfileManageRow, newItem: PhotoProfileManageRow) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PhotoProfileManageRow, newItem: PhotoProfileManageRow) =
                oldItem == newItem
        }
    }
}
