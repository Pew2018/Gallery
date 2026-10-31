package org.fossify.gallery.helpers

/** Stages all new grants locally. Cancellation or scope drift produces no write. */
internal class PhotoBindingTransaction(
    private val required: Map<String, String>,
    private val previous: Map<String, String>
) {
    private val approved = linkedMapOf<String, String>()
    fun approve(grants: Map<String, String>) {
        require(grants.all { (key, identity) -> required[key] == identity })
        approved.putAll(grants)
    }
    fun isApproved(key: String, identity: String) = approved[key] == identity
    fun complete(currentScope: Map<String, String>, valid: Boolean): Map<String, String>? {
        if (!valid || currentScope != required) return null
        val candidate = previous + approved
        return candidate.takeIf { required.all { (key, identity) -> it[key] == identity } }
    }
}
