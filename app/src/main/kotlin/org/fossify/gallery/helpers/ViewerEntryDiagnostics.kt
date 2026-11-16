package org.fossify.gallery.helpers

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import org.fossify.gallery.R
import java.util.concurrent.atomic.AtomicLong

internal object ViewerEntryDiagnostics {
    const val EXTRA_MEDIA_TYPE = "org.fossify.gallery.extra.MEDIA_ENTRY_TYPE"
    private val requests = AtomicLong()

    fun newRequest(): Long = if (GalleryDiagnosticLogger.isEnabled()) requests.incrementAndGet() else 0L

    fun enabled(request: Long) = request != 0L && GalleryDiagnosticLogger.isEnabled()

    inline fun event(request: Long, event: String, fields: () -> Map<String, Any?>) {
        if (!enabled(request)) return
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "ViewerEntry", event, request, fields())
    }

    fun backgroundState(drawable: Drawable?): String? = when (drawable) {
        null -> null
        is ColorDrawable -> "%08x/%d".format(drawable.color, drawable.alpha)
        is GradientDrawable -> drawable.color?.let { "GradientDrawable/%08x/%d".format(it.defaultColor, drawable.alpha) }
            ?: "GradientDrawable/${drawable.alpha}"
        else -> "${drawable.javaClass.simpleName}/${drawable.alpha}"
    }

    fun viewState(view: View?): String? = view?.let {
        "${if (it.isAttachedToWindow) 1 else 0}/${it.visibility}/${it.alpha}/${backgroundState(it.background) ?: "none"}"
    }
}

internal object ViewerReturnDiagnostics {
    const val EXTRA_REQUEST_ID = "org.fossify.gallery.extra.MEDIA_RETURN_ID"

    fun thumbnailState(image: ImageView?): Map<String, Any?> {
        val bound = image?.getTag(R.id.media_bound_path) as? String
        val loaded = image?.getTag(R.id.thumbnail_loaded_key)
        return mapOf(
            "boundMediaId" to bound?.let(GalleryDiagnosticLogger::anonymousId),
            "drawable" to image?.drawable?.javaClass?.simpleName,
            "loaded" to (loaded != null && loaded == image?.getTag(R.id.thumbnail_request_key)),
            "sharedName" to (image?.transitionName == PhotoViewerTransition.ELEMENT_NAME),
            "view" to ViewerEntryDiagnostics.viewState(image),
            "transitionAlpha" to image?.takeIf { Build.VERSION.SDK_INT >= 29 }?.transitionAlpha,
            "visibleBounds" to image?.let { view ->
                android.graphics.Rect().takeIf(view::getGlobalVisibleRect)?.toShortString()
            },
        )
    }

    // View-hierarchy clipping in screen coordinates. The compositor may present a different frame.
    fun visibleOnScreen(view: View): String? {
        val clip = Rect()
        if (!view.getGlobalVisibleRect(clip)) return null
        val origin = IntArray(2).also(view.rootView::getLocationOnScreen)
        clip.offset(origin[0], origin[1])
        return clip.toShortString()
    }

    fun drawableSize(image: ImageView?): String? = image?.drawable?.let {
        "${it.intrinsicWidth}x${it.intrinsicHeight}"
    }

    inline fun event(request: Long, event: String, fields: () -> Map<String, Any?> = { emptyMap() }) {
        if (!ViewerEntryDiagnostics.enabled(request)) return
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "ViewerReturn", event, request, fields())
    }
}
