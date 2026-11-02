package org.fossify.gallery.helpers.backup

import org.fossify.commons.helpers.ensureBackgroundThread

/** Reuse Commons' worker dispatch. Serialize backup jobs so two imports cannot overwrite a snapshot.
 * Nested calls on the same worker are reentrant; no new executor or global CoroutineScope. */
internal object BackupOperationRunner {
    private val lock = Any()
    fun <T> serialized(action: () -> T): T = synchronized(lock) { action() }
    fun execute(action: () -> Unit, failed: (Exception) -> Unit) = ensureBackgroundThread {
        synchronized(lock) {
            try { action() } catch (error: Exception) { failed(error) }
        }
    }
}
