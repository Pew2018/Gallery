package org.fossify.gallery.helpers

internal enum class ViewerScopeType {
    FOLDER, NOMEDIA_FOLDER, ALL_MEDIA, FAVORITES, RECYCLE_BIN, INLINE_SEARCH, GLOBAL_SEARCH, PHOTO_PROFILE, EXTERNAL_URI
}

/** Read-only handoff description. Real queries and grants remain with the source owners. */
internal data class ViewerSourceScope(
    val sourceType: ViewerScopeType,
    val scopeKey: String,
    val accessGeneration: Long?,
    val contentRevision: Long?,
    val sortSignature: String,
    val filterSignature: String,
    val committedAdapterVersion: Long,
    val mediaIdentity: ViewerMediumIdentity,
    val contentSignature: ViewerContentSignature,
    val requestGeneration: Long,
) {
    fun matchesHandoff(current: ViewerSourceScope, accessCurrent: Boolean): Boolean =
        accessCurrent && accessGeneration != null && contentRevision != null &&
            contentSignature.size > 0 && contentSignature.modified > 0 && this == current
}

/** Review table for transition traces. It is independent of the running navigation and animation gates. */
internal enum class TransitionPhase {
    PREPARING, ENTRY_CAPTURED, ENTERING, VIEWING, RETURN_PREPARING, INTERACTIVE_RETURN,
    RETURNING, CANCELLED, COMPLETE, FALLBACK, CLEANED;

    fun accepts(next: TransitionPhase): Boolean = when (this) {
        PREPARING -> next in setOf(ENTRY_CAPTURED, FALLBACK, CANCELLED)
        ENTRY_CAPTURED -> next in setOf(ENTERING, FALLBACK, CANCELLED)
        ENTERING -> next in setOf(VIEWING, FALLBACK, CANCELLED)
        VIEWING -> next in setOf(RETURN_PREPARING, INTERACTIVE_RETURN, FALLBACK)
        INTERACTIVE_RETURN -> next in setOf(VIEWING, RETURN_PREPARING, CANCELLED)
        RETURN_PREPARING -> next in setOf(RETURNING, FALLBACK, CANCELLED)
        RETURNING -> next in setOf(COMPLETE, FALLBACK, CANCELLED)
        CANCELLED, COMPLETE -> next == CLEANED
        FALLBACK -> next in setOf(VIEWING, COMPLETE, CLEANED)
        CLEANED -> false
    }
}

internal data class TransitionOperation(val viewerSessionId: Long, val requestId: Long, val generation: Long) {
    fun accepts(callback: TransitionOperation, current: TransitionPhase, next: TransitionPhase): Boolean =
        this == callback && current.accepts(next)
}
