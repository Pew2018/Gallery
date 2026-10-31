package org.fossify.gallery.helpers

import java.util.UUID

/** Authentication completion is accepted only in the generation in which it began. */
internal class PhotoAccessCapability {
    private var generation = 0L
    private var token: String? = null
    private var credentialCurrent: (() -> Boolean)? = null
    @Synchronized fun generation() = generation
    @Synchronized fun create(expectedGeneration: Long, current: () -> Boolean): String? {
        if (expectedGeneration != generation || !current()) return null
        credentialCurrent = current
        return UUID.randomUUID().toString().also { token = it }
    }
    @Synchronized fun active(candidate: String?): Boolean = candidate != null && candidate == token && credentialCurrent?.invoke() == true
    @Synchronized fun revoke() { generation++; token = null; credentialCurrent = null }
}
