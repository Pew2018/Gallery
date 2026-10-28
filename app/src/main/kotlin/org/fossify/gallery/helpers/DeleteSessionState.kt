package org.fossify.gallery.helpers

/** Process-scoped deletion settings; existing Activity teardown clears them. */
internal object DeleteSessionState {
    var skipConfirmation: Boolean = false
    var skipRecycleBin: Boolean = false

    fun reset() {
        skipConfirmation = false
        skipRecycleBin = false
    }
}
