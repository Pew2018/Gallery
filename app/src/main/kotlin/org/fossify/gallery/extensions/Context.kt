package org.fossify.gallery.extensions

import org.fossify.gallery.helpers.FavoriteAccessSessionManager

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Process
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.webp.WebpBitmapFactory
import com.bumptech.glide.integration.webp.decoder.WebpDownsampler
import com.bumptech.glide.integration.webp.decoder.WebpDrawable
import com.bumptech.glide.integration.webp.decoder.WebpDrawableTransformation
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.squareup.picasso.Picasso
import org.fossify.commons.extensions.doesThisOrParentHaveNoMedia
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getMimeTypeFromUri
import org.fossify.commons.extensions.getOTGPublicPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.isPng
import org.fossify.commons.extensions.isPortrait
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isWebP
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.otgPath
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.sumByLong
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.R
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.IsoTypeReader
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.LOCATION_OTG
import org.fossify.gallery.helpers.LOCATION_SD
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.MediaCachePrunePolicy
import org.fossify.gallery.helpers.MyWidgetProvider
import org.fossify.gallery.helpers.PicassoRoundedCornersTransformation
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.THUMBNAIL_FADE_DURATION_MS
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.FavoriteMediaLoadPolicy
import org.fossify.gallery.interfaces.DateTakensDao
import org.fossify.gallery.interfaces.DirectoryDao
import org.fossify.gallery.interfaces.FavoriteFoldersDao
import org.fossify.gallery.interfaces.FavoritesDao
import org.fossify.gallery.interfaces.MediumDao
import org.fossify.gallery.interfaces.WidgetsDao
import org.fossify.gallery.models.AlbumCover
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.svg.SvgSoftwareLayerSetter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

val Context.audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

fun Context.getHumanizedFilename(path: String): String {
    val humanized = humanizePath(path)
    return humanized.substring(humanized.lastIndexOf("/") + 1)
}

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.widgetsDB: WidgetsDao
    get() = GalleryDatabase.getInstance(applicationContext).WidgetsDao()

val Context.mediaDB: MediumDao get() = GalleryDatabase.getInstance(applicationContext).MediumDao()

val Context.directoryDB: DirectoryDao
    get() = GalleryDatabase.getInstance(applicationContext).DirectoryDao()

val Context.favoriteFoldersDB: FavoriteFoldersDao
    get() = GalleryDatabase.getInstance(applicationContext).FavoriteFoldersDao()

val Context.favoritesDB: FavoritesDao
    get() = GalleryDatabase.getInstance(applicationContext).FavoritesDao()

val Context.dateTakensDB: DateTakensDao
    get() = GalleryDatabase.getInstance(applicationContext).DateTakensDao()

val Context.recycleBin: File get() = filesDir

fun Context.movePinnedDirectoriesToFront(dirs: ArrayList<Directory>): ArrayList<Directory> {
    val pinnedFolders = config.pinnedFolders
    val favorites = dirs.filter { it.path == FAVORITES }
    val showRecycleBin = config.showRecycleBinAtFolders
    val recycleBin = if (showRecycleBin) dirs.filter { it.isRecycleBin() } else emptyList()
    val pinned = dirs.filter {
        it.path != FAVORITES &&
            (!showRecycleBin || !it.isRecycleBin()) &&
            pinnedFolders.contains(it.path)
    }
    val regular = dirs.filter {
        it.path != FAVORITES &&
            (!showRecycleBin || !it.isRecycleBin()) &&
            !pinnedFolders.contains(it.path)
    }

    dirs.clear()
    dirs.addAll(favorites)
    if (showRecycleBin && !config.showRecycleBinLast) dirs.addAll(recycleBin)
    dirs.addAll(pinned)
    dirs.addAll(regular)
    if (showRecycleBin && config.showRecycleBinLast) dirs.addAll(recycleBin)
    return dirs
}

fun Context.getSortedDirectories(
    source: ArrayList<Directory>,
    randomOrder: MutableMap<String, Long>? = null
): ArrayList<Directory> {
    return org.fossify.gallery.helpers.HomeDirectorySorter.sort(source.map { it.snapshot() },
        org.fossify.gallery.helpers.HomeDirectorySorter.Options(
            config.directorySorting, config.customFoldersOrder, config.pinnedFolders.toSet(),
            config.showRecycleBinAtFolders, config.showRecycleBinLast, randomOrder ?: hashMapOf()
        )).directories
}

fun Context.getDirsToShow(
    dirs: ArrayList<Directory>,
    allDirs: ArrayList<Directory>,
    currentPathPrefix: String,
    randomOrder: MutableMap<String, Long>? = null,
    sortDirs: Boolean = true,
    groupDirectSubfolders: Boolean = config.groupDirectSubfolders,
    directorySorter: ((ArrayList<Directory>) -> ArrayList<Directory>)? = null
): ArrayList<Directory> {
    val snapshots = dirs.mapTo(ArrayList()) { it.snapshot() }
    return if (groupDirectSubfolders) {
        snapshots.forEach {
            it.subfoldersCount = 0
            it.subfoldersMediaCount = it.mediaCnt
        }

        val filledDirs = fillWithSharedDirectParents(snapshots)
        val parentDirs = getDirectParentSubfolders(filledDirs, currentPathPrefix)
        updateSubfolderCounts(filledDirs, parentDirs)

        // show the current folder as an available option too, not just subfolders
        if (currentPathPrefix.isNotEmpty()) {
            val currentFolder = allDirs.firstOrNull { directory ->
                directory.path == currentPathPrefix && directory.containsMediaFilesDirectly && directory.mediaCnt > 0 &&
                    parentDirs.none { it.path == currentPathPrefix }
            }

            currentFolder?.snapshot()?.apply {
                subfoldersCount = 1
                subfoldersMediaCount = mediaCnt
                parentDirs.add(this)
            }
        }

        if (sortDirs) directorySorter?.invoke(parentDirs) ?: getSortedDirectories(parentDirs, randomOrder)
        else parentDirs
    } else {
        snapshots.forEach { it.subfoldersMediaCount = it.mediaCnt }
        snapshots
    }
}

private fun Context.groupParent(path: String, child: Directory) = Directory().apply {
    this.path = path
    name = getFolderNameFromPath(path)
    tmb = child.tmb
    location = getPathLocation(path)
    containsMediaFilesDirectly = false
}

fun Context.fillWithSharedDirectParents(dirs: ArrayList<Directory>): ArrayList<Directory> =
    org.fossify.gallery.helpers.DirectoryGrouping.fill(dirs, ::groupParent)

fun Context.getDirectParentSubfolders(dirs: ArrayList<Directory>, currentPathPrefix: String): ArrayList<Directory> =
    org.fossify.gallery.helpers.DirectoryGrouping.visible(dirs, currentPathPrefix, ::groupParent)

fun Context.updateSubfolderCounts(children: ArrayList<Directory>, parentDirs: ArrayList<Directory>) {
    org.fossify.gallery.helpers.DirectoryGrouping.aggregate(children, parentDirs, !config.directorySorting.isSortingAscending())
}

fun Context.getNoMediaFolders(callback: (folders: ArrayList<String>) -> Unit) {
    ensureBackgroundThread {
        callback(getNoMediaFoldersSync())
    }
}

fun Context.getNoMediaFoldersSync(knownFolders: Collection<String> = emptyList(),
    cancellationSignal: android.os.CancellationSignal? = null): ArrayList<String> {
    val folders = ArrayList<String>()

    val uri = Files.getContentUri("external")
    val projection = arrayOf(Files.FileColumns.DATA)
    val selection = "${Files.FileColumns.MEDIA_TYPE} = ? AND ${Files.FileColumns.TITLE} LIKE ?"
    val selectionArgs = arrayOf(Files.FileColumns.MEDIA_TYPE_NONE.toString(), "%$NOMEDIA%")
    val sortOrder = "${Files.FileColumns.DATE_MODIFIED} DESC"
    val OTGPath = config.OTGPath

    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder, cancellationSignal)
        if (cursor?.moveToFirst() == true) {
            do {
                cancellationSignal?.throwIfCanceled()
                val path = cursor.getStringValue(Files.FileColumns.DATA) ?: continue
                val noMediaFile = File(path)
                if (
                    getDoesFilePathExist(noMediaFile.absolutePath, OTGPath)
                    && noMediaFile.name == NOMEDIA && !noMediaFile.isDirectory
                ) {
                    folders.add(noMediaFile.parent)
                }
            } while (cursor.moveToNext())
        }
    } catch (error: android.os.OperationCanceledException) {
        throw error
    } catch (ignored: Exception) {
    } finally {
        cursor?.close()
    }

    val snapshot = org.fossify.gallery.helpers.NoMediaSnapshot()
    (knownFolders + config.everShownFolders + config.includedFolders).forEach { path ->
        cancellationSignal?.throwIfCanceled()
        if (path.startsWith('/')) folders.addAll(snapshot.markerFolders(path))
    }
    return folders.distinct().toCollection(ArrayList())
}

fun Context.rescanFolderMedia(path: String) {
    ensureBackgroundThread {
        rescanFolderMediaSync(path)
    }
}

fun Context.rescanFolderMediaSync(path: String) {
    getCachedMedia(path) { cached ->
        GetMediaAsynctask(
            context = applicationContext,
            mPath = path,
            isPickImage = false,
            isPickVideo = false,
            showAll = false
        ) { newMedia ->
            ensureBackgroundThread {
                val media = newMedia.filterIsInstance<Medium>() as ArrayList<Medium>
                try {
                    mediaDB.insertAll(media)

                    cached.forEach { thumbnailItem ->
                        if (!newMedia.contains(thumbnailItem)) {
                            val mediumPath = (thumbnailItem as? Medium)?.path
                            if (mediumPath != null) {
                                deleteDBPath(mediumPath)
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }.execute()
    }
}


fun Context.checkAppendingHidden(
    path: String,
    hidden: String,
    includedFolders: MutableSet<String>,
    noMediaFolders: ArrayList<String>
): String {
    val dirName = getFolderNameFromPath(path)
    val folderNoMediaStatuses = HashMap<String, Boolean>()
    noMediaFolders.forEach { folder ->
        folderNoMediaStatuses["$folder/$NOMEDIA"] = true
    }

    return if (
        path.doesThisOrParentHaveNoMedia(folderNoMediaStatuses, null)
        && !path.isThisOrParentIncluded(includedFolders)
    ) {
        "$dirName $hidden"
    } else {
        dirName
    }
}

fun Context.getFolderNameFromPath(path: String): String {
    return when (path) {
        internalStoragePath -> getString(org.fossify.commons.R.string.internal)
        sdCardPath -> getString(org.fossify.commons.R.string.sd_card)
        otgPath -> getString(org.fossify.commons.R.string.usb)
        FAVORITES -> getString(org.fossify.commons.R.string.favorites)
        RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
        else -> path.getFilenameFromPath()
    }
}

fun Context.loadImage(
    type: Int,
    path: String,
    target: MySquareImageView,
    horizontalScroll: Boolean,
    animateGifs: Boolean,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    skipMemoryCacheAtPaths: ArrayList<String>? = null,
    onError: (() -> Unit)? = null,
    onLoaded: (() -> Unit)? = null,
    crossFadeDuration: Int = THUMBNAIL_FADE_DURATION_MS
) {
    target.isHorizontalScrolling = horizontalScroll
    if (type == TYPE_SVGS) {
        loadSVG(
            path = path,
            target = target,
            cropThumbnails = cropThumbnails,
            roundCorners = roundCorners,
            signature = signature,
            crossFadeDuration = crossFadeDuration,
            onLoaded = onLoaded
        )
    } else {
        loadImageBase(
            path = path,
            target = target,
            cropThumbnails = cropThumbnails,
            roundCorners = roundCorners,
            signature = signature,
            skipMemoryCacheAtPaths = skipMemoryCacheAtPaths,
            animate = animateGifs,
            tryLoadingWithPicasso = type == TYPE_IMAGES && path.isPng(),
            onError = onError,
            onLoaded = onLoaded,
            crossFadeDuration = crossFadeDuration
        )
    }
}

fun Context.addTempFolderIfNeeded(dirs: ArrayList<Directory>): ArrayList<Directory> {
    val tempFolderPath = config.tempFolderPath
    return if (tempFolderPath.isNotEmpty()) {
        val directories = ArrayList<Directory>()
        val newFolder = Directory(
            id = null,
            path = tempFolderPath,
            tmb = "",
            name = tempFolderPath.getFilenameFromPath(),
            mediaCnt = 0,
            modified = 0,
            taken = 0,
            size = 0L,
            location = getPathLocation(tempFolderPath),
            types = 0,
            sortValue = ""
        )
        directories.add(newFolder)
        directories.addAll(dirs)
        directories
    } else {
        dirs
    }
}

fun Context.getPathLocation(path: String): Int {
    return when {
        isPathOnSD(path) -> LOCATION_SD
        isPathOnOTG(path) -> LOCATION_OTG
        else -> LOCATION_INTERNAL
    }
}

@SuppressLint("CheckResult")
fun Context.loadImageBase(
    path: String,
    target: MySquareImageView,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    skipMemoryCacheAtPaths: ArrayList<String>? = null,
    animate: Boolean = false,
    tryLoadingWithPicasso: Boolean = false,
    crossFadeDuration: Int = THUMBNAIL_FADE_DURATION_MS,
    onError: (() -> Unit)? = null,
    onLoaded: (() -> Unit)? = null
) {
    val bindingToken = target.getTag(R.id.thumbnail_request_key)
    val options = RequestOptions()
        .signature(signature)
        .skipMemoryCache(skipMemoryCacheAtPaths?.contains(path) == true)
        .priority(Priority.LOW)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .format(DecodeFormat.PREFER_ARGB_8888)
        .placeholder(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

    if (cropThumbnails) {
        options.optionalTransform(CenterCrop())
        options.optionalTransform(
            WebpDrawable::class.java,
            WebpDrawableTransformation(CenterCrop())
        )
    } else {
        options.optionalTransform(FitCenter())
        options.optionalTransform(WebpDrawable::class.java, WebpDrawableTransformation(FitCenter()))
    }

    // animation is only supported without rounded corners and the file must be a GIF or WEBP.
    if (animate && roundCorners == ROUNDED_CORNERS_NONE && (path.isGif() || path.isWebP())) {
        // this is required to make glide cache aware of changes
        options.decode(Drawable::class.java)
    } else {
        options.dontAnimate()
        // don't animate is not enough for webp files, decode as bitmap forces first frame use in animated webps
        options.decode(Bitmap::class.java)
    }

    if (roundCorners != ROUNDED_CORNERS_NONE) {
        val cornerSize =
            if (roundCorners == ROUNDED_CORNERS_SMALL) org.fossify.commons.R.dimen.rounded_corner_radius_small else org.fossify.commons.R.dimen.rounded_corner_radius_big
        val cornerRadius = resources.getDimension(cornerSize).toInt()
        val roundedCornersTransform = RoundedCorners(cornerRadius)
        options.optionalTransform(MultiTransformation(CenterCrop(), roundedCornersTransform))
        options.optionalTransform(
            WebpDrawable::class.java,
            MultiTransformation(
                WebpDrawableTransformation(CenterCrop()),
                WebpDrawableTransformation(roundedCornersTransform)
            )
        )
    }

    WebpBitmapFactory.sUseSystemDecoder = false // CVE-2023-4863
    var builder = Glide.with(target)
        .load(path)
        .apply(options)
        .set(WebpDownsampler.USE_SYSTEM_DECODER, false) // CVE-2023-4863
        .apply {
            if (crossFadeDuration > 0) transition(getOptionalCrossFadeTransition(crossFadeDuration)) else transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions().dontTransition())
        }

    builder = builder.listener(object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            targetBitmap: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            // A recycled ImageView must never receive a failed request's fallback.
            if (target.tag != path || target.getTag(R.id.thumbnail_request_key) != bindingToken) return true
            if (tryLoadingWithPicasso) {
                tryLoadingWithPicasso(path, target, cropThumbnails, roundCorners, signature, onError)
            } else {
                onError?.invoke()
            }

            return true
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            targetBitmap: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            if (target.tag == path && target.getTag(R.id.thumbnail_request_key) == bindingToken) {
                target.post {
                    if (target.tag == path && target.getTag(R.id.thumbnail_request_key) == bindingToken &&
                        target.drawable != null) onLoaded?.invoke()
                }
            }
            return false
        }
    })

    builder.into(target)
}

fun Context.loadSVG(
    path: String,
    target: MySquareImageView,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    crossFadeDuration: Int = THUMBNAIL_FADE_DURATION_MS,
    onLoaded: (() -> Unit)? = null,
) {
    target.scaleType = if (cropThumbnails) {
        ImageView.ScaleType.CENTER_CROP
    } else {
        ImageView.ScaleType.FIT_CENTER
    }

    val options = RequestOptions().signature(signature)
    val bindingToken = target.getTag(R.id.thumbnail_request_key)
    var builder = Glide.with(target)
        .`as`(PictureDrawable::class.java)
        .listener(SvgSoftwareLayerSetter())
        .load(path)
        .apply(options)
        .apply {
            if (crossFadeDuration > 0) transition(getOptionalCrossFadeTransition(crossFadeDuration)) else transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions().dontTransition())
        }

    if (roundCorners != ROUNDED_CORNERS_NONE) {
        val cornerSize = when (roundCorners) {
            ROUNDED_CORNERS_SMALL -> org.fossify.commons.R.dimen.rounded_corner_radius_small
            else -> org.fossify.commons.R.dimen.rounded_corner_radius_big
        }

        val cornerRadius = resources.getDimension(cornerSize).toInt()
        builder = builder.transform(CenterCrop(), RoundedCorners(cornerRadius))
    }

    builder.listener(object : RequestListener<PictureDrawable> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, targetBitmap: Target<PictureDrawable>, isFirstResource: Boolean
        ): Boolean = false

        override fun onResourceReady(
            resource: PictureDrawable, model: Any, targetBitmap: Target<PictureDrawable>,
            dataSource: DataSource, isFirstResource: Boolean
        ): Boolean {
            if (target.getTag(R.id.thumbnail_request_key) == bindingToken) {
                target.post {
                    if (target.getTag(R.id.thumbnail_request_key) == bindingToken && target.drawable != null) {
                        onLoaded?.invoke()
                    }
                }
            }
            return false
        }
    }).into(target)
}

// intended mostly for Android 11 issues, that fail loading PNG files bigger than 10 MB
fun Context.tryLoadingWithPicasso(
    path: String,
    view: MySquareImageView,
    cropThumbnails: Boolean,
    roundCorners: Int,
    signature: ObjectKey,
    onError: (() -> Unit)? = null
) {
    val bindingToken = view.getTag(R.id.thumbnail_request_key)
    var pathToLoad = "file://$path"
    pathToLoad = pathToLoad.replace("%", "%25").replace("#", "%23")

    try {
        var builder = Picasso.get()
            .load(pathToLoad)
            .stableKey(signature.toString())

        builder = if (cropThumbnails) {
            builder.centerCrop().fit()
        } else {
            builder.centerInside()
        }

        if (roundCorners != ROUNDED_CORNERS_NONE) {
            val cornerSize =
                if (roundCorners == ROUNDED_CORNERS_SMALL) org.fossify.commons.R.dimen.rounded_corner_radius_small else org.fossify.commons.R.dimen.rounded_corner_radius_big
            val cornerRadius = resources.getDimension(cornerSize).toInt()
            builder = builder.transform(PicassoRoundedCornersTransformation(cornerRadius.toFloat()))
        }

        // Picasso is a fallback: its own failure must terminate loading, and
        // an outdated callback must never replace a newer holder's image.
        builder.into(view, object : com.squareup.picasso.Callback {
            override fun onSuccess() = Unit
            override fun onError(e: Exception?) {
                if (view.tag == path && view.getTag(R.id.thumbnail_request_key) == bindingToken) onError?.invoke()
            }
        })
    } catch (e: Exception) {
        if (view.tag == path && view.getTag(R.id.thumbnail_request_key) == bindingToken) onError?.invoke()
    }
}

fun Context.getCachedDirectories(
    getVideosOnly: Boolean = false,
    getImagesOnly: Boolean = false,
    forceShowHidden: Boolean = false,
    forceShowExcluded: Boolean = false,
    onFailure: ((Exception) -> Unit)? = null,
    executor: java.util.concurrent.Executor? = null,
    isCurrent: () -> Boolean = { true },
    readCache: () -> List<Directory> = { directoryDB.getAll() },
    callback: (ArrayList<Directory>) -> Unit,
) {
    val load = Runnable {
        if (!isCurrent()) return@Runnable
        val result = try {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            } catch (ignored: Exception) {
            }

            val directories = try {
                ArrayList(readCache())
            } catch (e: Exception) {
                if (onFailure != null) throw e
                ArrayList()
            }

            if (!config.showRecycleBinAtFolders) {
                directories.removeAll { it.isRecycleBin() }
            }

            val shouldShowHidden = config.shouldShowHidden || forceShowHidden
            val excludedPaths = if (config.temporarilyShowExcluded || forceShowExcluded) {
                HashSet()
            } else {
                config.excludedFolders
            }

            val includedPaths = config.includedFolders

            val folderNoMediaStatuses = HashMap<String, Boolean>()
            val noMediaFolders = if (forceShowHidden) emptyList() else getNoMediaFoldersSync()
            noMediaFolders.forEach { folder ->
                folderNoMediaStatuses["$folder/$NOMEDIA"] = true
            }

            var filteredDirectories = directories.filter {
                it.path.shouldFolderBeVisible(
                    excludedPaths = excludedPaths,
                    includedPaths = includedPaths,
                    showHidden = shouldShowHidden,
                    folderNoMediaStatuses = folderNoMediaStatuses
                ) { path, hasNoMedia ->
                    folderNoMediaStatuses[path] = hasNoMedia
                }
            } as ArrayList<Directory>

            val filterMedia = config.filterMedia
            filteredDirectories = (when {
                getVideosOnly -> filteredDirectories.filter { it.types and TYPE_VIDEOS != 0 }
                getImagesOnly -> filteredDirectories.filter { it.types and TYPE_IMAGES != 0 }
                else -> filteredDirectories.filter {
                    (filterMedia and TYPE_IMAGES != 0 && it.types and TYPE_IMAGES != 0)
                            || (filterMedia and TYPE_VIDEOS != 0 && it.types and TYPE_VIDEOS != 0)
                            || (filterMedia and TYPE_GIFS != 0 && it.types and TYPE_GIFS != 0)
                            || (filterMedia and TYPE_RAWS != 0 && it.types and TYPE_RAWS != 0)
                            || (filterMedia and TYPE_SVGS != 0 && it.types and TYPE_SVGS != 0)
                            || (filterMedia and TYPE_PORTRAITS != 0 && it.types and TYPE_PORTRAITS != 0)
                }
            }) as ArrayList<Directory>

            if (shouldShowHidden) {
                val hiddenString = resources.getString(R.string.hidden)
                filteredDirectories.forEach {
                    val noMediaPath = "${it.path}/$NOMEDIA"
                    val hasNoMedia = if (folderNoMediaStatuses.keys.contains(noMediaPath)) {
                        folderNoMediaStatuses[noMediaPath]!!
                    } else {
                        it.path.doesThisOrParentHaveNoMedia(folderNoMediaStatuses) { path, hasNoMedia ->
                            val newPath = "$path/$NOMEDIA"
                            folderNoMediaStatuses[newPath] = hasNoMedia
                        }
                    }

                    it.name = if (hasNoMedia && !it.path.isThisOrParentIncluded(includedPaths)) {
                        "${it.name.removeSuffix(hiddenString).trim()} $hiddenString"
                    } else {
                        it.name.removeSuffix(hiddenString).trim()
                    }
                }
            }

            val clone = filteredDirectories.clone() as ArrayList<Directory>
            val loaded = ArrayList(clone.distinctBy { it.path.getDistinctPath() })
            loaded
        } catch (error: Exception) {
            android.util.Log.w("GalleryDirs", "Directory cache unavailable", error)
            if (onFailure != null) {
                if (isCurrent()) onFailure(error)
                return@Runnable
            }
            arrayListOf()
        }
        if (!isCurrent()) return@Runnable
        try { callback(result) } catch (error: Exception) {
            android.util.Log.e("GalleryDirs", "Directory cache consumer failed", error)
        }
        // A cache preview never authorizes database cleanup. The formal scan owns it.
    }
    if (executor != null) try { executor.execute(load) }
    catch (error: java.util.concurrent.RejectedExecutionException) {
        if (isCurrent()) onFailure?.invoke(error)
    } else ensureBackgroundThread { load.run() }

}

/** A bounded, provisional grid snapshot. Full cache callers keep their existing behavior. */
internal fun Context.getMediaPageCache(
    folder: String?, types: Int, target: Int, current: () -> Boolean,
    allowed: (String) -> Boolean
): ArrayList<Medium> {
    val visibility = org.fossify.gallery.helpers.MediaPageVisibility(this, folder == null, allowed)
    val accessByFolder = HashMap<String, () -> Boolean>()
    val preview = ArrayList<Medium>()
    val sorting = config.getFolderSorting(folder ?: SHOW_ALL)
    val rows = mediaDB.getPreviewMedia(folder, types, sorting and org.fossify.commons.helpers.SORT_DESCENDING == 0, target * 4)
    for (item in rows) {
        if (!current() || preview.size == target) break
        if (!visibility.allows(item.path)) continue
        val access = accessByFolder.getOrPut(item.parentPath) {
            org.fossify.gallery.helpers.FolderCachePreviewAccess.capture(this, item.parentPath)
        }
        if (!access()) continue
        if (!visibility.allowsIndexedFile(item.path)) continue
        preview.add(item)
    }
    // This orders the verified sample. The scan establishes global rank.
    MediaFetcher(this).sortMedia(preview, sorting)
    return preview
}

fun Context.getCachedMedia(
    path: String,
    getVideosOnly: Boolean = false,
    getImagesOnly: Boolean = false,
    allowedFavoritePaths: Set<String>? = null,
    callback: (ArrayList<ThumbnailItem>) -> Unit
) {
    if (path.isEmpty()) {
        // Display cached media without first doing an expensive MediaStore folder
        // enumeration or one Room SELECT per folder. The normal background
        // media refresh will reconcile newly added or removed files afterward.
        getCachedDirectories(getVideosOnly, getImagesOnly, onFailure = { error ->
            // Failed cache reads must still start an authoritative source scan.
            android.util.Log.w("GalleryMedia", "Root cache unavailable; scanning sources", error)
            callback(arrayListOf())
        }) { directories ->
            ensureBackgroundThread {
                val allowed = directories.asSequence().map { it.path.lowercase(Locale.ROOT) }.toHashSet()
                val fetcher = MediaFetcher(this)
                val filter = config.filterMedia
                val showHidden = config.shouldShowHidden
                val media = try {
                    mediaDB.getAllMedia().filter { item ->
                        item.parentPath.lowercase(Locale.ROOT) in allowed &&
                            !config.isFolderProtected(item.parentPath) &&
                            (showHidden || !item.path.contains("/.")) &&
                            (when {
                                getVideosOnly -> item.type == TYPE_VIDEOS
                                getImagesOnly -> item.type == TYPE_IMAGES
                                else -> item.type and filter != 0
                            })
                    }.toCollection(ArrayList())
                } catch (_: Exception) {
                    ArrayList<Medium>()
                }
                val preview = try {
                    fetcher.sortMedia(media, config.getFolderSorting(SHOW_ALL))
                    fetcher.groupMedia(media, SHOW_ALL)
                } catch (error: Exception) {
                    android.util.Log.w("GalleryMedia", "Root cache grouping failed; scanning source", error)
                    arrayListOf<ThumbnailItem>()
                }
                callback(preview)
            }
        }
        return
    }

    ensureBackgroundThread {
        val cached = try {
            val mediaFetcher = MediaFetcher(this)
            val foldersToScan = arrayListOf(path)

            var media = ArrayList<Medium>()
            if (path == FAVORITES) {
                // Cached metadata can contain protected media from an earlier
                // authorized session. It is never authorization by itself.
                media.addAll(mediaDB.getCachedFavoriteMedia().filter { item ->
                    if (allowedFavoritePaths != null) item.path in allowedFavoritePaths
                    else FavoriteAccessSessionManager.captureSnapshot(listOf(item.path), ::favoriteProtection)
                        .entries.values.all { it.locks.isEmpty() }
                })
            }

            if (path == RECYCLE_BIN) {
                media.addAll(getUpdatedDeletedMedia())
            }

            if (config.filterMedia and TYPE_PORTRAITS != 0) {
                val foldersToAdd = ArrayList<String>()
                for (folder in foldersToScan) {
                    val allFiles = File(folder).listFiles() ?: continue
                    allFiles.filter { it.name.startsWith("img_", true) && it.isDirectory }.forEach {
                        foldersToAdd.add(it.absolutePath)
                    }
                }
                foldersToScan.addAll(foldersToAdd)
            }

            val shouldShowHidden = config.shouldShowHidden
            foldersToScan.filter { path.isNotEmpty() || !config.isFolderProtected(it) }.forEach {
                try {
                    val currMedia = mediaDB.getMediaFromPath(it)
                    media.addAll(currMedia)
                } catch (ignored: Exception) {
                }
            }

            if (!shouldShowHidden) {
                media = media.filter { !it.path.contains("/.") } as ArrayList<Medium>
            }

            val filterMedia = config.filterMedia
            media = (when {
                getVideosOnly -> media.filter { it.type == TYPE_VIDEOS }
                getImagesOnly -> media.filter { it.type == TYPE_IMAGES }
                else -> media.filter {
                    (filterMedia and TYPE_IMAGES != 0 && it.type == TYPE_IMAGES)
                            || (filterMedia and TYPE_VIDEOS != 0 && it.type == TYPE_VIDEOS)
                            || (filterMedia and TYPE_GIFS != 0 && it.type == TYPE_GIFS)
                            || (filterMedia and TYPE_RAWS != 0 && it.type == TYPE_RAWS)
                            || (filterMedia and TYPE_SVGS != 0 && it.type == TYPE_SVGS)
                            || (filterMedia and TYPE_PORTRAITS != 0 && it.type == TYPE_PORTRAITS)
                }
            }) as ArrayList<Medium>

            // Cache rows are only preview candidates. Hide files confirmed missing
            // under a readable parent; do not mutate Room or favorite relationships.
            media = org.fossify.gallery.helpers.CachedMediaPreviewFilter.removeConfirmedMissing(media)

            val pathToUse = path.ifEmpty { SHOW_ALL }
            mediaFetcher.sortMedia(media, config.getFolderSorting(pathToUse))
            val grouped = mediaFetcher.groupMedia(media, pathToUse)
            grouped.clone() as ArrayList<ThumbnailItem>
            // Cached metadata is not proof of file existence. Missing permissions,
            // transient unmounted volumes and incomplete MediaStore scans must never
            // delete media favorites. Confirmed cache pruning is handled by the
            // guarded MediaCachePrunePolicy in the scan path.
        } catch (error: Exception) {
            android.util.Log.w("GalleryMedia", "Media cache unavailable; scanning source", error)
            arrayListOf()
        }
        callback(cached)
    }
}

fun Context.removeInvalidDBDirectories(dirs: ArrayList<Directory>? = null, scanComplete: Boolean = false) {
    if (!scanComplete) return
    val dirsToCheck = dirs ?: directoryDB.getAll()
    dirsToCheck.filter { directory ->
        val path = directory.path
        val folder = File(path)
        val parentReadable = folder.parentFile?.let {
            it.isDirectory && it.canRead()
        } == true
        // A disconnected SD card or a revoked storage grant is not deletion.
        // OTG paths need provider-level proof and must not be pruned here.
        !directory.areFavorites() &&
            !directory.isRecycleBin() &&
            path != config.tempFolderPath &&
            !isPathOnOTG(path) &&
            MediaCachePrunePolicy.canPruneDirectory(path, parentReadable, folder.exists())
    }.forEach { directory ->
        try {
            directoryDB.deleteDirPath(directory.path)
        } catch (error: Exception) {
            android.util.Log.w("GalleryDirs", "Could not prune directory", error)
        }
    }
}

fun Context.updateDBMediaPath(oldPath: String, newPath: String) {
    FavoriteAccessSessionManager.revokePath(oldPath)
    val newFilename = newPath.getFilenameFromPath()
    val newParentPath = newPath.getParentPath()
    try {
        mediaDB.updateMedium(newFilename, newPath, newParentPath, oldPath)
        favoritesDB.updateFavorite(newFilename, newPath, newParentPath, oldPath)
    } catch (ignored: Exception) {
    }
}

fun Context.updateDBDirectory(directory: Directory) {
    // Physical metadata must pass the scope/access/version gate in the repository.
    if (!directory.areFavorites() && !directory.isRecycleBin()) return
    try {
        directoryDB.updateDirectory(
            path = directory.path,
            thumbnail = directory.tmb,
            mediaCnt = directory.mediaCnt,
            lastModified = directory.modified,
            dateTaken = directory.taken,
            size = directory.size,
            mediaTypes = directory.types,
            sortValue = directory.sortValue
        )
    } catch (error: Exception) {
        android.util.Log.w("GalleryDirs", "Cannot update directory statistics", error)
    }
}

fun Context.getOTGFolderChildren(path: String) = getDocumentFile(path)?.listFiles()

fun Context.getOTGFolderChildrenNames(path: String): MutableList<String?>? {
    return getOTGFolderChildren(path)?.map { it.name }?.toMutableList()
}

fun Context.getFavoritePaths(): ArrayList<String> {
    return try {
        FavoriteMediaLoadPolicy.distinctSavedPaths(favoritesDB.getValidFavoritePaths())
    } catch (e: Exception) {
        android.util.Log.e("GalleryFavorites", "Cannot read saved media favorites", e)
        ArrayList()
    }
}

fun Context.getFavoriteFromPath(path: String): Favorite {
    return Favorite(null, path, path.getFilenameFromPath(), path.getParentPath())
}

fun Context.updateFavorite(
    path: String,
    isFavorite: Boolean,
    sourceAccessCheck: (String) -> Boolean = { false }
): Boolean {
    if (isFavorite) {
        val snapshot = runCatching {
            FavoriteAccessSessionManager.captureSnapshot(listOf(path), applicationContext::favoriteProtection)
        }.getOrNull() ?: return false
        val entry = snapshot.entries[path] ?: return false
        if (entry.locks.isNotEmpty() &&
            (!config.isFavoritePasswordProtectionOn || !sourceAccessCheck(path))
        ) return false
    }
    if (!isFavorite) FavoriteAccessSessionManager.revokePath(path)
    return try {
        if (isFavorite) favoritesDB.insert(getFavoriteFromPath(path))
        else favoritesDB.deleteFavoritePath(path)
        mediaDB.updateFavorite(path, isFavorite)
        org.fossify.gallery.helpers.PhotoContentRevision.changed()
        true
    } catch (e: Exception) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        false
    }
}

// remove the "recycle_bin" from the file path prefix, replace it with real bin path /data/user...
fun Context.getUpdatedDeletedMedia(): ArrayList<Medium> {
    val media = try {
        mediaDB.getDeletedMedia() as ArrayList<Medium>
    } catch (ignored: Exception) {
        ArrayList()
    }

    media.forEach {
        it.path = File(recycleBinPath, it.path.removePrefix(RECYCLE_BIN)).toString()
    }
    return media
}

fun Context.deleteDBPath(path: String) {
    deleteMediumWithPath(path.replaceFirst(recycleBinPath, RECYCLE_BIN))
}

fun Context.deleteMediumWithPath(path: String) {
    try {
        mediaDB.deleteMediumPath(path)
        if (path.startsWith("$RECYCLE_BIN/") || path.startsWith(recycleBinPath)) {
            org.fossify.gallery.helpers.RecycleBinMembershipRevision.changed()
        }
        org.fossify.gallery.helpers.PhotoContentRevision.changed()
    } catch (ignored: Exception) {
    }
}

fun Context.updateWidgets() {
    val widgetIDs = AppWidgetManager.getInstance(applicationContext)
        ?.getAppWidgetIds(ComponentName(applicationContext, MyWidgetProvider::class.java))
        ?: return

    if (widgetIDs.isNotEmpty()) {
        Intent(applicationContext, MyWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIDs)
            sendBroadcast(this)
        }
    }
}

// based on https://github.com/sannies/mp4parser/blob/master/examples/src/main/java/com/google/code/mp4parser/example/PrintStructure.java
fun Context.parseFileChannel(
    path: String,
    fc: FileChannel,
    level: Int,
    start: Long,
    end: Long,
    callback: () -> Unit
) {
    val fileChannelContainers = arrayListOf("moov", "trak", "mdia", "minf", "udta", "stbl")
    try {
        var iteration = 0
        var currEnd = end
        fc.position(start)
        if (currEnd <= 0) {
            currEnd = start + fc.size()
        }

        while (currEnd - fc.position() > 8) {
            // just a check to avoid deadloop at some videos
            if (iteration++ > 50) {
                return
            }

            val begin = fc.position()
            val byteBuffer = ByteBuffer.allocate(8)
            fc.read(byteBuffer)
            byteBuffer.rewind()
            val size = IsoTypeReader.readUInt32(byteBuffer)
            val type = IsoTypeReader.read4cc(byteBuffer)
            val newEnd = begin + size

            if (type == "uuid") {
                val fis = FileInputStream(File(path))
                fis.skip(begin)

                val sb = StringBuilder()
                val buffer = ByteArray(1024)
                while (sb.length < size) {
                    val n = fis.read(buffer)
                    if (n != -1) {
                        sb.append(String(buffer, 0, n))
                    } else {
                        break
                    }
                }

                val xmlString = sb.toString().lowercase(Locale.getDefault())
                if (
                    xmlString.contains("gspherical:projectiontype>equirectangular")
                    || xmlString.contains("gspherical:projectiontype=\"equirectangular\"")
                ) {
                    callback.invoke()
                }
                return
            }

            if (fileChannelContainers.contains(type)) {
                parseFileChannel(path, fc, level + 1, begin + 8, newEnd, callback)
            }

            fc.position(newEnd)
        }
    } catch (ignored: Exception) {
    }
}

fun Context.addPathToDB(path: String) {
    ensureBackgroundThread {
        if (!getDoesFilePathExist(path)) {
            return@ensureBackgroundThread
        }

        val type = when {
            path.isVideoFast() -> TYPE_VIDEOS
            path.isGif() -> TYPE_GIFS
            path.isRawFast() -> TYPE_RAWS
            path.isSvg() -> TYPE_SVGS
            path.isPortrait() -> TYPE_PORTRAITS
            else -> TYPE_IMAGES
        }

        try {
            val isFavorite = favoritesDB.isFavorite(path)
            val videoDuration = if (type == TYPE_VIDEOS) getDuration(path) ?: 0 else 0
            val medium = Medium(
                id = null,
                name = path.getFilenameFromPath(),
                path = path,
                parentPath = path.getParentPath(),
                modified = System.currentTimeMillis(),
                taken = System.currentTimeMillis(),
                size = File(path).length(),
                type = type,
                videoDuration = videoDuration,
                isFavorite = isFavorite,
                deletedTS = 0L,
                mediaStoreId = 0L
            )

            mediaDB.insert(medium)
        } catch (ignored: Exception) {
        }
    }
}

/** Default covers retain the album's own sort/group rules; valid custom covers need neither. */
fun Context.directoryCoverNeedsMediaOrder(path: String, covers: List<AlbumCover>): Boolean =
    covers.none { it.path == path && getDoesFilePathExist(it.tmb, config.OTGPath) }

fun Context.createDirectoryFromMedia(
    path: String,
    curMedia: ArrayList<Medium>,
    albumCovers: ArrayList<AlbumCover>,
    hiddenString: String,
    includedFolders: MutableSet<String>,
    getProperFileSize: Boolean,
    noMediaFolders: ArrayList<String>,
    statistics: org.fossify.gallery.models.DirectoryStats? = null,
): Directory {
    val OTGPath = config.OTGPath
    var thumbnail: String? = null

    albumCovers.forEach {
        if (it.path == path && getDoesFilePathExist(it.tmb, OTGPath)) {
            thumbnail = it.tmb
        }
    }

    if (thumbnail == null) {
        // Only default covers need folder grouping. Preserve the album's internal
        // order, without allocating another full list or mutating it for statistics.
        thumbnail = MediaFetcher(this).groupMedia(curMedia, path).asSequence().filterIsInstance<Medium>()
            .firstOrNull { getDoesFilePathExist(it.path, OTGPath) }?.path ?: ""
    }

    if (config.OTGPath.isNotEmpty() && thumbnail!!.startsWith(config.OTGPath)) {
        thumbnail = thumbnail!!.getOTGPublicPath(applicationContext)
    }

    val stats = statistics ?: org.fossify.gallery.models.DirectoryStats.calculate(curMedia,
        org.fossify.gallery.models.DirectoryStats.scope(config.filterMedia, config.shouldShowHidden), 0)
    val dirName = checkAppendingHidden(path, hiddenString, includedFolders, noMediaFolders)
    return Directory(
        id = null,
        path = path,
        tmb = thumbnail!!,
        name = dirName,
        mediaCnt = curMedia.size,
        modified = stats.maxModified,
        taken = stats.maxTaken,
        size = stats.bytes,
        location = getPathLocation(path),
        types = stats.types,
        sortValue = "",
        stats = stats,
        containsMediaFilesDirectly = curMedia.isNotEmpty()
    )
}


fun Context.updateDirectoryPath(path: String) {
    val cfg = config
    if (!path.startsWith('/') || org.fossify.gallery.helpers.FolderUnlockSessionManager
            .findProtectedAncestor(path, cfg::isFolderProtected) != null ||
        (!cfg.temporarilyShowExcluded && path.isThisOrParentExcluded(cfg.excludedFolders))) return
    val scope = org.fossify.gallery.helpers.DirectoryScanScope(cfg.filterMedia, cfg.shouldShowHidden)
    val version = org.fossify.gallery.helpers.DirectoryMetadataRepository.begin(path)
    try {
        val fetcher = MediaFetcher(applicationContext, reportReadFailures = true)
        val read = fetcher.readFolder(path, false, false, true, true, true, getFavoritePaths(),
            false, hashMapOf(), fetcher.directoryDates(path), null,
            sortResult = directoryCoverNeedsMediaOrder(path, cfg.parseAlbumCovers()))
        org.fossify.gallery.helpers.DirectoryMetadataRepository.commit(this,
            org.fossify.gallery.helpers.DirectoryScanSnapshot(path, version, scope, read, read.media))
    } catch (error: Exception) {
        org.fossify.gallery.helpers.DirectoryMetadataRepository.defer(path, version)
        android.util.Log.w("GalleryDirs", "Cannot refresh directory metadata", error)
    }
}

fun Context.getFileDateTaken(path: String): Long {
    val projection = arrayOf(
        Images.Media.DATE_TAKEN
    )

    val uri = Files.getContentUri("external")
    val selection = "${Images.Media.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getLongValue(Images.Media.DATE_TAKEN)
            }
        }
    } catch (ignored: Exception) {
    }

    return 0L
}

fun Context.getCompressionFormatFromUri(uri: Uri): CompressFormat {
    val type = getMimeTypeFromUri(uri)
    return when {
        type.equals("image/png", true) -> CompressFormat.PNG
        type.equals("image/webp", true) -> CompressFormat.WEBP
        else -> CompressFormat.JPEG
    }
}

fun Context.resolveUriScheme(
    uri: Uri,
    onPath: (String) -> Unit,
    onContentUri: (Uri) -> Unit,
    onUnknown: () -> Unit = { toast(R.string.unknown_file_location) }
) {
    when (uri.scheme) {
        "file" -> onPath(uri.path!!)
        "content" -> onContentUri(uri)
        else -> onUnknown()
    }
}


/** Captures only verification identity; never stores the user's entered secret. */
internal fun Context.favoriteProtection(path: String): FavoriteAccessSessionManager.Protection? =
    if (config.isFolderProtected(path)) FavoriteAccessSessionManager.Protection(
        config.getFolderProtectionType(path), config.getFolderProtectionHash(path)
    ) else null
