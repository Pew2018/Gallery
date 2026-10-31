package org.fossify.gallery.helpers

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID

enum class PhotoSourceType { FOLDER, FAVORITES }
data class PhotoSource(val type: PhotoSourceType, val path: String? = null)
data class PhotoProfile(val id: String, val name: String, val sources: List<PhotoSource>)
data class PhotoProfileState(val version: Int = 1, val profiles: List<PhotoProfile> = emptyList(), val selectedId: String? = null) {
    val current: PhotoProfile? get() = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
}

/** Checks a source folder and its lexical ancestors without resolving it through File/SAF. */
internal fun hasProtectedFolderBoundary(path: String, isProtected: (String) -> Boolean): Boolean {
    if (!path.startsWith('/')) return isProtected(path)
    var current = path.trimEnd('/').ifEmpty { "/" }
    while (true) {
        if (isProtected(current)) return true
        if (current == "/") return false
        val parentSeparator = current.lastIndexOf('/')
        current = if (parentSeparator <= 0) "/" else current.substring(0, parentSeparator)
    }
}

/** Run off the UI thread. Both raw and canonical ancestors are protected;
 * URI thumbnails remain URIs and are never passed to File. */
internal fun photoSourceThumbnailProtected(path: String, thumbnail: String, isProtected: (String) -> Boolean): Boolean {
    fun protected(value: String): Boolean {
        if (hasProtectedFolderBoundary(value, isProtected)) return true
        if (!value.startsWith('/')) return false
        return runCatching { hasProtectedFolderBoundary(java.io.File(value).canonicalPath, isProtected) }.getOrDefault(true)
    }
    return protected(path) || (thumbnail.isNotBlank() && protected(thumbnail))
}

internal fun photoSourceColumnCount(widthDp: Int): Int = (widthDp / 160).coerceIn(2, 4)

/** Dialog-local source selection; callers persist only an explicit confirmed snapshot. */
class PhotoSourceSelectionDraft(initial: Collection<String>) {
    val paths: MutableSet<String> = initial.mapNotNull(::stableId).toCollection(linkedSetOf())
    fun canSelect(path: String): Boolean = stableId(path) != null
    fun contains(path: String): Boolean = stableId(path)?.let(paths::contains) == true
    fun toggle(path: String) {
        val id = stableId(path) ?: return
        if (!paths.add(id)) paths.remove(id)
    }
    fun snapshot(): Set<String> = paths.toSet()
    private fun stableId(path: String): String? = when {
        path == org.fossify.commons.helpers.FAVORITES -> path
        path.startsWith('/') -> path.trimEnd('/').takeIf { it.isNotEmpty() }
        else -> null
    }
}

/** Each source status scan may publish only while it is still the newest request. */
internal class PhotoSourceStatusRequests {
    private var version = 0L
    @Synchronized fun begin(): Long = ++version
    @Synchronized fun isCurrent(request: Long): Boolean = request == version
    @Synchronized fun invalidate() { version++ }
}

/** Pure, versioned persistence. Invalid input fails closed; identities never depend on names. */
object PhotoProfileCodec {
    fun sources(values: List<PhotoSource>): List<PhotoSource> = values.mapNotNull {
        when (it.type) {
            PhotoSourceType.FAVORITES -> PhotoSource(PhotoSourceType.FAVORITES)
            PhotoSourceType.FOLDER -> it.path?.trimEnd('/')?.takeIf { path -> path.startsWith('/') && path.isNotBlank() }
                ?.let { path -> PhotoSource(PhotoSourceType.FOLDER, path) }
        }
    }.distinct()

    fun encode(state: PhotoProfileState): String = Gson().toJson(state)
    fun decode(json: String): PhotoProfileState = runCatching {
        if (json.isBlank()) return PhotoProfileState()
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("version").asInt == 1)
        val profiles = root.getAsJsonArray("profiles").mapNotNull { element ->
            runCatching {
                val item = element.asJsonObject
                val id = item.get("id").asString.also { UUID.fromString(it) }
                val name = item.get("name").asString.trim().also { require(it.isNotBlank()) }
                val parsedSources = item.getAsJsonArray("sources").mapNotNull { raw ->
                    runCatching {
                        val source = raw.asJsonObject
                        val type = PhotoSourceType.valueOf(source.get("type").asString)
                        PhotoSource(type, source.get("path")?.takeUnless { it.isJsonNull }?.asString)
                    }.getOrNull()
                }
                PhotoProfile(id, name, sources(parsedSources)).also { require(it.sources.isNotEmpty()) }
            }.getOrNull()
        }.distinctBy { it.id }
        val selected = root.get("selectedId")?.takeUnless { it.isJsonNull }?.asString
        PhotoProfileState(profiles = profiles, selectedId = selected?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id)
    }.getOrDefault(PhotoProfileState())
}

/** One atomic record holds both profiles and selection; cancellation never writes drafts. */
class PhotoProfileStore(private val read: () -> String, private val write: (String) -> Unit) {
    fun state() = PhotoProfileCodec.decode(read())
    fun select(id: String) {
        val state = state()
        if (state.profiles.any { it.id == id }) write(PhotoProfileCodec.encode(state.copy(selectedId = id)))
    }
    fun save(profile: PhotoProfile) {
        val clean = profile.copy(name = profile.name.trim(), sources = PhotoProfileCodec.sources(profile.sources))
        UUID.fromString(clean.id)
        require(clean.name.isNotEmpty() && clean.sources.isNotEmpty())
        val state = state()
        val profiles = state.profiles.filter { it.id != clean.id } + clean
        write(PhotoProfileCodec.encode(state.copy(profiles = profiles, selectedId = clean.id)))
    }
    fun delete(id: String) {
        val state = state()
        val profiles = state.profiles.filter { it.id != id }
        val selected = state.selectedId?.takeIf { selected -> profiles.any { it.id == selected } } ?: profiles.firstOrNull()?.id
        write(PhotoProfileCodec.encode(state.copy(profiles = profiles, selectedId = selected)))
    }
}

/** Exact parent membership: selecting a folder never implicitly selects its children. */
object PhotoProfilePolicy {
    fun includes(path: String, folders: Set<String>, favorites: Set<String>, includeFavorites: Boolean, authorized: (String) -> Boolean = { true }): Boolean =
        authorized(path) && (path.substringBeforeLast('/', "") in folders || (includeFavorites && path in favorites))

    fun <T> unique(items: List<T>, path: (T) -> String): List<T> = items.distinctBy(path)
}
