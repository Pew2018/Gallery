package org.fossify.gallery.adapters

import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import org.fossify.gallery.helpers.AdapterCommitKind
import org.fossify.gallery.helpers.AdapterSubmission
import org.fossify.gallery.helpers.DiagnosticLevel
import org.fossify.gallery.helpers.DiagnosticSequence
import org.fossify.gallery.helpers.GalleryDiagnosticLogger
import org.fossify.gallery.helpers.GalleryListDiffExecutor
import org.fossify.gallery.helpers.MediaUpdatePolicy
import org.fossify.gallery.helpers.MediaUpdateReason
import org.fossify.gallery.helpers.MediaThumbnailPriorityPolicy
import androidx.core.view.doOnNextLayout
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.allViews
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.helpers.FavoritesScrollRestorer
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.Priority
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
import org.fossify.gallery.extensions.gridThumbnailRequestBuilder
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
import org.fossify.gallery.helpers.InteractiveGridThumbnail
import org.fossify.gallery.helpers.PhotoViewerTransition
import org.fossify.gallery.helpers.StaticPhotoHeader
import org.fossify.gallery.helpers.StaticPhotoThumbnail
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
                setupThumbnail(itemView, tmbItem, thumbnailLoadPriority(adapterPosition))
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

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val image = holder.itemView.findViewById<ImageView>(R.id.medium_thumbnail) ?: return
        if (!fastScrollActive) thumbnailLoad(image)?.start(Priority.HIGH)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed) {
            val itemView = holder.itemView
            val tmb = itemView.allViews.firstOrNull { it.id == R.id.medium_thumbnail }
            if (tmb is ImageView) {
                InteractiveGridThumbnail.restoreBeforeRebind(tmb)
                if (tmb.transitionName == PhotoViewerTransition.ELEMENT_NAME) tmb.transitionName = null
                cancelThumbnailLoad(tmb, "holder_recycled")
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
    internal fun removeMediaPaths(paths: Set<String>, onCommitted: () -> Unit = {}) {
        if (paths.isEmpty() || recyclerView.adapter !== this) return
        ++diffGeneration
        pendingUpdate = null
        recyclerView.removeCallbacks(drainUpdates)
        val remaining = org.fossify.gallery.helpers.MediaUpdatePolicy.withoutDeletedPaths(media, paths)
        updateMedia(remaining, preserveScroll = false, onCommitted = onCommitted,
            updateReason = MediaUpdateReason.DELETE_RESULT)
    }

    private fun getSelectedItems() = selectedKeys.mapNotNull { getItemWithKey(it) } as ArrayList<Medium>

    private fun getSelectedPaths() = getSelectedItems().map { it.path } as ArrayList<String>

    private fun getFirstSelectedItemPath() = getItemWithKey(selectedKeys.first())?.path

    private fun getItemWithKey(key: Int): Medium? = media.firstOrNull { (it as? Medium)?.path?.hashCode() == key } as? Medium

    var committedVersion: Long = 1L
        private set
    private var diagnosticAdapterId = 0L
    private var diagnosticGridRequest: Long? = null
    private var diagnosticSampleWindow = 0L
    private var diagnosticSamples = 0

    @Volatile private var diffGeneration = 0
    private val refreshScroll = FavoritesScrollRestorer()
    private var initialTopLayoutPending = false
    private data class PendingUpdate(
        val items: ArrayList<ThumbnailItem>, val preserveScroll: Boolean,
        val current: () -> Boolean, val committed: () -> Unit, val generation: Int,
        val requestId: Long?, val diagnosticsEnabled: Boolean,
        val reason: MediaUpdateReason, val sorting: Int, val submittedAt: Long,
        val snapshotVersion: Long?, val snapshotStage: String?, val forceReplace: Boolean = false,
        val diagnosticSubmission: AdapterSubmission? = null,
        val preparedDiff: PreparedDiff? = null,
        val pinToStart: () -> Boolean = { false },
    )
    private data class PreparedDiff(
        val result: DiffUtil.DiffResult, val oldCount: Int, val elapsedMs: Long, val finishedAt: Long,
    )
    private var pendingUpdate: PendingUpdate? = null
    private var diffRunning = false
    private var fastScrollActive = false
    private var viewerReturnPublicationHeld = false
    private var lastDeferredGeneration = -1
    private val drainUpdates = Runnable { submitPendingUpdate() }
    private val admitThumbViewport = Runnable { startPendingVisibleThumbnails() }
    private var thumbAdmissionAfterLayout: androidx.core.view.OneShotPreDrawListener? = null
    private val scrollUpdates = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (fastScrollActive) waitForThumbViewport()
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) cancelInitialTopLayout()
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) refreshScroll.clear()
            if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
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
                    diagnosticSnapshotVersion: Long? = null, diagnosticSnapshotStage: String? = null,
                    diagnosticTraceId: Long? = null, pinToStart: () -> Boolean = { false }) {
        if (!isCurrent()) {
            if (GalleryDiagnosticLogger.isEnabled()) GalleryDiagnosticLogger.event(
                DiagnosticLevel.DEBUG, "MediaUI", "adapter_update_skipped", diagnosticTraceId,
                mapOf("adapterId" to diagnosticId(), "reason" to "stale_at_submit",
                    "requestGeneration" to diagnosticRequestId, "adapterVersion" to committedVersion)
            )
            return
        }
        val diagnosticsEnabled = org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()
        if (diagnosticsEnabled) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_submit", diagnosticRequestId,
            mapOf("itemCount" to newMedia.size, "preserveScroll" to preserveScroll,
                "diffGeneration" to (diffGeneration + 1), "adapterId" to System.identityHashCode(this),
                "adapterVersion" to committedVersion, "snapshotVersion" to diagnosticSnapshotVersion,
                "snapshotStage" to diagnosticSnapshotStage, "updateReason" to updateReason.name)
        )
        pendingUpdate?.let { logAdapterObservation(it, "adapter_update_skipped", "replaced") }
        if (diagnosticsEnabled && pendingUpdate != null) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_submit_replaced", diagnosticRequestId,
            mapOf("itemCount" to newMedia.size, "replacedGeneration" to pendingUpdate?.generation,
                "diffGeneration" to (diffGeneration + 1), "adapterId" to System.identityHashCode(this))
        )
        pendingUpdate = PendingUpdate(snapshot(newMedia), preserveScroll, isCurrent, onCommitted, ++diffGeneration,
            diagnosticRequestId, diagnosticsEnabled, updateReason, effectiveSorting,
            android.os.SystemClock.elapsedRealtime(),
            diagnosticSnapshotVersion, diagnosticSnapshotStage, pinToStart = pinToStart,
            diagnosticSubmission = if (diagnosticsEnabled) AdapterSubmission(
                diagnosticId(), diffGeneration, diagnosticTraceId
            ) else null)
        pendingUpdate?.let { logAdapterObservation(it, "adapter_update_submitted", "submitted") }
        recyclerView.removeCallbacks(drainUpdates)
        submitPendingUpdate()
    }

    /** One in-flight diff plus the newest pending catalog. Never queue every scan preview. */
    private fun submitPendingUpdate() {
        val pending = pendingUpdate ?: return
        if (!canCommit(pending)) {
            logAdapterObservation(pending, "adapter_update_skipped", "stale_or_detached")
            pendingUpdate = null
            return
        }
        if (deferCommit(pending)) return
        pendingUpdate = null
        pending.preparedDiff?.let {
            commitDiff(pending, it)
            return
        }
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
            restoreRefreshScroll(pending)
            logUpdate(pending, "adapter_snapshot_fast_commit", "same_content")
            logAdapterCommit(pending, AdapterCommitKind.NO_OP)
            pending.committed()
            return
        }
        // The proof is the content itself: every previously displayed row is still
        // at the same index and the incoming list is strictly longer. Stage names are
        // not frame evidence, so any scan publication with that prefix qualifies.
        // A date-descending insert at the head breaks the prefix and still diffs.
        if (pending.preserveScroll && pending.reason == MediaUpdateReason.SCAN &&
            MediaUpdatePolicy.isContentAppend(media, incoming)) {
            val oldCount = media.size
            sorting = pending.sorting
            media = incoming
            committedVersion++
            currentMediaHash = media.hashCode()
            listener?.updateMediaGridDecoration(media)
            notifyItemRangeInserted(oldCount, incoming.size - oldCount)
            restoreRefreshScroll(pending)
            logUpdate(pending, "adapter_snapshot_fast_commit", "append", oldCount)
            logAdapterCommit(pending, AdapterCommitKind.APPEND)
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
                    logAdapterObservation(pending, "adapter_update_skipped", "stale_diff")
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
                val prepared = result.getOrElse {
                    recoverDiff(pending, it)
                    return@runOnUiThread
                }
                pendingUpdate = pending.copy(preparedDiff = PreparedDiff(
                    prepared, old.size, (diffFinished - diffStarted).coerceAtLeast(0L), diffFinished
                ))
                submitPendingUpdate()
            }
        }
        try { diffExecutor.execute(work) } catch (error: java.util.concurrent.RejectedExecutionException) {
            diffRunning = false
            recoverDiff(pending, error)
        }
    }

    private fun permitsFlingCommit(pending: PendingUpdate): Boolean =
        pending.reason == MediaUpdateReason.SCAN && pending.preserveScroll

    private fun deferCommit(pending: PendingUpdate): Boolean {
        if (fastScrollActive || viewerReturnPublicationHeld) {
            logUpdate(pending, "adapter_update_deferred", if (viewerReturnPublicationHeld) "viewer_return" else "fast_scroll")
            return true
        }
        val computingLayout = recyclerView.isComputingLayout
        val scrolling = recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING ||
            recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING && !permitsFlingCommit(pending)
        if (!computingLayout && !scrolling) return false
        logUpdate(pending, "adapter_update_deferred", "layout_or_scroll")
        // Touch/return ownership releases via its listener. A layout boundary
        // needs only one frame callback, with the already computed diff intact.
        if (computingLayout && !scrolling) {
            recyclerView.removeCallbacks(drainUpdates)
            recyclerView.postOnAnimation(drainUpdates)
        }
        return true
    }

    private fun commitDiff(pending: PendingUpdate, prepared: PreparedDiff) {
        val incoming = pending.items
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
        prepared.result.dispatchUpdatesTo(this)
        logAdapterCommit(pending, AdapterCommitKind.DIFF)
        if (pending.diagnosticsEnabled)
            GalleryDiagnosticLogger.event(
                DiagnosticLevel.DEBUG, "MediaUI", "adapter_commit",
                pending.requestId, mapOf(
                    "itemCount" to incoming.size, "gridVersion" to committedVersion,
                    "diffGeneration" to pending.generation, "adapterId" to System.identityHashCode(this),
                    "oldCount" to prepared.oldCount, "diffMs" to prepared.elapsedMs,
                    "publicationWaitMs" to (android.os.SystemClock.elapsedRealtime() - prepared.finishedAt),
                    "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - pending.submittedAt),
                    "updateReason" to pending.reason.name, "commitStrategy" to "diff",
                    "snapshotVersion" to pending.snapshotVersion, "snapshotStage" to pending.snapshotStage,
                    "adapterVersion" to committedVersion, "scopeKey" to GalleryDiagnosticLogger.anonymousId(path)
                )
            )
        restoreRefreshScroll(pending)
        pending.committed()
        submitPendingUpdate()
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
        rememberRefreshScroll(pending)
        sorting = pending.sorting
        media = incoming
        committedVersion++
        currentMediaHash = media.hashCode()
        listener?.updateMediaGridDecoration(media)
        notifyItemRangeChanged(0, incoming.size)
        restoreRefreshScroll(pending)
        if (pending.diagnosticsEnabled)
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "adapter_reorder_commit",
                pending.requestId, mapOf("itemCount" to incoming.size, "gridVersion" to committedVersion,
                    "diffGeneration" to pending.generation, "adapterId" to System.identityHashCode(this))
            )
        logUpdate(pending, "adapter_snapshot_fast_commit", "reorder")
        logAdapterCommit(pending, AdapterCommitKind.REORDER)
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
        logAdapterCommit(pending, AdapterCommitKind.REPLACE)
        restoreRefreshScroll(pending)
        pending.committed()
    }

    private fun canCommit(pending: PendingUpdate): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return recyclerView.adapter === this && pending.current()
    }

    /** A thumb drag can leave RecyclerView.scrollState idle while scrollBy() moves it. */
    fun setFastScrollActive(active: Boolean, submitPending: Boolean = true) {
        val wasActive = fastScrollActive
        fastScrollActive = active
        if (wasActive != active && GalleryDiagnosticLogger.isEnabled()) {
            GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "thumbnail_thumb_state",
                diagnosticGridRequest, mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                    "active" to active, "scrollState" to recyclerView.scrollState))
        }
        if (active) {
            cancelInitialTopLayout()
            refreshScroll.clear()
            recyclerView.removeCallbacks(drainUpdates)
            thumbAdmissionAfterLayout?.removeListener()
            thumbAdmissionAfterLayout = null
            waitForThumbViewport()
        } else {
            if (submitPending) submitPendingUpdate()
            if (wasActive) {
                recyclerView.removeCallbacks(admitThumbViewport)
                beginThumbnailRecovery()
                // Let a pending list commit and its layout choose the final set of holders.
                recyclerView.postOnAnimation(admitThumbViewport)
            }
        }
    }

    // --- Settled-viewport thumbnail recovery accounting ----------------------
    // Measures the interval the user actually notices: from releasing the thumb to the
    // first visible resource and to the whole visible viewport being served. Every
    // number is tied to the adapter version and to the rows bound at release time, so
    // recycled or re-bound holders cannot be counted as recovered.
    private var thumbRecoveryGeneration = 0
    private var thumbRecoveryStartedAt = 0L
    private var thumbRecoveryAdapterVersion = -1L
    private val thumbRecoveryPending = LinkedHashSet<String>()
    private var thumbRecoveryFirstReadyMs = -1L

    private fun beginThumbnailRecovery() {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        val bounds = android.graphics.Rect()
        val pending = LinkedHashSet<String>()
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val image = child.findViewById<ImageView>(R.id.medium_thumbnail) ?: continue
            val load = thumbnailLoad(image) ?: continue
            if (load.pending == null) continue
            val row = media.getOrNull(recyclerView.getChildAdapterPosition(child)) as? Medium ?: continue
            if (image.getTag(R.id.media_bound_path) != row.path) continue
            if (!image.isShown || !image.getLocalVisibleRect(bounds)) continue
            pending.add(row.path)
        }
        thumbRecoveryGeneration++
        thumbRecoveryStartedAt = android.os.SystemClock.elapsedRealtime()
        thumbRecoveryAdapterVersion = committedVersion
        thumbRecoveryFirstReadyMs = -1L
        thumbRecoveryPending.clear()
        thumbRecoveryPending.addAll(pending)
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "thumbnail_recovery_window",
            diagnosticGridRequest, mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                "recoveryGeneration" to thumbRecoveryGeneration, "visiblePending" to pending.size))
    }

    /** Called once a visible thumbnail resource has actually been bound to its holder. */
    internal fun recordThumbnailReady(path: String) {
        if (thumbRecoveryPending.isEmpty()) return
        if (!thumbRecoveryPending.remove(path)) return
        if (!GalleryDiagnosticLogger.isEnabled()) return
        val elapsed = android.os.SystemClock.elapsedRealtime() - thumbRecoveryStartedAt
        if (thumbRecoveryFirstReadyMs < 0L) {
            thumbRecoveryFirstReadyMs = elapsed
            GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "thumbnail_recovery_first_ready",
                diagnosticGridRequest, mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                    "recoveryGeneration" to thumbRecoveryGeneration, "sinceFastScrollEndMs" to elapsed,
                    "remaining" to thumbRecoveryPending.size))
        }
        if (thumbRecoveryPending.isEmpty()) {
            GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "thumbnail_recovery_complete",
                diagnosticGridRequest, mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                    "recoveryGeneration" to thumbRecoveryGeneration, "sinceFastScrollEndMs" to elapsed,
                    "firstReadyMs" to thumbRecoveryFirstReadyMs,
                    "adapterVersionChanged" to (committedVersion != thumbRecoveryAdapterVersion)))
        }
    }

    private fun waitForThumbViewport() {
        recyclerView.removeCallbacks(admitThumbViewport)
        // Allow previews when the thumb pauses, while coalescing consecutive positions.
        recyclerView.postDelayed(admitThumbViewport, THUMB_VIEWPORT_QUIET_MS)
    }

    private fun startPendingVisibleThumbnails() {
        if (recyclerView.adapter !== this || activity.isFinishing || activity.isDestroyed) return
        if (recyclerView.isLayoutRequested && recyclerView.hasPendingAdapterUpdates()) {
            // No work may escape into the previous viewport before the queued layout.
            if (!fastScrollActive && thumbAdmissionAfterLayout == null) {
                thumbAdmissionAfterLayout = androidx.core.view.OneShotPreDrawListener.add(recyclerView) {
                    thumbAdmissionAfterLayout = null
                    if (!fastScrollActive) startPendingVisibleThumbnails()
                }
            }
            return
        }
        val bounds = android.graphics.Rect()
        var started = 0
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val image = child.findViewById<ImageView>(R.id.medium_thumbnail) ?: continue
            val load = thumbnailLoad(image) ?: continue
            val position = recyclerView.getChildAdapterPosition(child)
            val row = media.getOrNull(position) as? Medium ?: continue
            if (load.pending != null && image.getTag(R.id.media_bound_path) == row.path &&
                image.getTag(R.id.thumbnail_request_key) === load.token &&
                image.isShown && image.getLocalVisibleRect(bounds)) {
                load.start(Priority.HIGH)
                started++
            }
        }
        if (GalleryDiagnosticLogger.isEnabled()) {
            GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "thumbnail_viewport_admitted",
                diagnosticGridRequest, mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                    "phase" to if (fastScrollActive) "THUMB_PAUSE" else "THUMB_RELEASE", "started" to started,
                    "recoveryGeneration" to thumbRecoveryGeneration,
                    "sinceFastScrollEndMs" to if (thumbRecoveryStartedAt == 0L) -1L
                    else (android.os.SystemClock.elapsedRealtime() - thumbRecoveryStartedAt)))
        }
    }

    internal fun setViewerReturnPublicationHeld(held: Boolean) {
        viewerReturnPublicationHeld = held
        if (held) {
            refreshScroll.clear()
            recyclerView.removeCallbacks(drainUpdates)
        }
    }

    fun submitPendingUpdateAfterFastScroll() {
        if (!fastScrollActive) submitPendingUpdate()
    }

    internal fun clearRefreshScroll() = refreshScroll.clear()

    private fun rememberRefreshScroll(pending: PendingUpdate) {
        if (pending.pinToStart() || !pending.preserveScroll || pending.reason == MediaUpdateReason.USER_SORT) {
            refreshScroll.clear()
            return
        }
        // Two commits can arrive before RecyclerView lays out the first one.
        // In that window the attached holders still describe the prior list.
        if (!recyclerView.hasPendingAdapterUpdates()) refreshScroll.clear()
        refreshScroll.remember(recyclerView, media.map { (it as? Medium)?.path }, keepNeighbors = true,
            allowFling = permitsFlingCommit(pending))
    }

    private fun cancelInitialTopLayout() {
        if (!initialTopLayoutPending) return
        initialTopLayoutPending = false
        (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.scrollToPosition(RecyclerView.NO_POSITION)
    }

    private fun restoreRefreshScroll(pending: PendingUpdate) {
        if (pending.pinToStart() && canCommit(pending)) {
            refreshScroll.clear()
            (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.let { manager ->
                manager.scrollToPositionWithOffset(0, 0)
                initialTopLayoutPending = true
                recyclerView.doOnNextLayout { initialTopLayoutPending = false }
            }
            return
        }
        if (refreshScroll.pending == null) return
        if (!pending.preserveScroll || pending.reason == MediaUpdateReason.USER_SORT) {
            refreshScroll.clear()
            return
        }
        val paths = media.map { (it as? Medium)?.path }
        if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
            refreshScroll.restoreDuringFling(recyclerView, paths)
            return
        }
        val version = committedVersion
        refreshScroll.restore(recyclerView, paths, true, isCurrent = {
            !fastScrollActive && version == committedVersion && canCommit(pending) &&
                recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
        })
    }

    private fun diagnosticId(): Long {
        if (diagnosticAdapterId == 0L) diagnosticAdapterId = DiagnosticSequence.next()
        return diagnosticAdapterId
    }

    internal fun logInitialSnapshot(request: Long?, traceId: Long?) {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        diagnosticGridRequest = traceId
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "adapter_initial_snapshot", traceId,
            mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                "requestGeneration" to request, "itemCount" to media.size))
    }

    private fun logAdapterCommit(pending: PendingUpdate, kind: AdapterCommitKind) {
        diagnosticGridRequest = pending.diagnosticSubmission?.requestId
        logAdapterObservation(pending, "adapter_update_committed", kind.name, kind.visibleListChanged)
    }

    private fun logAdapterObservation(pending: PendingUpdate, event: String, strategy: String,
                                      visibleListChanged: Boolean? = null) {
        val submission = pending.diagnosticSubmission ?: return
        if (!GalleryDiagnosticLogger.isEnabled()) return
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", event, submission.requestId,
            mapOf("adapterId" to submission.adapterId, "diffGeneration" to submission.generation,
                "adapterVersion" to committedVersion, "requestGeneration" to pending.requestId,
                "itemCount" to pending.items.size, "strategy" to strategy, "visibleListChanged" to visibleListChanged,
                "snapshotVersion" to pending.snapshotVersion, "snapshotStage" to pending.snapshotStage,
                "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - pending.submittedAt)))
    }

    private var thumbnailRequestSequence = 0L
    private var thumbnailTraceWindow = 0L
    private var thumbnailTraceSamples = 0

    internal fun staticThumbnail(image: ImageView): StaticPhotoThumbnail? =
        (image.getTag(R.id.media_thumbnail_load) as? ThumbnailLoad)?.takeIf {
            it.token === image.getTag(R.id.thumbnail_loaded_key)
        }?.staticThumbnail?.takeIf { it.first.get() === image.drawable }?.second

    private class ThumbnailLoad(
        val token: Any,
        val trace: ((String, String?, Priority?, String?) -> Unit)?,
        var pending: ((Priority) -> Unit)? = null
    ) {
        var priority: Priority? = null
            private set
        var finished = false
        var staticThumbnail: Pair<WeakReference<Drawable>, StaticPhotoThumbnail>? = null

        fun start(priority: Priority) {
            val request = pending ?: return
            pending = null
            this.priority = priority
            trace?.invoke("REQUEST_STARTED", null, priority, null)
            request(priority)
        }
    }

    private fun thumbnailLoad(image: ImageView) =
        image.getTag(R.id.media_thumbnail_load) as? ThumbnailLoad

    private fun cancelThumbnailLoad(image: ImageView, reason: String) {
        val load = thumbnailLoad(image) ?: return
        if (!load.finished) load.trace?.invoke("REQUEST_CANCELLED", reason, load.priority, null)
        load.pending = null
        image.setTag(R.id.media_thumbnail_load, null)
    }

    private fun thumbnailTrace(
        image: ImageView, path: String, bindPriority: Priority
    ): ((String, String?, Priority?, String?) -> Unit)? {
        if (!GalleryDiagnosticLogger.isEnabled()) return null
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - thumbnailTraceWindow >= 1_000L) {
            thumbnailTraceWindow = now
            thumbnailTraceSamples = 0
        }
        if (thumbnailTraceSamples >= 4) return null
        thumbnailTraceSamples++
        val mediaId = GalleryDiagnosticLogger.anonymousId(path)
        val token = ++thumbnailRequestSequence
        val version = committedVersion
        val sourceRequest = diagnosticGridRequest
        return { stage, reason, priority, cache ->
            if (GalleryDiagnosticLogger.isEnabled()) {
                val bounds = android.graphics.Rect()
                val viewState = when {
                    !image.isAttachedToWindow -> "DETACHED"
                    image.isShown && image.getLocalVisibleRect(bounds) -> "VISIBLE"
                    else -> "ATTACHED"
                }
                val scrollState = if (fastScrollActive) "FAST_${recyclerView.scrollState}" else recyclerView.scrollState.toString()
                GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", "thumbnail_request", sourceRequest,
                    mapOf("stage" to stage, "reason" to reason, "adapterId" to diagnosticId(),
                        "adapterVersion" to version, "holderId" to System.identityHashCode(image),
                        "bindGeneration" to token, "thumbnailToken" to token, "mediaId" to mediaId,
                        "priority" to priority?.name, "bindPriority" to bindPriority.name, "cache" to cache,
                        "viewState" to viewState, "size" to "${image.width}x${image.height}",
                        "visibleSize" to "${bounds.width()}x${bounds.height()}", "scrollState" to scrollState,
                        "presented" to "NOT_OBSERVABLE"))
            }
        }
    }

    private fun logVisibleDrawable(image: ImageView, medium: Medium, key: Any, event: String, reason: String) {
        if (!GalleryDiagnosticLogger.isEnabled() || !image.isAttachedToWindow || !image.isShown ||
            recyclerView.windowVisibility != View.VISIBLE) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - diagnosticSampleWindow >= 1_000L) {
            diagnosticSampleWindow = now
            diagnosticSamples = 0
        }
        if (diagnosticSamples >= 8) return
        val visible = android.graphics.Rect()
        if (!image.getLocalVisibleRect(visible)) return
        diagnosticSamples++
        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaUI", event, diagnosticGridRequest,
            mapOf("adapterId" to diagnosticId(), "adapterVersion" to committedVersion,
                "holderId" to System.identityHashCode(image), "reason" to reason,
                "width" to image.width, "height" to image.height,
                "visibleWidth" to visible.width(), "visibleHeight" to visible.height(),
                "scrollState" to recyclerView.scrollState, "computingLayout" to recyclerView.isComputingLayout,
                "mediaId" to GalleryDiagnosticLogger.anonymousId(medium.path),
                "drawableKeyId" to GalleryDiagnosticLogger.anonymousId(key.toString())))
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
        recyclerView.removeCallbacks(admitThumbViewport)
        thumbAdmissionAfterLayout?.removeListener()
        thumbAdmissionAfterLayout = null
        fastScrollActive = false
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

    private fun thumbnailLoadPriority(position: Int): Priority {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return Priority.NORMAL
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        return MediaThumbnailPriorityPolicy.priority(position, firstVisible, lastVisible)
    }

    /**
     * Starts the queued thumbnail request of [path] at HIGH because the Grid is waiting
     * for exactly that drawable as the Viewer's shared-element return target.
     *
     * The 2026-11-24 device log showed a return target sitting at
     * `readiness=pixels_unavailable` for ~410 ms, i.e. the holder was bound and matched
     * the media identity but its request had not been dispatched yet - a `Priority`
     * decision taken when the holder was bound for a different reason. Only a request
     * that has not started is promoted: an in-flight decode already used its own
     * priority and must not be issued twice, and a finished one cannot be re-prioritised
     * without a new request, so both are left alone.
     */
    internal fun promoteQueuedThumbnail(path: String): Boolean {
        val grid = recyclerView ?: return false
        for (index in 0 until grid.childCount) {
            val image = grid.getChildAt(index).findViewById<ImageView>(R.id.medium_thumbnail) ?: continue
            if (image.getTag(R.id.media_bound_path) != path) continue
            val load = thumbnailLoad(image) ?: continue
            if (load.pending == null) continue
            load.start(Priority.HIGH)
            return true
        }
        return false
    }

    private fun setupThumbnail(view: View, medium: Medium, thumbnailPriority: Priority) {
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
            val requestKey = listOf(path, signature, medium.type, cropThumbnails, roundedCorners, animateGifs,
                scrollHorizontally, rotatedImagePaths.contains(path))
            if (mediumThumbnail.getTag(R.id.media_bound_path) != medium.path) {
                InteractiveGridThumbnail.restoreBeforeRebind(mediumThumbnail)
                if (mediumThumbnail.transitionName == PhotoViewerTransition.ELEMENT_NAME) mediumThumbnail.transitionName = null
            }
            mediumThumbnail.setTag(R.id.media_bound_path, medium.path)
            if (mediumThumbnail.getTag(R.id.thumbnail_request_key) != requestKey) {
                InteractiveGridThumbnail.restoreBeforeRebind(mediumThumbnail)
                cancelThumbnailLoad(mediumThumbnail, "binding_key_changed")
                // Change the token before cancellation so an obsolete failure cannot start a fallback.
                mediumThumbnail.setTag(R.id.thumbnail_request_key, requestKey)
                mediumThumbnail.setTag(R.id.thumbnail_loaded_key, null)
                Picasso.get().cancelRequest(mediumThumbnail)
                Glide.with(activity).clear(mediumThumbnail)
                mediumThumbnail.setImageDrawable(null)
                logVisibleDrawable(mediumThumbnail, medium, requestKey, "visible_drawable_cleared", "binding_key_changed")
                mediumThumbnail.tag = path
                OxygenThumbnailPlaceholder.apply(activity, mediumThumbnail, roundedCorners)
                mediumThumbnail.scaleType = if (cropThumbnails) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                val trace = thumbnailTrace(mediumThumbnail, medium.path, thumbnailPriority)
                val load = ThumbnailLoad(requestKey, trace)
                mediumThumbnail.setTag(R.id.media_thumbnail_load, load)
                trace?.invoke("REQUEST_CREATED", null, null, null)
                load.pending = { priority ->
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
                        priority = priority,
                        onLoadStage = trace?.let {
                            { stage, reason, cache ->
                                if (stage == "REQUEST_FAILED") load.finished = true
                                it(stage, reason, priority, cache)
                            }
                        },
                        onStaticThumbnail = if (medium.isImage() && !medium.isApng() && StaticPhotoHeader.isCandidate(path)) {
                            { drawable, evidence -> load.staticThumbnail = WeakReference(drawable) to evidence }
                        } else null,
                        onLoaded = {
                            if (mediumThumbnail.getTag(R.id.thumbnail_request_key) === load.token &&
                                mediumThumbnail.drawable != null) {
                                load.finished = true
                                mediumThumbnail.setTag(R.id.thumbnail_loaded_key, load.token)
                                trace?.invoke("DRAWABLE_BOUND", null, priority, null)
                                if (trace != null && mediumThumbnail.isAttachedToWindow) {
                                    androidx.core.view.OneShotPreDrawListener.add(mediumThumbnail) {
                                        if (mediumThumbnail.getTag(R.id.thumbnail_loaded_key) === load.token &&
                                            mediumThumbnail.isShown &&
                                            mediumThumbnail.getLocalVisibleRect(android.graphics.Rect())) {
                                            trace("FRAME_OBSERVED", "pre_draw", priority, null)
                                        }
                                    }
                                }
                                logVisibleDrawable(mediumThumbnail, medium, load.token, "visible_drawable_ready", "current_load")
                                recordThumbnailReady(medium.path)
                                onThumbnailLoaded?.invoke(medium.path, mediumThumbnail)
                            }
                        },
                        onError = {
                            if (mediumThumbnail.getTag(R.id.thumbnail_request_key) === load.token) {
                                mediumThumbnail.scaleType = ImageView.ScaleType.CENTER
                                mediumThumbnail.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_vector_warning_colored))
                            }
                        }
                    )
                }
                // RecyclerView binds distant landing positions before its visible range updates.
                // Keep near/initial high-priority work outside a moving thumb gesture.
                // Other holders start when attached or admitted with the settled thumb viewport.
                if (!fastScrollActive && (mediumThumbnail.isAttachedToWindow || thumbnailPriority == Priority.HIGH)) {
                    load.start(Priority.HIGH)
                }
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

    /**
     * Builds the exact thumbnail request a rebound holder would issue for [medium],
     * for the Viewer's bounded return prefetch (Phase 2.2). The returned key is the
     * holder's own request identity, so a prefetched resource warms the engine entry
     * the return will read, and the reserve is keyed identically to the holder.
     *
     * The size must be the Grid's measured thumbnail size - it participates in the
     * Glide cache key, so an approximation would decode pixels the return cannot use.
     */
    fun returnPrefetchRequest(medium: Medium, width: Int, height: Int): Pair<Any, RequestBuilder<Drawable>>? {
        if (width <= 0 || height <= 0) return null
        var path = medium.path
        if (hasOTGConnected && activity.isPathOnOTG(path)) {
            path = path.getOTGPublicPath(activity)
        }
        val roundedCorners = when {
            isListViewType -> ROUNDED_CORNERS_SMALL
            config.fileRoundedCorners -> ROUNDED_CORNERS_BIG
            else -> ROUNDED_CORNERS_NONE
        }
        val signature = medium.getKey()
        val requestKey = listOf(path, signature, medium.type, cropThumbnails, roundedCorners, animateGifs,
            scrollHorizontally, rotatedImagePaths.contains(path))
        // Build through the application RequestManager: the Grid Activity is stopped
        // while the Viewer is open, and an activity-scoped manager pauses its requests
        // on stop - the preparation would only run when the Viewer already returned.
        // The RequestManager is not part of Glide's engine key, so the cache entry and
        // the request identity stay exactly the holder's.
        val builder = activity.applicationContext.gridThumbnailRequestBuilder(
            path = path,
            cropThumbnails = cropThumbnails,
            roundCorners = roundedCorners,
            signature = signature,
            skipMemoryCacheAtPaths = rotatedImagePaths,
            animate = animateGifs,
            crossFadeDuration = 0,
        ).override(width, height)
        return requestKey to builder
    }

    private companion object {
        const val THUMB_VIEWPORT_QUIET_MS = 48L
    }

}
