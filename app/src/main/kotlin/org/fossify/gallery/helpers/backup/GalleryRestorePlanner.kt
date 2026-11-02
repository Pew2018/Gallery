package org.fossify.gallery.helpers.backup

import android.content.Context
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.*

/** Pure target computation after snapshot collection and path inspection. No writes or UI. */
internal class GalleryRestorePlanner(private val context: Context, private val access: BackupPathAccess) {
    fun plan(incoming: GalleryBackupSnapshot, local: GalleryLocalBackupState): GalleryRestorePlan {
        val legacy = LegacySettingsCodec.decode(incoming.legacySettings)
        require(legacy.applicable)
        OxygenSettingsBackupCodec.validate(incoming.oxygenSettings)
        PhotoProfileBackupCodec.encode(incoming.photoProfiles)
        require(FavoriteBackupCodec.decodeMediaPlan(FavoriteBackupCodec.encodeMediaPaths(incoming.mediaFavorites)).invalid == 0)
        FavoriteBackupCodec.encodeFolders(incoming.favoriteFolders)
        val requests = buildSet {
            (incoming.mediaFavorites + local.snapshot.mediaFavorites).forEach { add(BackupPathRequest(it, false)) }
            incoming.favoriteFolders.forEach { add(BackupPathRequest(it.path, true)) }
            incoming.photoProfiles.profiles.flatMap { it.sources }.forEach {
                if (it.type == PhotoSourceType.FOLDER) add(BackupPathRequest(it.path!!, true))
                else add(BackupPathRequest(FAVORITES, true))
            }
        }
        val statuses = requests.associateWith { access.inspect(it.path, it.folder) }
        val available = incoming.mediaFavorites.distinct().filter { statuses[BackupPathRequest(it, false)] == BackupPathStatus.AVAILABLE }
        val oldMedia = local.snapshot.mediaFavorites.toSet()
        val incomingMedia = incoming.mediaFavorites.toSet()
        val oldFolders = local.snapshot.favoriteFolders.associateBy { FavoriteFolderRules.normalizedPath(it.path) }
        val importedFolders = incoming.favoriteFolders.associateBy { FavoriteFolderRules.normalizedPath(it.path) }
        val photoMerge = PhotoProfileBackupCodec.merge(local.snapshot.photoProfiles, incoming.photoProfiles)
        val targets = GalleryRestoreMode.entries.associateWith { mode ->
            val merge = mode == GalleryRestoreMode.MERGE
            // Unsafe/unavailable local media are retained in REPLACE too, never silently removed.
            val retained = if (merge) oldMedia else oldMedia.filterTo(linkedSetOf()) {
                statuses[BackupPathRequest(it, false)] != BackupPathStatus.AVAILABLE
            }
            val media = (retained + available).sorted()
            val folders = if (merge) (oldFolders + importedFolders.filterKeys { it !in oldFolders }).values.toList()
                else importedFolders.values.toList()
            val profiles = if (merge) photoMerge.state else incoming.photoProfiles
            val values = LinkedHashMap<String, Any?>()
            if (!merge) {
                // Missing supported settings revert only inside the legacy/extension allowlists.
                val legacyKeys = LegacySettingsCodec.decode(local.snapshot.legacySettings).values.keys
                legacyKeys.filterNot { it in legacy.values }.forEach { values[it] = null }
                (OxygenSettingsBackupCodec.fixedKeys + local.preferences.keys.filter(OxygenSettingsBackupCodec::supportedKey))
                    .forEach { values[it] = null }
            }
            values.putAll(LegacySettingsRestore.prepare(context, Config(context), legacy, merge))
            values.putAll(incoming.oxygenSettings.values) // physical column keys override legacy current-orientation keys
            if (profiles != local.snapshot.photoProfiles) values[PhotoProfileRepository.KEY] = PhotoProfileCodec.encode(profiles)
            if (profiles.profiles.isEmpty() && !merge) values[PhotoProfileRepository.KEY] = PhotoProfileCodec.encode(profiles)
            val retainedSecurity = protectLocalVisibility(values, local.preferences)
            // Never call Config's activation setter: it would overwrite the imported legacy-color snapshot.
            prepareTheme(values, local.preferences)
            val changed = values.filter { (key, value) -> local.preferences[key] != value || (value == null && key in local.preferences) }
            GalleryRestoreTarget(mode, changed, media, folders.sortedBy { it.path }, profiles,
                RestoreCategoryCount(incomingMedia.size, incomingMedia.count { it in oldMedia }, media.count { it !in oldMedia },
                    removed = oldMedia.count { it !in media }),
                RestoreCategoryCount(importedFolders.size, importedFolders.keys.count { it in oldFolders },
                    importedFolders.keys.count { it !in oldFolders },
                    importedFolders.count { (key, value) -> key in oldFolders && oldFolders[key] != value },
                    oldFolders.keys.count { key -> folders.none { FavoriteFolderRules.normalizedPath(it.path) == key } }),
                RestoreCategoryCount(incoming.photoProfiles.profiles.size, photoMerge.result.existing,
                    if (merge) photoMerge.result.imported else profiles.profiles.count { profile -> local.snapshot.photoProfiles.profiles.none { it.id == profile.id } },
                    photoMerge.result.conflicts,
                    if (merge) 0 else local.snapshot.photoProfiles.profiles.count { old -> profiles.profiles.none { it.id == old.id } }), retainedSecurity)
        }
        return GalleryRestorePlan(incoming, local.token, statuses, legacy.values.size, targets)
    }

    private fun protectLocalVisibility(values: MutableMap<String, Any?>, local: Map<String, Any?>): Set<String> {
        val config = Config(context)
        val retained = linkedSetOf<String>()
        fun keepDisabled(key: String, guarded: Boolean) {
            if (guarded && values[key] == true && local[key] != true) {
                values[key] = local[key]
                retained.add(key)
            }
        }
        keepDisabled(SHOW_HIDDEN_MEDIA, config.isHiddenPasswordProtectionOn)
        keepDisabled("oxygen_favorite_show_hidden", config.isHiddenPasswordProtectionOn)
        keepDisabled("oxygen_favorite_show_excluded", config.isExcludedPasswordProtectionOn)
        if (config.isExcludedPasswordProtectionOn && EXCLUDED_FOLDERS in values) {
            @Suppress("UNCHECKED_CAST") val incoming = values[EXCLUDED_FOLDERS] as? Set<String> ?: emptySet()
            val combined = incoming + config.excludedFolders
            if (combined != incoming) { values[EXCLUDED_FOLDERS] = combined; retained.add(EXCLUDED_FOLDERS) }
        }
        return retained
    }

    private fun prepareTheme(values: MutableMap<String, Any?>, local: Map<String, Any?>) {
        fun effective(key: String, default: Any): Any = if (values.containsKey(key)) values[key] ?: default else local[key] ?: default
        val active = effective("oxygen_classic_theme_activated", false) as Boolean
        if (!active) return
        val preset = effective("oxygen_classic_theme_preset", "legacy") as String
        val accent = OxygenThemePresets.byId[preset]?.color ?: effective("oxygen_classic_custom_accent", values[PRIMARY_COLOR] ?: Config(context).primaryColor) as Int
        val colors = OxygenThemeColors.resolve(true, effective("oxygen_classic_appearance", "system") as String,
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES,
            accent, values[BACKGROUND_COLOR] as? Int ?: Config(context).backgroundColor, values[TEXT_COLOR] as? Int ?: Config(context).textColor)
        values[IS_SYSTEM_THEME_ENABLED] = false
        values[BACKGROUND_COLOR] = colors.pageBackground; values[TEXT_COLOR] = colors.primaryText
        values[PRIMARY_COLOR] = colors.rawAccent; values[ACCENT_COLOR] = colors.rawAccent
    }
}
