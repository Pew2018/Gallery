package org.fossify.gallery.helpers.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal data class GalleryBackupFile(val name: String, val size: Long, val sha256: String)
internal data class GalleryBackupManifest(val createdAt: String, val appId: String, val files: List<GalleryBackupFile>) {
    fun encode(): String = JsonObject().apply {
        addProperty("format", FORMAT); addProperty("version", 1)
        addProperty("createdAt", createdAt); addProperty("appId", appId)
        add("files", JsonArray().apply { files.forEach { file -> add(JsonObject().apply {
            addProperty("name", file.name); addProperty("size", file.size); addProperty("sha256", file.sha256)
        }) } })
    }.toString()

    companion object {
        const val FORMAT = "fossify-gallery-full-backup"
        val payloadNames = listOf("settings.txt", "favorites.txt", "favorite_folders.json", "photo_profiles.json", "oxygen_settings.json")
        val names = listOf("manifest.json") + payloadNames
        fun now(appId: String, files: List<GalleryBackupFile>) = GalleryBackupManifest(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(java.util.Date()), appId, files)

        fun decode(text: String): GalleryBackupManifest {
            val root = BackupJson.parse(text).asJsonObject
            require(string(root, "format") == FORMAT) { "Unsupported complete backup format" }
            if (root.get("version")?.toString() != "1") throw UnsupportedGalleryBackupVersion()
            val appId = string(root, "appId")
            require(appId in setOf("org.fossify.gallery", "org.fossify.gallery.debug")) { "Wrong application ID" }
            val created = string(root, "createdAt")
            require(created.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(Z|[+-][0-9]{2}:[0-9]{2})")))
            val position = ParsePosition(0)
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }.parse(created, position)
            require(parsed != null && position.index == created.length) { "Invalid backup date" }
            val raw = root.get("files")
            require(raw?.isJsonArray == true && raw.asJsonArray.size() == payloadNames.size)
            val files = raw.asJsonArray.map {
                val obj = it.asJsonObject
                val sizeValue = obj.get("size")
                require(sizeValue?.isJsonPrimitive == true && sizeValue.asJsonPrimitive.isNumber)
                val size = sizeValue.asString.toLongOrNull() ?: error("Invalid manifest size")
                val name = string(obj, "name")
                val hash = string(obj, "sha256")
                require(name in payloadNames && size in 0..BackupIo.MAX_BYTES && hash.matches(Regex("[0-9a-f]{64}")))
                GalleryBackupFile(name, size, hash)
            }
            require(files.map { it.name }.toSet() == payloadNames.toSet()) { "Duplicate/missing manifest record" }
            return GalleryBackupManifest(created, appId, files)
        }
        private fun string(obj: JsonObject, key: String): String {
            val field = obj.get(key)
            require(field?.isJsonPrimitive == true && field.asJsonPrimitive.isString) { "Missing manifest string: $key" }
            return field.asString
        }
    }
}
