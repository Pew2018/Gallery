package org.fossify.gallery.helpers

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Looper
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoritePaths

/** Immutable scope identity. Names and unrelated settings never invalidate a media request. */
data class PhotoProfileScope(
    val profileId: String,
    val selectedId: String?,
    val sources: List<PhotoSource>,
    val filter: Int,
    val sorting: Int,
    val excluded: Set<String>,
    val bindings: Map<String, String>,
    val hiddenPreference: Boolean,
    val duration: Boolean,
    val loadingPriority: Int,
    val protectionPreferences: Map<String, Any?>,
    val accessVersion: Long,
    val sessionId: String?,
    val allFiles: Boolean,
    val readPermissions: List<Int>
) {
    fun isCurrent(context: Context): Boolean = this == capture(context, profileId, sessionId) &&
        (sessionId == null || PhotoProfileAccessSessionManager.isActive(context, sessionId))

    companion object {
        fun capture(context: Context, profileId: String?, sessionId: String?): PhotoProfileScope? {
            val state = PhotoProfileRepository.of(context).state()
            val profile = state.profiles.firstOrNull { it.id == profileId } ?: return null
            val config = context.config
            val protections = context.getSharedPrefs().all.filterKeys { PhotoProtectionChanges.credentialsChanged(it) }
            return PhotoProfileScope(profile.id, state.selectedId, profile.sources.toList(),
                config.photoMediaFilter, config.getFolderSorting(PhotoProfileMediaLoader.PATH),
                config.excludedFolders.toSet(), config.photoSourceBindings.toMap(), config.shouldShowHidden,
                config.showThumbnailVideoDuration, config.fileLoadingPriority, protections,
                PhotoProfileAccessSessionManager.currentEventVersion(), sessionId,
                Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager(),
                listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                    .map { ContextCompat.checkSelfPermission(context, it) })
        }
    }
}

/** Captured on a worker; favorites remain a relationship, never a cached authorization. */
data class PhotoProfileLoadContext(
    val scope: PhotoProfileScope,
    val revision: Long,
    val favorites: Set<String>,
    /** Non-null only for a bounded request; contains exactly the paths whose membership was queried. */
    val favoriteMembershipPaths: Set<String>? = null,
    /** Constant-time relationship snapshot used to detect unrelated add/remove changes. */
    val favoriteCount: Long? = null
) {
    fun isCurrent(context: Context): Boolean {
        if (!scope.isCurrent(context) || revision != PhotoContentRevision.current()) return false
        // Activity publication performs a cheap scope/generation check only. A
        // bounded context's Room point checks were completed on its validation
        // worker; never move those DAO reads onto the UI thread.
        if (Looper.myLooper() == Looper.getMainLooper()) return true
        // Favorite membership is mutable independently of profile metadata. For a bounded
        // request recheck only its at-most-five point lookups instead of loading all favorites.
        if (favoriteCount != null && context.favoritesDB.getFavoritesCount() != favoriteCount) return false
        return favoriteMembershipPaths?.all { (context.favoritesDB.isFavorite(it)) == (it in favorites) } ?: true
    }
    companion object {
        fun capture(context: Context, profileId: String?, sessionId: String?): PhotoProfileLoadContext? {
            val revision = PhotoContentRevision.current()
            val scope = PhotoProfileScope.capture(context, profileId, sessionId) ?: return null
            // A profile without the virtual Favorites source has no dependency on
            // the library-wide favorite relation; don't read it for folder-only catalogs.
            val favorites = if (scope.sources.any { it.type == PhotoSourceType.FAVORITES }) {
                context.getFavoritePaths().toSet()
            } else emptySet()
            return PhotoProfileLoadContext(scope, revision, favorites).takeIf { it.isCurrent(context) }
        }

        /** Captures only fresh favorite membership for the small candidate window. */
        fun captureNeighbors(
            context: Context, profileId: String?, sessionId: String?, candidatePaths: Collection<String>
        ): PhotoProfileLoadContext? {
            if (candidatePaths.size > ViewerNeighborWindow.MAX_ITEMS) return null
            val revision = PhotoContentRevision.current()
            val scope = PhotoProfileScope.capture(context, profileId, sessionId) ?: return null
            val paths = candidatePaths.toSet()
            val includesFavorites = scope.sources.any { it.type == PhotoSourceType.FAVORITES }
            val favorites = if (includesFavorites) paths.filterTo(LinkedHashSet()) { context.favoritesDB.isFavorite(it) }
                else emptySet()
            val count = if (includesFavorites) context.favoritesDB.getFavoritesCount() else null
            return PhotoProfileLoadContext(scope, revision, favorites,
                if (includesFavorites) paths else emptySet(), count).takeIf { it.isCurrent(context) }
        }
    }
}

enum class PhotoLoadStage { CACHE_PREVIEW, INDEXED, COMPLETE }
enum class PhotoLoadReason { ENTER, REFRESH, SCOPE_CHANGED }
data class PhotoAccessSummary(val needsUnlock: Boolean, val publicOnly: Boolean)
data class PhotoLoadResult(
    val context: PhotoProfileLoadContext,
    val stage: PhotoLoadStage,
    val media: List<org.fossify.gallery.models.Medium>,
    val access: PhotoAccessSummary
)

/** Stale requests are discarded, not reported as successful empty albums. */
class PhotoLoadInvalidatedException : java.util.concurrent.CancellationException("Photo scope changed")
