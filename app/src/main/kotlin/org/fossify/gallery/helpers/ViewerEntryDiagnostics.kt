package org.fossify.gallery.helpers

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
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
        else -> "${drawable.javaClass.simpleName}/${drawable.alpha}"
    }

    fun viewState(view: View?): String? = view?.let {
        "${if (it.isAttachedToWindow) 1 else 0}/${it.visibility}/${it.alpha}/${backgroundState(it.background) ?: "none"}"
    }
}
