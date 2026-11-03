package org.fossify.gallery.helpers

import android.app.Activity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.fossify.gallery.R
import java.io.IOException

/** A failed scan never means that the previously displayed media was deleted. */
internal object MediaLoadFailure {
    fun message(error: Exception): Int = when {
        generateSequence<Throwable>(error) { it.cause }.any { it is java.util.concurrent.TimeoutException } -> R.string.folder_refresh_timeout
        generateSequence<Throwable>(error) { it.cause }.any { it is SecurityException } -> R.string.media_load_permission_error
        generateSequence<Throwable>(error) { it.cause }.any { it is IOException } -> R.string.media_load_storage_error
        else -> R.string.failed_to_load_media
    }

    fun present(
        activity: Activity, title: TextView, description: TextView, action: TextView,
        hasContent: Boolean, error: Exception, retry: () -> Unit
    ) {
        if (hasContent) {
            Toast.makeText(activity, message(error), Toast.LENGTH_LONG).show()
            return
        }
        title.setText(R.string.failed_to_load_media)
        title.visibility = View.VISIBLE
        description.setText(message(error))
        description.visibility = View.VISIBLE
        action.setText(R.string.media_load_retry)
        action.visibility = View.VISIBLE
        action.setOnClickListener { retry() }
    }
}
