package org.fossify.gallery.helpers.backup

import android.content.Context
import android.content.res.Configuration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.AlbumCover

/** Prepare every potentially failing conversion before the one scoped preference commit. */
object LegacySettingsRestore {
    fun apply(context: Context, config: Config, plan: SettingsImportPlan) {
        BackupPreferences.commit(context.getSharedPrefs(), prepare(context, config, plan))
    }

    fun prepare(context: Context, config: Config, plan: SettingsImportPlan, merge: Boolean = true): Map<String, Any> {
        require(plan.applicable) { "Settings plan is not applicable" }
        val values = LinkedHashMap(plan.values)
        for ((key, current) in listOf(INCLUDED_FOLDERS to config.includedFolders,
            EXCLUDED_FOLDERS to config.excludedFolders, PINNED_FOLDERS to config.pinnedFolders)) {
            @Suppress("UNCHECKED_CAST")
            val incoming = values[key] as? Set<String> ?: continue
            values[key] = ((if (merge) current else emptySet()) + incoming).filter { it.isNotEmpty() }.toSet()
            if (key == PINNED_FOLDERS && RECYCLE_BIN in incoming) values[SHOW_RECYCLE_BIN_LAST] = false
        }
        if (USE_ENGLISH in values && WAS_USE_ENGLISH_TOGGLED !in values) values[WAS_USE_ENGLISH_TOGGLED] = true
        val icon = values[APP_ICON_COLOR] as? Int
        if (icon != null) {
            require(icon in context.getAppIconColors()) { "Unsupported app icon color" }
            values[IS_USING_MODIFIED_APP_ICON] = icon != context.getColor(org.fossify.commons.R.color.color_primary)
        }
        if (ALBUM_COVERS in values) {
            val listType = object : TypeToken<List<AlbumCover>>() {}.type
            val raw = values[ALBUM_COVERS] as String
            val incoming: List<AlbumCover> = if (raw.isEmpty()) emptyList() else Gson().fromJson(raw, listType)
            val existing = if (merge) config.parseAlbumCovers().toMutableList() else mutableListOf<AlbumCover>()
            val paths = existing.mapTo(hashSetOf()) { it.path }
            incoming.filter { it.path !in paths && context.getDoesFilePathExist(it.tmb) }
                .forEach { if (paths.add(it.path)) existing.add(it) }
            values[ALBUM_COVERS] = Gson().toJson(existing)
        }
        // These setters choose orientation/scroll-specific keys, not always the wire-format key.
        val horizontal = values[SCROLL_HORIZONTALLY] as? Boolean ?: config.scrollHorizontally
        val portrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        for (key in listOf(DIR_COLUMN_CNT, MEDIA_COLUMN_CNT)) {
            val count = values.remove(key) ?: continue
            val actual = if (key == DIR_COLUMN_CNT) {
                if (portrait) { if (horizontal) DIR_HORIZONTAL_COLUMN_CNT else DIR_COLUMN_CNT }
                else { if (horizontal) DIR_LANDSCAPE_HORIZONTAL_COLUMN_CNT else DIR_LANDSCAPE_COLUMN_CNT }
            } else {
                if (portrait) { if (horizontal) MEDIA_HORIZONTAL_COLUMN_CNT else MEDIA_COLUMN_CNT }
                else { if (horizontal) MEDIA_LANDSCAPE_HORIZONTAL_COLUMN_CNT else MEDIA_LANDSCAPE_COLUMN_CNT }
            }
            values[actual] = count
        }
        return values
    }
}
