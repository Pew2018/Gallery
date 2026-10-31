package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** Cached metadata is provisional; every newly published batch has fresh access evidence. */
class PhotoProfileMediaLoader(
    private val context: Context,
    private val cancellationFetcher: MediaFetcher = MediaFetcher(context),
    private val sessionId: String? = null,
    private val cancelled: () -> Boolean = { false }
) {
    companion object {
        const val PATH = "__photo_profiles__"
        const val EXTRA_ID = "org.fossify.gallery.PHOTO_PROFILE_ID"
        val MEDIA_TYPES = TYPE_VIDEOS or TYPE_IMAGES or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS or TYPE_PORTRAITS
    }
    private fun stopped() = cancelled() || cancellationFetcher.shouldStop || Thread.currentThread().isInterrupted
    private fun checkCurrent(request: PhotoProfileLoadContext) {
        if (stopped() || !request.isCurrent(context) ||
            (request.scope.sources.any { it.type == PhotoSourceType.FAVORITES } &&
                request.favorites != context.getFavoritePaths().toSet())) throw PhotoLoadInvalidatedException()
    }

    /** Legacy final-only entry used by the viewer. */
    fun load(profileId: String?): ArrayList<ThumbnailItem> =
        ArrayList(loadStaged(profileId, allowPreview = false).media)

    fun loadStaged(
        profileId: String?,
        allowPreview: Boolean,
        anchorPath: String? = null,
        onStage: (PhotoLoadResult) -> Unit = {}
    ): PhotoLoadResult {
        val request = PhotoProfileLoadContext.capture(context, profileId, sessionId) ?: throw PhotoLoadInvalidatedException()
        val scope = request.scope
        PhotoProfileMediaCache.initialize(context.applicationContext)
        val fetcher = MediaFetcher(context, scope.filter and MEDIA_TYPES, ::stopped)
        val folders = scope.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }
            .filter { !it.contains("/.") && !FavoriteFolderRules.isExcluded(it, scope.excluded) }.toSet()
        val favoriteFiles = if (scope.sources.any { it.type == PhotoSourceType.FAVORITES } &&
            PhotoProfileAccessSessionManager.favoritesAuthorized(context, sessionId)) request.favorites.filter {
            it.substringBeforeLast('/') !in folders
        }.toSet() else emptySet()

        val randomRanks = HashMap<String, Int>()
        fun sort(media: ArrayList<Medium>) {
            if (scope.sorting and SORT_BY_RANDOM != 0) {
                media.forEach { randomRanks.getOrPut(it.path) { java.util.concurrent.ThreadLocalRandom.current().nextInt() } }
                media.sortWith(compareBy<Medium> { randomRanks[it.path] }.thenBy { it.path })
            } else fetcher.sortMedia(media, scope.sorting)
        }
        fun result(stage: PhotoLoadStage, media: List<Medium>): PhotoLoadResult {
            checkCurrent(request)
            val (safe, access) = PhotoAccessValidationBatch(context, request, ::stopped).validate(media)
            safe.forEach { if (it.taken <= 0L) it.taken = it.modified }
            val sorting = GalleryPerfTrace.begin()
            sort(safe)
            GalleryPerfTrace.end("photo_sort", sorting, safe.size)
            checkCurrent(request)
            return PhotoLoadResult(request, stage, safe, access)
        }

        var preview = emptyList<Medium>()
        if (allowPreview) {
            val started = GalleryPerfTrace.begin()
            val cached = PhotoProfileMediaCache.read(request) ?: runCatching {
                readDatabaseCache(folders, favoriteFiles)
            }.getOrDefault(emptyList())
            GalleryPerfTrace.end("photo_cache", started, cached.size)
            val sorted = ArrayList(cached)
            sort(sorted)
            preview = PhotoProfileMediaCache.preview(sorted, anchorPath)
            if (preview.isNotEmpty()) {
                val validated = result(PhotoLoadStage.CACHE_PREVIEW, preview)
                preview = validated.media
                if (preview.isNotEmpty()) onStage(validated)
            }
        }
        checkCurrent(request)
        val query = GalleryPerfTrace.begin()
        val indexed = fetcher.queryProfileIndexedMedia(folders, favoriteFiles, ArrayList(request.favorites))
        GalleryPerfTrace.end("photo_index", query, indexed.mediaByPath.size)
        if (allowPreview && indexed.mediaByPath.isNotEmpty()) {
            // Keep freshly validated preview-only files until filesystem reconciliation completes.
            // Full-access mode also needs a cheap existence check before showing stale indexed rows.
            val sortedIndex = ArrayList(indexed.mediaByPath.values)
            sort(sortedIndex)
            val indexedCandidates = PhotoProfileMediaCache.preview(sortedIndex, anchorPath).filter {
                (!scope.allFiles && android.os.Build.VERSION.SDK_INT >= 30) || java.io.File(it.path).isFile
            }
            val merged = LinkedHashMap<String, Medium>()
            preview.forEach { merged[it.path] = it }
            indexedCandidates.forEach { merged[it.path] = it }
            val stage = result(PhotoLoadStage.INDEXED, ArrayList(merged.values))
            if (stage.media.isNotEmpty()) onStage(stage)
        }
        checkCurrent(request)
        val scanning = GalleryPerfTrace.begin()
        val media = fetcher.scanProfileFileSupplements(folders, favoriteFiles, ArrayList(request.favorites), indexed)
        GalleryPerfTrace.end("photo_supplement", scanning, media.size)
        val complete = result(PhotoLoadStage.COMPLETE, media)
        checkCurrent(request)
        if (complete.access.publicOnly && scope.sessionId == null) {
            PhotoProfileMediaCache.offer(complete)
            // Public metadata only, never persist an authorization or a partial result.
            runCatching {
                checkCurrent(request)
                context.mediaDB.insertAll(complete.media.filter { it.deletedTS == 0L })
            }.onFailure { if (it is PhotoLoadInvalidatedException) throw it }
        }
        checkCurrent(request)
        return complete
    }

    private fun readDatabaseCache(folders: Set<String>, files: Set<String>): List<Medium> {
        val media = LinkedHashMap<String, Medium>()
        folders.toList().chunked(400).forEach { batch ->
            if (stopped()) throw PhotoLoadInvalidatedException()
            context.mediaDB.getCachedMediaFromFolders(batch).forEach { media[it.path] = it }
        }
        files.toList().chunked(400).forEach { batch ->
            if (stopped()) throw PhotoLoadInvalidatedException()
            context.mediaDB.getCachedMediaFromFiles(batch).forEach { media[it.path] = it }
        }
        return ArrayList(media.values)
    }
}
