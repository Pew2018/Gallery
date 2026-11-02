package org.fossify.gallery.helpers.backup

import com.google.gson.JsonObject
import org.fossify.gallery.helpers.*
import java.util.UUID

class InvalidStoredPhotoProfileBackup(cause: Exception) : IllegalArgumentException("Stored photo profiles are invalid; export cancelled", cause)

class UnsupportedPhotoBackupVersion : IllegalArgumentException("Unsupported photo profile backup version")

data class PhotoProfileImportResult(val state: PhotoProfileState, val result: BackupImportResult)

/** Only the structure model is serialized. Device credentials and source bindings are never read. */
object PhotoProfileBackupCodec {
    const val MAX_PROFILES = 1000
    const val MAX_SOURCES = 1000

    /** Export must validate original persisted bytes before runtime's tolerant decoder can drop records. */
    fun encodeStored(raw: String): String = try {
        encode(if (raw.isEmpty()) PhotoProfileState() else decodeStrict(raw))
    } catch (error: Exception) { throw InvalidStoredPhotoProfileBackup(error) }

    fun encode(state: PhotoProfileState): String = PhotoProfileCodec.encode(state).also { decodeStrict(it) }

    fun decodeStrict(json: String): PhotoProfileState {
        val element = BackupJson.parse(json)
        require(element.isJsonObject) { "Expected photo profile object" }
        val root = element.asJsonObject
        if (root.get("version")?.toString() != "1") throw UnsupportedPhotoBackupVersion()
        val array = root.get("profiles")
        require(array?.isJsonArray == true) { "Missing profiles array" }
        require(array.asJsonArray.size() <= MAX_PROFILES) { "Too many profiles" }
        val ids = hashSetOf<UUID>()
        val profiles = array.asJsonArray.map { value ->
            require(value.isJsonObject)
            val obj = value.asJsonObject
            val id = string(obj, "id")
            val uuid = UUID.fromString(id)
            require(uuid.toString().equals(id, true) && ids.add(uuid)) { "Invalid or duplicate UUID" }
            val rawName = string(obj, "name")
            val name = rawName.trim()
            require(name.isNotBlank() && rawName.length <= 1024 && rawName.none { it.isISOControl() }) { "Invalid profile name" }
            val sources = obj.get("sources")
            require(sources?.isJsonArray == true && sources.asJsonArray.size() in 1..MAX_SOURCES) { "Invalid sources array" }
            val parsed = sources.asJsonArray.map { raw ->
                require(raw.isJsonObject)
                val source = raw.asJsonObject
                val type = PhotoSourceType.valueOf(string(source, "type"))
                when (type) {
                    PhotoSourceType.FOLDER -> {
                        val path = string(source, "path")
                        require(validFolderPath(path)) { "Invalid source folder path" }
                        PhotoSource(type, path.trimEnd('/'))
                    }
                    PhotoSourceType.FAVORITES -> {
                        require(!source.has("path") || source.get("path").isJsonNull) { "Favorites must not have a path" }
                        PhotoSource(type)
                    }
                }
            }
            require(parsed.distinct().size == parsed.size) { "Duplicate source" }
            PhotoProfile(id, name, parsed)
        }
        val selectedRaw = root.get("selectedId")
        val selected = if (selectedRaw == null || selectedRaw.isJsonNull) null else string(root, "selectedId")
        require(selected == null || profiles.any { it.id == selected }) { "Selected profile is missing" }
        return PhotoProfileState(profiles = profiles, selectedId = selected ?: profiles.firstOrNull()?.id)
    }

    private fun string(obj: JsonObject, key: String): String {
        val field = obj.get(key)
        require(field?.isJsonPrimitive == true && field.asJsonPrimitive.isString) { "Missing string: $key" }
        return field.asString
    }

    internal fun validFolderPath(path: String): Boolean = path.startsWith('/') && path.length <= 4096 &&
        path.none { it.isISOControl() } && path.split('/').filter { it.isNotEmpty() }.let { parts ->
            parts.isNotEmpty() && parts.none { it == "." || it == ".." }
        }

    /** Preserve local order and selection. Append incoming records in their file order. */
    fun merge(local: PhotoProfileState, incoming: PhotoProfileState, newId: () -> String = { UUID.randomUUID().toString() }): PhotoProfileImportResult {
        val checked = decodeStrict(encode(incoming))
        val profiles = local.profiles.toMutableList()
        var imported = 0
        var existing = 0
        var conflicts = 0
        val mapped = hashMapOf<String, String>()
        fun equivalent(a: PhotoProfile, b: PhotoProfile) = a.name == b.name && a.sources.toSet() == b.sources.toSet()
        checked.profiles.forEach { profile ->
            val sameId = profiles.firstOrNull { it.id.equals(profile.id, true) }
            val copy = profiles.firstOrNull { equivalent(it, profile) }
            when {
                sameId != null && equivalent(sameId, profile) -> { existing++; mapped[profile.id] = sameId.id }
                copy != null -> { existing++; mapped[profile.id] = copy.id }
                else -> {
                    val id = if (sameId == null) profile.id else newId().also { candidate ->
                        require(UUID.fromString(candidate).toString().equals(candidate, true))
                        require(profiles.none { it.id.equals(candidate, true) })
                        conflicts++
                    }
                    profiles.add(profile.copy(id = id)); mapped[profile.id] = id; imported++
                }
            }
        }
        require(profiles.size <= MAX_PROFILES) { "Merged profile limit exceeded" }
        val selected = local.selectedId?.takeIf { id -> local.profiles.any { it.id == id } }
            ?: checked.selectedId?.let(mapped::get) ?: profiles.firstOrNull()?.id
        val state = PhotoProfileState(profiles = profiles, selectedId = selected)
        return PhotoProfileImportResult(state, BackupImportResult(checked.profiles.size, imported, existing, conflicts = conflicts))
    }
}
