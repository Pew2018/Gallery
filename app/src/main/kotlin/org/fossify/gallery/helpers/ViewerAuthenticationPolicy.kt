package org.fossify.gallery.helpers

/**
 * Intent extras are untrusted in exported Activities. A caller may freely
 * supply SKIP_AUTHENTICATION, so it is not proof that a folder was unlocked.
 * Fail closed if exported status cannot be determined.
 */
internal object ViewerAuthenticationPolicy {
    fun mayHonorSkipExtra(skipRequested: Boolean, exportedOrUnknown: Boolean): Boolean =
        skipRequested && !exportedOrUnknown
}
