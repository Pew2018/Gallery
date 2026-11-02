package org.fossify.gallery.helpers.backup

import android.content.Context
import com.google.gson.JsonObject
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.*

/** Audited allowlist. Raw prefs are filtered BEFORE encoding; credentials never enter this model. */
object OxygenSettingsBackupCodec {
    val columns = setOf(DIR_COLUMN_CNT, DIR_LANDSCAPE_COLUMN_CNT, DIR_HORIZONTAL_COLUMN_CNT,
        DIR_LANDSCAPE_HORIZONTAL_COLUMN_CNT, MEDIA_COLUMN_CNT, MEDIA_LANDSCAPE_COLUMN_CNT,
        MEDIA_HORIZONTAL_COLUMN_CNT, MEDIA_LANDSCAPE_HORIZONTAL_COLUMN_CNT)
    private val booleans = setOf("oxygen_classic_theme_activated", "oxygen_classic_toolbar_custom",
        "oxygen_classic_toolbar_manual_text", "oxygen_legacy_system", IS_SYSTEM_THEME_ENABLED,
        "oxygen_favorite_show_excluded", "oxygen_favorite_show_hidden", "oxygenos_classic_last_main_view_all",
        "gallery_last_favorites_view_files", MUTE_VIDEOS)
    private val colors = setOf("oxygen_classic_custom_accent", "oxygen_classic_toolbar_color",
        "oxygen_classic_toolbar_text_color", "oxygen_legacy_text", "oxygen_legacy_background",
        "oxygen_legacy_primary", "oxygen_legacy_accent", CUSTOM_TEXT_COLOR, CUSTOM_BACKGROUND_COLOR,
        CUSTOM_PRIMARY_COLOR, CUSTOM_ACCENT_COLOR, CUSTOM_APP_ICON_COLOR, LAST_EDITOR_DRAW_COLOR)
    private val strings = setOf("oxygen_classic_theme_preset", "oxygen_classic_appearance", "oxygen_toolbar_home_actions",
        "oxygen_toolbar_folder_actions", "oxygen_bottom_nav_order", CUSTOM_FOLDERS_ORDER, DEFAULT_FOLDER)
    val fixedKeys = booleans + colors + columns + strings + setOf("photo_media_filter", SLIDESHOW_ANIMATION, LAST_EDITOR_BRUSH_SIZE, PLAYBACK_SPEED, PLAYBACK_SPEED_PROGRESS)
    val dynamicPrefixes = listOf(SORT_FOLDER_PREFIX, GROUP_FOLDER_PREFIX, VIEW_TYPE_PREFIX)

    private fun validScope(scope: String): Boolean = PhotoProfileBackupCodec.validFolderPath(scope) ||
        scope in setOf(SHOW_ALL, FAVORITES, RECYCLE_BIN, PhotoProfileMediaLoader.PATH.lowercase(java.util.Locale.ROOT))

    fun supportedKey(key: String): Boolean = key in fixedKeys || dynamicPrefixes.any {
        key.startsWith(it) && validScope(key.removePrefix(it))
    }

    fun capture(context: Context): OxygenSettingsSnapshot {
        val raw = context.getSharedPrefs().all
        val values = raw.filterKeys(::supportedKey).mapValues { requireNotNull(it.value) }.toMutableMap()
        val config = Config(context)
        values.putAll(mapOf(
            "oxygen_classic_theme_activated" to config.oxygenThemeActivated,
            "oxygen_classic_theme_preset" to config.oxygenThemePresetId,
            "oxygen_classic_appearance" to config.oxygenAppearanceMode,
            "oxygen_classic_custom_accent" to config.oxygenCustomAccent,
            "oxygen_classic_toolbar_custom" to config.oxygenCustomToolbarEnabled,
            "oxygen_classic_toolbar_color" to config.oxygenToolbarColor,
            "oxygen_classic_toolbar_manual_text" to config.oxygenManualToolbarText,
            "oxygen_classic_toolbar_text_color" to config.oxygenToolbarTextColor,
            IS_SYSTEM_THEME_ENABLED to config.isSystemThemeEnabled,
            "oxygen_toolbar_home_actions" to config.toolbarHomeActions.joinToString(","),
            "oxygen_toolbar_folder_actions" to config.toolbarFolderActions.joinToString(","),
            "oxygen_bottom_nav_order" to config.bottomNavigationOrder.joinToString(","),
            "oxygen_favorite_show_excluded" to config.favoriteShowExcluded,
            "oxygen_favorite_show_hidden" to config.favoriteShowHidden,
            "oxygenos_classic_last_main_view_all" to config.lastMainViewAll,
            "gallery_last_favorites_view_files" to config.lastFavoritesViewFiles,
            "photo_media_filter" to config.photoMediaFilter))
        // Resolve all eight column defaults, independently of current orientation and scroll mode.
        columns.forEach { key ->
            if (key !in values) {
                val directory = key.startsWith("dir_")
                val horizontal = "horizontal" in key
                val resource = if (directory) {
                    if (horizontal) org.fossify.gallery.R.integer.directory_columns_horizontal_scroll
                    else org.fossify.gallery.R.integer.directory_columns_vertical_scroll
                } else {
                    if (horizontal) org.fossify.gallery.R.integer.media_columns_horizontal_scroll
                    else org.fossify.gallery.R.integer.media_columns_vertical_scroll
                }
                values[key] = context.resources.getInteger(resource)
            }
        }
        return validate(OxygenSettingsSnapshot(values = values))
    }

    fun encode(snapshot: OxygenSettingsSnapshot): String {
        val clean = validate(snapshot)
        val root = JsonObject().apply { addProperty("version", 1) }
        val values = JsonObject()
        clean.values.toSortedMap().forEach { (key, value) ->
            when (value) {
                is Boolean -> values.addProperty(key, value)
                is Int -> values.addProperty(key, value)
                is Float -> values.addProperty(key, value)
                is String -> values.addProperty(key, value)
                else -> error("Unsupported extension value type")
            }
        }
        root.add("values", values)
        return root.toString()
    }

    fun decode(text: String): OxygenSettingsSnapshot = try {
        decodeValues(text)
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid extension settings backup", error)
    }

    private fun decodeValues(text: String): OxygenSettingsSnapshot {
        val element = BackupJson.parse(text)
        require(element.isJsonObject)
        val root = element.asJsonObject
        require(root.get("version")?.toString() == "1") { "Unsupported extension settings version" }
        require(root.get("values")?.isJsonObject == true)
        val values = root.getAsJsonObject("values").entrySet().associate { (key, raw) ->
            require(raw.isJsonPrimitive) { "Invalid extension value" }
            val primitive = raw.asJsonPrimitive
            val value: Any = when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isString -> primitive.asString
                primitive.isNumber -> if (key == PLAYBACK_SPEED) primitive.asString.toFloatOrNull() ?: error("Invalid playback speed")
                    else primitive.asString.toIntOrNull() ?: error("Extension integer required")
                else -> error("Invalid extension value")
            }
            key to value
        }
        return validate(OxygenSettingsSnapshot(values = values))
    }

    fun validate(snapshot: OxygenSettingsSnapshot): OxygenSettingsSnapshot {
        require(snapshot.version == 1 && snapshot.values.size <= 10000)
        val clean = linkedMapOf<String, Any>()
        snapshot.values.forEach { (key, value) ->
            require(supportedKey(key)) { "Unsupported extension preference: $key" }
            require(key.length <= 4200 && key.none { it.isISOControl() })
            val normalized: Any = when {
                key in booleans -> { require(value is Boolean); value }
                key == PLAYBACK_SPEED -> { require(value is Float && value.isFinite() && value in 0.25f..3f); value }
                key == PLAYBACK_SPEED_PROGRESS -> { require(value is Int && value in -1..325); value }
                key in colors -> { require(value is Int); value }
                key in columns -> { require(value is Int && value in 1..MAX_COLUMN_COUNT); value }
                key == "photo_media_filter" -> { require(value is Int && value >= 0 && value and 63 == value); value }
                key == SLIDESHOW_ANIMATION -> { require(value is Int && value in 0..2); value }
                key == LAST_EDITOR_BRUSH_SIZE -> { require(value is Int && value in 0..100); value }
                key in strings -> {
                    require(value is String && value.none { it.isISOControl() } && value.length <= 1024 * 1024)
                    when (key) {
                        "oxygen_classic_theme_preset" -> require(value in OxygenThemePresets.byId || value in setOf("legacy", "custom"))
                        "oxygen_classic_appearance" -> require(value in setOf("system", "light", "dark"))
                        DEFAULT_FOLDER -> require(value.isEmpty() || validScope(value))
                        CUSTOM_FOLDERS_ORDER -> require(value.isEmpty() || value.split("|||").all(::validScope))
                    }
                    when (key) {
                        "oxygen_toolbar_home_actions", "oxygen_toolbar_folder_actions" -> {
                            val scope = if (key == "oxygen_toolbar_home_actions") ToolbarQuickScope.HOME else ToolbarQuickScope.FOLDER
                            val actions = value.split(',')
                            require(actions.size in 1..5 && actions.distinct().size == actions.size && actions.all { it in ToolbarQuickActions.options(scope) })
                            ToolbarQuickActions.normalize(scope, actions).joinToString(",")
                        }
                        "oxygen_bottom_nav_order" -> {
                            val items = value.split(',')
                            require(items.size == 3 && items.toSet() == NavigationOrder.defaults.toSet())
                            NavigationOrder.normalize(items).joinToString(",")
                        }
                        else -> value
                    }
                }
                key.startsWith(VIEW_TYPE_PREFIX) -> { require(value is Int && value in 1..3); value }
                key.startsWith(GROUP_FOLDER_PREFIX) -> { require(value is Int && value > 0 && value and 3327 == value); value }
                key.startsWith(SORT_FOLDER_PREFIX) -> { require(value is Int && value > 0 && value and 1048575 == value); value }
                else -> error("Unsupported extension key")
            }
            clean[key] = normalized
        }
        return snapshot.copy(values = clean)
    }
}
