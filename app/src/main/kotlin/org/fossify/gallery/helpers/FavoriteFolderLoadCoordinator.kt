package org.fossify.gallery.helpers

/** Pure request-generation state; safe to test without an Android Activity. */
sealed class FavoriteFolderLoadStatus {
    data object Idle : FavoriteFolderLoadStatus()
    data object AwaitingAuthentication : FavoriteFolderLoadStatus()
    data object Loading : FavoriteFolderLoadStatus()
    data object Loaded : FavoriteFolderLoadStatus()
    data object Empty : FavoriteFolderLoadStatus()
    data object Error : FavoriteFolderLoadStatus()
}

data class FavoriteFolderLoadKey(
    val showHidden: Boolean,
    val showExcluded: Boolean,
    val authorizationPending: Boolean,
    val excludedRoots: Set<String>,
)

class FavoriteFolderLoadCoordinator {
    @Volatile private var generation = 0L
    private var activeKey: FavoriteFolderLoadKey? = null
    @Volatile var status: FavoriteFolderLoadStatus = FavoriteFolderLoadStatus.Idle
        private set

    /** Returns null only when an identical load is already in flight. */
    fun begin(key: FavoriteFolderLoadKey, force: Boolean = false): Long? {
        if (!force && status == FavoriteFolderLoadStatus.Loading && key == activeKey) return null
        generation++
        activeKey = key
        status = FavoriteFolderLoadStatus.Loading
        return generation
    }

    fun isCurrent(request: Long): Boolean = request == generation

    fun complete(request: Long, count: Int, authorizationPending: Boolean): Boolean {
        if (!isCurrent(request) || status != FavoriteFolderLoadStatus.Loading) return false
        status = when {
            authorizationPending -> FavoriteFolderLoadStatus.AwaitingAuthentication
            count == 0 -> FavoriteFolderLoadStatus.Empty
            else -> FavoriteFolderLoadStatus.Loaded
        }
        return true
    }

    fun fail(request: Long): Boolean {
        if (!isCurrent(request) || status != FavoriteFolderLoadStatus.Loading) return false
        status = FavoriteFolderLoadStatus.Error
        return true
    }

    /** Revokes any pending result, without relying on an asynchronous completion callback. */
    fun invalidate() {
        generation++
        activeKey = null
        status = FavoriteFolderLoadStatus.Idle
    }
}
