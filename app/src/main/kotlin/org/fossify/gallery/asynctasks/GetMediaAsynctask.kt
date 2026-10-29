package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
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
    val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context)
    private var scanFailure: Exception? = null

    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        return try {
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
                FavoriteAccessSessionManager.retainFavorites(favoriteSessionId, savedFavorites.toSet()).orEmpty()
            } else {
                savedFavorites.filter { path ->
                    FavoriteAccessSessionManager.captureSnapshot(listOf(path), context::favoriteProtection)
                        .entries.values.all { it.locks.isEmpty() }
                }.toSet()
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
        if (mPath == FAVORITES && context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.util.Log.d("GalleryFavorites", "savedPaths=${favoritePaths.size}, scannedMedia=${result.count { it is Medium }}")
        }
        result
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
                val safe = if (mPath == FAVORITES && favoriteSessionId != null) {
                    FavoriteAccessPolicy.authorizedItems(favoriteSessionId, media) { (it as? Medium)?.path }
                } else media
                callback(safe)
            } else onFailure(error)
        }
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}

