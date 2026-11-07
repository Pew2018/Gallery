package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenSearchNavigation
import org.fossify.gallery.helpers.SearchExitGate
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.ActivitySearchBinding
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.io.File

class SearchActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private var mLastSearchedText = ""

    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mAllMedia = ArrayList<ThumbnailItem>()

    private val binding by viewBinding(ActivitySearchBinding::inflate)
    private val exitGate = SearchExitGate()
    private val initialFocusRequest = Runnable {
        if (!exitGate.exiting && !isFinishing && !isDestroyed) {
            binding.searchMenu.focusView()
            showKeyboard(binding.searchMenu.binding.topToolbarSearch)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        org.fossify.gallery.helpers.OxygenClassicEdgeGlow.install(binding.root, this)
        setupOptionsMenu()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.searchMenu),
            padBottomImeAndSystem = listOf(binding.searchGrid)
        )
        binding.searchEmptyTextPlaceholder.setTextColor(getProperTextColor())
        getAllMedia()
        binding.searchFastscroller.updateColors(getProperPrimaryColor())
        styleFastScroller()
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        styleFastScroller()
    }

    override fun onStop() {
        binding.searchMenu.removeCallbacks(initialFocusRequest)
        OxygenSearchNavigation.hideImeAndClearFocus(this, binding.searchMenu)
        super.onStop()
    }

    override fun onDestroy() {
        binding.searchMenu.removeCallbacks(initialFocusRequest)
        mCurrAsyncTask?.stopFetching()
        super.onDestroy()
    }

    /** Toolbar arrow always exits this dedicated search, independent of query text. */
    private fun exitSearch() {
        if (!exitGate.beginExit()) return
        binding.searchMenu.removeCallbacks(initialFocusRequest)
        mCurrAsyncTask?.stopFetching()
        OxygenSearchNavigation.hideImeAndClearFocus(this, binding.searchMenu)
        if (binding.searchMenu.isSearchOpen) binding.searchMenu.closeSearch()
        finish()
    }

    private fun setupOptionsMenu() {
        OxygenToolbarStyle.preparePopup(this, binding.searchMenu)
        binding.searchMenu.requireToolbar().inflateMenu(R.menu.menu_search)
        binding.searchMenu.toggleHideOnScroll(true)
        binding.searchMenu.setupMenu()
        binding.searchMenu.toggleForceArrowBackIcon(true)
        // Commons installs its focus listener with post; ours remains cancellable.
        binding.searchMenu.post(initialFocusRequest)
        binding.searchMenu.updateHintText(getString(org.fossify.commons.R.string.search_files))

        binding.searchMenu.onNavigateBackClickListener = ::exitSearch
        // Expanded Commons search arrows call closeSearch(), NOT navigate back.
        // Override only this toolbar control; system back stays platform-owned.
        binding.searchMenu.binding.topToolbarSearchIcon.setOnClickListener { exitSearch() }

        binding.searchMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            textChanged(text)
        }

        binding.searchMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.toggle_filename -> toggleFilenameVisibility()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.searchMenu.updateColors()
        OxygenSystemBars.apply(this)
        OxygenToolbarStyle.apply(this, binding.searchMenu, getString(org.fossify.commons.R.string.search))
    }

    private fun textChanged(text: String) {
        val source = mAllMedia
        ensureBackgroundThread {
            try {
                val filtered = ArrayList(source.filter { it is Medium && it.name.contains(text, true) })
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(filtered as ArrayList<Medium>, "")
                runOnUiThread {
                    if (exitGate.exiting || isFinishing || isDestroyed || text != mLastSearchedText || source !== mAllMedia) return@runOnUiThread
                    if (grouped.isEmpty()) {
                        binding.searchEmptyTextPlaceholder.text = getString(org.fossify.commons.R.string.no_items_found)
                        binding.searchEmptyTextPlaceholder.beVisible()
                    } else {
                        binding.searchEmptyTextPlaceholder.beGone()
                    }

                    getMediaAdapter()?.updateMedia(grouped, preserveScroll = true, isCurrent = {
                        !exitGate.exiting && !isFinishing && !isDestroyed &&
                            text == mLastSearchedText && source === mAllMedia
                    })
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun setupAdapter() {
        val currAdapter = binding.searchGrid.adapter
        if (currAdapter == null) {
            MediaAdapter(this, mAllMedia, this, false, false, "", binding.searchGrid) {
                if (it is Medium) {
                    itemClicked(it.path)
                }
            }.apply {
                binding.searchGrid.adapter = this
            }
            setupLayoutManager()
            handleGridSpacing(mAllMedia)
        } else if (mLastSearchedText.isEmpty()) {
            val source = mAllMedia
            (currAdapter as MediaAdapter).updateMedia(source, preserveScroll = false, isCurrent = {
                !exitGate.exiting && !isFinishing && !isDestroyed && source === mAllMedia && mLastSearchedText.isEmpty()
            })
        } else {
            textChanged(mLastSearchedText)
        }

        setupScrollDirection()
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem>) {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            if (binding.searchGrid.itemDecorationCount > 0) {
                binding.searchGrid.removeItemDecorationAt(0)
            }

            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val decoration = GridSpacingItemDecoration(spanCount, spacing, config.scrollHorizontally, config.fileRoundedCorners, media, true)
            binding.searchGrid.addItemDecoration(decoration)
        }
    }

    private fun getMediaAdapter() = binding.searchGrid.adapter as? MediaAdapter

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun itemClicked(path: String) {
        if (!path.isVideoFast()) {
            openInViewPager(path)
            return
        }

        if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
    }

    private fun openInViewPager(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(PATH, path)
            putExtra(SHOW_ALL, false)
            startActivity(this)
        }
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.searchGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.searchGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.searchFastscroller.setScrollVertically(!scrollHorizontally)
        styleFastScroller()
    }

    private fun styleFastScroller() {
        org.fossify.gallery.helpers.OxygenFastScroller.apply(
            binding.searchFastscroller, binding.searchGrid,
            org.fossify.gallery.helpers.OxygenThemeEngine.snapshot(this, config)
        )
    }

    private fun getAllMedia() {
        getCachedMedia("") {
            if (it.isNotEmpty()) {
                mAllMedia = ArrayList(it)
            }
            runOnUiThread {
                if (exitGate.exiting || isFinishing || isDestroyed) return@runOnUiThread
                setupAdapter()
                startAsyncTask()
            }
        }
    }

    private fun startAsyncTask() {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, "", showAll = true,
            cachedMediaFallback = mAllMedia.filterIsInstance<Medium>().map { it.copy() },
            onPartialFailure = {
                if (!exitGate.exiting && !isFinishing && !isDestroyed) toast(R.string.media_load_partial_error)
            }, onFailure = {
            if (!exitGate.exiting && !isFinishing && !isDestroyed) {
                binding.searchEmptyTextPlaceholder.setText(R.string.failed_to_load_media)
                binding.searchEmptyTextPlaceholder.beVisibleIf(getMediaAdapter()?.itemCount == 0)
            }
        }) {
            if (exitGate.exiting || isFinishing || isDestroyed) return@GetMediaAsynctask
            mAllMedia = ArrayList(it)
            setupAdapter()
            textChanged(mLastSearchedText)
        }

        mCurrAsyncTask!!.execute()
    }

    override fun refreshItems() {
        startAsyncTask()
    }

    override fun tryDeleteFiles(
        fileDirItems: ArrayList<FileDirItem>,
        skipRecycleBin: Boolean,
        onDeleted: (Set<String>) -> Unit
    ) {
        val filtered = fileDirItems.filter { File(it.path).isFile && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            onDeleted(emptySet())
            return
        }

        val movingToRecycleBin = config.useRecycleBin && !skipRecycleBin &&
            !filtered.first().path.startsWith(recycleBinPath)
        if (movingToRecycleBin) {
            val movingItems = resources.getQuantityString(org.fossify.commons.R.plurals.moving_items_into_bin, filtered.size, filtered.size)
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered, permanentDelete = false, onDeleted = onDeleted)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    onDeleted(emptySet())
                    runOnUiThread { if (!isFinishing && !isDestroyed) startAsyncTask() }
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(org.fossify.commons.R.plurals.deleting_items, filtered.size, filtered.size)
            toast(deletingItems)
            deleteFilteredFiles(filtered, permanentDelete = true, onDeleted = onDeleted)
        }
    }

    private fun deleteFilteredFiles(
        filtered: ArrayList<FileDirItem>,
        permanentDelete: Boolean,
        onDeleted: (Set<String>) -> Unit
    ) {
        deleteFiles(filtered) {
            ensureBackgroundThread {
                val deletedPaths = filtered.mapNotNullTo(LinkedHashSet()) { item ->
                    val sourceGone = !getDoesFilePathExist(item.path)
                    val recycledCopyExists = !permanentDelete &&
                        getDoesFilePathExist("$recycleBinPath/${item.path}")
                    item.path.takeIf { sourceGone && (permanentDelete || recycledCopyExists) }
                }
                if (permanentDelete && deletedPaths.isNotEmpty()) {
                    deletedPaths.forEach { mediaDB.deleteMediumPath(it) }
                    if (deletedPaths.any { it.startsWith("${org.fossify.gallery.helpers.RECYCLE_BIN}/") }) {
                        org.fossify.gallery.helpers.RecycleBinMembershipRevision.changed()
                    }
                    org.fossify.gallery.helpers.PhotoContentRevision.changed()
                }
                runOnUiThread {
                    onDeleted(deletedPaths)
                    if (deletedPaths.isNotEmpty()) {
                        mAllMedia.removeAll { it is Medium && it.path in deletedPaths }
                        getAllMedia()
                    }
                    if (deletedPaths.size < filtered.size) toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        }
    }

    override fun selectedPaths(paths: ArrayList<String>) {}

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) { handleGridSpacing(media) }
}
