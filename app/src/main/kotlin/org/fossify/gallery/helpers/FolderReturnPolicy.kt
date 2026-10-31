package org.fossify.gallery.helpers

/** Whether a fully loaded ordinary album may skip a redundant return scan. */
internal object FolderReturnPolicy {
    fun canReuse(
        publicOrdinaryFolder: Boolean,
        completeDisplay: Boolean,
        sameSettings: Boolean,
        sameMediaStore: Boolean,
        sameDirectoryTimestamp: Boolean,
        unchangedViewerContent: Boolean,
        ageMillis: Long
    ): Boolean = publicOrdinaryFolder && completeDisplay && sameSettings &&
        sameMediaStore && sameDirectoryTimestamp && unchangedViewerContent &&
        ageMillis in 0..60_000L
}
