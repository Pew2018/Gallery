package org.fossify.gallery.dialogs

import android.content.DialogInterface
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.R
import org.fossify.gallery.adapters.toItemBinding
import org.fossify.gallery.databinding.DialogChangeFolderThumbnailStyleBinding
import org.fossify.gallery.databinding.DirectoryItemGridRoundedCornersBinding
import org.fossify.gallery.databinding.DirectoryItemGridSquareBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*

class ChangeFolderThumbnailStyleDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) : DialogInterface.OnClickListener {
    private var config = activity.config
    private val binding = DialogChangeFolderThumbnailStyleBinding.inflate(activity.layoutInflater).apply {
        dialogFolderLimitTitle.isChecked = config.limitFolderTitle
    }

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, this)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { classicDialog ->
                    OxygenClassicDialog.apply(activity, classicDialog, binding.root)
                    setupStyle()
                    setupMediaCount()
                    updateSample()
                }
            }
    }

    private fun setupStyle() {
        val styleRadio = binding.dialogRadioFolderStyle
        styleRadio.setOnCheckedChangeListener { group, checkedId ->
            updateSample()
        }

        val styleBtn = when (config.folderStyle) {
            FOLDER_STYLE_SQUARE -> binding.dialogRadioFolderSquare
            else -> binding.dialogRadioFolderRoundedCorners
        }

        styleBtn.isChecked = true
    }

    private fun setupMediaCount() {
        val countRadio = binding.dialogRadioFolderCountHolder
        countRadio.setOnCheckedChangeListener { group, checkedId ->
            updateSample()
        }

        val countBtn = when (config.showFolderMediaCount) {
            FOLDER_MEDIA_CNT_LINE -> binding.dialogRadioFolderCountLine
            FOLDER_MEDIA_CNT_BRACKETS -> binding.dialogRadioFolderCountBrackets
            else -> binding.dialogRadioFolderCountNone
        }

        countBtn.isChecked = true
    }

    private fun updateSample() {
        val photoCount = 36
        val folderName = "Camera"
        binding.apply {
            val useRoundedCornersLayout = binding.dialogRadioFolderStyle.checkedRadioButtonId == R.id.dialog_radio_folder_rounded_corners
            binding.dialogFolderSampleHolder.removeAllViews()

            val sampleBinding = if (useRoundedCornersLayout) {
                DirectoryItemGridRoundedCornersBinding.inflate(activity.layoutInflater).toItemBinding()
            } else {
                DirectoryItemGridSquareBinding.inflate(activity.layoutInflater).toItemBinding()
            }
            val sampleView = sampleBinding.root
            binding.dialogFolderSampleHolder.addView(sampleView)

            val sampleSize = activity.resources.getDimensionPixelSize(R.dimen.sample_thumbnail_size)
            (sampleView.layoutParams as RelativeLayout.LayoutParams).apply {
                width = sampleSize
                height = sampleSize
                addRule(RelativeLayout.CENTER_HORIZONTAL)
            }

            when (binding.dialogRadioFolderCountHolder.checkedRadioButtonId) {
                R.id.dialog_radio_folder_count_line -> {
                    sampleBinding.dirName.text = folderName
                    sampleBinding.photoCnt.text = photoCount.toString()
                    sampleBinding.photoCnt.beVisible()
                }

                R.id.dialog_radio_folder_count_brackets -> {
                    sampleBinding.photoCnt.beGone()
                    sampleBinding.dirName.text = "$folderName ($photoCount)"
                }

                else -> {
                    sampleBinding.dirName.text = folderName
                    sampleBinding.photoCnt.beGone()
                }
            }

            val options = RequestOptions().centerCrop()
            var builder = Glide.with(activity)
                .load(R.drawable.oxygen_folder_preview_scene)
                .apply(options)

            // The preview overlays labels over an image, not a white dialog
            // surface. Use the same contrasting labels in both layout modes.
            sampleBinding.dirName.setTextColor(android.graphics.Color.WHITE)
            sampleBinding.photoCnt.setTextColor(android.graphics.Color.WHITE)
            if (useRoundedCornersLayout) {
                val cornerRadius = root.resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_big).toInt()
                builder = builder.transform(CenterCrop(), RoundedCorners(cornerRadius))
                sampleView.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat())
                    }
                }
                sampleView.clipToOutline = true
            }

            builder.into(sampleBinding.dirThumbnail)
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val style = when (binding.dialogRadioFolderStyle.checkedRadioButtonId) {
            R.id.dialog_radio_folder_square -> FOLDER_STYLE_SQUARE
            else -> FOLDER_STYLE_ROUNDED_CORNERS
        }

        val count = when (binding.dialogRadioFolderCountHolder.checkedRadioButtonId) {
            R.id.dialog_radio_folder_count_line -> FOLDER_MEDIA_CNT_LINE
            R.id.dialog_radio_folder_count_brackets -> FOLDER_MEDIA_CNT_BRACKETS
            else -> FOLDER_MEDIA_CNT_NONE
        }

        config.folderStyle = style
        config.showFolderMediaCount = count
        config.limitFolderTitle = binding.dialogFolderLimitTitle.isChecked
        callback()
    }
}
