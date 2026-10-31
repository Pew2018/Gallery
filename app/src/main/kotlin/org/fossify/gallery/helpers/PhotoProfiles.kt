package org.fossify.gallery.helpers

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.fossify.gallery.models.Directory
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

/** Keep the picker at two columns in a normal phone dialog, but allow a single
 * column when the actual dialog content is too narrow to keep cards legible. */
internal fun photoSourceColumnCount(widthDp: Int): Int = when {
    widthDp < 300 -> 1
    widthDp < 520 -> 2
    widthDp < 760 -> 3
    else -> 4
}

/** Dialog-local source selection; callers persist only an explicit confirmed snapshot. */
class PhotoSourceSelectionDraft(initial: Collection<String>) {
    private val resolvedIdsByLexicalPath = HashMap<String, String>()
    val paths: MutableSet<String> = initial.mapNotNull(::stableId).toCollection(linkedSetOf())

    fun canSelect(path: String): Boolean = stableId(path) != null
    fun contains(path: String): Boolean = stableId(path)?.let(paths::contains) == true
    internal fun identity(path: String): String? = stableId(path)
    /** Select idempotently; adding an already selected source never toggles it off. */
    fun select(path: String): Boolean {
        val id = stableId(path) ?: return false
        return paths.add(id)
    }
    fun toggle(path: String) {
        val id = stableId(path) ?: return
        if (!paths.add(id)) paths.remove(id)
    }
    fun snapshot(): Set<String> = paths.toSet()

    /** Install identities resolved in the background; this mutation stays with the UI-owned draft.
     * Selection operations afterwards are in-memory lookups only. */
    fun installResolvedIdentities(identities: Map<String, String>) {
        val current = paths.toList()
        resolvedIdsByLexicalPath.clear()
        resolvedIdsByLexicalPath.putAll(identities)
        paths.clear()
        current.forEach { id -> paths.add(resolvedIdsByLexicalPath[id] ?: id) }
    }

    private fun stableId(path: String): String? = when {
        path == org.fossify.commons.helpers.FAVORITES -> path
        path.startsWith('/') -> pathKey(path).let { resolvedIdsByLexicalPath[it] ?: it }
            .takeIf { it.isNotEmpty() && it != "/" }
        else -> null
    }

    private fun pathKey(path: String): String {
        val segments = ArrayDeque<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return "/" + segments.joinToString("/")
    }
}

/** Build path aliases in the background once per picker candidate batch. */
internal data class PhotoSourceIdentityBatch(
    val identityByLexicalPath: Map<String, String>,
    val unresolvedLexicalPaths: Set<String>
)

internal fun resolvePhotoSourceIdentityBatch(paths: Collection<String>): PhotoSourceIdentityBatch {
    val result = LinkedHashMap<String, String>()
    val canonicalByLexicalPath = HashMap<String, String>()
    val unresolved = HashSet<String>()
    paths.filter { it.startsWith('/') }.forEach { path ->
        val lexical = normalizePhotoSourcePath(path)
        val canonical = canonicalByLexicalPath.getOrPut(lexical) {
            runCatching { java.io.File(path).canonicalPath }.getOrElse {
                unresolved.add(lexical)
                lexical
            }
        }
        result[lexical] = canonical
        result[normalizePhotoSourcePath(canonical)] = canonical
    }
    return PhotoSourceIdentityBatch(result, unresolved)
}

internal fun resolvePhotoSourceIdentities(paths: Collection<String>): Map<String, String> =
    resolvePhotoSourceIdentityBatch(paths).identityByLexicalPath

internal fun photoSourceResolvedIdentity(path: String, identities: Map<String, String>): String =
    identities[normalizePhotoSourcePath(path)] ?: normalizePhotoSourcePath(path)

internal fun normalizePhotoSourcePath(path: String): String {
    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return "/" + segments.joinToString("/")
}

internal data class PhotoSourceCandidateBatch(
    val directories: ArrayList<Directory>,
    val identityByPath: Map<String, String>,
    val protectedPaths: Set<String>
)

/** Canonicalize candidates and compute their protected state once, on a worker. */
internal fun preparePhotoSourceCandidateBatch(
    indexed: Collection<Directory>,
    manual: Collection<Directory>,
    retainedPaths: Collection<String>,
    favoritesName: String,
    favoritesProtected: Boolean,
    isProtected: (String) -> Boolean
): PhotoSourceCandidateBatch {
    val identityPaths = buildList {
        indexed.forEach { add(it.path); add(it.tmb) }
        manual.forEach { add(it.path); add(it.tmb) }
        addAll(retainedPaths)
    }
    val identityBatch = resolvePhotoSourceIdentityBatch(identityPaths)
    val identities = identityBatch.identityByLexicalPath
    val candidates = mergePhotoSourceCandidates(indexed, manual, retainedPaths, favoritesName, identities)
    val protectedPathCache = HashMap<String, Boolean>()
    fun isProtectedCached(path: String) = protectedPathCache.getOrPut(path) {
        runCatching { isProtected(path) }.getOrDefault(true)
    }
    fun protected(path: String): Boolean {
        if (hasProtectedFolderBoundary(path, ::isProtectedCached)) return true
        if (!path.startsWith('/')) return false
        val lexical = normalizePhotoSourcePath(path)
        if (lexical in identityBatch.unresolvedLexicalPaths) return true
        val canonical = identities[lexical] ?: return true
        return hasProtectedFolderBoundary(canonical, ::isProtectedCached)
    }
    val protectedPaths = candidates.filter { directory ->
        (directory.path == org.fossify.commons.helpers.FAVORITES && favoritesProtected) ||
            protected(directory.path) || (directory.tmb.isNotBlank() && protected(directory.tmb))
    }.mapTo(hashSetOf()) { it.path }
    return PhotoSourceCandidateBatch(candidates, identities, protectedPaths)
}

/** Keep every independently selectable indexed folder, including nested folders and Favorites. */
internal fun photoSourceCandidates(directories: Collection<Directory>, draft: PhotoSourceSelectionDraft): ArrayList<Directory> =
    directories.filter { draft.canSelect(it.path) }
        .distinctBy { draft.identity(it.path) }
        .toCollection(ArrayList())

/** Merge the current scan with dialog-local additions and persisted, currently unindexed sources. */
internal fun mergePhotoSourceCandidates(
    indexed: Collection<Directory>,
    manual: Collection<Directory>,
    retainedPaths: Collection<String>,
    favoritesName: String
): ArrayList<Directory> = mergePhotoSourceCandidates(
    indexed, manual, retainedPaths, favoritesName,
    resolvePhotoSourceIdentities(indexed.flatMap { listOf(it.path, it.tmb) } + manual.flatMap { listOf(it.path, it.tmb) } + retainedPaths)
)

internal fun mergePhotoSourceCandidates(
    indexed: Collection<Directory>,
    manual: Collection<Directory>,
    retainedPaths: Collection<String>,
    favoritesName: String,
    identities: Map<String, String>
): ArrayList<Directory> {
    val byPath = LinkedHashMap<String, Directory>()
    fun key(path: String) = photoSourceResolvedIdentity(path, identities)
    indexed.forEach { byPath[key(it.path)] = it }
    manual.forEach { byPath.putIfAbsent(key(it.path), it) }
    retainedPaths.forEach { path ->
        if (path == org.fossify.commons.helpers.FAVORITES) {
            byPath.putIfAbsent(path, Directory().apply { this.path = path; name = favoritesName })
        } else if (path.startsWith('/')) {
            byPath.putIfAbsent(key(path), Directory().apply {
                this.path = key(path)
                name = java.io.File(this.path).name.ifBlank { this.path }
                // A negative count means the folder is a candidate but is not indexed.
                subfoldersMediaCount = -1
            })
        }
    }
    return ArrayList(byPath.values)
}

internal fun photoSourceSearchMatches(directory: Directory, query: String): Boolean =
    query.isEmpty() || directory.name.contains(query, ignoreCase = true) || directory.path.contains(query, ignoreCase = true)

/** Each source status scan may publish only while it is still the newest request. */
internal class PhotoSourceStatusRequests {
    data class Request internal constructor(val id: Long, val sources: List<PhotoSource>, val stateVersion: Long, val resumeVersion: Long)
    private var version = 0L
    private var active: Request? = null
    private var lastCompleted: Request? = null

    /** Coalesces the same snapshot during one resume cycle, including just-completed work. */
    @Synchronized fun begin(sources: List<PhotoSource>, stateVersion: Long, resumeVersion: Long): Request? {
        val snapshot = sources.toList()
        active?.takeIf { it.sources == snapshot && it.stateVersion == stateVersion && it.resumeVersion == resumeVersion }?.let { return null }
        lastCompleted?.takeIf { it.sources == snapshot && it.stateVersion == stateVersion && it.resumeVersion == resumeVersion }?.let { return null }
        return Request(++version, snapshot, stateVersion, resumeVersion).also { active = it }
    }

    @Synchronized fun isCurrent(request: Request): Boolean = active == request
    @Synchronized fun finish(request: Request) {
        if (active == request) {
            active = null
            lastCompleted = request
        }
    }
    @Synchronized fun invalidate() { version++; active = null; lastCompleted = null }
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
    fun updateExisting(profile: PhotoProfile) {
        val clean = profile.copy(name = profile.name.trim(), sources = PhotoProfileCodec.sources(profile.sources))
        UUID.fromString(clean.id)
        require(clean.name.isNotEmpty() && clean.sources.isNotEmpty())
        val state = state()
        require(state.profiles.any { it.id == clean.id }) { "Profile no longer exists" }
        val profiles = state.profiles.map { if (it.id == clean.id) clean else it }
        // Editing metadata or sources must never implicitly activate this profile.
        write(PhotoProfileCodec.encode(state.copy(profiles = profiles)))
    }
    fun delete(id: String) {
        val state = state()
        val profiles = state.profiles.filter { it.id != id }
        val selected = state.selectedId?.takeIf { selected -> profiles.any { it.id == selected } } ?: profiles.firstOrNull()?.id
        write(PhotoProfileCodec.encode(state.copy(profiles = profiles, selectedId = selected)))
    }
}

internal data class PhotoProfileStateChanges(
    val changed: Boolean,
    val reloadMedia: Boolean,
    val refreshCurrentDisplay: Boolean
)

/** Classify a management session's persisted changes for its caller. */
internal fun photoProfileStateChanges(before: PhotoProfileState, after: PhotoProfileState): PhotoProfileStateChanges {
    val oldCurrent = before.current
    val newCurrent = after.current
    val selectionChanged = before.selectedId != after.selectedId || oldCurrent?.id != newCurrent?.id
    val currentSourcesChanged = oldCurrent?.id == newCurrent?.id && oldCurrent?.sources != newCurrent?.sources
    val currentNameChanged = oldCurrent?.id == newCurrent?.id && oldCurrent?.name != newCurrent?.name
    return PhotoProfileStateChanges(
        changed = before != after,
        reloadMedia = selectionChanged || currentSourcesChanged,
        refreshCurrentDisplay = selectionChanged || currentNameChanged
    )
}

/** Exact parent membership: selecting a folder never implicitly selects its children. */
object PhotoProfilePolicy {
    fun includes(path: String, folders: Set<String>, favorites: Set<String>, includeFavorites: Boolean, authorized: (String) -> Boolean = { true }): Boolean =
        authorized(path) && (path.substringBeforeLast('/', "") in folders || (includeFavorites && path in favorites))

    fun <T> unique(items: List<T>, path: (T) -> String): List<T> = items.distinctBy(path)
}
