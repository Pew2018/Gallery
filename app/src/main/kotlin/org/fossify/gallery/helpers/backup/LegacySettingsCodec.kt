package org.fossify.gallery.helpers.backup

import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.*
import java.text.SimpleDateFormat
import java.util.Locale

/** No persistence or UI is performed while constructing this plan. Duplicate keys reject the file. */
data class SettingsImportPlan(
    val values: Map<String, Any>,
    val unknownKeys: Set<String>,
    val invalidKeys: Map<String, String>,
    val duplicateKeys: Set<String>
) {
    val applicable: Boolean get() = values.isNotEmpty() && invalidKeys.isEmpty() && duplicateKeys.isEmpty()
}

object LegacySettingsCodec {
    private enum class Kind { TEXT, BOOLEAN, INT, FLOAT, SET, COVERS }
    private val schema = linkedMapOf(
        TEXT_COLOR to Kind.INT,
        BACKGROUND_COLOR to Kind.INT,
        PRIMARY_COLOR to Kind.INT,
        ACCENT_COLOR to Kind.INT,
        APP_ICON_COLOR to Kind.INT,
        USE_ENGLISH to Kind.BOOLEAN,
        WAS_USE_ENGLISH_TOGGLED to Kind.BOOLEAN,
        WIDGET_BG_COLOR to Kind.INT,
        WIDGET_TEXT_COLOR to Kind.INT,
        DATE_FORMAT to Kind.TEXT,
        USE_24_HOUR_FORMAT to Kind.BOOLEAN,
        INCLUDED_FOLDERS to Kind.SET,
        EXCLUDED_FOLDERS to Kind.SET,
        SHOW_HIDDEN_MEDIA to Kind.BOOLEAN,
        FILE_LOADING_PRIORITY to Kind.INT,
        AUTOPLAY_VIDEOS to Kind.BOOLEAN,
        REMEMBER_LAST_VIDEO_POSITION to Kind.BOOLEAN,
        LOOP_VIDEOS to Kind.BOOLEAN,
        GESTURE_VIDEO_PLAYER to Kind.BOOLEAN,
        VIDEO_PLAYER_TYPE to Kind.INT,
        ALLOW_VIDEO_GESTURES to Kind.BOOLEAN,
        ANIMATE_GIFS to Kind.BOOLEAN,
        CROP_THUMBNAILS to Kind.BOOLEAN,
        SHOW_THUMBNAIL_VIDEO_DURATION to Kind.BOOLEAN,
        SHOW_THUMBNAIL_FILE_TYPES to Kind.BOOLEAN,
        MARK_FAVORITE_ITEMS to Kind.BOOLEAN,
        SCROLL_HORIZONTALLY to Kind.BOOLEAN,
        ENABLE_PULL_TO_REFRESH to Kind.BOOLEAN,
        MAX_BRIGHTNESS to Kind.BOOLEAN,
        ULTRA_HDR_RENDERING to Kind.BOOLEAN,
        BLACK_BACKGROUND to Kind.BOOLEAN,
        HIDE_SYSTEM_UI to Kind.BOOLEAN,
        ALLOW_INSTANT_CHANGE to Kind.BOOLEAN,
        KEEP_SCREEN_ON to Kind.BOOLEAN,
        ALLOW_PHOTO_GESTURES to Kind.BOOLEAN,
        ALLOW_DOWN_GESTURE to Kind.BOOLEAN,
        ALLOW_ROTATING_WITH_GESTURES to Kind.BOOLEAN,
        SHOW_NOTCH to Kind.BOOLEAN,
        SCREEN_ROTATION to Kind.INT,
        ALLOW_ZOOMING_IMAGES to Kind.BOOLEAN,
        SHOW_HIGHEST_QUALITY to Kind.BOOLEAN,
        ALLOW_ONE_TO_ONE_ZOOM to Kind.BOOLEAN,
        SHOW_EXTENDED_DETAILS to Kind.BOOLEAN,
        HIDE_EXTENDED_DETAILS to Kind.BOOLEAN,
        EXTENDED_DETAILS to Kind.INT,
        DELETE_EMPTY_FOLDERS to Kind.BOOLEAN,
        KEEP_LAST_MODIFIED to Kind.BOOLEAN,
        SKIP_DELETE_CONFIRMATION to Kind.BOOLEAN,
        BOTTOM_ACTIONS to Kind.BOOLEAN,
        VISIBLE_BOTTOM_ACTIONS to Kind.INT,
        USE_RECYCLE_BIN to Kind.BOOLEAN,
        SHOW_RECYCLE_BIN_AT_FOLDERS to Kind.BOOLEAN,
        SHOW_RECYCLE_BIN_LAST to Kind.BOOLEAN,
        SORT_ORDER to Kind.INT,
        DIRECTORY_SORT_ORDER to Kind.INT,
        GROUP_BY to Kind.INT,
        GROUP_DIRECT_SUBFOLDERS to Kind.BOOLEAN,
        PINNED_FOLDERS to Kind.SET,
        DISPLAY_FILE_NAMES to Kind.BOOLEAN,
        FILTER_MEDIA to Kind.INT,
        DIR_COLUMN_CNT to Kind.INT,
        MEDIA_COLUMN_CNT to Kind.INT,
        SHOW_ALL to Kind.BOOLEAN,
        SHOW_WIDGET_FOLDER_NAME to Kind.BOOLEAN,
        VIEW_TYPE_FILES to Kind.INT,
        VIEW_TYPE_FOLDERS to Kind.INT,
        SLIDESHOW_INTERVAL to Kind.INT,
        SLIDESHOW_INCLUDE_VIDEOS to Kind.BOOLEAN,
        SLIDESHOW_INCLUDE_GIFS to Kind.BOOLEAN,
        SLIDESHOW_RANDOM_ORDER to Kind.BOOLEAN,
        SLIDESHOW_MOVE_BACKWARDS to Kind.BOOLEAN,
        SLIDESHOW_LOOP to Kind.BOOLEAN,
        LAST_EDITOR_CROP_ASPECT_RATIO to Kind.INT,
        LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X to Kind.FLOAT,
        LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y to Kind.FLOAT,
        LAST_CONFLICT_RESOLUTION to Kind.INT,
        LAST_CONFLICT_APPLY_TO_ALL to Kind.BOOLEAN,
        EDITOR_BRUSH_COLOR to Kind.INT,
        EDITOR_BRUSH_HARDNESS to Kind.FLOAT,
        EDITOR_BRUSH_SIZE to Kind.FLOAT,
        FOLDER_THUMBNAIL_STYLE to Kind.INT,
        FOLDER_MEDIA_COUNT to Kind.INT,
        LIMIT_FOLDER_TITLE to Kind.BOOLEAN,
        THUMBNAIL_SPACING to Kind.INT,
        FILE_ROUNDED_CORNERS to Kind.BOOLEAN,
        SEARCH_ALL_FILES_BY_DEFAULT to Kind.BOOLEAN,
        ALBUM_COVERS to Kind.COVERS
    )

    val supportedKeys: Set<String> get() = schema.keys

    fun encode(values: Map<String, Any>): String = values.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }

    fun decode(text: String): SettingsImportPlan {
        require(text.toByteArray(Charsets.UTF_8).size <= BackupIo.MAX_BYTES) { "Backup is too large" }
        val values = linkedMapOf<String, Any>()
        val unknown = linkedSetOf<String>()
        val invalid = linkedMapOf<String, String>()
        val seen = hashSetOf<String>()
        val duplicates = linkedSetOf<String>()
        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, line ->
            require(index < 100000) { "Too many setting records" }
            if (line.isBlank()) return@forEachIndexed
            val split = line.indexOf('=')
            if (split <= 0) { invalid["line ${index + 1}"] = "Missing key=value separator"; return@forEachIndexed }
            val key = line.substring(0, split)
            val raw = line.substring(split + 1)
            if (!seen.add(key)) duplicates.add(key)
            val kind = schema[key]
            if (kind == null) { unknown.add(key); return@forEachIndexed }
            try {
                require(raw.none { it.isISOControl() }) { "Control character in value" }
                val value: Any = when (kind) {
                    Kind.BOOLEAN -> when (raw.lowercase(Locale.ROOT)) {
                        "true" -> true
                        "false" -> false
                        else -> error("Expected true or false")
                    }
                    Kind.INT -> raw.toIntOrNull()?.also { validateInt(key, it) } ?: error("Invalid integer")
                    Kind.FLOAT -> raw.toFloatOrNull()?.also {
                        require(it.isFinite()) { "Non-finite float" }
                        when (key) {
                            EDITOR_BRUSH_HARDNESS, EDITOR_BRUSH_SIZE -> require(it in 0f..1f) { "Brush value outside 0..1" }
                            else -> require(it > 0f) { "Aspect ratio must be positive" }
                        }
                    } ?: error("Invalid float")
                    Kind.SET -> raw.split(',').filter { it.isNotEmpty() }.toSet()
                    Kind.COVERS -> raw.also { validateCovers(it) }
                    Kind.TEXT -> raw.also {
                        if (key == DATE_FORMAT) { require(it.isNotBlank()); SimpleDateFormat(it, Locale.US) }
                    }
                }
                values[key] = value
            } catch (error: Exception) { invalid[key] = error.message ?: "Invalid value" }
        }
        return SettingsImportPlan(values, unknown, invalid, duplicates)
    }

    internal fun validateCovers(raw: String) {
        if (raw.isEmpty()) return // Legacy default export.
        val root = BackupJson.parse(raw)
        require(root.isJsonArray) { "Album covers must be an array" }
        require(root.asJsonArray.size() <= 100000)
        root.asJsonArray.forEach {
            require(it.isJsonObject)
            for (field in listOf("path", "tmb")) {
                val value = it.asJsonObject.get(field)
                require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "Missing cover $field" }
                require(value.asString.none { char -> char.isISOControl() })
            }
        }
    }

    private fun validateInt(key: String, value: Int) {
        when (key) {
            DIR_COLUMN_CNT, MEDIA_COLUMN_CNT -> require(value in 1..MAX_COLUMN_COUNT) { "Value outside the supported range for $key: $value" }
            FILE_LOADING_PRIORITY -> require(value in 0..2) { "Value outside the supported range for $key: $value" }
            VIDEO_PLAYER_TYPE -> require(value in 0..1) { "Value outside the supported range for $key: $value" }
            VIEW_TYPE_FILES, VIEW_TYPE_FOLDERS -> require(value in 1..3) { "Value outside the supported range for $key: $value" }
            SCREEN_ROTATION -> require(value in 0..2) { "Value outside the supported range for $key: $value" }
            FOLDER_THUMBNAIL_STYLE -> require(value in 1..2) { "Value outside the supported range for $key: $value" }
            FOLDER_MEDIA_COUNT -> require(value in 1..3) { "Value outside the supported range for $key: $value" }
            THUMBNAIL_SPACING -> require(value in 0..64) { "Value outside the supported range for $key: $value" }
            SLIDESHOW_INTERVAL -> require(value in 1..86400) { "Value outside the supported range for $key: $value" }
            LAST_EDITOR_CROP_ASPECT_RATIO -> require(value in 0..4) { "Value outside the supported range for $key: $value" }
            LAST_CONFLICT_RESOLUTION -> require(value in 1..4) { "Value outside the supported range for $key: $value" }
            FILTER_MEDIA -> require(value >= 0 && value and 63 == value) { "Value outside the supported range for $key: $value" }
            EXTENDED_DETAILS -> require(value >= 0 && value and 4095 == value) { "Value outside the supported range for $key: $value" }
            VISIBLE_BOTTOM_ACTIONS -> require(value >= 0) { "Value outside the supported range for $key: $value" }
            GROUP_BY -> require(value > 0 && value and 3327 == value) { "Value outside the supported range for $key: $value" }
            SORT_ORDER, DIRECTORY_SORT_ORDER -> require(value > 0 && value and 1048575 == value) { "Value outside the supported range for $key: $value" }
        }
    }
}
