package org.fossify.gallery.helpers

import android.app.Activity
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.squareup.picasso.Picasso
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.gallery.R
import org.fossify.gallery.extensions.loadImage
import org.fossify.commons.views.MySquareImageView

/** Shared folder thumbnail loading and recycling rules for the album grid and source picker. */
object DirectoryThumbnailBinder {
    fun clear(activity: Activity, target: MySquareImageView, corners: Int) {
        Picasso.get().cancelRequest(target)
        Glide.with(activity).clear(target)
        target.tag = null
        target.setImageDrawable(null)
        OxygenThumbnailPlaceholder.apply(activity, target, corners)
    }

    fun bind(
        activity: Activity,
        target: MySquareImageView,
        path: String,
        signature: ObjectKey,
        crop: Boolean,
        animateGifs: Boolean,
        horizontalScroll: Boolean,
        corners: Int,
        onError: () -> Unit
    ) {
        OxygenThumbnailPlaceholder.apply(activity, target, corners)
        Picasso.get().cancelRequest(target)
        target.scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        if (target.tag != path) {
            Glide.with(activity).clear(target)
            target.setImageDrawable(null)
            target.tag = path
        }
        if (path.isBlank()) return
        val type = when {
            path.isVideoFast() -> TYPE_VIDEOS
            path.isGif() -> TYPE_GIFS
            path.isRawFast() -> TYPE_RAWS
            path.isSvg() -> TYPE_SVGS
            else -> TYPE_IMAGES
        }
        activity.loadImage(
            type = type,
            path = path,
            target = target,
            horizontalScroll = horizontalScroll,
            animateGifs = animateGifs,
            cropThumbnails = crop,
            roundCorners = corners,
            signature = signature,
            onError = {
                target.scaleType = ImageView.ScaleType.CENTER
                target.setImageResource(R.drawable.ic_vector_warning_colored)
                onError()
            }
        )
    }
}
