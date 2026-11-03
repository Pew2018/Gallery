package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import java.io.File
import java.io.IOException

/** Provider/index failures are handled by the caller; individual folders fail independently. */
internal object FolderMediaScan {
    data class Result(val media: ArrayList<Medium>, val failures: Map<String, Exception>, val successfulSources: Int) {
        val outcome get() = GalleryScanResult.sources(successfulSources, failures)
    }

    fun load(folders: List<String>, cached: List<Medium>, cancelled: () -> Boolean,
             scan: (String) -> ArrayList<Medium>): Result {
        val media = ArrayList<Medium>()
        val failures = LinkedHashMap<String, Exception>()
        var successfulSources = 0
        for (folder in folders) {
            if (cancelled()) break
            try { media.addAll(scan(folder)); successfulSources++ }
            catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException || error is android.os.OperationCanceledException) throw error
                failures[folder] = error
            }
        }
        if (cancelled()) throw java.util.concurrent.CancellationException("Media scan retired")
        // Empty plus unreadable sources is not proof that the visible library is empty.
        if (media.isEmpty() && failures.isNotEmpty()) throw IOException("No readable media result", failures.values.first())
        cached.filter { File(it.path).parent in failures }.forEach { media.add(it.copy()) }
        return Result(media.distinctBy { it.path }.toCollection(ArrayList()), failures, successfulSources)
    }
}
