package org.fossify.gallery.activities

import android.animation.LayoutTransition
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityPhotoProfileEditBinding
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import java.io.File
import java.util.UUID
import org.fossify.commons.helpers.ensureBackgroundThread

class PhotoProfileEditActivity : SimpleActivity() {
    companion object {
        const val EXTRA_PROFILE_ID = "photo_profile_edit_id"
        const val EXTRA_CREATING = "photo_profile_edit_creating"
        const val REQUEST_CODE = 8401
        private const val STATE_DRAFT = "photo_profile_edit_draft"
        private const val STATE_SOURCE_ERROR = "photo_profile_edit_source_error"
        private const val STATE_SOURCE_PICKER_OPEN = "photo_profile_edit_source_picker_open"
        private const val STATE_SOURCE_PICKER_DRAFT = "photo_profile_edit_source_picker_draft"
        fun launch(activity: android.app.Activity, profile: PhotoProfile? = null, creating: Boolean = profile == null) {
            activity.startActivityForResult(Intent(activity, PhotoProfileEditActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, profile?.id)
                putExtra(EXTRA_CREATING, creating)
            }, REQUEST_CODE)
        }
    }

    private val binding by viewBinding(ActivityPhotoProfileEditBinding::inflate)
    private val store by lazy { PhotoProfileRepository.of(this) }
    private var creating = true
    private var showSourceError = false
    private var sourcePickerOpen = false
    private var restoreSourcePickerOnResume = false
    private var sourcePickerDraftPaths: Set<String>? = null
    private lateinit var draft: PhotoProfile
    private var editingBaseline: PhotoProfile? = null
    private val sourceStatusRequests = PhotoSourceStatusRequests()
    private var resumeVersion = 0L
    private var sourceStatuses: Map<PhotoSource, PhotoSourceStatus> = emptyMap()
    // Preserve each row's identity (including pressed ripple) across status refreshes.
    private val sourceRows = LinkedHashMap<PhotoSource, LinearLayout>()
    private val sourceDividers = LinkedHashMap<PhotoSource, View>()
    private var sourceRowsRendered = false
    private val sourceLayoutTransition by lazy {
        LayoutTransition().apply {
            disableTransitionType(LayoutTransition.APPEARING)
            // Removed children are retained only by ViewGroup's disappearing-view
            // animation, never by sourceRows or a delayed application callback.
            setAnimator(LayoutTransition.DISAPPEARING,
                android.animation.ObjectAnimator.ofFloat(null, "alpha", 0f))
            setDuration(LayoutTransition.DISAPPEARING, OxygenClassicMotion.CHECKBOX_MS)
            setStartDelay(LayoutTransition.DISAPPEARING, 0L)
            setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, OxygenClassicMotion.CHECKBOX_MS)
            setStartDelay(LayoutTransition.CHANGE_APPEARING, 0L)
            setAnimateParentHierarchy(false)
            for (kind in intArrayOf(LayoutTransition.CHANGE_APPEARING,
                LayoutTransition.CHANGE_DISAPPEARING, LayoutTransition.CHANGING)) {
                setDuration(kind, OxygenClassicMotion.LIST_MOVE_MS)
                setInterpolator(kind, OxygenClassicMotion.easing())
            }
        }
    }
    private val authListenerId = "photo-profile-edit-${System.identityHashCode(this)}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        creating = intent.getBooleanExtra(EXTRA_CREATING, true)
        val original = intent.getStringExtra(EXTRA_PROFILE_ID)?.let { id -> store.state().profiles.firstOrNull { it.id == id } }
        if (!creating && original == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        editingBaseline = original
        draft = savedInstanceState?.getString(STATE_DRAFT)?.let { runCatching { com.google.gson.Gson().fromJson(it, PhotoProfile::class.java) }.getOrNull() }
            ?: original ?: PhotoProfile(UUID.randomUUID().toString(), "", emptyList())
        if (!creating && draft.id != original?.id) draft = original!!
        showSourceError = savedInstanceState?.getBoolean(STATE_SOURCE_ERROR) ?: false
        restoreSourcePickerOnResume = savedInstanceState?.getBoolean(STATE_SOURCE_PICKER_OPEN) ?: false
        sourcePickerOpen = restoreSourcePickerOnResume
        sourcePickerDraftPaths = savedInstanceState?.getStringArrayList(STATE_SOURCE_PICKER_DRAFT)?.toSet()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.photoProfileEditAppbar),
            padBottomImeAndSystem = listOf(binding.photoProfileEditScroll),
            moveBottomSystem = listOf(binding.photoProfileEditAddSource)
        )
        binding.photoProfileEditToolbar.apply {
            OxygenToolbarStyle.preparePopup(this@PhotoProfileEditActivity, this)
            title = getString(if (creating) R.string.photo_profiles_create else R.string.photo_profiles_edit)
            inflateMenu(R.menu.menu_photo_profile_edit)
            setOnMenuItemClickListener { item -> if (item.itemId == R.id.photo_profile_edit_save) { saveDraft(); true } else false }
        }
        binding.photoProfileEditToolbar.setNavigationOnClickListener { finish() }
        binding.photoProfileEditName.setText(draft.name)
        applyPageColors()
        binding.photoProfileEditAddSource.setOnClickListener { chooseSources() }
        PhotoProfileAccessSessionManager.addListener(authListenerId) { _ ->
            sourceStatusRequests.invalidate()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    sourceStatuses = emptyMap()
                    renderSources()
                    checkSourceStatuses()
                }
            }
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.photoProfileEditAddSource) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val params = view.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.marginEnd = dp(16) + bars.right
            view.layoutParams = params
            view.translationY = PhotoProfileFabImePosition.translationY(
                insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()),
                ime.bottom,
                bars.bottom
            ).toFloat()
            view.visibility = View.VISIBLE
            insets
        }
        androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(
            binding.photoProfileEditAddSource,
            object : androidx.core.view.WindowInsetsAnimationCompat.Callback(
                androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onProgress(
                    insets: androidx.core.view.WindowInsetsCompat,
                    runningAnimations: MutableList<androidx.core.view.WindowInsetsAnimationCompat>
                ): androidx.core.view.WindowInsetsCompat {
                    binding.photoProfileEditAddSource.translationY = PhotoProfileFabImePosition.translationY(
                        insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()),
                        insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom,
                        insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
                    ).toFloat()
                    return insets
                }
            }
        )
        renderSources()
    }

    override fun onResume() {
        super.onResume()
        resumeVersion++
        setupTopAppBar(binding.photoProfileEditAppbar, NavigationIcon.Arrow, topBarColor = OxygenThemeEngine.toolbarSurface(this, config))
        OxygenToolbarStyle.applyToolbar(this, binding.photoProfileEditToolbar)
        OxygenSystemBars.apply(this)
        applyPageColors()
        if (::draft.isInitialized) checkSourceStatuses()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (restoreSourcePickerOnResume && ::draft.isInitialized) {
            restoreSourcePickerOnResume = false
            binding.root.post {
                if (!isFinishing && !isDestroyed && sourcePickerOpen) chooseSources(restoring = true)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureName()
        outState.putString(STATE_DRAFT, com.google.gson.Gson().toJson(draft))
        outState.putBoolean(STATE_SOURCE_ERROR, showSourceError)
        outState.putBoolean(STATE_SOURCE_PICKER_OPEN, sourcePickerOpen)
        sourcePickerDraftPaths?.let { outState.putStringArrayList(STATE_SOURCE_PICKER_DRAFT, ArrayList(it)) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        sourceStatusRequests.invalidate()
        PhotoProfileAccessSessionManager.removeListener(authListenerId)
        sourceRows.values.forEach { it.animate().cancel(); it.alpha = 1f }
        binding.photoProfileEditSources.layoutTransition = null
        sourceRows.clear()
        sourceDividers.clear()
        super.onDestroy()
    }

    private fun captureName() { if (::draft.isInitialized) draft = draft.copy(name = binding.photoProfileEditName.text.toString()) }

    private fun applyPageColors() {
        val tokens = OxygenThemeEngine.snapshot(this, config)
        binding.root.setBackgroundColor(tokens.pageBackground)
        binding.photoProfileEditScroll.setBackgroundColor(tokens.pageBackground)
        binding.photoProfileEditNameLabel.setTextColor(
            tokens.interactiveAccent
        )
        OxygenClassicPageInput.style(binding.photoProfileEditName, tokens)
        // Align entered text with the label and dynamic source rows.
        binding.photoProfileEditName.apply {
            setPaddingRelative(0, paddingTop, 0, paddingBottom)
        }
        binding.photoProfileEditSourcesTitle.setTextColor(tokens.interactiveAccent)
        binding.photoProfileEditEmpty.setTextColor(tokens.secondaryText)
        binding.photoProfileEditAddSource.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(tokens.rawAccent)
            val onAccent = if (OxygenThemeColors.contrastRatio(android.graphics.Color.WHITE, tokens.rawAccent) >= 3.0)
                android.graphics.Color.WHITE else android.graphics.Color.BLACK
            imageTintList = android.content.res.ColorStateList.valueOf(onAccent)
            rippleColor = androidx.core.graphics.ColorUtils.setAlphaComponent(onAccent, 30)
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(com.google.android.material.shape.RelativeCornerSize(0.5f)).build()
        }
        binding.photoProfileEditError.setTextColor(
            OxygenThemeColors.readableAccent(android.graphics.Color.RED, tokens.pageBackground)
        )
    }

    private fun renderSources() {
        val colors = OxygenThemeEngine.snapshot(this, config)
        val container = binding.photoProfileEditSources
        val animate = sourceRowsRendered && OxygenClassicMotion.enabled(this)
        val transition = if (OxygenClassicMotion.enabled(this)) sourceLayoutTransition else null
        if (container.layoutTransition !== transition) container.layoutTransition = transition
        binding.photoProfileEditEmpty.visibility = if (draft.sources.isEmpty()) View.VISIBLE else View.GONE
        val desired = ArrayList<View>(draft.sources.size * 2)
        draft.sources.forEachIndexed { index, source ->
            val row = sourceRows[source] ?: makeSourceRow(source, colors).also { created ->
                sourceRows[source] = created
                if (animate) created.alpha = 0f
            }
            // Async access validation changes only the status caption, never the row identity.
            row.findViewWithTag<TextView>("photo_source_status")?.apply {
                val status = sourceStatuses[source]?.takeIf { it != PhotoSourceStatus.READY }
                visibility = if (status == null) View.GONE else View.VISIBLE
                if (status != null) setText(status.stringRes)
            }
            desired.add(row)
            if (index < draft.sources.lastIndex) {
                desired.add(sourceDividers.getOrPut(source) {
                    View(this).apply {
                        setBackgroundColor(OxygenThemeColors.blend(
                            colors.pageBackground, colors.primaryText, 0.10))
                        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                    }
                })
            }
        }
        // Remove obsolete rows first. Otherwise moving a retained row over a removed
        // one detaches it and aborts its ripple/layout animation on every deletion.
        for (index in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(index)
            if (child !in desired) {
                child.animate().cancel()
                // A just-added row may still be fading in. Fade out from that
                // actual alpha instead of flashing it fully opaque first.
                container.removeViewAt(index)
            } else if (!animate) {
                child.animate().cancel()
                child.alpha = 1f
            }
        }
        // Reconcile in place rather than removeAllViews(): unchanged rows never detach.
        desired.forEachIndexed { index, child ->
            if (container.getChildAt(index) !== child) {
                (child.parent as? android.view.ViewGroup)?.removeView(child)
                container.addView(child, index.coerceAtMost(container.childCount))
                if (child is LinearLayout && child.alpha == 0f) {
                    child.animate().cancel()
                    if (animate) child.animate().alpha(1f)
                        .setDuration(OxygenClassicMotion.CHECKBOX_MS)
                        .setInterpolator(OxygenClassicMotion.easing())
                        .withEndAction { child.alpha = 1f }.start()
                    else child.alpha = 1f
                }
            }
        }
        for (index in container.childCount - 1 downTo desired.size) {
            container.getChildAt(index).animate().cancel()
            container.removeViewAt(index)
        }
        val keep = draft.sources.toSet()
        sourceRows.keys.retainAll(keep)
        sourceDividers.keys.retainAll(keep)
        binding.photoProfileEditError.visibility =
            if (showSourceError && draft.sources.isEmpty()) View.VISIBLE else View.GONE
        sourceRowsRendered = true
    }

    private fun makeSourceRow(source: PhotoSource, colors: OxygenThemeTokens): LinearLayout {
        val row = LinearLayout(this).apply {
            tag = source
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            minimumHeight = dp(72)
        }
        row.addView(android.widget.ImageView(this).apply {
            setImageResource(if (source.type == PhotoSourceType.FAVORITES)
                org.fossify.commons.R.drawable.ic_star_vector
            else org.fossify.commons.R.drawable.ic_folder_vector)
            imageTintList = android.content.res.ColorStateList.valueOf(colors.secondaryText)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.addView(TextView(this).apply {
            text = if (source.type == PhotoSourceType.FAVORITES)
                getString(R.string.photo_profiles_favorites)
            else File(source.path.orEmpty()).name.ifBlank { source.path.orEmpty() }
            textSize = 16f
            setTextColor(OxygenClassicPageInput.readableTextColor(colors.primaryText, colors))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        labels.addView(TextView(this).apply {
            text = source.path ?: getString(R.string.photo_profiles_favorites)
            textSize = 12f
            setTextColor(OxygenClassicPageInput.readableTextColor(colors.secondaryText, colors))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })
        labels.addView(TextView(this).apply {
            tag = "photo_source_status"
            textSize = 12f
            setTextColor(OxygenClassicPageInput.readableTextColor(colors.secondaryText, colors))
            visibility = View.GONE
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageButton(this).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
            imageTintList = android.content.res.ColorStateList.valueOf(
                OxygenClassicPageInput.readableTextColor(colors.primaryText, colors))
            contentDescription = getString(R.string.photo_profiles_remove_source)
            background = OxygenClassicDialog.ripple(
                colors.interactiveAccent, resources.displayMetrics.density)
            setOnClickListener {
                sourceStatusRequests.invalidate()
                draft = draft.copy(sources = draft.sources.filterNot { it == source })
                sourceStatuses = sourceStatuses - source
                renderSources()
                checkSourceStatuses()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun checkSourceStatuses() {
        if (!creating && store.state().profiles.firstOrNull { it.id == draft.id } != editingBaseline) {
            sourceStatusRequests.invalidate()
            sourceStatuses = emptyMap()
            renderSources()
            return
        }
        val snapshot = draft.sources.toList()
        val request = sourceStatusRequests.begin(snapshot, PhotoProfileAccessSessionManager.currentEventVersion(), resumeVersion) ?: return
        val app = applicationContext
        ensureBackgroundThread {
            val statuses = PhotoSourceStatusResolver.inspect(app, snapshot) {
                isFinishing || isDestroyed || !sourceStatusRequests.isCurrent(request)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || !sourceStatusRequests.isCurrent(request) || draft.sources != snapshot ||
                    PhotoProfileAccessSessionManager.currentEventVersion() != request.stateVersion) {
                    sourceStatusRequests.finish(request)
                    return@runOnUiThread
                }
                if (!creating && store.state().profiles.firstOrNull { it.id == draft.id } != editingBaseline) {
                    sourceStatusRequests.invalidate()
                    return@runOnUiThread
                }
                sourceStatuses = statuses
                renderSources()
                sourceStatusRequests.finish(request)
            }
        }
    }

    private fun chooseSources(restoring: Boolean = false) {
        if (sourcePickerOpen && !restoring) return
        val paths = sourcePickerDraftPaths?.toMutableSet() ?: draft.sources
            .filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toMutableSet().apply {
                if (draft.sources.any { it.type == PhotoSourceType.FAVORITES }) add(org.fossify.commons.helpers.FAVORITES)
            }
        sourcePickerOpen = true
        PickDirectoryDialog(this, "", false, true, false, false,
            PickDirectoryDialog.SourceSelection(
                draft = PhotoSourceSelectionDraft(paths),
                onConfirmed = { chosen, available ->
                    sourcePickerOpen = false
                    sourcePickerDraftPaths = null
                    val selected = chosen.map { path ->
                        if (path == org.fossify.commons.helpers.FAVORITES) PhotoSource(PhotoSourceType.FAVORITES)
                        else PhotoSource(PhotoSourceType.FOLDER, path)
                    }
                    val existingSources = draft.sources.toList()
                    ensureBackgroundThread {
                        val oldFolderPaths = existingSources.mapNotNull { source ->
                            source.path.takeIf { source.type == PhotoSourceType.FOLDER }
                        }
                        val identityMap = resolvePhotoSourceIdentities(available + oldFolderPaths)
                        val availableIdentities = available.mapNotNullTo(hashSetOf()) { path ->
                            if (path.startsWith('/')) photoSourceResolvedIdentity(path, identityMap) else path
                        }
                        val unavailable = existingSources.filter { source ->
                            source.type == PhotoSourceType.FOLDER && source.path?.let { path ->
                                photoSourceResolvedIdentity(path, identityMap) !in availableIdentities
                            } == true
                        }
                        val updatedSources = PhotoProfileCodec.sources(selected + unavailable)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            draft = draft.copy(sources = updatedSources)
                            sourceStatusRequests.invalidate()
                            sourceStatuses = emptyMap()
                            showSourceError = false
                            renderSources()
                            checkSourceStatuses()
                        }
                    }
                },
                onDraftChanged = { sourcePickerDraftPaths = it },
                onCancelled = {
                    sourcePickerOpen = false
                    sourcePickerDraftPaths = null
                }
            ), {})
    }

    private fun saveDraft() {
        captureName()
        val name = draft.name.trim()
        if (name.isEmpty()) { binding.photoProfileEditName.error = getString(R.string.photo_profiles_name_required); return }
        if (draft.sources.isEmpty()) { showSourceError = true; binding.photoProfileEditError.visibility = View.VISIBLE; binding.photoProfileEditScroll.smoothScrollTo(0, binding.photoProfileEditError.bottom); return }
        if (!creating && store.state().profiles.firstOrNull { it.id == draft.id } != editingBaseline) {
            sourceStatusRequests.invalidate()
            binding.photoProfileEditName.error = getString(R.string.photo_profiles_edit_target_missing)
            return
        }
        runCatching {
            if (creating) store.save(draft.copy(name = name)) else store.updateExisting(draft.copy(name = name))
        }.onSuccess {
            setResult(RESULT_OK)
            finish()
        }.onFailure { binding.photoProfileEditName.error = getString(R.string.photo_profiles_edit_target_missing) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
