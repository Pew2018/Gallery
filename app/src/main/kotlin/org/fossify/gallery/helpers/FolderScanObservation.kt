package org.fossify.gallery.helpers

import android.os.FileObserver
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Detect changes to a direct folder that may not have reached MediaStore yet. */
internal class FolderScanObservation(folder: File, onChanged: (() -> Unit)? = null) {
    private val changed = AtomicBoolean(false)
    @Suppress("DEPRECATION") // The path overload is available across the app's supported API levels.
    private val observer = object : FileObserver(folder.path,
        MODIFY or ATTRIB or CLOSE_WRITE or MOVED_FROM or MOVED_TO or CREATE or DELETE or DELETE_SELF or MOVE_SELF) {
        override fun onEvent(event: Int, path: String?) {
            changed.set(true)
            onChanged?.invoke()
        }
    }

    val unchanged: Boolean get() = !changed.get()

    init { observer.startWatching() }

    fun stop() {
        changed.set(true)
        observer.stopWatching()
    }
}
