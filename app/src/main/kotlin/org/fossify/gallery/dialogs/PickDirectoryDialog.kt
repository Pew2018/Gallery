package org.fossify.gallery.dialogs

import android.graphics.Color
import android.content.res.ColorStateList
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.gallery.helpers.OxygenThemeEngine
import com.google.android.material.shape.RelativeCornerSize
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDefaultCopyDestinationPath
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databinding.DialogDirectoryPickerBinding
import org.fossify.gallery.extensions.addTempFolderIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.getDirsToShow
import org.fossify.gallery.extensions.getDistinctPath
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.models.Directory
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.OxygenClassicPalette
import org.fossify.gallery.helpers.OxygenThemeColors

class PickDirectoryDialog(
    val activity: BaseSimpleActivity,
    val sourcePath: String,
    showOtherFolderButton: Boolean,
    val showFavoritesBin: Boolean,
    val isPickingCopyMoveDestination: Boolean,
    val isPickingFolderForWidget: Boolean,
    val callback: (path: String) -> Unit
) {
    private fun styleFastScroller() {
        org.fossify.gallery.helpers.OxygenFastScroller.apply(
            binding.directoriesFastscroller, binding.directoriesGrid,
            org.fossify.gallery.helpers.OxygenThemeEngine.snapshot(activity, activity.config)
        )
    }
    private var dialog: AlertDialog? = null
    private val selectionDelivered = AtomicBoolean(false)
    private var directoryLoadVersion = 0
    private var isLoadingDirectories = false

    private fun deliverSelectionOnce(path: String) {
        if (selectionDelivered.compareAndSet(false, true)) callback(path)
    }
    private var shownDirectories = ArrayList<Directory>()
    private var allDirectories = ArrayList<Directory>()
    private var openedSubfolders = arrayListOf("")
    private var binding = DialogDirectoryPickerBinding.inflate(activity.layoutInflater)
    private var isGridViewType = activity.config.viewTypeFolders == VIEW_TYPE_GRID
    private var showHidden = activity.config.shouldShowHidden
    private var currentPathPrefix = ""
    private val config = activity.config
    private val searchView = binding.folderSearchView
    private val searchEditText = searchView.binding.topToolbarSearch
    private val searchBarContainer = searchView.binding.searchBarContainer

    init {
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).apply {
            orientation = if (activity.config.scrollHorizontally && isGridViewType) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            spanCount = if (isGridViewType) activity.config.dirColumnCnt else 1
        }

        binding.directoriesFastscroller.updateColors(activity.getProperPrimaryColor())
        styleFastScroller()
        binding.directoriesLoading.indeterminateTintList = ColorStateList.valueOf(
            OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
        )

        configureSearchView()

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)

        if (showOtherFolderButton) {
            builder.setNeutralButton(R.string.other_folder) { dialogInterface, i -> showOtherFolder() }
        }

        builder.apply {
            activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.select_destination) { alertDialog ->
                dialog = alertDialog
                OxygenClassicDialog.apply(activity, alertDialog, binding.root)
                binding.root.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(OxygenThemeEngine.dialogSurface(activity, config))
                    cornerRadius = 3f * activity.resources.displayMetrics.density
                }
                binding.root.clipToOutline = true
                binding.directoriesCurrentPath.setTextColor(activity.getProperTextColor())
                binding.directoriesShowHidden.apply {
                    // Commons is a Material FAB. Restore the circular 2017 shape
                    // without replacing its ripple, touch handling or action.
                    shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                        .setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                    backgroundTintList = ColorStateList.valueOf(
                        OxygenThemeColors.toolbarBackground(activity.getProperPrimaryColor())
                    )
                    clearColorFilter()
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                }
                binding.directoriesShowHidden.beVisibleIf(!context.config.shouldShowHidden)
                binding.directoriesShowHidden.setOnClickListener {
                    activity.handleHiddenFolderPasswordProtection {
                        binding.directoriesShowHidden.beGone()
                        showHidden = true
                        fetchDirectories(true)
                    }
                }

                alertDialog.onBackPressedDispatcher.addCallback(alertDialog) {
                    if (searchView.isSearchOpen) {
                        searchView.closeSearch()
                    } else if (activity.config.groupDirectSubfolders) {
                        if (currentPathPrefix.isEmpty()) {
                            isEnabled = false
                            alertDialog.onBackPressedDispatcher.onBackPressed()
                        } else {
                            openedSubfolders.removeAt(openedSubfolders.lastIndex)
                            currentPathPrefix = openedSubfolders.last()
                            gotDirectories(allDirectories)
                        }
                    } else {
                        isEnabled = false
                        alertDialog.onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }

        fetchDirectories(false)
    }

    private fun configureSearchView() = with(searchView) {
        updateHintText(context.getString(org.fossify.commons.R.string.search_folders))
        searchEditText.imeOptions = EditorInfo.IME_ACTION_DONE

        toggleHideOnScroll(!config.scrollHorizontally)
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenu.updateSearchViewUi() {
        requireToolbar().beInvisible()
        updateColors()
        setBackgroundColor(Color.TRANSPARENT)
        searchBarContainer.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun MySearchMenu.setSearchViewListeners() {
        onSearchOpenListener = {
            updateSearchViewLeftIcon(org.fossify.commons.R.drawable.ic_cross_vector)
        }

        onSearchClosedListener = {
            searchEditText.clearFocus()
            activity.hideKeyboard(searchEditText)
            updateSearchViewLeftIcon(org.fossify.commons.R.drawable.ic_search_vector)
        }

        onSearchTextChangedListener = { text ->
            filterFolderListBySearchQuery(text)
        }
    }

    private fun updateSearchViewLeftIcon(iconResId: Int) = with(searchView.binding.topToolbarSearchIcon) {
        post {
            setImageResource(iconResId)
        }
    }

    private fun filterFolderListBySearchQuery(query: String) {
        val adapter = binding.directoriesGrid.adapter as? DirectoryAdapter
        var dirsToShow = allDirectories
        if (query.isNotEmpty()) {
            dirsToShow = dirsToShow.filter { it.name.contains(query, true) }.toMutableList() as ArrayList
        }
        dirsToShow = activity.getSortedDirectories(dirsToShow)
        checkPlaceholderVisibility(dirsToShow)

        val filteredFolderListUpdated = adapter?.dirs != dirsToShow
        if (filteredFolderListUpdated) {
            adapter?.updateDirs(dirsToShow)

            binding.directoriesGrid.apply {
                post {
                    scrollToPosition(0)
                }
            }
        }
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) = with(binding) {
        // Loading is not empty: distinguish the two without covering cached rows.
        val empty = dirs.isEmpty() && !isLoadingDirectories
        directoriesEmptyPlaceholder.beVisibleIf(empty)
        if (empty) {
            directoriesEmptyPlaceholder.text = root.context.getString(org.fossify.commons.R.string.no_items_found)
        }
        directoriesFastscroller.beVisibleIf(dirs.isNotEmpty())
    }

    private fun fetchDirectories(forceShowHiddenAndExcluded: Boolean) {
        val requestVersion = ++directoryLoadVersion
        isLoadingDirectories = true
        // Preserve already visible directories during a hidden-folder refresh.
        binding.directoriesLoading.visibility =
            if (shownDirectories.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        if (shownDirectories.isEmpty()) {
            binding.directoriesEmptyPlaceholder.beGone()
            binding.directoriesFastscroller.beGone()
        }
        activity.getCachedDirectories(forceShowHidden = forceShowHiddenAndExcluded, forceShowExcluded = forceShowHiddenAndExcluded) { directories ->
            directories.forEach { directory ->
                directory.subfoldersMediaCount = directory.mediaCnt
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || directoryLoadVersion != requestVersion ||
                    dialog?.isShowing == false) return@runOnUiThread
                isLoadingDirectories = false
                binding.directoriesLoading.visibility = android.view.View.GONE
                allDirectories.clear()
                gotDirectories(activity.addTempFolderIfNeeded(directories))
            }
        }
    }

    private fun showOtherFolder() {
        activity.hideKeyboard(searchEditText)
        FilePickerDialog(
            activity,
            activity.getDefaultCopyDestinationPath(showHidden, sourcePath),
            !isPickingCopyMoveDestination && !isPickingFolderForWidget,
            showHidden,
            true,
            true
        ) {
            config.lastCopyPath = it
            activity.handleLockedFolderOpening(it) { success ->
                if (success) {
                    deliverSelectionOnce(it)
                }
            }
        }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>) {
        if (allDirectories.isEmpty()) {
            allDirectories = newDirs.clone() as ArrayList<Directory>
        }

        val distinctDirs = newDirs.filter { showFavoritesBin || (!it.isRecycleBin() && !it.areFavorites()) }.distinctBy { it.path.getDistinctPath() }
            .toMutableList() as ArrayList<Directory>
        val sortedDirs = activity.getSortedDirectories(distinctDirs)
        val dirs = activity.getDirsToShow(sortedDirs, allDirectories, currentPathPrefix).clone() as ArrayList<Directory>
        binding.directoriesCurrentPath.apply {
            text = currentPathPrefix
            beVisibleIf(currentPathPrefix.isNotEmpty())
        }
        checkPlaceholderVisibility(dirs)
        if (dirs.hashCode() == shownDirectories.hashCode()) {
            return
        }

        shownDirectories = dirs
        val adapter = DirectoryAdapter(activity, dirs.clone() as ArrayList<Directory>, null, binding.directoriesGrid, true) {
            val clickedDir = it as Directory
            val path = clickedDir.path
            if (clickedDir.subfoldersCount == 1 || !activity.config.groupDirectSubfolders) {
                if (isPickingCopyMoveDestination && path.trimEnd('/') == sourcePath) {
                    activity.toast(org.fossify.commons.R.string.source_and_destination_same)
                    return@DirectoryAdapter
                } else if (isPickingCopyMoveDestination && activity.isRestrictedWithSAFSdk30(path) && !activity.isInDownloadDir(path)) {
                    activity.toast(org.fossify.commons.R.string.system_folder_copy_restriction, Toast.LENGTH_LONG)
                    return@DirectoryAdapter
                } else {
                    activity.handleLockedFolderOpening(path) { success ->
                        if (success) {
                            deliverSelectionOnce(path)
                        }
                    }
                    dialog?.dismiss()
                }
            } else {
                currentPathPrefix = path
                openedSubfolders.add(path)
                gotDirectories(allDirectories)
            }
        }

        val scrollHorizontally = activity.config.scrollHorizontally && isGridViewType
        binding.apply {
            directoriesGrid.adapter = adapter
            directoriesFastscroller.setScrollVertically(!scrollHorizontally)
            styleFastScroller()
        }
    }
}
