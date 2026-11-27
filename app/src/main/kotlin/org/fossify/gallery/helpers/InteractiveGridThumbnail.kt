package org.fossify.gallery.helpers

import android.widget.ImageView
import org.fossify.gallery.R

/** The view owns the saved alpha until native mapping or the adapter takes the binding back. */
internal class InteractiveGridThumbnail private constructor(
    private val image: ImageView,
    val originalAlpha: Float,
) {
    val ownsView get() = image.getTag(R.id.interactive_grid_thumbnail) === this

    fun restore() {
        if (!ownsView) return
        image.setTag(R.id.interactive_grid_thumbnail, null)
        if (image.alpha == 0f) image.alpha = originalAlpha
    }

    companion object {
        fun suppress(image: ImageView): InteractiveGridThumbnail =
            InteractiveGridThumbnail(image, image.alpha).also {
                image.setTag(R.id.interactive_grid_thumbnail, it)
                image.alpha = 0f
            }

        fun restoreBeforeRebind(image: ImageView) {
            (image.getTag(R.id.interactive_grid_thumbnail) as? InteractiveGridThumbnail)?.restore()
        }
    }
}
