package org.fossify.gallery.adapters

import java.util.concurrent.Executor
import org.fossify.gallery.helpers.GalleryListDiffExecutor
import org.fossify.gallery.helpers.MediaUpdatePolicy
import org.fossify.gallery.helpers.MediaUpdateReason
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.allViews
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.helpers.FavoritesScrollRestorer
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import com.bumptech.glide.Glide
import com.squareup.picasso.Picasso
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.RenameDialog
import org.fossify.commons.dialogs.RenameItemDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getOTGPublicPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.sumByLong
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.PhotoItemGridBinding
import org.fossify.gallery.databinding.PhotoItemListBinding
import org.fossify.gallery.databinding.ThumbnailSectionBinding
import org.fossify.gallery.databinding.VideoItemGridBinding
import org.fossify.gallery.databinding.VideoItemListBinding
import org.fossify.gallery.dialogs.DeleteWithRememberDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getShortcutImage
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.launchResizeImageDialog
import org.fossify.gallery.extensions.launchResizeMultipleImagesDialog
import org.fossify.gallery.extensions.loadImage
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.rescanFolderMedia
import org.fossify.gallery.extensions.restoreRecycleBinPaths
import org.fossify.gallery.extensions.saveRotatedImageToFile
import org.fossify.gallery.extensions.setAs
import org.fossify.gallery.extensions.shareMediaPaths
import org.fossify.gallery.extensions.shareMediumPath
import org.fossify.gallery.extensions.showRestoreConfirmationDialog
import org.fossify.gallery.extensions.toggleFileVisibility
import org.fossify.gallery.extensions.tryCopyMoveFilesTo
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.extensions.updateFavoritePaths
import org.fossify.gallery.extensions.ensureFavoriteAddAllowed
import org.fossify.gallery.helpers.FastScrollerBubble
import org.fossify.gallery.helpers.OxygenThumbnailPlaceholder
import org.fossify.gallery.helpers.OxygenSelectionMode
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_BIG
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

class MediaAdapter(
    activity: BaseSimpleActivity,
    var media: ArrayList<ThumbnailItem>,
    val listener: MediaOperationsListener?,
    val isAGetIntent: Boolean,
    val allowMultiplePicks: Boolean,
    val path: String,
    recyclerView: MyRecyclerView,
    private val diffExecutor: Executor = GalleryListDiffExecutor,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private val ITEM_SECTION = 0
    private val ITEM_MEDIUM_VIDEO_PORTRAIT = 1
    private val ITEM_MEDIUM_PHOTO = 2

    private val config = activity.config
    private var deleteDialogShowing = false
    private val viewType = config.getFolderViewType(if (config.showAll) SHOW_ALL else path)
    private val isListViewType = viewType == VIEW_TYPE_LIST
    private var rotatedImagePaths = ArrayList<String>()
    private var currentMediaHash = media.hashCode()
    private val hasOTGConnected = activity.hasOTGConnected()

    private var scrollHorizontally = config.scrollHorizontally
    private var animateGifs = config.animateGifs
    private var cropThumbnails = config.cropThumbnails
    private var displayFilenames = config.displayFileNames
    private var showFileTypes = config.showThumbnailFileTypes

    var sorting = config.getFolderSorting(if (config.showAll) SHOW_ALL else path)
    var dateFormat = config.dateFormat
    var timeFormat = activity.getTimeFormat()
    var onThumbnailLoaded: ((String, ImageView) -> Unit)? = null

    init {
        actModeCallback = OxygenSelectionMode.wrap(activity, actModeCallback)
        setupDragListener(true)
        setHasStableIds(true)
        recyclerView.itemAnimator?.endAnimations()
        recyclerView.itemAnimator = null
    }

    override fun getActionMenuId() = R.menu.cab_media

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = if (viewType == ITEM_SECTION) {
            ThumbnailSectionBinding.inflate(layoutInflater, parent, false)
        } else {
            if (isListViewType) {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemListBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemListBinding.inflate(layoutInflater, parent, false)
                }
            } else {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemGridBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemGridBinding.inflate(layoutInflater, parent, false)
                }
            }
        }
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val tmbItem = media.getOrNull(position) ?: return
        val allowLongPress = (!isAGetIntent || allowMultiplePicks) && tmbItem is Medium
        holder.bindView(tmbItem, tmbItem is Medium, allowLongPress) { itemView, adapterPosition ->
            if (tmbItem is Medium) {
                setupThumbnail(itemView, tmbItem)
            } else {
                setupSection(itemView, tmbItem as ThumbnailSection)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = media.size

    override fun getItemViewType(position: Int): Int {
        val tmbItem = media[position]
        return when {
            tmbItem is ThumbnailSection -> ITEM_SECTION
            (tmbItem as Medium).isVideo() || tmbItem.isPortrait() -> ITEM_MEDIUM_VIDEO_PORTRAIT
            else -> ITEM_MEDIUM_PHOTO
        }
    }

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }

        val isOneItemSelected = isOneItemSelected()
        val selectedPaths = selectedItems.map { it.path } as ArrayList<String>
        val isInRecycleBin = selectedItems.firstOrNull()?.getIsInRecycleBin() == true
        menu.apply {
            findItem(R.id.cab_rename).isVisible = !isInRecycleBin
            findItem(R.id.cab_add_to_favorites).isVisible = !isInRecycleBin
            findItem(R.id.cab_fix_date_taken).isVisible = !isInRecycleBin
            findItem(R.id.cab_move_to).isVisible = !isInRecycleBin
            findItem(R.id.cab_open_with).isVisible = isOneItemSelected
            findItem(R.id.cab_edit).isVisible = isOneItemSelected
            findItem(R.id.cab_set_as).isVisible = isOneItemSelected
            findItem(R.id.cab_resize).isVisible = canResize(selectedItems)
            findItem(R.id.cab_confirm_selection).isVisible = isAGetIntent && allowMultiplePicks && selectedKeys.isNotEmpty()
            findItem(R.id.cab_restore_recycle_bin_files).isVisible = selectedPaths.all { it.startsWith(activity.recycleBinPath) }
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected

            checkHideBtnVisibility(this, selectedItems)
            checkFavoriteBtnVisibility(this, selectedItems)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_confirm_selection -> confirmSelection()
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> checkMediaManagementAndRename()
            R.id.cab_edit -> editFile()
            R.id.cab_hide -> toggleFileVisibility(true)
            R.id.cab_unhide -> toggleFileVisibility(false)
            R.id.cab_add_to_favorites -> toggleFavorites(true)
            R.id.cab_remove_from_favorites -> toggleFavorites(false)
            R.id.cab_restore_recycle_bin_files -> restoreFiles()
            R.id.cab_share -> shareMedia()
            R.id.cab_rotate_right -> rotateSelection(90)
            R.id.cab_rotate_left -> rotateSelection(270)
            R.id.cab_rotate_one_eighty -> rotateSelection(180)
            R.id.cab_copy_to -> checkMediaManagementAndCopy(true)
            R.id.cab_move_to -> moveFilesTo()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_select_all -> selectAll()
            R.id.cab_open_with -> openPath()
            R.id.cab_fix_date_taken -> fixDateTaken()
            R.id.cab_set_as -> setAs()
            R.id.cab_resize -> resize()
            R.id.cab_delete -> checkDeleteConfirmation()
        }
    }

    override fun getSelectableItemCount() = media.filter { it is Medium }.size

    override fun getIsItemSelectable(position: Int) = !isASectionTitle(position)

    override fun getItemSelectionKey(position: Int) = (media.getOrNull(position) as? Medium)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = media.indexOfFirst { (it as? Medium)?.path?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed) {
            val itemView = holder.itemView
            val tmb = itemView.allViews.firstOrNull { it.id == R.id.medium_thumbnail }
            if (tmb is ImageView) {
                tmb.tag = null
                tmb.setTag(R.id.media_bound_path, null)
                tmb.setTag(R.id.thumbnail_request_key, null)
                tmb.setTag(R.id.thumbnail_loaded_key, null)
                Picasso.get().cancelRequest(tmb)
                Glide.with(activity).clear(tmb)
                tmb.setImageDrawable(null)
            }
        }
    }

    fun isASectionTitle(position: Int) = media.getOrNull(position) is ThumbnailSection

    private fun checkHideBtnVisibility(menu: Menu, selectedItems: ArrayList<Medium>) {
        val isInRecycleBin = selectedItems.firstOrNull()?.getIsInRecycleBin() == true
        menu.findItem(R.id.cab_hide).isVisible = (!isRPlus() || isExternalStorageManager()) && !isInRecycleBin && selectedItems.any { !it.isHidden() }
        menu.findItem(R.id.cab_unhide).isVisible = (!isRPlus() || isExternalStorageManager()) && !isInRecycleBin && selectedItems.any { it.isHidden() }
    }

    private fun checkFavoriteBtnVisibility(menu: Menu, selectedItems: ArrayList<Medium>) {
        menu.findItem(R.id.cab_add_to_favorites).isVisible = selectedItems.none { it.getIsInRecycleBin() } && selectedItems.any { !it.isFavorite }
        menu.findItem(R.id.cab_remove_from_favorites).isVisible = selectedItems.none { it.getIsInRecycleBin() } && selectedItems.any { it.isFavorite }
    }

    private fun confirmSelection() {
        listener?.selectedPaths(getSelectedPaths())
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            val path = getFirstSelectedItemPath() ?: return
            PropertiesDialog(activity, path, config.shouldShowHidden)
        } else {
            val paths = getSelectedPaths()
            PropertiesDialog(activity, paths, config.shouldShowHidden)
        }
    }

    private fun checkMediaManagementAndRename() {
        activity.handleMediaManagementPrompt {
            renameFile()
        }
    }

    private fun renameFile() {
        val firstPath = getFirstSelectedItemPath() ?: return

        val isSDOrOtgRootFolder = activity.isAStorageRootFolder(firstPath.getParentPath()) && !firstPath.startsWith(activity.internalStoragePath)
        if (isRPlus() && isSDOrOtgRootFolder && !isExternalStorageManager()) {
            activity.toast(org.fossify.commons.R.string.rename_in_sd_card_system_restriction, Toast.LENGTH_LONG)
            finishActMode()
            return
        }

        if (selectedKeys.size == 1) {
            RenameItemDialog(activity, firstPath) {
                ensureBackgroundThread {
                    activity.updateDBMediaPath(firstPath, it)

                    activity.runOnUiThread {
                        listener?.refreshItems()
                        finishActMode()
                    }
                }
            }
        } else {
            RenameDialog(activity, getSelectedPaths(), true) {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun editFile() {
        val path = getFirstSelectedItemPath() ?: return
        activity.openEditor(path)
    }

    private fun openPath() {
        val path = getFirstSelectedItemPath() ?: return
        activity.openPath(path, true)
    }

    private fun setAs() {
        val path = getFirstSelectedItemPath() ?: return
        activity.setAs(path)
    }

    private fun resize() {
        val paths = getSelectedItems().filter { it.isImage() }.map { it.path }
        if (isOneItemSelected()) {
            val path = paths.first()
            activity.launchResizeImageDialog(path) {
                finishActMode()
                listener?.refreshItems()
            }
        } else {
            activity.launchResizeMultipleImagesDialog(paths) {
                finishActMode()
                listener?.refreshItems()
            }
        }
    }

    private fun canResize(selectedItems: ArrayList<Medium>): Boolean {
        val selectionContainsImages = selectedItems.any { it.isImage() }
        if (!selectionContainsImages) {
            return false
        }

        val parentPath = selectedItems.first { it.isImage() }.parentPath
        val isCommonParent = selectedItems.all { parentPath == it.parentPath }
        val isRestrictedDir = activity.isRestrictedWithSAFSdk30(parentPath)
        return isExternalStorageManager() || (isCommonParent && !isRestrictedDir)
    }

    private fun toggleFileVisibility(hide: Boolean) {
        ensureBackgroundThread {
            getSelectedItems().forEach {
                activity.toggleFileVisibility(it.path, hide)
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun toggleFavorites(add: Boolean) {
        val items = getSelectedItems()
        val toAdd = if (add) items.filterNot { it.isFavorite } else emptyList()
        val sourceCheck: (String) -> Boolean = { path ->
            (activity as? MediaActivity)?.canAddFavoriteFromSource(path) == true
        }
        val applyChange = {
            ensureBackgroundThread {
                var failedAdds = 0
                items.forEach { item ->
                    val updated = activity.updateFavorite(item.path, add, sourceCheck)
                    if (updated) item.isFavorite = add else if (add) failedAdds++
                }
                activity.runOnUiThread {
                    if (failedAdds > 0) activity.toast(org.fossify.gallery.R.string.favorite_add_blocked_changed)
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }
        if (add) {
            activity.ensureFavoriteAddAllowed(toAdd.map { it.path }, sourceCheck) { allowed ->
                if (allowed) applyChange()
            }
        } else {
            applyChange()
        }
    }

    private fun restoreFiles() {
        val paths = getSelectedPaths()
        if (paths.size > 1) {
            activity.showRestoreConfirmationDialog(paths.size) {
                doRestoreFiles(paths)
            }
        } else {
            doRestoreFiles(paths)
        }
    }

    private fun doRestoreFiles(paths: ArrayList<String>) {
        activity.restoreRecycleBinPaths(paths) {
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun shareMedia() {
        if (selectedKeys.size == 1 && selectedKeys.first() != -1) {
            activity.shareMediumPath(getSelectedItems().first().path)
        } else if (selectedKeys.size > 1) {
            activity.shareMediaPaths(getSelectedPaths())
        }
    }

    private fun handleRotate(paths: List<String>, degrees: Int) {
        var fileCnt = paths.size
        rotatedImagePaths.clear()
        activity.toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            paths.forEach {
                rotatedImagePaths.add(it)
                activity.saveRotatedImageToFile(it, it, degrees, true) {
                    fileCnt--
                    if (fileCnt == 0) {
                        activity.runOnUiThread {
                            listener?.refreshItems()
                            finishActMode()
                        }
                    }
                }
            }
        }
    }

    private fun rotateSelection(degrees: Int) {
        val paths = getSelectedPaths().filter { it.isImageFast() }

        if (paths.any { activity.needsStupidWritePermissions(it) }) {
            activity.handleSAFDialog(paths.first { activity.needsStupidWritePermissions(it) }) {
                if (it) {
                    handleRotate(paths, degrees)
                }
            }
        } else {
            handleRotate(paths, degrees)
        }
    }

    private fun moveFilesTo() {
        activity.handleDeletePasswordProtection {
            checkMediaManagementAndCopy(false)
        }
    }

    private fun checkMediaManagementAndCopy(isCopyOperation: Boolean) {
        activity.handleMediaManagementPrompt {
            copyMoveTo(isCopyOperation)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val paths = getSelectedPaths()

        val recycleBinPath = activity.recycleBinPath
        val fileDirItems = paths.asSequence().filter { isCopyOperation || !it.startsWith(recycleBinPath) }.map {
            FileDirItem(it, it.getFilenameFromPath())
        }.toMutableList() as ArrayList

        if (!isCopyOperation && paths.any { it.startsWith(recycleBinPath) }) {
            activity.toast(org.fossify.commons.R.string.moving_recycle_bin_items_disabled, Toast.LENGTH_LONG)
        }

        if (fileDirItems.isEmpty()) {
            return
        }

        activity.tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            val destinationPath = it
            config.tempFolderPath = ""
            activity.applicationContext.rescanFolderMedia(destinationPath)
            activity.applicationContext.rescanFolderMedia(fileDirItems.first().getParentPath())

            val newPaths = fileDirItems.map { "$destinationPath/${it.name}" }.toMutableList() as ArrayList<String>
            activity.rescanPaths(newPaths) {
                activity.fixDateTaken(newPaths, false)
            }

            if (!isCopyOperation) {
                listener?.refreshItems()
                activity.updateFavoritePaths(fileDirItems, destinationPath)
            }
        }
    }

    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val path = getSelectedPaths().first()
            val drawable = resources.getDrawable(R.drawable.shortcut_image).mutate()
            activity.getShortcutImage(path, drawable) {
                val intent = Intent(activity, ViewPagerActivity::class.java).apply {
                    putExtra(PATH, path)
                    putExtra(SHOW_ALL, config.showAll)
                    putExtra(SHOW_FAVORITES, path == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, path == RECYCLE_BIN)
                    action = Intent.ACTION_VIEW
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val shortcut = ShortcutInfo.Builder(activity, path)
                    .setShortLabel(path.getFilenameFromPath())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun fixDateTaken() {
        ensureBackgroundThread {
            activity.fixDateTaken(getSelectedPaths(), true) {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun checkDeleteConfirmation() {
        activity.handleMediaManagementPrompt {
            if (config.isDeletePasswordProtectionOn) {
                activity.handleDeletePasswordProtection {
                    deleteFiles(config.tempSkipRecycleBin)
                }
            } else if (config.tempSkipDeleteConfirmation || config.skipDeleteConfirmation) {
                deleteFiles(config.tempSkipRecycleBin)
            } else {
                askConfirmDelete()
            }
        }
    }

    private fun askConfirmDelete() {
        if (deleteDialogShowing || selectedKeys.isEmpty()) return
        val selectedMedia = getSelectedItems()
        if (selectedMedia.isEmpty()) {
            selectedKeys.clear()
            finishActMode()
            return
        }
        val itemsCnt = selectedMedia.size
        val firstPath = selectedMedia.first().path
        val fileDirItem = selectedMedia.first().toFileDirItem()
        val size = fileDirItem.getProperSize(activity, countHidden = true).formatSize()
        val itemsAndSize = if (itemsCnt == 1) {
            fileDirItem.mediaStoreId = selectedMedia.first().mediaStoreId
            "\"${firstPath.getFilenameFromPath()}\" ($size)"
        } else {
            val fileDirItems = ArrayList<FileDirItem>(selectedMedia.size)
            selectedMedia.forEach { medium ->
                val curFileDirItem = medium.toFileDirItem()
                fileDirItems.add(curFileDirItem)
            }
            val fileSize = fileDirItems.sumByLong { it.getProperSize(activity, countHidden = true) }.formatSize()
            val deleteItemsString = resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
            "$deleteItemsString ($fileSize)"
        }

        val isRecycleBin = firstPath.startsWith(activity.recycleBinPath)
        val baseString =
            if (config.useRecycleBin && !config.tempSkipRecycleBin && !isRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), itemsAndSize)
        val showSkipRecycleBinOption = config.useRecycleBin && !isRecycleBin

        deleteDialogShowing = true
        DeleteWithRememberDialog(
            activity, question, showSkipRecycleBinOption,
            onDismiss = { deleteDialogShowing = false }
        ) { remember, skipRecycleBin ->
            config.tempSkipDeleteConfirmation = remember
            config.tempSkipRecycleBin = remember && skipRecycleBin
            deleteFiles(skipRecycleBin)
        }
    }

    private fun deleteFiles(skipRecycleBin: Boolean) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val selectedPaths = getSelectedItems().mapTo(LinkedHashSet()) { it.path }
        if (selectedPaths.isEmpty()) {
            selectedKeys.clear()
            finishActMode()
            return
        }
        val SAFPath = selectedPaths.firstOrNull { activity.needsStupidWritePermissions(it) } ?: selectedPaths.first()
        activity.handleSAFDialog(SAFPath) {
            if (!it || !isDeleteContextCurrent()) {
                return@handleSAFDialog
            }

            val sdk30SAFPath = selectedPaths.firstOrNull { activity.isAccessibleWithSAFSdk30(it) } ?: selectedPaths.first()
            activity.checkManageMediaOrHandleSAFDialogSdk30(sdk30SAFPath) {
                if (!it || !isDeleteContextCurrent()) {
                    return@checkManageMediaOrHandleSAFDialogSdk30
                }

                // Resolve the original path identities against the current list after
                // permission UI has returned. Metadata objects and adapter positions
                // captured before that UI are not stable across progressive updates.
                val currentItems = media.filterIsInstance<Medium>().filter { it.path in selectedPaths }
                if (currentItems.isEmpty()) {
                    finishActMode()
                    return@checkManageMediaOrHandleSAFDialogSdk30
                }
                val fileDirItems = ArrayList<FileDirItem>(currentItems.size)
                currentItems.forEach { medium ->
                    fileDirItems.add(medium.toFileDirItem())
                }
                val operationPaths = currentItems.mapTo(LinkedHashSet()) { it.path }
                val host = listener
                if (host == null) return@checkManageMediaOrHandleSAFDialogSdk30
                host.tryDeleteFiles(fileDirItems, skipRecycleBin) { deletedPaths ->
                    activity.runOnUiThread {
                        if (deletedPaths.isNotEmpty() && isDeleteContextCurrent()) {
                            removeMediaPaths(deletedPaths.intersect(operationPaths))
                        } else if (isDeleteContextCurrent()) {
                            finishActMode()
                        }
                    }
                }
            }
        }
    }

    private fun isDeleteContextCurrent(): Boolean =
        !activity.isFinishing && !activity.isDestroyed && recyclerView.adapter === this

    /** Apply confirmed deletions by stable path through the normal single-flight diff path. */
    internal fun removeMediaPaths(paths: Set<String>) {
        if (paths.isEmpty() || recyclerView.adapter !== this) return
        ++diffGeneration
        pendingUpdate = null
        recyclerView.removeCallbacks(drainUpdates)
        val remaining = media.filterNot { it is Medium && it.path in paths }
            .toCollection(ArrayList<ThumbnailItem>())
        updateMedia(remaining, preserveScroll = false, updateReason = MediaUpdateReason.DELETE_RESULT)
    }

    private fun getSelectedItems() = selectedKeys.mapNotNull { getItemWithKey(it) } as ArrayList<Medium>

    private fun getSelectedPaths() = getSelectedItems().map { it.path } as ArrayList<String>

    private fun getFirstSelectedItemPath() = getItemWithKey(selectedKeys.first())?.path

    private fun getItemWithKey(key: Int): Medium? = media.firstOrNull { (it as? Medium)?.path?.hashCode() == key } as? Medium

    var committedVersion: Long = 1L
        private set
    @Volatile private var diffGeneration = 0
    private val refreshScroll = FavoritesScrollRestorer()
    private data class PendingUpdate(
        val items: ArrayList<ThumbnailItem>, val preserveScroll: Boolean,
        val current: () -> Boolean, val committed: () -> Unit, val generation: Int,
        val requestId: Long?, val diagnosticsEnabled: Boolean,
        val reason: MediaUpdateReason, val sorting: Int, val submittedAt: Long,
        val snapshotVersion: Long?, val snapshotStage: String?, val forceReplace: Boolean = false
    )
    private var pendingUpdate: PendingUpdate? = null
    private var diffRunning = false
    private var lastDeferredGeneration = -1
    private val drainUpdates = Runnable { submitPendingUpdate() }
    private val scrollUpdates = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) refreshScroll.clear()
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                recyclerView.removeCallbacks(drainUpdates)
                submitPendingUpdate()
            }
        }
    }
    private val durationPayload = Any()
    private val durationOverrides = HashMap<String, Medium>()

    private fun itemIdentity(item: ThumbnailItem): String = when (item) {
        is Medium -> "media:${item.path}"
        is ThumbnailSection -> "section:${item.key}"
        else -> error("Unknown thumbnail item")
    }

    override fun getItemId(position: Int): Long = media.getOrNull(position)?.let { item ->
        itemIdentity(item).fold(1469598103934665603L) { hash, ch ->
            (hash xor ch.code.toLong()) * 1099511628211L
        }
    } ?: RecyclerView.NO_ID

    private fun snapshot(items: List<ThumbnailItem>): ArrayList<ThumbnailItem> = items.map { item ->
        when (item) {
            is Medium -> item.copy()
            is ThumbnailSection -> item.copy()
            else -> item
        }
    }.toCollection(ArrayList())

    fun updateMedia(newMedia: ArrayList<ThumbnailItem>, preserveScroll: Boolean = true,
                    isCurrent: () -> Boolean = { true }, onCommitted: () -> Unit = {},
                    diagnosticRequestId: Long? = null, updateReason: MediaUpdateReason = MediaUpdateReason.SCAN,
                    effectiveSorting: Int = config.getFolderSorting(if (config.showAll) SHOW_ALL else path),
                    diagnosticSnapshotVersion: Long? = null, diagnosticSnapshotStage: String? = null) {
        if (!isCurrent()) return
        val diagnosticsEnabled = org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()
        if (diagnosticsEnabled) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_submit", diagnosticRequestId,
            mapOf("itemCount" to newMedia.size, "preserveScroll" to preserveScroll,
                "diffGeneration" to (diffGeneration + 1), "adapterId" to System.identityHashCode(this),
                "adapterVersion" to committedVersion, "snapshotVersion" to diagnosticSnapshotVersion,
                "snapshotStage" to diagnosticSnapshotStage, "updateReason" to updateReason.name)
        )
        if (diagnosticsEnabled && pendingUpdate != null) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_submit_replaced", diagnosticRequestId,
            mapOf("itemCount" to newMedia.size, "replacedGeneration" to pendingUpdate?.generation,
                "diffGeneration" to (diffGeneration + 1), "adapterId" to System.identityHashCode(this))
        )
        pendingUpdate = PendingUpdate(snapshot(newMedia), preserveScroll, isCurrent, onCommitted, ++diffGeneration,
            diagnosticRequestId, diagnosticsEnabled, updateReason, effectiveSorting,
            android.os.SystemClock.elapsedRealtime(),
            diagnosticSnapshotVersion, diagnosticSnapshotStage)
        recyclerView.removeCallbacks(drainUpdates)
        submitPendingUpdate()
    }

    /** One in-flight diff plus the newest pending catalog. Never queue every scan preview. */
    private fun submitPendingUpdate() {
        val pending = pendingUpdate ?: return
        if (!canCommit(pending)) {
            pendingUpdate = null
            return
        }
        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE || recyclerView.isComputingLayout) {
            logUpdate(pending, "adapter_update_deferred", "layout_or_scroll")
            recyclerView.removeCallbacks(drainUpdates)
            recyclerView.postDelayed(drainUpdates, 80)
            return
        }
        pendingUpdate = null
        val incoming = pending.items
        applyDurationOverrides(incoming)
        if (MediaUpdatePolicy.sameDisplayedContent(media, incoming)) {
            // Keep the newest database snapshot for later actions without asking
            // RecyclerView to diff or rebind rows whose visible content is stable.
            sorting = pending.sorting
            media = incoming
            committedVersion++
            currentMediaHash = media.hashCode()
            if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_same_content_skip",
                    pending.requestId, mapOf("itemCount" to incoming.size, "diffGeneration" to pending.generation,
                        "adapterId" to System.identityHashCode(this), "adapterVersion" to committedVersion)
                )
            logUpdate(pending, "adapter_snapshot_fast_commit", "same_content")
            pending.committed()
            return
        }
        if (MediaUpdatePolicy.isPureMediaReorder(media, incoming)) {
            commitReorder(pending)
            return
        }
        if (pending.forceReplace || MediaUpdatePolicy.shouldReplace(media, incoming, pending.reason, diffRunning)) {
            commitReplacement(pending)
            return
        }
        if (diffRunning) {
            pendingUpdate = pending
            logUpdate(pending, "adapter_update_deferred", "diff_running")
            return
        }
        val old = snapshot(media)
        diffRunning = true
        val work = Runnable {
            val diffStarted = android.os.SystemClock.elapsedRealtime()
            logUpdate(pending, "adapter_diff_started", "diff", old.size,
                queueWaitMs = diffStarted - pending.submittedAt)
            val result = runCatching { DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = incoming.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    if (pending.generation != diffGeneration) throw java.util.concurrent.CancellationException()
                    return itemIdentity(old[oldItemPosition]) == itemIdentity(incoming[newItemPosition])
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val before = old[oldItemPosition]
                    val after = incoming[newItemPosition]
                    return if (before is Medium && after is Medium) MediaUpdatePolicy.sameContent(before, after)
                    else before == after
                }
                override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                    val before = old[oldItemPosition] as? Medium ?: return null
                    val after = incoming[newItemPosition] as? Medium ?: return null
                    return if (MediaUpdatePolicy.durationOnly(before, after)) durationPayload else null
                }
            }, false) }
            val diffFinished = android.os.SystemClock.elapsedRealtime()
            activity.runOnUiThread {
                diffRunning = false
                logUpdate(pending, "adapter_diff_finished", if (result.isSuccess) "diff" else "failed_or_cancelled",
                    old.size, diffMs = diffFinished - diffStarted)
                if (pending.generation != diffGeneration || !canCommit(pending)) {
                    if (pending.diagnosticsEnabled)
                        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_diff_dropped",
                            pending.requestId, mapOf("itemCount" to incoming.size,
                                "diffGeneration" to pending.generation, "adapterId" to System.identityHashCode(this),
                                "adapterVersion" to committedVersion)
                        )
                    submitPendingUpdate()
                    return@runOnUiThread
                }
                if (result.isFailure) {
                    recoverDiff(pending, result.exceptionOrNull()!!)
                    return@runOnUiThread
                }
                val diff = result.getOrThrow()
                if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE || recyclerView.isComputingLayout) {
                    pendingUpdate = pending
                    recyclerView.postDelayed(drainUpdates, 80)
                    return@runOnUiThread
                }
                // Details may have completed while the background diff ran.
                applyDurationOverrides(incoming)
                rememberRefreshScroll(pending)
                sorting = pending.sorting
                media = incoming
                committedVersion++
                currentMediaHash = media.hashCode()
                selectedKeys.retainAll(media.filterIsInstance<Medium>().map { it.path.hashCode() }.toSet())
                if (selectedKeys.isEmpty()) finishActMode() else actMode?.invalidate()
                listener?.updateMediaGridDecoration(media)
                diff.dispatchUpdatesTo(this)
                if (pending.diagnosticsEnabled)
                    org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_commit",
                        pending.requestId, mapOf(
                            "itemCount" to incoming.size, "gridVersion" to committedVersion,
                            "diffGeneration" to pending.generation, "adapterId" to System.identityHashCode(this),
                            "oldCount" to old.size,
                            "diffMs" to (diffFinished - diffStarted).coerceAtLeast(0L),
                            "publicationWaitMs" to (android.os.SystemClock.elapsedRealtime() - diffFinished),
                            "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - pending.submittedAt),
                            "updateReason" to pending.reason.name, "commitStrategy" to "diff",
                            "snapshotVersion" to pending.snapshotVersion, "snapshotStage" to pending.snapshotStage,
                            "adapterVersion" to committedVersion,
                            "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(path)
                        )
                    )
                if (refreshScroll.pending != null) refreshScroll.restore(recyclerView,
                    media.map { (it as? Medium)?.path }, true, isCurrent = {
                        pending.generation == diffGeneration && recyclerView.adapter === this && pending.current() &&
                            recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
                    })
                pending.committed()
                submitPendingUpdate()
            }
        }
        try { diffExecutor.execute(work) } catch (error: java.util.concurrent.RejectedExecutionException) {
            diffRunning = false
            recoverDiff(pending, error)
        }
    }

    private fun recoverDiff(pending: PendingUpdate, error: Throwable) {
        logUpdate(pending, "adapter_diff_failed", error::class.java.simpleName)
        pendingUpdate = pending.copy(forceReplace = true)
        recyclerView.post(drainUpdates)
    }

    private fun commitReorder(pending: PendingUpdate) {
        val incoming = pending.items
        // A plain sort is a permutation of the same authorized rows. Commit
        // the new position-to-item mapping directly and rebind visible cells;
        // DiffUtil's edit-distance search is needlessly expensive for a large
        // permutation. Other updates use the bounded replacement or Diff
        // policy, preserving any section headers.
        refreshScroll.clear()
        sorting = pending.sorting
        media = incoming
        committedVersion++
        currentMediaHash = media.hashCode()
        listener?.updateMediaGridDecoration(media)
        notifyItemRangeChanged(0, incoming.size)
        if (pending.diagnosticsEnabled)
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_reorder_commit",
                pending.requestId, mapOf("itemCount" to incoming.size, "gridVersion" to committedVersion,
                    "diffGeneration" to pending.generation, "adapterId" to System.identityHashCode(this))
            )
        logUpdate(pending, "adapter_snapshot_fast_commit", "reorder")
        pending.committed()
    }

    /** Whole-snapshot replacement is atomic on main, after the same access/layout
     * gates as a diff. The old worker remains single-flight but cannot commit. */
    private fun commitReplacement(pending: PendingUpdate) {
        val oldCount = media.size
        rememberRefreshScroll(pending)
        sorting = pending.sorting
        media = pending.items
        committedVersion++
        currentMediaHash = media.hashCode()
        selectedKeys.retainAll(media.filterIsInstance<Medium>().map { it.path.hashCode() }.toSet())
        if (selectedKeys.isEmpty()) finishActMode() else actMode?.invalidate()
        listener?.updateMediaGridDecoration(media)
        notifyDataSetChanged()
        logUpdate(pending, "adapter_snapshot_fast_commit", "replace", oldCount)
        if (refreshScroll.pending != null) refreshScroll.restore(recyclerView,
            media.map { (it as? Medium)?.path }, true, isCurrent = {
                pending.generation == diffGeneration && recyclerView.adapter === this && pending.current() &&
                    !activity.isFinishing && !activity.isDestroyed &&
                    recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
            })
        pending.committed()
    }

    private fun canCommit(pending: PendingUpdate): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return recyclerView.adapter === this && pending.current()
    }

    private fun rememberRefreshScroll(pending: PendingUpdate) {
        refreshScroll.clear()
        if (!pending.preserveScroll || pending.reason == MediaUpdateReason.USER_SORT) return
        if (path == FAVORITES || path == PhotoProfileMediaLoader.PATH) return
        refreshScroll.remember(recyclerView, media.map { (it as? Medium)?.path })
    }

    private fun logUpdate(pending: PendingUpdate, event: String, strategy: String, oldCount: Int = media.size,
                          queueWaitMs: Long? = null, diffMs: Long? = null) {
        if (!pending.diagnosticsEnabled) return
        if (event == "adapter_update_deferred") {
            if (lastDeferredGeneration == pending.generation) return
            lastDeferredGeneration = pending.generation
        }
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            if (event == "adapter_diff_failed") org.fossify.gallery.helpers.DiagnosticLevel.WARN
            else org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", event, pending.requestId,
            mapOf("generation" to pending.generation, "adapterId" to System.identityHashCode(this),
                "adapterVersion" to committedVersion,
                "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(path),
                "updateReason" to pending.reason.name, "itemCount" to pending.items.size, "oldCount" to oldCount,
                "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - pending.submittedAt),
                "commitStrategy" to strategy,
                "sorting" to pending.sorting, "queueWaitMs" to queueWaitMs, "diffMs" to diffMs,
                "snapshotVersion" to pending.snapshotVersion, "snapshotStage" to pending.snapshotStage))
    }

    private fun applyDurationOverrides(items: List<ThumbnailItem>) {
        items.filterIsInstance<Medium>().forEach { item ->
            durationOverrides[item.path]?.takeIf { it.modified == item.modified && it.size == item.size }
                ?.let { item.videoDuration = it.videoDuration }
        }
    }

    fun updateVideoDurations(details: List<Medium>) {
        details.forEach { durationOverrides[it.path] = it.copy() }
        if (recyclerView.isComputingLayout) {
            recyclerView.post { if (recyclerView.adapter === this) updateVideoDurations(details) }
            return
        }
        media.forEachIndexed { index, item ->
            if (item is Medium) {
                val detail = durationOverrides[item.path]
                if (detail != null && detail.modified == item.modified && detail.size == item.size &&
                    detail.videoDuration != item.videoDuration) {
                    item.videoDuration = detail.videoDuration
                    notifyItemChanged(index, durationPayload)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val medium = media.getOrNull(position) as? Medium
        if (medium != null && payloads.isNotEmpty() && payloads.all { it === durationPayload }) {
            bindItem(holder.itemView, medium).videoDuration?.apply {
                text = medium.videoDuration.getFormattedDuration()
                beVisibleIf(medium.isVideo() && config.showThumbnailVideoDuration)
            }
        } else onBindViewHolder(holder, position)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(scrollUpdates)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        ++diffGeneration
        pendingUpdate = null
        recyclerView.removeCallbacks(drainUpdates)
        recyclerView.removeOnScrollListener(scrollUpdates)
        durationOverrides.clear()
        refreshScroll.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun updateDisplayFilenames(displayFilenames: Boolean) {
        this.displayFilenames = displayFilenames
        notifyDataSetChanged()
    }

    fun updateAnimateGifs(animateGifs: Boolean) {
        this.animateGifs = animateGifs
        notifyDataSetChanged()
    }

    fun updateCropThumbnails(cropThumbnails: Boolean) {
        this.cropThumbnails = cropThumbnails
        notifyDataSetChanged()
    }

    fun updateShowFileTypes(showFileTypes: Boolean) {
        this.showFileTypes = showFileTypes
        notifyDataSetChanged()
    }

    private fun setupThumbnail(view: View, medium: Medium) {
        val isSelected = selectedKeys.contains(medium.path.hashCode())
        bindItem(view, medium).apply {
            val padding = if (config.thumbnailSpacing <= 1) {
                config.thumbnailSpacing
            } else {
                0
            }

            mediaItemHolder.setPadding(padding, padding, padding, padding)

            favorite.beVisibleIf(medium.isFavorite && config.markFavoriteItems)

            playPortraitOutline?.beVisibleIf(medium.isVideo() || medium.isPortrait())
            if (medium.isVideo()) {
                playPortraitOutline?.setImageResource(
                    if (isListViewType) {
                        org.fossify.commons.R.drawable.ic_play_outline_vector
                    } else {
                        org.fossify.commons.R.drawable.ic_play_vector
                    }
                )
                playPortraitOutline?.beVisible()
            } else if (medium.isPortrait()) {
                playPortraitOutline?.setImageResource(R.drawable.ic_portrait_photo_vector)
                playPortraitOutline?.beVisibleIf(showFileTypes)
            }

            if (showFileTypes && (medium.isGIF() || medium.isRaw() || medium.isSVG())) {
                fileType?.setText(
                    when (medium.type) {
                        TYPE_GIFS -> R.string.gif
                        TYPE_RAWS -> R.string.raw
                        else -> R.string.svg
                    }
                )
                fileType?.beVisible()
            } else {
                fileType?.beGone()
            }

            mediumName.beVisibleIf(displayFilenames || isListViewType)
            mediumName.text = medium.name
            mediumName.tag = medium.path

            val showVideoDuration = medium.isVideo() && config.showThumbnailVideoDuration
            if (showVideoDuration) {
                videoDuration?.text = medium.videoDuration.getFormattedDuration()
            }
            videoDuration?.beVisibleIf(showVideoDuration)
            if (isListViewType) {
                videoDuration?.setTextColor(textColor)
            }

            mediumCheck.beVisibleIf(isSelected)
            if (isSelected) {
                mediumCheck.background?.applyColorFilter(properPrimaryColor)
                mediumCheck.applyColorFilter(contrastColor)
            }

            if (isListViewType) {
                mediaItemHolder.isSelected = isSelected
            }

            var path = medium.path
            if (hasOTGConnected && root.context.isPathOnOTG(path)) {
                path = path.getOTGPublicPath(root.context)
            }

            val roundedCorners = when {
                isListViewType -> ROUNDED_CORNERS_SMALL
                config.fileRoundedCorners -> ROUNDED_CORNERS_BIG
                else -> ROUNDED_CORNERS_NONE
            }

            val signature = medium.getKey()
            mediumThumbnail.setTag(R.id.media_bound_path, medium.path)
            val requestKey = listOf(path, signature, medium.type, cropThumbnails, roundedCorners, animateGifs,
                scrollHorizontally, rotatedImagePaths.contains(path))
            if (mediumThumbnail.getTag(R.id.thumbnail_request_key) != requestKey) {
                // Change the token before cancellation so an obsolete failure cannot start a fallback.
                mediumThumbnail.setTag(R.id.thumbnail_request_key, requestKey)
                mediumThumbnail.setTag(R.id.thumbnail_loaded_key, null)
                Picasso.get().cancelRequest(mediumThumbnail)
                Glide.with(activity).clear(mediumThumbnail)
                mediumThumbnail.setImageDrawable(null)
                mediumThumbnail.tag = path
                OxygenThumbnailPlaceholder.apply(activity, mediumThumbnail, roundedCorners)
                mediumThumbnail.scaleType = if (cropThumbnails) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                activity.loadImage(
                    type = medium.type,
                    path = path,
                    target = mediumThumbnail,
                    horizontalScroll = scrollHorizontally,
                    animateGifs = animateGifs,
                    cropThumbnails = cropThumbnails,
                    roundCorners = roundedCorners,
                    signature = signature,
                    skipMemoryCacheAtPaths = rotatedImagePaths,
                    crossFadeDuration = 0,
                    onLoaded = {
                        mediumThumbnail.post {
                            if (mediumThumbnail.getTag(R.id.thumbnail_request_key) == requestKey &&
                                mediumThumbnail.drawable != null) {
                                mediumThumbnail.setTag(R.id.thumbnail_loaded_key, requestKey)
                                onThumbnailLoaded?.invoke(medium.path, mediumThumbnail)
                            }
                        }
                    },
                    onError = {
                        if (mediumThumbnail.getTag(R.id.thumbnail_request_key) == requestKey) {
                            mediumThumbnail.scaleType = ImageView.ScaleType.CENTER
                            mediumThumbnail.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_vector_warning_colored))
                        }
                    }
                )
            }

            if (isListViewType) {
                mediumName.setTextColor(textColor)
                playPortraitOutline?.applyColorFilter(textColor)
            }
        }
    }

    private fun setupSection(view: View, section: ThumbnailSection) {
        ThumbnailSectionBinding.bind(view).apply {
            thumbnailSection.text = section.title
            thumbnailSection.setTextColor(textColor)
        }
    }

    override fun onChange(position: Int): String {
        val mediaPosition = FastScrollerBubble.findMediaIndex(media, position)
        val medium = mediaPosition?.let { media.getOrNull(it) as? Medium }
        return medium?.getBubbleText(sorting, activity, dateFormat, timeFormat)
            ?: activity.getString(R.string.unknown_file_location)
    }

    private fun bindItem(view: View, medium: Medium): MediaItemBinding {
        return if (isListViewType) {
            if (!medium.isVideo() && !medium.isPortrait()) {
                PhotoItemListBinding.bind(view).toMediaItemBinding()
            } else {
                VideoItemListBinding.bind(view).toMediaItemBinding()
            }
        } else {
            if (!medium.isVideo() && !medium.isPortrait()) {
                PhotoItemGridBinding.bind(view).toMediaItemBinding()
            } else {
                VideoItemGridBinding.bind(view).toMediaItemBinding()
            }
        }
    }
}
