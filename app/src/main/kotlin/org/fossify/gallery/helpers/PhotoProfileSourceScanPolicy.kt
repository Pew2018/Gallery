package org.fossify.gallery.helpers

/**
 * Select only source roots that can be scanned with the *current* photo capability.
 *
 * This is a scan plan, not media authorization. Every candidate from a permitted
 * root must still pass fresh raw/canonical-path protection and visibility checks.
 */
internal object PhotoProfileSourceScanPolicy {
    data class Plan(
        val permittedFolders: Set<String>,
        val blockedFolders: Set<String>,
        val favoritesPermitted: Boolean,
        val favoritesSelected: Boolean
    ) {
        val hasBlockedSources: Boolean get() = blockedFolders.isNotEmpty() ||
            (favoritesSelected && !favoritesPermitted)
    }

    fun select(
        folders: Collection<String>,
        favoritesSelected: Boolean,
        folderPermitted: (String) -> Boolean,
        favoritesPermitted: () -> Boolean,
        cancelled: () -> Boolean = { false }
    ): Plan {
        val permitted = LinkedHashSet<String>()
        val blocked = LinkedHashSet<String>()
        folders.forEach { path ->
            if (cancelled()) throw PhotoLoadInvalidatedException()
            // A failed canonical/protection read must not turn into a scan grant.
            val allowed = try {
                folderPermitted(path)
            } catch (cancel: java.util.concurrent.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            if (cancelled()) throw PhotoLoadInvalidatedException()
            (if (allowed) permitted else blocked).add(path)
        }
        if (cancelled()) throw PhotoLoadInvalidatedException()
        val favoritesAllowed = if (favoritesSelected) favoritesPermitted() else false
        if (cancelled()) throw PhotoLoadInvalidatedException()
        return Plan(permitted, blocked, favoritesAllowed, favoritesSelected)
    }
}
