package org.fossify.gallery.helpers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream

/**
 * Two separate wire formats. Media favorites retain the original Fossify
 * newline-delimited TXT format; folder favorites use a versioned JSON object.
 * This codec does not touch Android storage or mutate a database.
 */
internal object FavoriteBackupCodec {
    private const val FOLDER_FORMAT = "fossify-gallery-favorite-folders"
    private const val FOLDER_VERSION = 1
    private const val MAX_BACKUP_CHARS = 8 * 1024 * 1024
    private const val MAX_RECORDS = 100000

    data class FolderRecord(val path: String, val addedAt: Long)

    fun readLimited(input: InputStream): String =
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val text = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val size = reader.read(buffer)
                if (size == -1) break
                require(text.length + size <= MAX_BACKUP_CHARS) { "Backup is too large" }
                text.append(buffer, 0, size)
            }
            text.toString()
        }

    /** The original app writes a raw path per line, without a header. */
    fun decodeMediaPaths(raw: String): List<String> {
        val text = raw.removePrefix("\uFEFF")
        require(!text.trimStart().startsWith("{") && !text.trimStart().startsWith("[")) {
            "JSON is not a legacy media favorites TXT backup"
        }
        val paths = text.lineSequence().map { it.removeSuffix("\r") }
            .filter { it.isNotBlank() }.toList()
        require(paths.size <= MAX_RECORDS) { "Too many favorite media paths" }
        require(paths.all { (it.startsWith("/") || it.startsWith("content://")) && !it.contains('\u0000') }) {
            "Invalid legacy media favorites path"
        }
        return paths.distinct()
    }

    fun encodeFolders(records: List<FolderRecord>): String {
        require(records.size <= MAX_RECORDS) { "Too many favorite folder paths" }
        val root = JsonObject()
        root.addProperty("format", FOLDER_FORMAT)
        root.addProperty("version", FOLDER_VERSION)
        val folders = JsonArray()
        records.forEach { record ->
            validate(record)
            val item = JsonObject()
            item.addProperty("path", record.path)
            item.addProperty("addedAt", record.addedAt)
            folders.add(item)
        }
        root.add("folders", folders)
        return root.toString()
    }

    fun decodeFolders(raw: String): List<FolderRecord> {
        val element = try {
            JsonParser.parseString(raw.removePrefix("\uFEFF"))
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid folder favorites JSON", error)
        }
        require(element.isJsonObject) { "Expected a folder favorites object" }
        val root = element.asJsonObject
        require(root.get("format")?.isJsonPrimitive == true &&
            root.get("format").asString == FOLDER_FORMAT) { "Not a folder favorites backup" }
        require(root.get("version")?.toString() == FOLDER_VERSION.toString()) {
            "Unsupported folder favorites backup version"
        }
        val folderArray = root.get("folders")
        require(folderArray?.isJsonArray == true) { "Missing folder favorites array" }
        require(folderArray.asJsonArray.size() <= MAX_RECORDS) { "Too many favorite folders" }
        val unique = LinkedHashMap<String, FolderRecord>()
        folderArray.asJsonArray.forEach { element ->
            require(element.isJsonObject) { "Expected a folder record" }
            val obj = element.asJsonObject
            val path = obj.get("path")
            val timestamp = obj.get("addedAt")
            require(path?.isJsonPrimitive == true && path.asJsonPrimitive.isString) { "Invalid folder path" }
            require(timestamp?.isJsonPrimitive == true && timestamp.asJsonPrimitive.isNumber &&
                timestamp.asString.matches(Regex("0|[1-9][0-9]*"))) {
                "Invalid favorite folder timestamp"
            }
            val record = try {
                FolderRecord(path.asString, timestamp.asString.toLong())
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid favorite folder record", error)
            }
            validate(record)
            unique.putIfAbsent(FavoriteFolderRules.normalizedPath(record.path), record)
        }
        return unique.values.toList()
    }

    private fun validate(record: FolderRecord) {
        require((record.path.startsWith("/") || record.path.startsWith("content://")) &&
            record.path != "/" && record.path != "content://" &&
            record.path.length <= 4096 && !record.path.contains('\u0000') &&
            !record.path.contains('\n') && !record.path.contains('\r')) {
            "Invalid favorite folder path"
        }
        require(record.addedAt >= 0) { "Invalid favorite folder timestamp" }
    }
}
