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
        target.setTag(R.id.thumbnail_request_key, null)
        target.setTag(R.id.thumbnail_loaded_key, null)
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
        placeholderColor: Int = photoDialogColors(activity).secondary,
        onError: () -> Unit
    ) {
        val requestKey = listOf(path, signature, crop, animateGifs, horizontalScroll, corners)
        if (target.getTag(R.id.thumbnail_request_key) == requestKey &&
            target.getTag(R.id.thumbnail_loaded_key) == requestKey && target.drawable != null
        ) {
            trace("cover_bind_skip")
            return
        }
        // A cover that is already on screen for *this* path must not be blanked while the
        // replacement decodes. Glide installs its transparent placeholder the moment a
        // request starts (extensions/Context.loadImageBase -> RequestOptions.placeholder),
        // so the item's OxygenThumbnailPlaceholder background shows through for at least
        // one frame before the bitmap arrives. A rebind without a media change - the home
        // rescan that follows every return from a folder publishes a fresh Directory list
        // and can dispatch item changes - therefore read as "the cover flashes grey, then
        // the thumbnail comes back". Keeping the current frame as the request's
        // placeholder makes that reload invisible.
        val retainedFrame = if (path.isNotEmpty() && target.tag == path) target.drawable else null
        target.setTag(R.id.thumbnail_request_key, requestKey)
        target.setTag(R.id.thumbnail_loaded_key, null)
        if (retainedFrame == null) {
            OxygenThumbnailPlaceholder.apply(activity, target, corners)
            trace("cover_placeholder")
        } else {
            trace("cover_hold_frame")
        }
        target.clearColorFilter()
        Picasso.get().cancelRequest(target)
        target.scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        if (path.isBlank()) {
            Glide.with(activity).clear(target)
            target.tag = null
            target.scaleType = ImageView.ScaleType.CENTER
            target.setImageResource(R.drawable.photo_source_folder)
            target.setColorFilter(placeholderColor)
            return
        }
        if (target.tag != path) {
            Glide.with(activity).clear(target)
            target.setImageDrawable(null)
            target.tag = path
        }
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
            crossFadeDuration = 0,
            placeholderDrawable = retainedFrame,
            onLoaded = {
                if (target.tag == path && target.getTag(R.id.thumbnail_request_key) == requestKey && target.drawable != null) {
                    target.setTag(R.id.thumbnail_loaded_key, requestKey)
                }
            },
            onError = {
                if (target.getTag(R.id.thumbnail_request_key) != requestKey) return@loadImage
                target.scaleType = ImageView.ScaleType.CENTER
                target.setImageResource(R.drawable.ic_vector_warning_colored)
                onError()
            }
        )
    }

    /** Timeline hook for device traces; no-op unless diagnostic logging is enabled. */
    private fun trace(event: String) {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "CoverBinding", event)
    }
}
