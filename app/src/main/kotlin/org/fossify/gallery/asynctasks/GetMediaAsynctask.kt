package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

class GetMediaAsynctask(
    val context: Context, val mPath: String, val isPickImage: Boolean = false, val isPickVideo: Boolean = false,
    val showAll: Boolean, val onFailure: (Exception) -> Unit = {},
    private val favoritePathsOverride: ArrayList<String>? = null,
    private val favoriteSessionId: String? = null,
    private val photoProfileId: String? = null,
    private val photoSessionId: String? = null,
    val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context)
    private var scanFailure: Exception? = null
    private var profilePreferences: Map<String, *>? = null

    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        return try {
        if (mPath == PhotoProfileMediaLoader.PATH || photoProfileId != null) {
            profilePreferences = context.getSharedPrefs().all
            return PhotoProfileMediaLoader(context, mediaFetcher, photoSessionId) { isCancelled }.load(photoProfileId)
        }
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val folderGrouping = context.config.getFolderGrouping(pathToUse)
        val folderSorting = context.config.getFolderSorting(pathToUse)
        val getProperDateTaken = folderSorting and SORT_BY_DATE_TAKEN != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

        val getProperLastModified = folderSorting and SORT_BY_DATE_MODIFIED != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

        val getProperFileSize = folderSorting and SORT_BY_SIZE != 0
        val savedFavorites = context.getFavoritePaths()
        val favoritePaths = if (mPath == FAVORITES) {
            val allowed = if (favoriteSessionId != null) {
                FavoriteAccessSessionManager.validatePaths(favoriteSessionId, savedFavorites)?.paths.orEmpty()
            } else {
                runCatching {
                    FavoriteAccessPolicy.unprotectedPaths(
                        FavoriteAccessSessionManager.captureSnapshot(savedFavorites, context::favoriteProtection)
                    )
                }.getOrDefault(emptySet())
            }
            (favoritePathsOverride ?: savedFavorites).filter { it in allowed }.toCollection(ArrayList())
        } else favoritePathsOverride ?: savedFavorites
        val getVideoDurations = context.config.showThumbnailVideoDuration
        val lastModifieds = if (getProperLastModified) mediaFetcher.getLastModifieds() else HashMap()
        val dateTakens = if (getProperDateTaken) mediaFetcher.getDateTakens() else HashMap()

        val media = if (showAll) {
            val foldersToScan = mediaFetcher.getFoldersToScan().filter { it != RECYCLE_BIN && it != FAVORITES && !context.config.isFolderProtected(it) }
            val media = ArrayList<Medium>()
            // Scoped-storage MediaStore can be queried just once for all folders.
            // Reusing the grouped result avoids O(folderCount) full-store queries.
            val scopedFiles = mediaFetcher.getAndroid11FolderMedia(
                isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens
            )
            foldersToScan.forEach {
                if (isCancelled || mediaFetcher.shouldStop) return@forEach
                val newMedia = mediaFetcher.getFilesFrom(
                    it, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens, scopedFiles
                )
                media.addAll(newMedia)
            }

            mediaFetcher.sortMedia(media, context.config.getFolderSorting(SHOW_ALL))
            media
        } else {
            mediaFetcher.getFilesFrom(
                mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                getVideoDurations, lastModifieds, dateTakens, null
            )
        }

        val result = mediaFetcher.groupMedia(media, pathToUse)
        val safeResult = if (mPath == FAVORITES && favoriteSessionId != null) {
            // Revalidate the complete captured snapshot once after scanning,
            // then use the returned capability set for a pure in-memory filter.
            val currentFavorites = context.getFavoritePaths().toSet()
            val allowed = FavoriteAccessSessionManager.retainFavorites(favoriteSessionId, currentFavorites).orEmpty()
            if (allowed.isEmpty()) ArrayList() else ArrayList(result.filter { item ->
                val path = (item as? Medium)?.path
                path == null || path in allowed
            })
        } else result
        if (mPath == FAVORITES && context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.util.Log.d("GalleryFavorites", "savedPaths=${favoritePaths.size}, scannedMedia=${safeResult.count { it is Medium }}")
        }
        safeResult
        } catch (error: Exception) {
            scanFailure = error
            ArrayList()
        }
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        if (!isCancelled) {
            val error = scanFailure
            if (error == null) {
                callback(if (profilePreferences != null && (profilePreferences != context.getSharedPrefs().all ||
                    (photoSessionId != null && !PhotoProfileAccessSessionManager.isActive(context, photoSessionId)))) arrayListOf() else media)
            } else onFailure(error)
        }
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}

