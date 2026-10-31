package org.fossify.gallery.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import android.content.res.ColorStateList
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import org.fossify.gallery.helpers.OxygenThemeEngine
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
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.adapters.PhotoSourceDirectoryAdapter
import org.fossify.gallery.helpers.photoSourceColumnCount
import org.fossify.gallery.helpers.photoSourceThumbnailProtected
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
import org.fossify.gallery.helpers.photoSourceCandidates
import org.fossify.gallery.helpers.PhotoSourceSelectionDraft
import org.fossify.gallery.helpers.mergePhotoSourceCandidates
import org.fossify.gallery.helpers.photoSourceSearchMatches
import org.fossify.gallery.helpers.ManualPhotoSourceIssue
import org.fossify.gallery.helpers.ManualPhotoSourceValidator

class PickDirectoryDialog(
    val activity: BaseSimpleActivity,
    val sourcePath: String,
    showOtherFolderButton: Boolean,
    val showFavoritesBin: Boolean,
    val isPickingCopyMoveDestination: Boolean,
    val isPickingFolderForWidget: Boolean,
    val sourceSelection: SourceSelection? = null,
    val callback: (path: String) -> Unit
) {
    data class SourceSelection(val draft: PhotoSourceSelectionDraft, val onConfirmed: (Set<String>, Set<String>) -> Unit)
    private fun styleFastScroller() {
        org.fossify.gallery.helpers.OxygenFastScroller.apply(
            binding.directoriesFastscroller, binding.directoriesGrid,
            org.fossify.gallery.helpers.OxygenThemeEngine.snapshot(activity, activity.config)
        )
        org.fossify.gallery.helpers.OxygenFastScroller.installPickerTouchHandling(
            binding.directoriesFastscroller, binding.directoriesGrid
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
    private var indexedDirectories = ArrayList<Directory>()
    private val initialSourcePaths = sourceSelection?.draft?.snapshot().orEmpty()
    private val manualDirectories = LinkedHashMap<String, Directory>()
    private var openedSubfolders = arrayListOf("")
    private var binding = DialogDirectoryPickerBinding.inflate(activity.layoutInflater)
    private var isGridViewType = sourceSelection != null || activity.config.viewTypeFolders == VIEW_TYPE_GRID
    private var protectedSourcePaths: Set<String> = emptySet()
    private val selectionDraft = sourceSelection?.draft?.snapshot()?.let(::PhotoSourceSelectionDraft)
    private var showHidden = activity.config.shouldShowHidden
    private var temporarilyRevealAll = false
    private var currentPathPrefix = ""
    private var manualPickerScrollAnchor: String? = null
    private var manualPickerScrollOffset = 0
    private val config = activity.config
    private val searchView = binding.folderSearchView
    private val searchEditText = searchView.binding.topToolbarSearch
    private val searchBarContainer = searchView.binding.searchBarContainer

    init {
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).apply {
            orientation = if (sourceSelection == null && activity.config.scrollHorizontally && isGridViewType) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            spanCount = if (sourceSelection != null) photoSourceColumnCount(
                (binding.directoriesGrid.width / activity.resources.displayMetrics.density).toInt().takeIf { it > 0 } ?: 360
            ) else if (isGridViewType) activity.config.dirColumnCnt else 1
        }

        if (sourceSelection != null) binding.directoriesGrid.addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount =
                    photoSourceColumnCount(((right - left) / view.resources.displayMetrics.density).toInt())
            }
        }
        binding.directoriesFastscroller.updateColors(activity.getProperPrimaryColor())
        styleFastScroller()
        binding.directoriesLoading.indeterminateTintList = ColorStateList.valueOf(
            OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
        )

        configureSearchView()

        val builder = activity.getAlertDialogBuilder()
            .setTitle(if (sourceSelection == null) org.fossify.commons.R.string.select_destination else R.string.photo_source_picker_title)
            .setPositiveButton(if (sourceSelection == null) org.fossify.commons.R.string.ok else R.string.photo_source_add, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)

        if (showOtherFolderButton && sourceSelection == null) {
            builder.setNeutralButton(R.string.other_folder) { dialogInterface, i -> showOtherFolder() }
        }

        builder.apply {
            activity.setupDialogStuff(binding.root, this, if (sourceSelection == null)
                org.fossify.commons.R.string.select_destination else R.string.photo_source_picker_title) { alertDialog ->
                dialog = alertDialog
                OxygenClassicDialog.apply(activity, alertDialog, binding.root)
                if (sourceSelection != null) {
                    binding.directoriesSelectionSummary.visibility = android.view.View.VISIBLE
                    binding.directoriesAddOtherFolder.apply {
                        visibility = android.view.View.VISIBLE
                        setTextColor(org.fossify.gallery.helpers.photoDialogColors(activity).accent)
                        setOnClickListener { chooseManualSourceFolder() }
                    }
                    updateSelectionSummary()
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (selectionDelivered.compareAndSet(false, true))
                            sourceSelection.onConfirmed(selectionDraft!!.snapshot(), allDirectories.map { it.path }.toSet())
                        alertDialog.dismiss()
                    }
                }
                binding.directoriesCurrentPath.setTextColor(if (sourceSelection != null)
                    org.fossify.gallery.helpers.photoDialogColors(activity).text else activity.getProperTextColor())
                if (sourceSelection != null) binding.directoriesSelectionSummary.setTextColor(
                    org.fossify.gallery.helpers.photoDialogColors(activity).secondary)
                binding.directoriesOverflowMenu.apply {
                    val dark = OxygenThemeEngine.snapshot(activity, config).dark
                    imageTintList = ColorStateList.valueOf(if (dark) Color.WHITE else Color.rgb(33, 33, 33))
                    setOnClickListener { showPickerOptions() }
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

    private fun updateSelectionSummary() {
        if (sourceSelection == null) return
        if (activity.isDestroyed) return
        binding.directoriesSelectionSummary.text = activity.getString(R.string.photo_source_selected_count, selectionDraft!!.snapshot().size)
    }

    /** A reversible, dialog-local reveal; Commons preferences are never rewritten. */
    private fun showPickerOptions() {
        val dark = OxygenThemeEngine.snapshot(activity, config).dark
        val theme = if (dark) R.style.OxygenClassicPopupTheme_Dark else R.style.OxygenClassicPopupTheme_Light
        val popup = PopupMenu(ContextThemeWrapper(activity, theme), binding.directoriesOverflowMenu, Gravity.END)
        popup.menu.add(
            0, 1, 0,
            if (temporarilyRevealAll) R.string.picker_restore_default_folders
            else R.string.picker_show_hidden_excluded_folders
        )
        popup.setOnMenuItemClickListener {
            if (temporarilyRevealAll) {
                temporarilyRevealAll = false
                showHidden = config.shouldShowHidden
                // A previously visible excluded ancestor may no longer exist after restoring filters.
                currentPathPrefix = ""
                openedSubfolders.clear()
                openedSubfolders.add("")
                fetchDirectories(false)
            } else {
                activity.handleHiddenFolderPasswordProtection {
                    if (dialog?.isShowing == true) {
                        temporarilyRevealAll = true
                        showHidden = true
                        fetchDirectories(true)
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun configureSearchView() = with(searchView) {
        updateHintText(context.getString(org.fossify.commons.R.string.search_folders))
        searchEditText.imeOptions = EditorInfo.IME_ACTION_DONE

        toggleHideOnScroll(sourceSelection != null || !config.scrollHorizontally)
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenu.updateSearchViewUi() {
        requireToolbar().beInvisible()
        updateColors()
        setBackgroundColor(Color.TRANSPARENT)
        searchBarContainer.setBackgroundColor(Color.TRANSPARENT)

        // Commons 7.1.3 paints the *inner* toolbar_container with an accent
        // color-filtered M3 pill. Reset that layer, not just the outer wrapper.
        // OxygenOS 7.1.1 uses a quiet rectangular search field and 1dp rule.
        val dp = resources.displayMetrics.density
        val light = ColorUtils.calculateLuminance(activity.getProperBackgroundColor()) >= 0.5
        val surface = if (light) Color.WHITE else Color.rgb(48, 48, 48)
        val primaryText = if (light) Color.rgb(33, 33, 33) else Color.rgb(250, 250, 250)
        val secondaryText = if (light) Color.rgb(117, 117, 117) else Color.rgb(189, 189, 189)
        val underline = if (light) Color.rgb(189, 189, 189) else Color.rgb(117, 117, 117)
        searchEditText.background = ColorDrawable(Color.TRANSPARENT)
        searchEditText.backgroundTintList = null
        fun paintUnderline(focused: Boolean) {
            val stroke = if (focused)
                OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), surface)
                else underline
            val height = ((if (focused) 2f else 1f) * dp + 0.5f).toInt().coerceAtLeast(1)
            binding.toolbarContainer.background = LayerDrawable(
                arrayOf(ColorDrawable(surface), ColorDrawable(stroke))
            ).apply {
                setLayerGravity(1, Gravity.BOTTOM)
                setLayerHeight(1, height)
            }
        }
        val originalFocusListener = searchEditText.onFocusChangeListener
        searchEditText.onFocusChangeListener = android.view.View.OnFocusChangeListener { v, focused ->
            originalFocusListener?.onFocusChange(v, focused)
            paintUnderline(focused)
        }
        paintUnderline(searchEditText.hasFocus())
        binding.topToolbarSearchIcon.setColorFilter(primaryText)
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        searchEditText.setTextColor(primaryText)
        searchEditText.setHintTextColor(secondaryText)
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
        val sourceAdapter = binding.directoriesGrid.adapter as? PhotoSourceDirectoryAdapter
        var dirsToShow = if (sourceSelection != null && query.isEmpty()) shownDirectories else allDirectories
        if (query.isNotEmpty()) {
            dirsToShow = dirsToShow.filter { photoSourceSearchMatches(it, query) }.toMutableList() as ArrayList
        }
        dirsToShow = activity.getSortedDirectories(dirsToShow)
        checkPlaceholderVisibility(dirsToShow)

        val filteredFolderListUpdated = (sourceAdapter?.directories ?: adapter?.dirs) != dirsToShow
        if (filteredFolderListUpdated) {
            adapter?.updateDirs(dirsToShow)
            sourceAdapter?.updateDirectories(dirsToShow)

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
            val preparedDirectories = activity.addTempFolderIfNeeded(directories)
            // Favorites is a dynamic virtual source, so keep it available even when
            // its current media count is zero or it is already selected in a draft.
            if (sourceSelection != null && preparedDirectories.none { it.path == org.fossify.commons.helpers.FAVORITES }) {
                preparedDirectories.add(Directory().apply {
                    path = org.fossify.commons.helpers.FAVORITES
                    name = activity.getString(org.fossify.commons.R.string.favorites)
                })
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || directoryLoadVersion != requestVersion ||
                    dialog?.isShowing == false) return@runOnUiThread
                isLoadingDirectories = false
                binding.directoriesLoading.visibility = android.view.View.GONE
                indexedDirectories = ArrayList(preparedDirectories)
                val candidates = if (sourceSelection != null) mergeCandidates(preparedDirectories) else preparedDirectories
                protectedSourcePaths = if (sourceSelection == null) emptySet() else candidates.filter { directory ->
                    (directory.path == org.fossify.commons.helpers.FAVORITES && activity.config.isFavoritePasswordProtectionOn) ||
                        photoSourceThumbnailProtected(directory.path, directory.tmb, activity.config::isFolderProtected)
                }.mapTo(hashSetOf()) { it.path }
                allDirectories = ArrayList(candidates)
                shownDirectories.clear()
                gotDirectories(candidates)
                filterFolderListBySearchQuery(searchEditText.text?.toString().orEmpty())
            }
        }
    }

    private fun mergeCandidates(indexed: Collection<Directory>): ArrayList<Directory> =
        mergePhotoSourceCandidates(indexed, manualDirectories.values, initialSourcePaths, activity.getString(org.fossify.commons.R.string.favorites))

    private fun chooseManualSourceFolder() {
        if (sourceSelection == null || dialog?.isShowing != true) return
        val layoutManager = binding.directoriesGrid.layoutManager as? org.fossify.commons.views.MyGridLayoutManager
        val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
        manualPickerScrollAnchor = (binding.directoriesGrid.adapter as? PhotoSourceDirectoryAdapter)
            ?.directories?.getOrNull(firstVisible)?.path
        manualPickerScrollOffset = if (firstVisible != RecyclerView.NO_POSITION)
            layoutManager?.findViewByPosition(firstVisible)?.top ?: 0 else 0
        activity.hideKeyboard(searchEditText)
        val startPath = activity.getDefaultCopyDestinationPath(showHidden, sourcePath)
        // This is a nested Commons folder browser only. It returns a path and never
        // commits or dismisses the transactional media source chooser.
        FilePickerDialog(
            activity,
            currPath = startPath,
            pickFile = false,
            showHidden = showHidden,
            showFAB = false,
            canAddShowHiddenButton = false
        ) { path ->
            onManualFolderChosen(path)
        }
    }

    private fun onManualFolderChosen(path: String) {
        ensureBackgroundThread {
            val issue = ManualPhotoSourceValidator.validate(activity, path)
            val canonical = if (issue == null) runCatching { File(path).canonicalPath }.getOrNull() else null
            activity.runOnUiThread {
                if (activity.isDestroyed || dialog?.isShowing != true) return@runOnUiThread
                if (issue != null || canonical == null) {
                    val message = when (issue) {
                        ManualPhotoSourceIssue.INVALID -> R.string.photo_source_folder_invalid
                        ManualPhotoSourceIssue.MISSING -> R.string.photo_source_folder_missing
                        ManualPhotoSourceIssue.NOT_DIRECTORY -> R.string.photo_source_folder_not_directory
                        ManualPhotoSourceIssue.NO_ACCESS, null -> R.string.photo_source_folder_no_access
                        ManualPhotoSourceIssue.HIDDEN -> R.string.photo_source_folder_hidden
                        ManualPhotoSourceIssue.EXCLUDED -> R.string.photo_source_folder_excluded
                        ManualPhotoSourceIssue.UNSUPPORTED -> R.string.photo_source_folder_unsupported
                    }
                    activity.toast(message, Toast.LENGTH_LONG)
                    return@runOnUiThread
                }
                addManualCandidate(canonical)
            }
        }
    }

    private fun addManualCandidate(canonical: String) {
        val canonicalKey = canonical.getDistinctPath()
        val existing = allDirectories.firstOrNull { it.path.getDistinctPath() == canonicalKey }
        if (existing == null) {
            val candidate = Directory().apply {
                this.path = canonical
                name = File(canonical).name.ifBlank { canonical }
                tmb = ""
                subfoldersMediaCount = -1
            }
            manualDirectories[canonical] = candidate
        }
        selectionDraft?.select(existing?.path ?: canonical)
        val candidates = mergeCandidates(indexedDirectories)
        allDirectories = ArrayList(candidates)
        protectedSourcePaths = candidates.filter { directory ->
            (directory.path == org.fossify.commons.helpers.FAVORITES && activity.config.isFavoritePasswordProtectionOn) ||
                photoSourceThumbnailProtected(directory.path, directory.tmb, activity.config::isFolderProtected)
        }.mapTo(hashSetOf()) { it.path }
        gotDirectories(candidates)
        filterFolderListBySearchQuery(searchEditText.text?.toString().orEmpty())
        updateSelectionSummary()
        val visibleQuery = searchEditText.text?.toString().orEmpty()
        val chosenName = existing?.name ?: File(canonical).name
        if (visibleQuery.isEmpty() || chosenName.contains(visibleQuery, true) || canonical.contains(visibleQuery, true)) {
            val visible = (binding.directoriesGrid.adapter as? PhotoSourceDirectoryAdapter)?.directories.orEmpty()
            val chosenIndex = visible.indexOfFirst { it.path.getDistinctPath() == canonicalKey }
            if (chosenIndex >= 0) binding.directoriesGrid.post { binding.directoriesGrid.scrollToPosition(chosenIndex) }
        } else {
            val visible = (binding.directoriesGrid.adapter as? PhotoSourceDirectoryAdapter)?.directories.orEmpty()
            val anchorIndex = visible.indexOfFirst { it.path == manualPickerScrollAnchor }
            if (anchorIndex >= 0) binding.directoriesGrid.post {
                (binding.directoriesGrid.layoutManager as? org.fossify.commons.views.MyGridLayoutManager)
                    ?.scrollToPositionWithOffset(anchorIndex, manualPickerScrollOffset)
            }
        }
        (binding.directoriesGrid.adapter as? PhotoSourceDirectoryAdapter)?.refreshSelection(existing?.path ?: canonical)
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
        if (allDirectories.isEmpty() && sourceSelection == null) {
            allDirectories = newDirs.clone() as ArrayList<Directory>
        }

        val distinctDirs = if (selectionDraft != null) photoSourceCandidates(newDirs, selectionDraft)
        else newDirs.filter { showFavoritesBin || (!it.isRecycleBin() && !it.areFavorites()) }
            .distinctBy { it.path.getDistinctPath() }.toMutableList() as ArrayList<Directory>
        if (sourceSelection != null) {
            // This is the full selectable range, independent of search or visible rows.
            allDirectories = ArrayList(distinctDirs)
        }
        val sortedDirs = activity.getSortedDirectories(distinctDirs)
        // Source selection always displays the complete candidate index. This keeps
        // nested folders independently selectable without a second navigation mode.
        val dirs = if (sourceSelection != null) sortedDirs
            else activity.getDirsToShow(sortedDirs, allDirectories, currentPathPrefix).clone() as ArrayList<Directory>
        binding.directoriesCurrentPath.apply {
            text = if (sourceSelection != null) currentPathPrefix.substringAfterLast('/') else currentPathPrefix
            beVisibleIf(currentPathPrefix.isNotEmpty())
        }
        checkPlaceholderVisibility(dirs)
        if (dirs == shownDirectories && binding.directoriesGrid.adapter != null) {
            return
        }

        shownDirectories = dirs
        if (sourceSelection != null) {
            val adapter = binding.directoriesGrid.adapter as? PhotoSourceDirectoryAdapter
            if (adapter != null) {
                adapter.updateDirectories(dirs, protectedSourcePaths)
            } else {
                binding.directoriesGrid.adapter = PhotoSourceDirectoryAdapter(activity, dirs, selectionDraft!!,
                    protectedSourcePaths, ::updateSelectionSummary)
            }
            binding.directoriesFastscroller.setScrollVertically(true)
            styleFastScroller()
            return
        }
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

        val scrollHorizontally = sourceSelection == null && activity.config.scrollHorizontally && isGridViewType
        binding.apply {
            directoriesGrid.adapter = adapter
            directoriesFastscroller.setScrollVertically(!scrollHorizontally)
            styleFastScroller()
        }
    }
}
