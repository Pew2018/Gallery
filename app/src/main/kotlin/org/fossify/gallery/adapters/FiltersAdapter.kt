package org.fossify.gallery.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.EditorFilterItemBinding
import org.fossify.gallery.models.FilterItem

class FiltersAdapter(
    val context: Context,
    val filterItems: ArrayList<FilterItem>,
    private val selectedAccent: Int,
    val itemClick: (Int) -> Unit
) :
    RecyclerView.Adapter<FiltersAdapter.ViewHolder>() {

    private var currentSelection = filterItems.first()
    private val strokeBackground = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke((2 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), selectedAccent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(filterItems[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = EditorFilterItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = filterItems.size

    fun getCurrentFilter() = currentSelection

    private fun setCurrentFilter(position: Int) {
        val filterItem = filterItems.getOrNull(position) ?: return
        if (currentSelection != filterItem) {
            currentSelection = filterItem
            notifyDataSetChanged()
            itemClick.invoke(position)
        }
    }

    inner class ViewHolder(private val binding: EditorFilterItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(filterItem: FilterItem): View {
            binding.apply {
                editorFilterItemLabel.text = filterItem.filter.name
                editorFilterItemLabel.setTextColor(if (getCurrentFilter() == filterItem) selectedAccent else Color.WHITE)
                editorFilterItemThumbnail.setImageBitmap(filterItem.bitmap)
                editorFilterItemThumbnail.background = if (getCurrentFilter() == filterItem) {
                    strokeBackground
                } else {
                    null
                }

                root.setOnClickListener {
                    setCurrentFilter(adapterPosition)
                }
            }
            return itemView
        }
    }
}
