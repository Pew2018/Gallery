package org.fossify.gallery.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogFilterMediaBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*

class FilterMediaDialog(val activity: BaseSimpleActivity, private val photoProfile: Boolean = false, val callback: (result: Int) -> Unit) {
    private val binding = DialogFilterMediaBinding.inflate(activity.layoutInflater)

    init {
        val filterMedia = if (photoProfile) activity.config.photoMediaFilter else activity.config.filterMedia
        binding.apply {
            filterMediaImages.isChecked = filterMedia and TYPE_IMAGES != 0
            filterMediaVideos.isChecked = filterMedia and TYPE_VIDEOS != 0
            filterMediaGifs.isChecked = filterMedia and TYPE_GIFS != 0
            filterMediaRaws.isChecked = filterMedia and TYPE_RAWS != 0
            filterMediaSvgs.isChecked = filterMedia and TYPE_SVGS != 0
            filterMediaPortraits.isChecked = filterMedia and TYPE_PORTRAITS != 0
        }

        OxygenClassicDialog.show(
            activity,
            OxygenClassicDialog.builder(activity, activity.getString(R.string.filter_media))
                .setView(binding.root)
                .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
                .setNegativeButton(org.fossify.commons.R.string.cancel, null),
            binding.root
        )
    }

    private fun dialogConfirmed() {
        var result = 0
        if (binding.filterMediaImages.isChecked)
            result += TYPE_IMAGES
        if (binding.filterMediaVideos.isChecked)
            result += TYPE_VIDEOS
        if (binding.filterMediaGifs.isChecked)
            result += TYPE_GIFS
        if (binding.filterMediaRaws.isChecked)
            result += TYPE_RAWS
        if (binding.filterMediaSvgs.isChecked)
            result += TYPE_SVGS
        if (binding.filterMediaPortraits.isChecked)
            result += TYPE_PORTRAITS

        if (result == 0) {
            result = getDefaultFileFilter()
        }

        if ((if (photoProfile) activity.config.photoMediaFilter else activity.config.filterMedia) != result) {
            if (photoProfile) activity.config.photoMediaFilter = result else activity.config.filterMedia = result
            callback(result)
        }
    }
}
