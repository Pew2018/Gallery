package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.Configuration
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.gallery.R
import org.fossify.gallery.models.AlbumCover
import java.util.Arrays
import java.util.Locale

class Config(context: Context) : BaseConfig(context) {
    companion object {
        private const val LAST_MAIN_VIEW_ALL = "oxygenos_classic_last_main_view_all"
        private const val LAST_FAVORITES_VIEW_FILES = "gallery_last_favorites_view_files"
        private const val FAVORITE_SHOW_EXCLUDED = "oxygen_favorite_show_excluded"
        private const val FAVORITE_SHOW_HIDDEN = "oxygen_favorite_show_hidden"
        private const val BOTTOM_NAV_ORDER = "oxygen_bottom_nav_order"
        private const val TOOLBAR_HOME_ACTIONS = "oxygen_toolbar_home_actions"
        private const val TOOLBAR_FOLDER_ACTIONS = "oxygen_toolbar_folder_actions"
        fun newInstance(context: Context) = Config(context)
    }

    internal data class FavoriteCredentialSnapshot(
        val enabled: Boolean,
        val hash: String,
        val type: Int,
        val fallbackHash: String,
        val fallbackType: Int,
        val biometricPrimary: Boolean = type == PROTECTION_FINGERPRINT
    ) {
        val isConfigured: Boolean
            get() = if (biometricPrimary) {
                type == PROTECTION_FINGERPRINT && fallbackHash.isNotBlank() && fallbackType in 0..1
            } else {
                hash.isNotBlank() && type in 0..1
            }
    }

    internal data class FavoriteCredentialProof(
        val credential: FavoriteCredentialSnapshot,
        val sessionGeneration: Long
    )

    internal fun favoriteCredentialSnapshot(): FavoriteCredentialSnapshot {
        val values = prefs.all
        val type = values[FAVORITE_PROTECTION_TYPE] as? Int ?: PROTECTION_PATTERN
        return FavoriteCredentialSnapshot(
            enabled = values[FAVORITE_PASSWORD_ENABLED] as? Boolean ?: false,
            hash = values[FAVORITE_PASSWORD_HASH] as? String ?: "",
            type = type,
            fallbackHash = values[FAVORITE_FALLBACK_PASSWORD_HASH] as? String ?: "",
            fallbackType = values[FAVORITE_FALLBACK_PROTECTION_TYPE] as? Int ?: PROTECTION_PATTERN,
            // Upgrade legacy installs that only persisted the integer type.
            biometricPrimary = values[FAVORITE_BIOMETRIC_PRIMARY] as? Boolean ?: (type == PROTECTION_FINGERPRINT)
        )
    }

    internal fun isFavoriteCredentialCurrent(snapshot: FavoriteCredentialSnapshot): Boolean =
        snapshot.enabled && snapshot.isConfigured && favoriteCredentialSnapshot() == snapshot

    /** Credential for the virtual media-favorites album, independent of folder locks. */
    var isFavoritePasswordProtectionOn: Boolean
        get() = prefs.getBoolean(FAVORITE_PASSWORD_ENABLED, false)
        private set(value) = prefs.edit().putBoolean(FAVORITE_PASSWORD_ENABLED, value).apply()

    var favoritePasswordHash: String
        get() = prefs.getString(FAVORITE_PASSWORD_HASH, "") ?: ""
        set(value) = prefs.edit().putString(FAVORITE_PASSWORD_HASH, value).apply()

    var favoriteProtectionType: Int
        get() = prefs.getInt(FAVORITE_PROTECTION_TYPE, PROTECTION_PATTERN)
        set(value) = prefs.edit().putInt(FAVORITE_PROTECTION_TYPE, value).apply()

    var favoriteFallbackPasswordHash: String
        get() = prefs.getString(FAVORITE_FALLBACK_PASSWORD_HASH, "") ?: ""
        private set(value) = prefs.edit().putString(FAVORITE_FALLBACK_PASSWORD_HASH, value).apply()

    var favoriteFallbackProtectionType: Int
        get() = prefs.getInt(FAVORITE_FALLBACK_PROTECTION_TYPE, PROTECTION_PATTERN)
        private set(value) = prefs.edit().putInt(FAVORITE_FALLBACK_PROTECTION_TYPE, value).apply()

    fun saveFavoriteCredential(hash: String, type: Int, fallbackHash: String = "", fallbackType: Int = PROTECTION_PATTERN) {
        val valid = if (type == PROTECTION_FINGERPRINT) fallbackHash.isNotBlank() && fallbackType in 0..1
        else hash.isNotBlank() && type in 0..1
        if (!valid) return
        prefs.edit()
            .putString(FAVORITE_PASSWORD_HASH, hash)
            .putInt(FAVORITE_PROTECTION_TYPE, type)
            .putString(FAVORITE_FALLBACK_PASSWORD_HASH, fallbackHash)
            .putInt(FAVORITE_FALLBACK_PROTECTION_TYPE, fallbackType)
            .putBoolean(FAVORITE_BIOMETRIC_PRIMARY, type == PROTECTION_FINGERPRINT)
            .putBoolean(FAVORITE_PASSWORD_ENABLED, true)
            .apply()
    }

    /**
     * Save biometrics as the primary mode and the PIN/pattern only as recovery.
     * The explicit type prevents a fallback dialog result from replacing the main mode.
     */
    internal fun saveFavoriteBiometricCredential(
        fallbackHash: String,
        fallbackType: Int
    ): FavoriteCredentialSnapshot? {
        if (fallbackHash.isBlank() || fallbackType !in 0..1) return null
        val persisted = prefs.edit()
            .putString(FAVORITE_PASSWORD_HASH, "")
            .putInt(FAVORITE_PROTECTION_TYPE, PROTECTION_FINGERPRINT)
            .putString(FAVORITE_FALLBACK_PASSWORD_HASH, fallbackHash)
            .putInt(FAVORITE_FALLBACK_PROTECTION_TYPE, fallbackType)
            .putBoolean(FAVORITE_BIOMETRIC_PRIMARY, true)
            .putBoolean(FAVORITE_PASSWORD_ENABLED, true)
            .commit()
        if (!persisted) return null

        val saved = favoriteCredentialSnapshot()
        return saved.takeIf {
            it.enabled &&
                it.biometricPrimary &&
                it.type == PROTECTION_FINGERPRINT &&
                it.fallbackHash == fallbackHash &&
                it.fallbackType == fallbackType &&
                isFavoriteCredentialCurrent(it)
        }
    }

    fun clearFavoriteCredential() {
        prefs.edit()
            .putBoolean(FAVORITE_PASSWORD_ENABLED, false)
            .remove(FAVORITE_PASSWORD_HASH)
            .remove(FAVORITE_PROTECTION_TYPE)
            .remove(FAVORITE_FALLBACK_PASSWORD_HASH)
            .remove(FAVORITE_FALLBACK_PROTECTION_TYPE)
            .remove(FAVORITE_BIOMETRIC_PRIMARY)
            .apply()
    }

    internal data class PhotoCredentialSnapshot(
        val enabled: Boolean,
        val hash: String,
        val type: Int,
        val fallbackHash: String,
        val fallbackType: Int,
        val biometricPrimary: Boolean = type == PROTECTION_FINGERPRINT
    ) {
        val isConfigured: Boolean
            get() = if (biometricPrimary) {
                type == PROTECTION_FINGERPRINT && fallbackHash.isNotBlank() && fallbackType in 0..1
            } else {
                hash.isNotBlank() && type in 0..1
            }
    }

    internal data class PhotoCredentialProof(
        val credential: PhotoCredentialSnapshot,
        val sessionGeneration: Long,
        val requestId: String,
        val verified: PhotoVerifiedCredential?,
        val biometricEvidence: PhotoBiometricEvidence? = null
    )

    internal fun photoCredentialSnapshot(): PhotoCredentialSnapshot {
        val values = prefs.all
        val type = values["photo_protect_protection_type"] as? Int ?: PROTECTION_PATTERN
        return PhotoCredentialSnapshot(
            enabled = values["photo_protect_password_enabled"] as? Boolean ?: false,
            hash = values["photo_protect_password_hash"] as? String ?: "",
            type = type,
            fallbackHash = values["photo_protect_fallback_password_hash"] as? String ?: "",
            fallbackType = values["photo_protect_fallback_protection_type"] as? Int ?: PROTECTION_PATTERN,
            // Upgrade legacy installs that only persisted the integer type.
            biometricPrimary = values["photo_protect_biometric_primary"] as? Boolean ?: (type == PROTECTION_FINGERPRINT)
        )
    }

    internal fun isPhotoCredentialCurrent(snapshot: PhotoCredentialSnapshot): Boolean =
        snapshot.enabled && snapshot.isConfigured && photoCredentialSnapshot() == snapshot

    /** Credential for the photo browsing profiles, independent of folder locks. */
    var isPhotoPasswordProtectionOn: Boolean
        get() = prefs.getBoolean("photo_protect_password_enabled", false)
        private set(value) = prefs.edit().putBoolean("photo_protect_password_enabled", value).apply()

    var photoPasswordHash: String
        get() = prefs.getString("photo_protect_password_hash", "") ?: ""
        set(value) = prefs.edit().putString("photo_protect_password_hash", value).apply()

    var photoProtectionType: Int
        get() = prefs.getInt("photo_protect_protection_type", PROTECTION_PATTERN)
        set(value) = prefs.edit().putInt("photo_protect_protection_type", value).apply()

    var photoFallbackPasswordHash: String
        get() = prefs.getString("photo_protect_fallback_password_hash", "") ?: ""
        private set(value) = prefs.edit().putString("photo_protect_fallback_password_hash", value).apply()

    var photoFallbackProtectionType: Int
        get() = prefs.getInt("photo_protect_fallback_protection_type", PROTECTION_PATTERN)
        private set(value) = prefs.edit().putInt("photo_protect_fallback_protection_type", value).apply()

    fun savePhotoCredential(hash: String, type: Int, fallbackHash: String = "", fallbackType: Int = PROTECTION_PATTERN) {
        val valid = if (type == PROTECTION_FINGERPRINT) fallbackHash.isNotBlank() && fallbackType in 0..1
        else hash.isNotBlank() && type in 0..1
        if (!valid) return
        if (!isPhotoPasswordProtectionOn) photoSourceBindings = emptyMap()
        prefs.edit()
            .putString("photo_protect_password_hash", hash)
            .putInt("photo_protect_protection_type", type)
            .putString("photo_protect_fallback_password_hash", fallbackHash)
            .putInt("photo_protect_fallback_protection_type", fallbackType)
            .putBoolean("photo_protect_biometric_primary", type == PROTECTION_FINGERPRINT)
            .putBoolean("photo_protect_password_enabled", true)
            .apply()
    }

    /**
     * Save biometrics as the primary mode and the PIN/pattern only as recovery.
     * The explicit type prevents a fallback dialog result from replacing the main mode.
     */
    internal fun savePhotoBiometricCredential(
        fallbackHash: String,
        fallbackType: Int
    ): PhotoCredentialSnapshot? {
        if (fallbackHash.isBlank() || fallbackType !in 0..1) return null
        if (!isPhotoPasswordProtectionOn) photoSourceBindings = emptyMap()
        val persisted = prefs.edit()
            .putString("photo_protect_password_hash", "")
            .putInt("photo_protect_protection_type", PROTECTION_FINGERPRINT)
            .putString("photo_protect_fallback_password_hash", fallbackHash)
            .putInt("photo_protect_fallback_protection_type", fallbackType)
            .putBoolean("photo_protect_biometric_primary", true)
            .putBoolean("photo_protect_password_enabled", true)
            .commit()
        if (!persisted) return null

        val saved = photoCredentialSnapshot()
        return saved.takeIf {
            it.enabled &&
                it.biometricPrimary &&
                it.type == PROTECTION_FINGERPRINT &&
                it.fallbackHash == fallbackHash &&
                it.fallbackType == fallbackType &&
                isPhotoCredentialCurrent(it)
        }
    }

    fun clearPhotoCredential() {
        photoSourceBindings = emptyMap()
        prefs.edit()
            .putBoolean("photo_protect_password_enabled", false)
            .remove("photo_protect_password_hash")
            .remove("photo_protect_protection_type")
            .remove("photo_protect_fallback_password_hash")
            .remove("photo_protect_fallback_protection_type")
            .remove("photo_protect_biometric_primary")
            .apply()
    }

    var photoMediaFilter: Int
        get() = prefs.getInt("photo_media_filter", PhotoProfileMediaLoader.MEDIA_TYPES)
        set(value) = prefs.edit().putInt("photo_media_filter", value).apply()

    internal var photoSourceBindings: Map<String, String>
        get() = runCatching {
            Gson().fromJson<Map<String, String>>(prefs.getString("photo_source_bindings", "{}"),
                object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        }.getOrDefault(emptyMap())
        set(value) = prefs.edit().putString("photo_source_bindings", Gson().toJson(value)).apply()

    /** The selected main Photos/Folders view, independent of the active bottom tab. */
    var lastMainViewAll: Boolean
        get() = prefs.getBoolean(LAST_MAIN_VIEW_ALL, if (appRunCount <= 1) true else showAll)
        set(value) = prefs.edit().putBoolean(LAST_MAIN_VIEW_ALL, value).apply()


    /** Upgrade default is the existing favorites-folder page; independent of All Photos. */
    var lastFavoritesViewFiles: Boolean
        get() = prefs.getBoolean(LAST_FAVORITES_VIEW_FILES, false)
        set(value) = prefs.edit().putBoolean(LAST_FAVORITES_VIEW_FILES, value).apply()

    // Classic appearance preferences are separate from Commons' legacy colors.
    // Existing installations keep their colors until this feature is explicitly used.
    var oxygenThemeActivated: Boolean
        get() = prefs.getBoolean("oxygen_classic_theme_activated", false)
        set(value) {
            val wasActive = oxygenThemeActivated
            if (value && !wasActive) {
                // Snapshot the user's existing Commons colors BEFORE the new palette takes over.
                prefs.edit()
                    .putInt("oxygen_legacy_text", textColor)
                    .putInt("oxygen_legacy_background", backgroundColor)
                    .putInt("oxygen_legacy_primary", primaryColor)
                    .putInt("oxygen_legacy_accent", accentColor)
                    .putBoolean("oxygen_legacy_system", isSystemThemeEnabled)
                    .apply()
            } else if (!value && wasActive) {
                // The legacy Commons color picker remains available on upgrades.
                textColor = prefs.getInt("oxygen_legacy_text", textColor)
                backgroundColor = prefs.getInt("oxygen_legacy_background", backgroundColor)
                primaryColor = prefs.getInt("oxygen_legacy_primary", primaryColor)
                accentColor = prefs.getInt("oxygen_legacy_accent", accentColor)
                isSystemThemeEnabled = prefs.getBoolean("oxygen_legacy_system", isSystemThemeEnabled)
            }
            prefs.edit().putBoolean("oxygen_classic_theme_activated", value).apply()
        }

    var oxygenThemePresetId: String
        get() = prefs.getString("oxygen_classic_theme_preset", "legacy") ?: "legacy"
        set(value) = prefs.edit().putString("oxygen_classic_theme_preset", value).apply()

    var oxygenAppearanceMode: String
        get() = prefs.getString("oxygen_classic_appearance", "system") ?: "system"
        set(value) = prefs.edit().putString("oxygen_classic_appearance", value).apply()

    var oxygenCustomAccent: Int
        get() = prefs.getInt("oxygen_classic_custom_accent", primaryColor)
        set(value) = prefs.edit().putInt("oxygen_classic_custom_accent", value).apply()

    // Legacy toolbar overrides are retained for upgrades/export compatibility only.
    // Visible toolbar colors are derived from the current accent by OxygenThemeEngine.
    var oxygenCustomToolbarEnabled: Boolean
        get() = prefs.getBoolean("oxygen_classic_toolbar_custom", false)
        set(value) = prefs.edit().putBoolean("oxygen_classic_toolbar_custom", value).apply()

    var oxygenToolbarColor: Int
        get() = prefs.getInt("oxygen_classic_toolbar_color", 0xFF212121.toInt())
        set(value) = prefs.edit().putInt("oxygen_classic_toolbar_color", value).apply()

    var oxygenManualToolbarText: Boolean
        get() = prefs.getBoolean("oxygen_classic_toolbar_manual_text", false)
        set(value) = prefs.edit().putBoolean("oxygen_classic_toolbar_manual_text", value).apply()

    var oxygenToolbarTextColor: Int
        get() = prefs.getInt("oxygen_classic_toolbar_text_color", android.graphics.Color.WHITE)
        set(value) = prefs.edit().putInt("oxygen_classic_toolbar_text_color", value).apply()

    var favoriteShowExcluded: Boolean
        get() = prefs.getBoolean(FAVORITE_SHOW_EXCLUDED, false)
        set(value) = prefs.edit().putBoolean(FAVORITE_SHOW_EXCLUDED, value).apply()

    var favoriteShowHidden: Boolean
        get() = prefs.getBoolean(FAVORITE_SHOW_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(FAVORITE_SHOW_HIDDEN, value).apply()

    /** Independent home and folder toolbar layouts. Absent settings use three classic actions. */
    var toolbarHomeActions: List<String>
        get() = ToolbarQuickActions.normalize(
            ToolbarQuickScope.HOME,
            prefs.getString(TOOLBAR_HOME_ACTIONS, null)?.split(",") ?: emptyList()
        )
        set(actions) = prefs.edit().putString(
            TOOLBAR_HOME_ACTIONS,
            ToolbarQuickActions.normalize(ToolbarQuickScope.HOME, actions).joinToString(",")
        ).apply()

    var toolbarFolderActions: List<String>
        get() = ToolbarQuickActions.normalize(
            ToolbarQuickScope.FOLDER,
            prefs.getString(TOOLBAR_FOLDER_ACTIONS, null)?.split(",") ?: emptyList()
        )
        set(actions) = prefs.edit().putString(
            TOOLBAR_FOLDER_ACTIONS,
            ToolbarQuickActions.normalize(ToolbarQuickScope.FOLDER, actions).joinToString(",")
        ).apply()

    /** Stable IDs, not the view indices; malformed saved orders fall back safely. */
    var bottomNavigationOrder: List<String>
        get() {
            val saved = prefs.getString(BOTTOM_NAV_ORDER, "")?.split(",") ?: emptyList()
            return NavigationOrder.normalize(saved)
        }
        set(order) {
            val normalized = NavigationOrder.normalize(order)
            prefs.edit().putString(BOTTOM_NAV_ORDER, normalized.joinToString(",")).apply()
        }

    var directorySorting: Int
        get(): Int = prefs.getInt(DIRECTORY_SORT_ORDER, SORT_BY_DATE_MODIFIED or SORT_DESCENDING)
        set(order) = prefs.edit().putInt(DIRECTORY_SORT_ORDER, order).apply()

    fun saveFolderGrouping(path: String, value: Int) {
        if (path.isEmpty()) {
            groupBy = value
        } else {
            prefs.edit().putInt(GROUP_FOLDER_PREFIX + path.lowercase(Locale.getDefault()), value).apply()
        }
    }

    fun getFolderGrouping(path: String): Int {
        var groupBy = prefs.getInt(GROUP_FOLDER_PREFIX + path.lowercase(Locale.getDefault()), groupBy)
        if (path != SHOW_ALL && groupBy and GROUP_BY_FOLDER != 0) {
            groupBy -= GROUP_BY_FOLDER + 1
        }
        return groupBy
    }

    fun removeFolderGrouping(path: String) {
        prefs.edit().remove(GROUP_FOLDER_PREFIX + path.lowercase(Locale.getDefault())).apply()
    }

    fun hasCustomGrouping(path: String) = prefs.contains(GROUP_FOLDER_PREFIX + path.lowercase(Locale.getDefault()))

    fun saveFolderViewType(path: String, value: Int) {
        if (path.isEmpty()) {
            viewTypeFiles = value
        } else {
            prefs.edit().putInt(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()), value).apply()
        }
    }

    fun getFolderViewType(path: String) = prefs.getInt(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()), viewTypeFiles)

    fun removeFolderViewType(path: String) {
        prefs.edit().remove(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault())).apply()
    }

    fun hasCustomViewType(path: String) = prefs.contains(VIEW_TYPE_PREFIX + path.lowercase(Locale.getDefault()))

    var wasHideFolderTooltipShown: Boolean
        get() = prefs.getBoolean(HIDE_FOLDER_TOOLTIP_SHOWN, false)
        set(wasShown) = prefs.edit().putBoolean(HIDE_FOLDER_TOOLTIP_SHOWN, wasShown).apply()

    var shouldShowHidden = showHiddenMedia || temporarilyShowHidden

    var showHiddenMedia: Boolean
        get() = prefs.getBoolean(SHOW_HIDDEN_MEDIA, false)
        set(showHiddenFolders) = prefs.edit().putBoolean(SHOW_HIDDEN_MEDIA, showHiddenFolders).apply()

    var temporarilyShowHidden: Boolean
        get() = prefs.getBoolean(TEMPORARILY_SHOW_HIDDEN, false)
        set(temporarilyShowHidden) = prefs.edit().putBoolean(TEMPORARILY_SHOW_HIDDEN, temporarilyShowHidden).apply()

    var temporarilyShowExcluded: Boolean
        get() = prefs.getBoolean(TEMPORARILY_SHOW_EXCLUDED, false)
        set(temporarilyShowExcluded) = prefs.edit().putBoolean(TEMPORARILY_SHOW_EXCLUDED, temporarilyShowExcluded).apply()

    var isThirdPartyIntent: Boolean
        get() = prefs.getBoolean(IS_THIRD_PARTY_INTENT, false)
        set(isThirdPartyIntent) = prefs.edit().putBoolean(IS_THIRD_PARTY_INTENT, isThirdPartyIntent).apply()

    var pinnedFolders: Set<String>
        get() = prefs.getStringSet(PINNED_FOLDERS, HashSet<String>())!!
        set(pinnedFolders) = prefs.edit().putStringSet(PINNED_FOLDERS, pinnedFolders).apply()

    var showAll: Boolean
        // Existing users keep their saved choice; new installs start in Photos.
        get() = prefs.getBoolean(SHOW_ALL, true)
        set(showAll) = prefs.edit().putBoolean(SHOW_ALL, showAll).apply()

    fun addPinnedFolders(paths: Set<String>) {
        val currPinnedFolders = HashSet<String>(pinnedFolders)
        currPinnedFolders.addAll(paths)
        pinnedFolders = currPinnedFolders.filter { it.isNotEmpty() }.toHashSet()
        if (paths.contains(RECYCLE_BIN)) {
            showRecycleBinLast = false
        }
    }

    fun removePinnedFolders(paths: Set<String>) {
        val currPinnedFolders = HashSet<String>(pinnedFolders)
        currPinnedFolders.removeAll(paths)
        pinnedFolders = currPinnedFolders
    }

    fun addExcludedFolder(path: String) {
        addExcludedFolders(HashSet<String>(Arrays.asList(path)))
    }

    fun addExcludedFolders(paths: Set<String>) {
        val currExcludedFolders = HashSet<String>(excludedFolders)
        currExcludedFolders.addAll(paths)
        excludedFolders = currExcludedFolders.filter { it.isNotEmpty() }.toHashSet()
    }

    fun removeExcludedFolder(path: String) {
        val currExcludedFolders = HashSet<String>(excludedFolders)
        currExcludedFolders.remove(path)
        excludedFolders = currExcludedFolders
    }

    var excludedFolders: MutableSet<String>
        get() = prefs.getStringSet(EXCLUDED_FOLDERS, HashSet())!!
        set(excludedFolders) = prefs.edit().remove(EXCLUDED_FOLDERS).putStringSet(EXCLUDED_FOLDERS, excludedFolders).apply()

    var isExcludedPasswordProtectionOn: Boolean
        get() = prefs.getBoolean(EXCLUDED_PASSWORD_PROTECTION, false)
        set(isExcludedPasswordProtectionOn) = prefs.edit().putBoolean(EXCLUDED_PASSWORD_PROTECTION, isExcludedPasswordProtectionOn).apply()

    var excludedPasswordHash: String
        get() = prefs.getString(EXCLUDED_PASSWORD_HASH, "")!!
        set(excludedPasswordHash) = prefs.edit().putString(EXCLUDED_PASSWORD_HASH, excludedPasswordHash).apply()

    var excludedProtectionType: Int
        get() = prefs.getInt(EXCLUDED_PROTECTION_TYPE, PROTECTION_PATTERN)
        set(excludedProtectionType) = prefs.edit().putInt(EXCLUDED_PROTECTION_TYPE, excludedProtectionType).apply()

    fun addIncludedFolder(path: String) {
        val currIncludedFolders = HashSet<String>(includedFolders)
        currIncludedFolders.add(path)
        includedFolders = currIncludedFolders
    }

    fun addIncludedFolders(paths: Set<String>) {
        val currIncludedFolders = HashSet<String>(includedFolders)
        currIncludedFolders.addAll(paths)
        includedFolders = currIncludedFolders.filter { it.isNotEmpty() }.toHashSet()
    }

    fun removeIncludedFolder(path: String) {
        val currIncludedFolders = HashSet<String>(includedFolders)
        currIncludedFolders.remove(path)
        includedFolders = currIncludedFolders
    }

    var includedFolders: MutableSet<String>
        get() = prefs.getStringSet(INCLUDED_FOLDERS, HashSet<String>())!!
        set(includedFolders) = prefs.edit().remove(INCLUDED_FOLDERS).putStringSet(INCLUDED_FOLDERS, includedFolders).apply()

    var autoplayVideos: Boolean
        get() = prefs.getBoolean(AUTOPLAY_VIDEOS, false)
        set(autoplayVideos) = prefs.edit().putBoolean(AUTOPLAY_VIDEOS, autoplayVideos).apply()

    var animateGifs: Boolean
        get() = prefs.getBoolean(ANIMATE_GIFS, false)
        set(animateGifs) = prefs.edit().putBoolean(ANIMATE_GIFS, animateGifs).apply()

    var maxBrightness: Boolean
        get() = prefs.getBoolean(MAX_BRIGHTNESS, false)
        set(maxBrightness) = prefs.edit().putBoolean(MAX_BRIGHTNESS, maxBrightness).apply()

    var ultraHdrRendering: Boolean
        get() = prefs.getBoolean(ULTRA_HDR_RENDERING, true)
        set(ultraHdrRendering) = prefs.edit().putBoolean(ULTRA_HDR_RENDERING, ultraHdrRendering).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(PLAYBACK_SPEED, 1f)
        set(playbackSpeed) = prefs.edit().putFloat(PLAYBACK_SPEED, playbackSpeed).apply()

    var playbackSpeedProgress: Int
        get() = prefs.getInt(PLAYBACK_SPEED_PROGRESS, -1)
        set(playbackSpeedProgress) = prefs.edit().putInt(PLAYBACK_SPEED_PROGRESS, playbackSpeedProgress).apply()

    var cropThumbnails: Boolean
        get() = prefs.getBoolean(CROP_THUMBNAILS, true)
        set(cropThumbnails) = prefs.edit().putBoolean(CROP_THUMBNAILS, cropThumbnails).apply()

    var showThumbnailVideoDuration: Boolean
        get() = prefs.getBoolean(SHOW_THUMBNAIL_VIDEO_DURATION, true)
        set(showThumbnailVideoDuration) = prefs.edit().putBoolean(SHOW_THUMBNAIL_VIDEO_DURATION, showThumbnailVideoDuration).apply()

    var showThumbnailFileTypes: Boolean
        get() = prefs.getBoolean(SHOW_THUMBNAIL_FILE_TYPES, true)
        set(showThumbnailFileTypes) = prefs.edit().putBoolean(SHOW_THUMBNAIL_FILE_TYPES, showThumbnailFileTypes).apply()

    var markFavoriteItems: Boolean
        get() = prefs.getBoolean(MARK_FAVORITE_ITEMS, true)
        set(markFavoriteItems) = prefs.edit().putBoolean(MARK_FAVORITE_ITEMS, markFavoriteItems).apply()

    var screenRotation: Int
        get() = prefs.getInt(SCREEN_ROTATION, ROTATE_BY_SYSTEM_SETTING)
        set(screenRotation) = prefs.edit().putInt(SCREEN_ROTATION, screenRotation).apply()

    var fileLoadingPriority: Int
        get() = prefs.getInt(FILE_LOADING_PRIORITY, PRIORITY_SPEED)
        set(fileLoadingPriority) = prefs.edit().putInt(FILE_LOADING_PRIORITY, fileLoadingPriority).apply()

    var loopVideos: Boolean
        get() = prefs.getBoolean(LOOP_VIDEOS, false)
        set(loop) = prefs.edit().putBoolean(LOOP_VIDEOS, loop).apply()

    var muteVideos: Boolean
        get() = prefs.getBoolean(MUTE_VIDEOS, false)
        set(muteVideos) = prefs.edit().putBoolean(MUTE_VIDEOS, muteVideos).apply()

    var gestureVideoPlayer: Boolean
        get() = prefs.getBoolean(GESTURE_VIDEO_PLAYER, false)
        set(gestureVideoPlayer) = prefs.edit().putBoolean(GESTURE_VIDEO_PLAYER, gestureVideoPlayer)
            .apply()

    var videoPlayerType: Int
        get() = prefs.getInt(VIDEO_PLAYER_TYPE, if (appRunCount <= 1) VIDEO_PLAYER_APP else VIDEO_PLAYER_SYSTEM)
        set(videoPlayerType) = prefs.edit().putInt(VIDEO_PLAYER_TYPE, videoPlayerType).apply()

    var displayFileNames: Boolean
        get() = prefs.getBoolean(DISPLAY_FILE_NAMES, false)
        set(display) = prefs.edit().putBoolean(DISPLAY_FILE_NAMES, display).apply()

    var blackBackground: Boolean
        get() = prefs.getBoolean(BLACK_BACKGROUND, true)
        set(blackBackground) = prefs.edit().putBoolean(BLACK_BACKGROUND, blackBackground).apply()

    var filterMedia: Int
        get() = prefs.getInt(FILTER_MEDIA, getDefaultFileFilter())
        set(filterMedia) = prefs.edit().putInt(FILTER_MEDIA, filterMedia).apply()

    var dirColumnCnt: Int
        get() = prefs.getInt(getDirectoryColumnsField(), getDefaultDirectoryColumnCount())
        set(dirColumnCnt) = prefs.edit().putInt(getDirectoryColumnsField(), dirColumnCnt).apply()

    var defaultFolder: String
        get() = prefs.getString(DEFAULT_FOLDER, "")!!
        set(defaultFolder) = prefs.edit().putString(DEFAULT_FOLDER, defaultFolder).apply()

    var allowInstantChange: Boolean
        get() = prefs.getBoolean(ALLOW_INSTANT_CHANGE, false)
        set(allowInstantChange) = prefs.edit().putBoolean(ALLOW_INSTANT_CHANGE, allowInstantChange).apply()

    private fun getDirectoryColumnsField(): String {
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (isPortrait) {
            if (scrollHorizontally) {
                DIR_HORIZONTAL_COLUMN_CNT
            } else {
                DIR_COLUMN_CNT
            }
        } else {
            if (scrollHorizontally) {
                DIR_LANDSCAPE_HORIZONTAL_COLUMN_CNT
            } else {
                DIR_LANDSCAPE_COLUMN_CNT
            }
        }
    }

    private fun getDefaultDirectoryColumnCount() = context.resources.getInteger(
        if (scrollHorizontally) {
            R.integer.directory_columns_horizontal_scroll
        } else {
            R.integer.directory_columns_vertical_scroll
        }
    )

    var mediaColumnCnt: Int
        get() = prefs.getInt(getMediaColumnsField(), getDefaultMediaColumnCount())
        set(mediaColumnCnt) = prefs.edit().putInt(getMediaColumnsField(), mediaColumnCnt).apply()

    private fun getMediaColumnsField(): String {
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (isPortrait) {
            if (scrollHorizontally) {
                MEDIA_HORIZONTAL_COLUMN_CNT
            } else {
                MEDIA_COLUMN_CNT
            }
        } else {
            if (scrollHorizontally) {
                MEDIA_LANDSCAPE_HORIZONTAL_COLUMN_CNT
            } else {
                MEDIA_LANDSCAPE_COLUMN_CNT
            }
        }
    }

    private fun getDefaultMediaColumnCount() = context.resources.getInteger(
        if (scrollHorizontally) {
            R.integer.media_columns_horizontal_scroll
        } else {
            R.integer.media_columns_vertical_scroll
        }
    )

    var albumCovers: String
        get() = prefs.getString(ALBUM_COVERS, "")!!
        set(albumCovers) = prefs.edit().putString(ALBUM_COVERS, albumCovers).apply()

    fun parseAlbumCovers(): ArrayList<AlbumCover> {
        val listType = object : TypeToken<List<AlbumCover>>() {}.type
        return Gson().fromJson<ArrayList<AlbumCover>>(albumCovers, listType) ?: ArrayList(1)
    }

    var hideSystemUI: Boolean
        get() = prefs.getBoolean(HIDE_SYSTEM_UI, false)
        set(hideSystemUI) = prefs.edit().putBoolean(HIDE_SYSTEM_UI, hideSystemUI).apply()

    var deleteEmptyFolders: Boolean
        get() = prefs.getBoolean(DELETE_EMPTY_FOLDERS, false)
        set(deleteEmptyFolders) = prefs.edit().putBoolean(DELETE_EMPTY_FOLDERS, deleteEmptyFolders).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEEP_SCREEN_ON, true)
        set(keepScreenOn) = prefs.edit().putBoolean(KEEP_SCREEN_ON, keepScreenOn).apply()

    var allowPhotoGestures: Boolean
        get() = prefs.getBoolean(ALLOW_PHOTO_GESTURES, false)
        set(allowPhotoGestures) = prefs.edit().putBoolean(ALLOW_PHOTO_GESTURES, allowPhotoGestures).apply()

    var allowVideoGestures: Boolean
        get() = prefs.getBoolean(ALLOW_VIDEO_GESTURES, true)
        set(allowVideoGestures) = prefs.edit().putBoolean(ALLOW_VIDEO_GESTURES, allowVideoGestures).apply()

    var slideshowInterval: Int
        get() = prefs.getInt(SLIDESHOW_INTERVAL, SLIDESHOW_DEFAULT_INTERVAL)
        set(slideshowInterval) = prefs.edit().putInt(SLIDESHOW_INTERVAL, slideshowInterval).apply()

    var slideshowIncludeVideos: Boolean
        get() = prefs.getBoolean(SLIDESHOW_INCLUDE_VIDEOS, false)
        set(slideshowIncludeVideos) = prefs.edit().putBoolean(SLIDESHOW_INCLUDE_VIDEOS, slideshowIncludeVideos).apply()

    var slideshowIncludeGIFs: Boolean
        get() = prefs.getBoolean(SLIDESHOW_INCLUDE_GIFS, false)
        set(slideshowIncludeGIFs) = prefs.edit().putBoolean(SLIDESHOW_INCLUDE_GIFS, slideshowIncludeGIFs).apply()

    var slideshowRandomOrder: Boolean
        get() = prefs.getBoolean(SLIDESHOW_RANDOM_ORDER, false)
        set(slideshowRandomOrder) = prefs.edit().putBoolean(SLIDESHOW_RANDOM_ORDER, slideshowRandomOrder).apply()

    var slideshowMoveBackwards: Boolean
        get() = prefs.getBoolean(SLIDESHOW_MOVE_BACKWARDS, false)
        set(slideshowMoveBackwards) = prefs.edit().putBoolean(SLIDESHOW_MOVE_BACKWARDS, slideshowMoveBackwards).apply()

    var slideshowAnimation: Int
        get() = prefs.getInt(SLIDESHOW_ANIMATION, SLIDESHOW_ANIMATION_SLIDE)
        set(slideshowAnimation) = prefs.edit().putInt(SLIDESHOW_ANIMATION, slideshowAnimation).apply()

    var loopSlideshow: Boolean
        get() = prefs.getBoolean(SLIDESHOW_LOOP, false)
        set(loopSlideshow) = prefs.edit().putBoolean(SLIDESHOW_LOOP, loopSlideshow).apply()

    var tempFolderPath: String
        get() = prefs.getString(TEMP_FOLDER_PATH, "")!!
        set(tempFolderPath) = prefs.edit().putString(TEMP_FOLDER_PATH, tempFolderPath).apply()

    var viewTypeFolders: Int
        get() = prefs.getInt(VIEW_TYPE_FOLDERS, VIEW_TYPE_GRID)
        set(viewTypeFolders) = prefs.edit().putInt(VIEW_TYPE_FOLDERS, viewTypeFolders).apply()

    var viewTypeFiles: Int
        get() = prefs.getInt(VIEW_TYPE_FILES, VIEW_TYPE_GRID)
        set(viewTypeFiles) = prefs.edit().putInt(VIEW_TYPE_FILES, viewTypeFiles).apply()

    var showExtendedDetails: Boolean
        get() = prefs.getBoolean(SHOW_EXTENDED_DETAILS, false)
        set(showExtendedDetails) = prefs.edit().putBoolean(SHOW_EXTENDED_DETAILS, showExtendedDetails).apply()

    var hideExtendedDetails: Boolean
        get() = prefs.getBoolean(HIDE_EXTENDED_DETAILS, true)
        set(hideExtendedDetails) = prefs.edit().putBoolean(HIDE_EXTENDED_DETAILS, hideExtendedDetails).apply()

    var extendedDetails: Int
        get() = prefs.getInt(EXTENDED_DETAILS, EXT_RESOLUTION or EXT_LAST_MODIFIED or EXT_EXIF_PROPERTIES)
        set(extendedDetails) = prefs.edit().putInt(EXTENDED_DETAILS, extendedDetails).apply()

    var wasNewAppShown: Boolean
        get() = prefs.getBoolean(WAS_NEW_APP_SHOWN, false)
        set(wasNewAppShown) = prefs.edit().putBoolean(WAS_NEW_APP_SHOWN, wasNewAppShown).apply()

    var lastFilepickerPath: String
        get() = prefs.getString(LAST_FILEPICKER_PATH, "")!!
        set(lastFilepickerPath) = prefs.edit().putString(LAST_FILEPICKER_PATH, lastFilepickerPath).apply()

    // Session-only choices must not survive process death or a fresh launch.
    var tempSkipDeleteConfirmation: Boolean
        get() = DeleteSessionState.skipConfirmation
        set(value) { DeleteSessionState.skipConfirmation = value }

    var tempSkipRecycleBin: Boolean
        get() = DeleteSessionState.skipRecycleBin
        set(value) { DeleteSessionState.skipRecycleBin = value }

    var wereFavoritesPinned: Boolean
        get() = prefs.getBoolean(WERE_FAVORITES_PINNED, false)
        set(wereFavoritesPinned) = prefs.edit().putBoolean(WERE_FAVORITES_PINNED, wereFavoritesPinned).apply()

    var wasRecycleBinPinned: Boolean
        get() = prefs.getBoolean(WAS_RECYCLE_BIN_PINNED, false)
        set(wasRecycleBinPinned) = prefs.edit().putBoolean(WAS_RECYCLE_BIN_PINNED, wasRecycleBinPinned).apply()

    var wasSVGShowingHandled: Boolean
        get() = prefs.getBoolean(WAS_SVG_SHOWING_HANDLED, false)
        set(wasSVGShowingHandled) = prefs.edit().putBoolean(WAS_SVG_SHOWING_HANDLED, wasSVGShowingHandled).apply()

    var groupBy: Int
        get() = prefs.getInt(GROUP_BY, GROUP_BY_NONE)
        set(groupBy) = prefs.edit().putInt(GROUP_BY, groupBy).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, true)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var bottomActions: Boolean
        get() = prefs.getBoolean(BOTTOM_ACTIONS, true)
        set(bottomActions) = prefs.edit().putBoolean(BOTTOM_ACTIONS, bottomActions).apply()

    fun removeLastVideoPosition(path: String) {
        prefs.edit().remove("$LAST_VIDEO_POSITION_PREFIX${path.lowercase(Locale.getDefault())}").apply()
    }

    fun saveLastVideoPosition(path: String, value: Int) {
        if (path.isNotEmpty()) {
            prefs.edit().putInt("$LAST_VIDEO_POSITION_PREFIX${path.lowercase(Locale.getDefault())}", value).apply()
        }
    }

    fun getLastVideoPosition(path: String) = prefs.getInt("$LAST_VIDEO_POSITION_PREFIX${path.lowercase(Locale.getDefault())}", 0)

    fun getAllLastVideoPositions() = prefs.all.filterKeys {
        it.startsWith(LAST_VIDEO_POSITION_PREFIX)
    }

    var rememberLastVideoPosition: Boolean
        get() = prefs.getBoolean(REMEMBER_LAST_VIDEO_POSITION, false)
        set(rememberLastVideoPosition) {
            if (!rememberLastVideoPosition) {
                getAllLastVideoPositions().forEach {
                    prefs.edit().remove(it.key).apply()
                }
            }
            prefs.edit().putBoolean(REMEMBER_LAST_VIDEO_POSITION, rememberLastVideoPosition).apply()
        }

    var visibleBottomActions: Int
        get() = prefs.getInt(VISIBLE_BOTTOM_ACTIONS, DEFAULT_BOTTOM_ACTIONS)
        set(visibleBottomActions) = prefs.edit().putInt(VISIBLE_BOTTOM_ACTIONS, visibleBottomActions).apply()

    // if a user hides a folder, then enables temporary hidden folder displaying, make sure we show it properly
    var everShownFolders: Set<String>
        get() = prefs.getStringSet(EVER_SHOWN_FOLDERS, getEverShownFolders())!!
        set(everShownFolders) = prefs.edit().putStringSet(EVER_SHOWN_FOLDERS, everShownFolders).apply()

    private fun getEverShownFolders() = hashSetOf(
        internalStoragePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath}/Screenshots",
        "$internalStoragePath/WhatsApp/Media/WhatsApp Images",
        "$internalStoragePath/WhatsApp/Media/WhatsApp Images/Sent",
        "$internalStoragePath/WhatsApp/Media/WhatsApp Video",
        "$internalStoragePath/WhatsApp/Media/WhatsApp Video/Sent",
        "$internalStoragePath/WhatsApp/Media/.Statuses",
        "$internalStoragePath/Android/media/com.whatsapp/WhatsApp/Media",
        "$internalStoragePath/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
        "$internalStoragePath/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"
    )

    var showRecycleBinAtFolders: Boolean
        get() = prefs.getBoolean(SHOW_RECYCLE_BIN_AT_FOLDERS, true)
        set(showRecycleBinAtFolders) = prefs.edit().putBoolean(SHOW_RECYCLE_BIN_AT_FOLDERS, showRecycleBinAtFolders).apply()

    var allowZoomingImages: Boolean
        get() = prefs.getBoolean(ALLOW_ZOOMING_IMAGES, true)
        set(allowZoomingImages) = prefs.edit().putBoolean(ALLOW_ZOOMING_IMAGES, allowZoomingImages).apply()

    var lastBinCheck: Long
        get() = prefs.getLong(LAST_BIN_CHECK, 0L)
        set(lastBinCheck) = prefs.edit().putLong(LAST_BIN_CHECK, lastBinCheck).apply()

    var showHighestQuality: Boolean
        get() = prefs.getBoolean(SHOW_HIGHEST_QUALITY, false)
        set(showHighestQuality) = prefs.edit().putBoolean(SHOW_HIGHEST_QUALITY, showHighestQuality).apply()

    var showRecycleBinLast: Boolean
        get() = prefs.getBoolean(SHOW_RECYCLE_BIN_LAST, false)
        set(showRecycleBinLast) = prefs.edit().putBoolean(SHOW_RECYCLE_BIN_LAST, showRecycleBinLast).apply()

    var allowDownGesture: Boolean
        get() = prefs.getBoolean(ALLOW_DOWN_GESTURE, true)
        set(allowDownGesture) = prefs.edit().putBoolean(ALLOW_DOWN_GESTURE, allowDownGesture).apply()

    var lastEditorCropAspectRatio: Int
        get() = prefs.getInt(LAST_EDITOR_CROP_ASPECT_RATIO, ASPECT_RATIO_FREE)
        set(lastEditorCropAspectRatio) = prefs.edit().putInt(LAST_EDITOR_CROP_ASPECT_RATIO, lastEditorCropAspectRatio).apply()

    var lastEditorCropOtherAspectRatioX: Float
        get() = prefs.getFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, 2f)
        set(lastEditorCropOtherAspectRatioX) = prefs.edit().putFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, lastEditorCropOtherAspectRatioX).apply()

    var lastEditorCropOtherAspectRatioY: Float
        get() = prefs.getFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, 1f)
        set(lastEditorCropOtherAspectRatioY) = prefs.edit().putFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, lastEditorCropOtherAspectRatioY).apply()

    var groupDirectSubfolders: Boolean
        get() = prefs.getBoolean(GROUP_DIRECT_SUBFOLDERS, false)
        set(groupDirectSubfolders) = prefs.edit().putBoolean(GROUP_DIRECT_SUBFOLDERS, groupDirectSubfolders).apply()

    var showWidgetFolderName: Boolean
        get() = prefs.getBoolean(SHOW_WIDGET_FOLDER_NAME, true)
        set(showWidgetFolderName) = prefs.edit().putBoolean(SHOW_WIDGET_FOLDER_NAME, showWidgetFolderName).apply()

    var allowOneToOneZoom: Boolean
        get() = prefs.getBoolean(ALLOW_ONE_TO_ONE_ZOOM, false)
        set(allowOneToOneZoom) = prefs.edit().putBoolean(ALLOW_ONE_TO_ONE_ZOOM, allowOneToOneZoom).apply()

    var allowRotatingWithGestures: Boolean
        get() = prefs.getBoolean(ALLOW_ROTATING_WITH_GESTURES, true)
        set(allowRotatingWithGestures) = prefs.edit().putBoolean(ALLOW_ROTATING_WITH_GESTURES, allowRotatingWithGestures).apply()

    var lastEditorDrawColor: Int
        get() = prefs.getInt(LAST_EDITOR_DRAW_COLOR, primaryColor)
        set(lastEditorDrawColor) = prefs.edit().putInt(LAST_EDITOR_DRAW_COLOR, lastEditorDrawColor).apply()

    var lastEditorBrushSize: Int
        get() = prefs.getInt(LAST_EDITOR_BRUSH_SIZE, 50)
        set(lastEditorBrushSize) = prefs.edit().putInt(LAST_EDITOR_BRUSH_SIZE, lastEditorBrushSize).apply()

    var showNotch: Boolean
        get() = prefs.getBoolean(SHOW_NOTCH, true)
        set(showNotch) = prefs.edit().putBoolean(SHOW_NOTCH, showNotch).apply()

    val showNotchFlow = ::showNotch.asFlowNonNull(true)

    var spamFoldersChecked: Boolean
        get() = prefs.getBoolean(SPAM_FOLDERS_CHECKED, false)
        set(spamFoldersChecked) = prefs.edit().putBoolean(SPAM_FOLDERS_CHECKED, spamFoldersChecked).apply()

    var editorBrushColor: Int
        get() = prefs.getInt(EDITOR_BRUSH_COLOR, -1)
        set(editorBrushColor) = prefs.edit().putInt(EDITOR_BRUSH_COLOR, editorBrushColor).apply()

    var editorBrushHardness: Float
        get() = prefs.getFloat(EDITOR_BRUSH_HARDNESS, 0.5f)
        set(editorBrushHardness) = prefs.edit().putFloat(EDITOR_BRUSH_HARDNESS, editorBrushHardness).apply()

    var editorBrushSize: Float
        get() = prefs.getFloat(EDITOR_BRUSH_SIZE, 0.05f)
        set(editorBrushSize) = prefs.edit().putFloat(EDITOR_BRUSH_SIZE, editorBrushSize).apply()

    var wereFavoritesMigrated: Boolean
        get() = prefs.getBoolean(WERE_FAVORITES_MIGRATED, false)
        set(wereFavoritesMigrated) = prefs.edit().putBoolean(WERE_FAVORITES_MIGRATED, wereFavoritesMigrated).apply()

    var showFolderMediaCount: Int
        get() = prefs.getInt(FOLDER_MEDIA_COUNT, FOLDER_MEDIA_CNT_LINE)
        set(showFolderMediaCount) = prefs.edit().putInt(FOLDER_MEDIA_COUNT, showFolderMediaCount).apply()

    var folderStyle: Int
        get() = prefs.getInt(FOLDER_THUMBNAIL_STYLE, FOLDER_STYLE_SQUARE)
        set(folderStyle) = prefs.edit().putInt(FOLDER_THUMBNAIL_STYLE, folderStyle).apply()

    var limitFolderTitle: Boolean
        get() = prefs.getBoolean(LIMIT_FOLDER_TITLE, false)
        set(limitFolderTitle) = prefs.edit().putBoolean(LIMIT_FOLDER_TITLE, limitFolderTitle).apply()

    var thumbnailSpacing: Int
        get() = prefs.getInt(THUMBNAIL_SPACING, 1)
        set(thumbnailSpacing) = prefs.edit().putInt(THUMBNAIL_SPACING, thumbnailSpacing).apply()

    var fileRoundedCorners: Boolean
        get() = prefs.getBoolean(FILE_ROUNDED_CORNERS, false)
        set(fileRoundedCorners) = prefs.edit().putBoolean(FILE_ROUNDED_CORNERS, fileRoundedCorners).apply()

    var customFoldersOrder: String
        get() = prefs.getString(CUSTOM_FOLDERS_ORDER, "")!!
        set(customFoldersOrder) = prefs.edit().putString(CUSTOM_FOLDERS_ORDER, customFoldersOrder).apply()

    var avoidShowingAllFilesPrompt: Boolean
        get() = prefs.getBoolean(AVOID_SHOWING_ALL_FILES_PROMPT, false)
        set(avoidShowingAllFilesPrompt) = prefs.edit().putBoolean(AVOID_SHOWING_ALL_FILES_PROMPT, avoidShowingAllFilesPrompt).apply()

    var searchAllFilesByDefault: Boolean
        get() = prefs.getBoolean(SEARCH_ALL_FILES_BY_DEFAULT, false)
        set(searchAllFilesByDefault) = prefs.edit().putBoolean(SEARCH_ALL_FILES_BY_DEFAULT, searchAllFilesByDefault).apply()

    var lastExportedFavoritesFolder: String
        get() = prefs.getString(LAST_EXPORTED_FAVORITES_FOLDER, "")!!
        set(lastExportedFavoritesFolder) = prefs.edit().putString(LAST_EXPORTED_FAVORITES_FOLDER, lastExportedFavoritesFolder).apply()

    var showPermissionRationale: Boolean
        get() = prefs.getBoolean(SHOW_PERMISSION_RATIONALE, false)
        set(showPermissionRationale) = prefs.edit().putBoolean(SHOW_PERMISSION_RATIONALE, showPermissionRationale).apply()
}
