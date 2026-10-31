package org.fossify.gallery.helpers

/**
 * Only ordinary absolute-path folders use the bounded MediaStore lookup.
 * Virtual albums, show-all and OTG keep their existing loading/authentication paths.
 */
internal object MediaQueryScope {
    fun shouldUseFolderQuery(
        showAll: Boolean,
        path: String,
        scopedStorageAvailable: Boolean,
        isOtg: Boolean
    ): Boolean = scopedStorageAvailable && !showAll && path.startsWith('/') && !isOtg
}
