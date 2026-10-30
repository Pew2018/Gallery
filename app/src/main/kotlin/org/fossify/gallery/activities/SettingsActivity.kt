package org.fossify.gallery.activities

import org.fossify.gallery.extensions.configurePhotoPassword
import org.fossify.gallery.extensions.authenticatePhotoPassword
import org.fossify.gallery.extensions.showPhotoPasswordDialog

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.helpers.OxygenThemeEngine
import android.text.TextUtils
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.*
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivitySettingsBinding
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.models.FavoriteFolder
import org.fossify.gallery.dialogs.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.AlbumCover
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val PICK_IMPORT_SOURCE_INTENT = 1
        private const val SELECT_EXPORT_FAVORITES_FILE_INTENT = 2
        private const val SELECT_IMPORT_FAVORITES_FILE_INTENT = 3
        private const val SELECT_EXPORT_FAVORITE_FOLDERS_INTENT = 4
        private const val SELECT_IMPORT_FAVORITE_FOLDERS_INTENT = 5
    }

    private var mRecycleBinContentSize = 0L
    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private var activeSettingsCategory: String? = null
    private val settingsPageViews = mutableListOf<View>()
    private val settingsPageInitialVisibility = mutableListOf<Int>()
    private lateinit var settingsHub: android.widget.LinearLayout
    private lateinit var settingsBackCallback: OnBackPressedCallback

    private data class SettingsCategory(
        val id: String, val title: Int, val summary: Int, val icon: Int, val first: Int, val last: Int
    )

    private val settingsCategories by lazy {
        listOf(
            SettingsCategory("appearance", R.string.classic_settings_appearance, R.string.settings_hub_appearance_summary, R.drawable.ic_photo_filter_vector, 0, 7),
            SettingsCategory("gallery", R.string.settings_hub_gallery, R.string.settings_hub_gallery_summary, R.drawable.ic_folders_vector, 8, 10),
            SettingsCategory("photos", R.string.settings_hub_photos, R.string.settings_hub_photos_summary, R.drawable.ic_portrait_photo_vector, 11, 24),
            SettingsCategory("controls", R.string.settings_hub_controls, R.string.settings_hub_controls_summary, R.drawable.ic_aspect_ratio_vector, 25, 31),
            SettingsCategory("videos", R.string.classic_settings_video, R.string.settings_hub_videos_summary, R.drawable.ic_play_vector, 32, 40),
            SettingsCategory("files", R.string.classic_settings_files, R.string.settings_hub_files_summary, R.drawable.ic_files_vector, 41, 54),
            SettingsCategory("security", R.string.classic_settings_security, R.string.settings_hub_security_summary, org.fossify.commons.R.drawable.ic_lock_vector, 55, 61),
            SettingsCategory("advanced", R.string.classic_settings_advanced, R.string.settings_hub_advanced_summary, org.fossify.commons.R.drawable.ic_settings_cog_vector, 62, 74)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        settingsBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                navigateSettingsBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, settingsBackCallback)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.settingsAppbar),
            padBottomSystem = listOf(binding.settingsNestedScrollview)
        )
        // Resolve the first toolbar frame with the same theme token as onResume.
        binding.settingsToolbar.setBackgroundColor(OxygenThemeEngine.toolbarSurface(this, config))
    }

    override fun onResume() {
        super.onResume()
        // All ordinary toolbars share the user's chosen surface, even on light themes.
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow,
            topBarColor = OxygenThemeEngine.toolbarSurface(this, config))
        OxygenToolbarStyle.applyToolbar(this, binding.settingsToolbar)
        OxygenSystemBars.apply(this)
        setupSettingItems()
        setupBottomNavigationOrder()
        setupSettingsHub()
        if (intent.getBooleanExtra(MANAGE_FAVORITE_PASSWORD, false)) {
            intent.removeExtra(MANAGE_FAVORITE_PASSWORD)
            openSettingsCategory(settingsCategories.first { it.id == "security" })
            binding.settingsFavoritePasswordHolder.post {
                binding.settingsFavoritePasswordHolder.performClick()
            }
        }
        binding.settingsToolbar.setNavigationOnClickListener {
            if (!navigateSettingsBack()) finish()
        }
    }

    private fun setupSettingsHub() {
        if (settingsPageViews.isEmpty()) {
            settingsPageViews.addAll((0 until binding.settingsHolder.childCount).map { binding.settingsHolder.getChildAt(it) })
            settingsPageInitialVisibility.addAll(settingsPageViews.map { it.visibility })
            settingsHub = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(16))
            }
            binding.settingsHolder.addView(settingsHub, 0)
        }

        val colors = OxygenThemeEngine.snapshot(this, config)
        settingsHub.removeAllViews()
        settingsCategories.forEachIndexed { index, category ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                val value = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                background = androidx.appcompat.content.res.AppCompatResources.getDrawable(this@SettingsActivity, value.resourceId)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                minimumHeight = dp(68)
                setOnClickListener { openSettingsCategory(category) }
            }
            row.addView(android.widget.ImageView(this).apply {
                setImageResource(category.icon)
                imageTintList = android.content.res.ColorStateList.valueOf(colors.interactiveAccent)
                contentDescription = null
            }, android.widget.LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(20) })
            val textColumn = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
            textColumn.addView(android.widget.TextView(this).apply {
                text = getString(category.title)
                textSize = 16f
                setTextColor(colors.primaryText)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            })
            textColumn.addView(android.widget.TextView(this).apply {
                text = getString(category.summary)
                textSize = 12f
                setTextColor(colors.secondaryText)
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(textColumn, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(android.widget.ImageView(this).apply {
                setImageResource(org.fossify.commons.R.drawable.ic_chevron_right_vector)
                imageTintList = android.content.res.ColorStateList.valueOf(colors.secondaryText)
                contentDescription = null
            }, android.widget.LinearLayout.LayoutParams(dp(20), dp(20)))
            settingsHub.addView(row, android.widget.LinearLayout.LayoutParams(-1, -2))
            if (index < settingsCategories.lastIndex) {
                settingsHub.addView(View(this).apply {
                    setBackgroundColor(org.fossify.gallery.helpers.OxygenThemeColors.blend(
                        colors.pageBackground, colors.primaryText, 0.10
                    ))
                },
                    android.widget.LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(60) })
            }
        }
        renderSettingsPage()
    }

    private fun openSettingsCategory(category: SettingsCategory) {
        animateSettingsPageChange(forward = true) {
            activeSettingsCategory = category.id
            settingsBackCallback.isEnabled = true
            binding.settingsToolbar.title = getString(category.title)
            renderSettingsPage()
            binding.settingsNestedScrollview.scrollTo(0, 0)
        }
    }

    private fun animateSettingsPageChange(forward: Boolean, changePage: () -> Unit) {
        val content = binding.settingsNestedScrollview
        content.animate().cancel()
        val animationsEnabled = runCatching {
            android.provider.Settings.Global.getFloat(
                contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) > 0f
        }.getOrDefault(true)
        if (!animationsEnabled) {
            changePage()
            content.alpha = 1f
            content.translationX = 0f
            return
        }
        content.alpha = 0.82f
        content.translationX = dp(if (forward) 14 else -14).toFloat()
        changePage()
        content.animate().alpha(1f).translationX(0f).setDuration(180L).start()
    }

    private fun renderSettingsPage() {
        val selected = settingsCategories.firstOrNull { it.id == activeSettingsCategory }
        settingsHub.visibility = if (selected == null) View.VISIBLE else View.GONE
        settingsPageViews.forEachIndexed { index, view ->
            view.visibility = if (selected != null && index in selected.first..selected.last &&
                settingsPageInitialVisibility[index] == View.VISIBLE) View.VISIBLE else View.GONE
        }
        binding.settingsToolbar.title = getString(selected?.title ?: org.fossify.commons.R.string.settings)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun navigateSettingsBack(): Boolean {
        if (activeSettingsCategory == null) return false
        animateSettingsPageChange(forward = false) {
            activeSettingsCategory = null
            settingsBackCallback.isEnabled = false
            renderSettingsPage()
            binding.settingsNestedScrollview.scrollTo(0, 0)
        }
        return true
    }


    private fun setupSettingItems() {
        setupCustomizeColors()
        setupAppIcon()
        setupUseEnglish()
        setupLanguage()
        setupChangeDateTimeFormat()
        setupFileLoadingPriority()
        setupManageIncludedFolders()
        setupManageExcludedFolders()
        setupManageHiddenFolders()
        setupSearchAllFiles()
        setupShowHiddenItems()
        setupAutoplayVideos()
        setupRememberLastVideo()
        setupLoopVideos()
        setupOpenVideosOnSeparateScreen()
        setupOnVideoTap()
        setupMaxBrightness()
        setupUltraHdrRendering()
        setupCropThumbnails()
        setupAnimateGifs()
        setupDarkBackground()
        setupScrollHorizontally()
        setupScreenRotation()
        setupHideSystemUI()
        setupHiddenItemPasswordProtection()
        setupExcludedItemPasswordProtection()
        setupAppPasswordProtection()
        setupFileDeletionPasswordProtection()
        setupFavoritePasswordProtection()
        setupPhotoPasswordProtection()
        setupDeleteEmptyFolders()
        setupKeepScreenOn()
        setupAllowPhotoGestures()
        setupAllowVideoGestures()
        setupAllowDownGesture()
        setupAllowRotatingWithGestures()
        setupShowNotch()
        setupBottomActions()
        setupFileThumbnailStyle()
        setupFolderThumbnailStyle()
        setupKeepLastModified()
        setupEnablePullToRefresh()
        setupAllowZoomingImages()
        setupShowHighestQuality()
        setupAllowOneToOneZoom()
        setupAllowInstantChange()
        setupShowExtendedDetails()
        setupHideExtendedDetails()
        setupManageExtendedDetails()
        setupSkipDeleteConfirmation()
        setupManageBottomActions()
        binding.settingsToolbarQuickActionsHolder.setOnClickListener {
            org.fossify.gallery.helpers.ToolbarQuickActionsDialog.show(this)
        }
        setupUseRecycleBin()
        setupShowRecycleBin()
        setupShowRecycleBinLast()
        setupEmptyRecycleBin()
        updateTextColors(binding.settingsHolder)
        setupClearCache()
        setupExportFavorites()
        setupImportFavorites()
        setupExportFavoriteFolders()
        setupImportFavoriteFolders()
        setupExportSettings()
        setupImportSettings()

        val colors = OxygenThemeEngine.snapshot(this, config)
        binding.settingsCoordinator.setBackgroundColor(colors.pageBackground)
        binding.settingsHolder.setBackgroundColor(colors.pageBackground)
        // Commons does not visit ordinary SwitchCompat; apply semantic text to every row.
        OxygenClassicControls.applySwitches(binding.settingsHolder, colors)
        ClassicSettingsText.apply(binding.settingsHolder, colors)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val inputStream = contentResolver.openInputStream(resultData.data!!)
            parseFile(inputStream)
        } else if (requestCode == SELECT_EXPORT_FAVORITES_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val outputStream = contentResolver.openOutputStream(resultData.data!!)
            exportFavoritesTo(outputStream)
        } else if (requestCode == SELECT_IMPORT_FAVORITES_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            runCatching { contentResolver.openInputStream(resultData.data!!) }
                .onSuccess(::importFavorites).onFailure { error -> showErrorToast(error as? Exception ?: Exception(error)) }
        } else if (requestCode == SELECT_EXPORT_FAVORITE_FOLDERS_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            runCatching { contentResolver.openOutputStream(resultData.data!!) }
                .onSuccess(::exportFavoriteFoldersTo).onFailure { error -> showErrorToast(error as? Exception ?: Exception(error)) }
        } else if (requestCode == SELECT_IMPORT_FAVORITE_FOLDERS_INTENT && resultCode == Activity.RESULT_OK && resultData?.data != null) {
            runCatching { contentResolver.openInputStream(resultData.data!!) }
                .onSuccess(::importFavoriteFolders).onFailure { error -> showErrorToast(error as? Exception ?: Exception(error)) }
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            startActivity(Intent(this, OxygenThemeActivity::class.java))
        }
    }

    private fun setupAppIcon() {
        binding.settingsAppIconHolder.setOnClickListener {
            startActivity(Intent(this, OxygenThemeActivity::class.java).putExtra(
                OxygenThemeActivity.EXTRA_ICON_COLORS, true
            ))
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupChangeDateTimeFormat() {
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFileLoadingPriority() {
        binding.settingsFileLoadingPriorityHolder.beGoneIf(isRPlus() && !isExternalStorageManager())
        binding.settingsFileLoadingPriority.text = getFileLoadingPriorityText()
        binding.settingsFileLoadingPriorityHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(PRIORITY_SPEED, getString(R.string.speed)),
                RadioItem(PRIORITY_COMPROMISE, getString(R.string.compromise)),
                RadioItem(PRIORITY_VALIDITY, getString(R.string.avoid_showing_invalid_files))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fileLoadingPriority) {
                config.fileLoadingPriority = it as Int
                binding.settingsFileLoadingPriority.text = getFileLoadingPriorityText()
            }
        }
    }

    private fun getFileLoadingPriorityText() = getString(
        when (config.fileLoadingPriority) {
            PRIORITY_SPEED -> R.string.speed
            PRIORITY_COMPROMISE -> R.string.compromise
            else -> R.string.avoid_showing_invalid_files
        }
    )

    private fun setupManageIncludedFolders() {
        if (isRPlus() && !isExternalStorageManager()) {
            binding.settingsManageIncludedFolders.text =
                "${getString(R.string.manage_included_folders)} (${getString(org.fossify.commons.R.string.no_permission)})"
        } else {
            binding.settingsManageIncludedFolders.setText(R.string.manage_included_folders)
        }

        binding.settingsManageIncludedFoldersHolder.setOnClickListener {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                startActivity(Intent(this, IncludedFoldersActivity::class.java))
            }
        }
    }

    private fun setupManageExcludedFolders() {
        binding.settingsManageExcludedFoldersHolder.setOnClickListener {
            handleExcludedFolderPasswordProtection {
                startActivity(Intent(this, ExcludedFoldersActivity::class.java))
            }
        }
    }

    private fun setupManageHiddenFolders() {
        binding.settingsManageHiddenFoldersHolder.beGoneIf(isQPlus())
        binding.settingsManageHiddenFoldersHolder.setOnClickListener {
            handleHiddenFolderPasswordProtection {
                startActivity(Intent(this, HiddenFoldersActivity::class.java))
            }
        }
    }

    private fun setupShowHiddenItems() {
        if (isRPlus() && !isExternalStorageManager()) {
            binding.settingsShowHiddenItems.text =
                "${getString(org.fossify.commons.R.string.show_hidden_items)} (${getString(org.fossify.commons.R.string.no_permission)})"
        } else {
            binding.settingsShowHiddenItems.setText(org.fossify.commons.R.string.show_hidden_items)
        }

        binding.settingsShowHiddenItems.isChecked = config.showHiddenMedia
        binding.settingsShowHiddenItemsHolder.setOnClickListener {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else if (config.showHiddenMedia) {
                toggleHiddenItems()
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleHiddenItems()
                }
            }
        }
    }

    private fun toggleHiddenItems() {
        binding.settingsShowHiddenItems.toggle()
        config.showHiddenMedia = binding.settingsShowHiddenItems.isChecked
    }

    private fun setupSearchAllFiles() {
        binding.settingsSearchAllFiles.isChecked = config.searchAllFilesByDefault
        binding.settingsSearchAllFilesHolder.setOnClickListener {
            binding.settingsSearchAllFiles.toggle()
            config.searchAllFilesByDefault = binding.settingsSearchAllFiles.isChecked
        }
    }

    private fun setupAutoplayVideos() {
        binding.settingsAutoplayVideos.isChecked = config.autoplayVideos
        binding.settingsAutoplayVideosHolder.setOnClickListener {
            binding.settingsAutoplayVideos.toggle()
            config.autoplayVideos = binding.settingsAutoplayVideos.isChecked
        }
    }

    private fun setupRememberLastVideo() {
        binding.settingsRememberLastVideoPosition.isChecked = config.rememberLastVideoPosition
        binding.settingsRememberLastVideoPositionHolder.setOnClickListener {
            binding.settingsRememberLastVideoPosition.toggle()
            config.rememberLastVideoPosition = binding.settingsRememberLastVideoPosition.isChecked
        }
    }

    private fun setupLoopVideos() {
        binding.settingsLoopVideos.isChecked = config.loopVideos
        binding.settingsLoopVideosHolder.setOnClickListener {
            binding.settingsLoopVideos.toggle()
            config.loopVideos = binding.settingsLoopVideos.isChecked
        }
    }

    private fun setupOpenVideosOnSeparateScreen() {
        binding.settingsOpenVideosOnSeparateScreen.isChecked = config.gestureVideoPlayer
        binding.settingsOpenVideosOnSeparateScreenHolder.setOnClickListener {
            binding.settingsOpenVideosOnSeparateScreen.toggle()
            config.gestureVideoPlayer = binding.settingsOpenVideosOnSeparateScreen.isChecked
        }
    }

    private fun setupOnVideoTap() {
        // External playback is an explicit action in the player, not a global default.
        binding.settingsOnVideoTapHolder.visibility = android.view.View.GONE
        binding.settingsOnVideoTap.text = getString(R.string.open_in_app_player)
        return
        /* Legacy dialog removed from the interaction flow.
        binding.settingsOnVideoTap.text = getVideoPlayerTypeText()
        binding.settingsOnVideoTapHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(VIDEO_PLAYER_APP, getString(R.string.open_in_app_player)),
                RadioItem(VIDEO_PLAYER_SYSTEM, getString(R.string.open_system_default_player))
            )

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.videoPlayerType
            ) {
                config.videoPlayerType = it as Int
                binding.settingsOnVideoTap.text = getVideoPlayerTypeText()
            }
        }
        */
    }

    private fun getVideoPlayerTypeText() = getString(
        when (config.videoPlayerType) {
            VIDEO_PLAYER_APP -> R.string.open_in_app_player
            else -> R.string.open_system_default_player
        }
    )

    private fun setupBottomNavigationOrder() {
        binding.settingsBottomNavigationOrderHolder.setOnClickListener {
            BottomNavigationOrderDialog.show(this@SettingsActivity)
        }
    }

    private fun setupMaxBrightness() {
        binding.settingsMaxBrightness.isChecked = config.maxBrightness
        binding.settingsMaxBrightnessHolder.setOnClickListener {
            binding.settingsMaxBrightness.toggle()
            config.maxBrightness = binding.settingsMaxBrightness.isChecked
        }
    }

    private fun setupUltraHdrRendering() {
        binding.settingsUltraHdrRenderingHolder.beVisibleIf(ColorModeHelper.isGainmapSupported())
        binding.settingsUltraHdrRendering.isChecked = config.ultraHdrRendering
        binding.settingsUltraHdrRenderingHolder.setOnClickListener {
            binding.settingsUltraHdrRendering.toggle()
            config.ultraHdrRendering = binding.settingsUltraHdrRendering.isChecked
        }
    }

    private fun setupCropThumbnails() {
        binding.settingsCropThumbnails.isChecked = config.cropThumbnails
        binding.settingsCropThumbnailsHolder.setOnClickListener {
            binding.settingsCropThumbnails.toggle()
            config.cropThumbnails = binding.settingsCropThumbnails.isChecked
        }
    }

    private fun setupAnimateGifs() {
        binding.settingsAnimateGifs.isChecked = config.animateGifs
        binding.settingsAnimateGifsHolder.setOnClickListener {
            binding.settingsAnimateGifs.toggle()
            config.animateGifs = binding.settingsAnimateGifs.isChecked
        }
    }

    private fun setupDarkBackground() {
        binding.settingsBlackBackground.isChecked = config.blackBackground
        binding.settingsBlackBackgroundHolder.setOnClickListener {
            binding.settingsBlackBackground.toggle()
            config.blackBackground = binding.settingsBlackBackground.isChecked
        }
    }

    private fun setupScrollHorizontally() {
        binding.settingsScrollHorizontally.isChecked = config.scrollHorizontally
        binding.settingsScrollHorizontallyHolder.setOnClickListener {
            binding.settingsScrollHorizontally.toggle()
            config.scrollHorizontally = binding.settingsScrollHorizontally.isChecked

            if (config.scrollHorizontally) {
                config.enablePullToRefresh = false
                binding.settingsEnablePullToRefresh.isChecked = false
            }
        }
    }

    private fun setupHideSystemUI() {
        binding.settingsHideSystemUi.isChecked = config.hideSystemUI
        binding.settingsHideSystemUiHolder.setOnClickListener {
            binding.settingsHideSystemUi.toggle()
            config.hideSystemUI = binding.settingsHideSystemUi.isChecked
        }
    }

    private fun setupHiddenItemPasswordProtection() {
        binding.settingsHiddenItemPasswordProtectionHolder.beGoneIf(isRPlus() && !isExternalStorageManager())
        binding.settingsHiddenItemPasswordProtection.isChecked = config.isHiddenPasswordProtectionOn
        binding.settingsHiddenItemPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isHiddenPasswordProtectionOn) config.hiddenProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.hiddenPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isHiddenPasswordProtectionOn
                    binding.settingsHiddenItemPasswordProtection.isChecked = !hasPasswordProtection
                    config.isHiddenPasswordProtectionOn = !hasPasswordProtection
                    config.hiddenPasswordHash = if (hasPasswordProtection) "" else hash
                    config.hiddenProtectionType = type

                    if (config.isHiddenPasswordProtectionOn) {
                        val confirmationTextId = if (config.hiddenProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupExcludedItemPasswordProtection() {
        binding.settingsExcludedItemPasswordProtectionHolder.beGoneIf(binding.settingsHiddenItemPasswordProtectionHolder.isVisible())
        binding.settingsExcludedItemPasswordProtection.isChecked = config.isExcludedPasswordProtectionOn
        binding.settingsExcludedItemPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isExcludedPasswordProtectionOn) config.excludedProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.excludedPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isExcludedPasswordProtectionOn
                    binding.settingsExcludedItemPasswordProtection.isChecked = !hasPasswordProtection
                    config.isExcludedPasswordProtectionOn = !hasPasswordProtection
                    config.excludedPasswordHash = if (hasPasswordProtection) "" else hash
                    config.excludedProtectionType = type

                    if (config.isExcludedPasswordProtectionOn) {
                        val confirmationTextId = if (config.excludedProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupAppPasswordProtection() {
        binding.settingsAppPasswordProtection.isChecked = config.isAppPasswordProtectionOn
        binding.settingsAppPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.appPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isAppPasswordProtectionOn
                    binding.settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                    config.isAppPasswordProtectionOn = !hasPasswordProtection
                    config.appPasswordHash = if (hasPasswordProtection) "" else hash
                    config.appProtectionType = type

                    if (config.isAppPasswordProtectionOn) {
                        val confirmationTextId = if (config.appProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupFileDeletionPasswordProtection() {
        binding.settingsFileDeletionPasswordProtection.isChecked = config.isDeletePasswordProtectionOn
        binding.settingsFileDeletionPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isDeletePasswordProtectionOn) config.deleteProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.deletePasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isDeletePasswordProtectionOn
                    binding.settingsFileDeletionPasswordProtection.isChecked = !hasPasswordProtection
                    config.isDeletePasswordProtectionOn = !hasPasswordProtection
                    config.deletePasswordHash = if (hasPasswordProtection) "" else hash
                    config.deleteProtectionType = type

                    if (config.isDeletePasswordProtectionOn) {
                        val confirmationTextId = if (config.deleteProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupPhotoPasswordProtection() {
        fun status() {
            binding.settingsPhotoPasswordStatus.setText(if (config.photoCredentialSnapshot().enabled)
                R.string.photo_password_set else R.string.photo_password_not_set)
        }
        status()
        binding.settingsPhotoPasswordHolder.setOnClickListener {
            if (!config.photoCredentialSnapshot().enabled) {
                configurePhotoPassword { status() }
            } else authenticatePhotoPassword { proof ->
                if (proof != null) showPhotoPasswordDialog(R.string.photo_password_title) {
                    setItems(arrayOf(getString(R.string.photo_password_change), getString(R.string.photo_password_disable))) { _, index ->
                        if (index == 0) configurePhotoPassword { status() }
                        else {
                            config.clearPhotoCredential()
                            org.fossify.gallery.helpers.PhotoProfileAccessSessionManager.revokeAll()
                            status()
                        }
                    }
                }
            }
        }
    }

    private fun setupFavoritePasswordProtection() {
        fun updateStatus() {
            val credential = config.favoriteCredentialSnapshot()
            binding.settingsFavoritePasswordStatus.text = when {
                !credential.enabled && config.isFolderProtected(FAVORITES) ->
                    getString(R.string.favorite_password_legacy_status)
                !credential.enabled -> getString(R.string.favorite_password_not_set)
                !credential.isConfigured -> getString(R.string.favorite_password_enabled_invalid)
                credential.biometricPrimary -> getString(
                    R.string.favorite_password_set_biometric,
                    getString(
                        if (credential.fallbackType == PROTECTION_PIN) org.fossify.commons.R.string.pin
                        else org.fossify.commons.R.string.pattern
                    )
                )
                else -> getString(
                    R.string.favorite_password_set_primary,
                    getString(
                        if (credential.type == PROTECTION_PIN) org.fossify.commons.R.string.pin
                        else org.fossify.commons.R.string.pattern
                    )
                )
            }
        }
        updateStatus()
        binding.settingsFavoritePasswordHolder.setOnClickListener {
            prepareFavoritePasswordProtection protectionReady@{ prepared ->
                updateStatus()
                if (!prepared) return@protectionReady
                if (config.isFavoritePasswordProtectionOn && !config.favoriteCredentialSnapshot().isConfigured) {
                    toast(R.string.favorite_password_invalid)
                    return@protectionReady
                }
                if (!config.isFavoritePasswordProtectionOn) {
                    showFavoritePasswordDialog(R.string.favorite_password_title) {
                        setItems(arrayOf(
                            getString(R.string.favorite_password_enable),
                            getString(R.string.favorite_password_remain_off)
                        )) { _, which ->
                            if (which == 0) configureFavoritePassword { configured ->
                                updateStatus()
                                if (configured != null) toast(R.string.favorite_password_saved)
                            }
                        }
                    }
                    return@protectionReady
                }

                authenticateFavoritePassword { authenticated ->
                    if (authenticated == null || isFinishing || isDestroyed) return@authenticateFavoritePassword
                    showFavoritePasswordDialog(R.string.favorite_password_manage_title) {
                        setItems(arrayOf(
                            getString(R.string.favorite_password_change),
                            getString(R.string.favorite_password_disable)
                        )) { _, which ->
                            if (which == 0) {
                                configureFavoritePassword { configured ->
                                    updateStatus()
                                    if (configured != null) toast(R.string.favorite_password_saved)
                                }
                            } else if (which == 1) {
                                disableFavoritePasswordSafely {
                                    updateStatus()
                                    toast(R.string.favorite_password_disabled)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun disableFavoritePasswordSafely(onDisabled: () -> Unit) {
        val credential = config.favoriteCredentialSnapshot()
        ensureBackgroundThread {
            val firstPaths = applicationContext.getFavoritePaths().toSet()
            val firstSnapshot = runCatching {
                FavoriteAccessSessionManager.captureSnapshot(firstPaths, applicationContext::favoriteProtection)
            }.getOrNull()
            val latestPaths = applicationContext.getFavoritePaths().toSet()
            val latestSnapshot = runCatching {
                FavoriteAccessSessionManager.captureSnapshot(latestPaths, applicationContext::favoriteProtection)
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val stable = firstSnapshot != null &&
                    firstPaths == latestPaths &&
                    firstSnapshot == latestSnapshot &&
                    config.isFavoritePasswordProtectionOn &&
                    config.isFavoriteCredentialCurrent(credential)
                if (!stable) {
                    toast(R.string.favorite_password_disable_retry)
                    return@runOnUiThread
                }
                if (FavoriteAccessPolicy.hasProtectedFavorites(latestSnapshot!!)) {
                    showFavoritePasswordDialog(R.string.favorite_password_title) {
                        setMessage(R.string.favorite_password_disable_blocked)
                        setPositiveButton(org.fossify.commons.R.string.ok, null)
                    }
                    return@runOnUiThread
                }
                config.clearFavoriteCredential()
                FavoriteAccessSessionManager.revokeAll()
                onDisabled()
            }
        }
    }

    private fun setupDeleteEmptyFolders() {
        binding.settingsDeleteEmptyFolders.isChecked = config.deleteEmptyFolders
        binding.settingsDeleteEmptyFoldersHolder.setOnClickListener {
            binding.settingsDeleteEmptyFolders.toggle()
            config.deleteEmptyFolders = binding.settingsDeleteEmptyFolders.isChecked
        }
    }

    private fun setupKeepScreenOn() {
        binding.settingsKeepScreenOnFullscreenPhotos.isChecked = config.keepScreenOn
        binding.settingsKeepScreenOnFullscreenPhotosHolder.setOnClickListener {
            binding.settingsKeepScreenOnFullscreenPhotos.toggle()
            config.keepScreenOn = binding.settingsKeepScreenOnFullscreenPhotos.isChecked
        }
    }

    private fun setupAllowPhotoGestures() {
        binding.settingsAllowPhotoGestures.isChecked = config.allowPhotoGestures
        binding.settingsAllowPhotoGesturesHolder.setOnClickListener {
            binding.settingsAllowPhotoGestures.toggle()
            config.allowPhotoGestures = binding.settingsAllowPhotoGestures.isChecked
        }
    }

    private fun setupAllowVideoGestures() {
        binding.settingsAllowVideoGestures.isChecked = config.allowVideoGestures
        binding.settingsAllowVideoGesturesHolder.setOnClickListener {
            binding.settingsAllowVideoGestures.toggle()
            config.allowVideoGestures = binding.settingsAllowVideoGestures.isChecked
        }
    }

    private fun setupAllowDownGesture() {
        binding.settingsAllowDownGesture.isChecked = config.allowDownGesture
        binding.settingsAllowDownGestureHolder.setOnClickListener {
            binding.settingsAllowDownGesture.toggle()
            config.allowDownGesture = binding.settingsAllowDownGesture.isChecked
        }
    }

    private fun setupAllowRotatingWithGestures() {
        binding.settingsAllowRotatingWithGestures.isChecked = config.allowRotatingWithGestures
        binding.settingsAllowRotatingWithGesturesHolder.setOnClickListener {
            binding.settingsAllowRotatingWithGestures.toggle()
            config.allowRotatingWithGestures = binding.settingsAllowRotatingWithGestures.isChecked
        }
    }

    private fun setupShowNotch() {
        binding.settingsShowNotchHolder.beVisibleIf(isPiePlus())
        binding.settingsShowNotch.isChecked = config.showNotch
        binding.settingsShowNotchHolder.setOnClickListener {
            binding.settingsShowNotch.toggle()
            config.showNotch = binding.settingsShowNotch.isChecked
        }
    }

    private fun setupFileThumbnailStyle() {
        binding.settingsFileThumbnailStyleHolder.setOnClickListener {
            ChangeFileThumbnailStyleDialog(this)
        }
    }

    private fun setupFolderThumbnailStyle() {
        binding.settingsFolderThumbnailStyle.text = getFolderStyleText()
        binding.settingsFolderThumbnailStyleHolder.setOnClickListener {
            ChangeFolderThumbnailStyleDialog(this) {
                binding.settingsFolderThumbnailStyle.text = getFolderStyleText()
            }
        }
    }

    private fun getFolderStyleText() = getString(
        when (config.folderStyle) {
            FOLDER_STYLE_SQUARE -> R.string.square
            else -> R.string.rounded_corners
        }
    )

    private fun setupKeepLastModified() {
        binding.settingsKeepLastModified.isChecked = config.keepLastModified
        binding.settingsKeepLastModifiedHolder.setOnClickListener {
            handleMediaManagementPrompt {
                binding.settingsKeepLastModified.toggle()
                config.keepLastModified = binding.settingsKeepLastModified.isChecked
            }
        }
    }

    private fun setupEnablePullToRefresh() {
        binding.settingsEnablePullToRefresh.isChecked = config.enablePullToRefresh
        // Pull-to-refresh is now a core navigation affordance in every gallery view.
        binding.settingsEnablePullToRefreshHolder.beGone()
        binding.settingsEnablePullToRefreshHolder.setOnClickListener {
            binding.settingsEnablePullToRefresh.toggle()
            config.enablePullToRefresh = binding.settingsEnablePullToRefresh.isChecked
        }
    }

    private fun setupAllowZoomingImages() {
        binding.settingsAllowZoomingImages.isChecked = config.allowZoomingImages
        updateDeepZoomToggleButtons()
        binding.settingsAllowZoomingImagesHolder.setOnClickListener {
            binding.settingsAllowZoomingImages.toggle()
            config.allowZoomingImages = binding.settingsAllowZoomingImages.isChecked
            updateDeepZoomToggleButtons()
        }
    }

    private fun updateDeepZoomToggleButtons() {
        binding.settingsAllowRotatingWithGesturesHolder.beVisibleIf(config.allowZoomingImages)
        binding.settingsShowHighestQualityHolder.beVisibleIf(config.allowZoomingImages)
        binding.settingsAllowOneToOneZoomHolder.beVisibleIf(config.allowZoomingImages)
    }

    private fun setupShowHighestQuality() {
        binding.settingsShowHighestQuality.isChecked = config.showHighestQuality
        binding.settingsShowHighestQualityHolder.setOnClickListener {
            binding.settingsShowHighestQuality.toggle()
            config.showHighestQuality = binding.settingsShowHighestQuality.isChecked
        }
    }

    private fun setupAllowOneToOneZoom() {
        binding.settingsAllowOneToOneZoom.isChecked = config.allowOneToOneZoom
        binding.settingsAllowOneToOneZoomHolder.setOnClickListener {
            binding.settingsAllowOneToOneZoom.toggle()
            config.allowOneToOneZoom = binding.settingsAllowOneToOneZoom.isChecked
        }
    }

    private fun setupAllowInstantChange() {
        binding.settingsAllowInstantChange.isChecked = config.allowInstantChange
        binding.settingsAllowInstantChangeHolder.setOnClickListener {
            binding.settingsAllowInstantChange.toggle()
            config.allowInstantChange = binding.settingsAllowInstantChange.isChecked
        }
    }

    private fun setupShowExtendedDetails() {
        binding.settingsShowExtendedDetails.isChecked = config.showExtendedDetails
        updateExtendedDetailsButtons()
        binding.settingsShowExtendedDetailsHolder.setOnClickListener {
            binding.settingsShowExtendedDetails.toggle()
            config.showExtendedDetails = binding.settingsShowExtendedDetails.isChecked
            updateExtendedDetailsButtons()
        }
    }

    private fun setupHideExtendedDetails() {
        binding.settingsHideExtendedDetails.isChecked = config.hideExtendedDetails
        binding.settingsHideExtendedDetailsHolder.setOnClickListener {
            binding.settingsHideExtendedDetails.toggle()
            config.hideExtendedDetails = binding.settingsHideExtendedDetails.isChecked
        }
    }

    private fun setupManageExtendedDetails() {
        binding.settingsManageExtendedDetailsHolder.setOnClickListener {
            ManageExtendedDetailsDialog(this) {
                if (config.extendedDetails == 0) {
                    binding.settingsShowExtendedDetailsHolder.callOnClick()
                }
            }
        }
    }

    private fun updateExtendedDetailsButtons() {
        binding.settingsManageExtendedDetailsHolder.beVisibleIf(config.showExtendedDetails)
        binding.settingsHideExtendedDetailsHolder.beVisibleIf(config.showExtendedDetails)
    }

    private fun setupSkipDeleteConfirmation() {
        binding.settingsSkipDeleteConfirmation.isChecked = config.skipDeleteConfirmation
        binding.settingsSkipDeleteConfirmationHolder.setOnClickListener {
            binding.settingsSkipDeleteConfirmation.toggle()
            config.skipDeleteConfirmation = binding.settingsSkipDeleteConfirmation.isChecked
        }
    }

    private fun setupScreenRotation() {
        binding.settingsScreenRotation.text = getScreenRotationText()
        binding.settingsScreenRotationHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ROTATE_BY_SYSTEM_SETTING, getString(R.string.screen_rotation_system_setting)),
                RadioItem(ROTATE_BY_DEVICE_ROTATION, getString(R.string.screen_rotation_device_rotation)),
                RadioItem(ROTATE_BY_ASPECT_RATIO, getString(R.string.screen_rotation_aspect_ratio))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.screenRotation) {
                config.screenRotation = it as Int
                binding.settingsScreenRotation.text = getScreenRotationText()
            }
        }
    }

    private fun getScreenRotationText() = getString(
        when (config.screenRotation) {
            ROTATE_BY_SYSTEM_SETTING -> R.string.screen_rotation_system_setting
            ROTATE_BY_DEVICE_ROTATION -> R.string.screen_rotation_device_rotation
            else -> R.string.screen_rotation_aspect_ratio
        }
    )

    private fun setupBottomActions() {
        binding.settingsBottomActionsCheckbox.isChecked = config.bottomActions
        binding.settingsManageBottomActionsHolder.beVisibleIf(config.bottomActions)
        binding.settingsBottomActionsCheckboxHolder.setOnClickListener {
            binding.settingsBottomActionsCheckbox.toggle()
            config.bottomActions = binding.settingsBottomActionsCheckbox.isChecked
            binding.settingsManageBottomActionsHolder.beVisibleIf(config.bottomActions)
        }
    }

    private fun setupManageBottomActions() {
        binding.settingsManageBottomActionsHolder.setOnClickListener {
            ManageBottomActionsDialog(this) {
                if (config.visibleBottomActions == 0) {
                    binding.settingsBottomActionsCheckboxHolder.callOnClick()
                    config.bottomActions = false
                    config.visibleBottomActions = DEFAULT_BOTTOM_ACTIONS
                }
            }
        }
    }

    private fun setupUseRecycleBin() {
        updateRecycleBinButtons()
        binding.settingsUseRecycleBin.isChecked = config.useRecycleBin
        binding.settingsUseRecycleBinHolder.setOnClickListener {
            binding.settingsUseRecycleBin.toggle()
            config.useRecycleBin = binding.settingsUseRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun setupShowRecycleBin() {
        binding.settingsShowRecycleBin.isChecked = config.showRecycleBinAtFolders
        binding.settingsShowRecycleBinHolder.setOnClickListener {
            binding.settingsShowRecycleBin.toggle()
            config.showRecycleBinAtFolders = binding.settingsShowRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun setupShowRecycleBinLast() {
        binding.settingsShowRecycleBinLast.isChecked = config.showRecycleBinLast
        binding.settingsShowRecycleBinLastHolder.setOnClickListener {
            binding.settingsShowRecycleBinLast.toggle()
            config.showRecycleBinLast = binding.settingsShowRecycleBinLast.isChecked
            if (config.showRecycleBinLast) {
                config.removePinnedFolders(setOf(RECYCLE_BIN))
            }
        }
    }

    private fun updateRecycleBinButtons() {
        binding.settingsShowRecycleBinLastHolder.beVisibleIf(config.useRecycleBin && config.showRecycleBinAtFolders)
        binding.settingsEmptyRecycleBinHolder.beVisibleIf(config.useRecycleBin)
        binding.settingsShowRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() {
        ensureBackgroundThread {
            try {
                mRecycleBinContentSize = mediaDB.getDeletedMedia().sumByLong { medium ->
                    val size = medium.size
                    if (size == 0L) {
                        val path = medium.path.removePrefix(RECYCLE_BIN).prependIndent(recycleBinPath)
                        File(path).length()
                    } else {
                        size
                    }
                }
            } catch (ignored: Exception) {
            }

            runOnUiThread {
                binding.settingsEmptyRecycleBinSize.text = mRecycleBinContentSize.formatSize()
            }
        }

        binding.settingsEmptyRecycleBinHolder.setOnClickListener {
            if (mRecycleBinContentSize == 0L) {
                toast(org.fossify.commons.R.string.recycle_bin_empty)
            } else {
                showRecycleBinEmptyingDialog {
                    emptyTheRecycleBin()
                    mRecycleBinContentSize = 0L
                    binding.settingsEmptyRecycleBinSize.text = 0L.formatSize()
                }
            }
        }
    }

    private fun setupClearCache() {
        ensureBackgroundThread {
            val size = cacheDir.getProperSize(true).formatSize()
            runOnUiThread {
                binding.settingsClearCacheSize.text = size
            }
        }

        binding.settingsClearCacheHolder.setOnClickListener {
            ensureBackgroundThread {
                cacheDir.deleteRecursively()
                runOnUiThread {
                    binding.settingsClearCacheSize.text = cacheDir.getProperSize(true).formatSize()
                }
            }
        }
    }

    private fun setupExportFavorites() {
        binding.settingsExportFavoritesHolder.setOnClickListener {
            if (isQPlus()) {
                ExportFavoritesDialog(this, getExportFavoritesFilename(), true) { path, filename ->
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, filename)
                        addCategory(Intent.CATEGORY_OPENABLE)

                        try {
                            startActivityForResult(this, SELECT_EXPORT_FAVORITES_FILE_INTENT)
                        } catch (e: ActivityNotFoundException) {
                            toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                        } catch (e: Exception) {
                            showErrorToast(e)
                        }
                    }
                }
            } else {
                handlePermission(PERMISSION_WRITE_STORAGE) {
                    if (it) {
                        ExportFavoritesDialog(this, getExportFavoritesFilename(), false) { path, filename ->
                            val file = File(path)
                            getFileOutputStream(file.toFileDirItem(this), true) {
                                exportFavoritesTo(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun exportFavoritesTo(outputStream: OutputStream?) {
        if (outputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        ensureBackgroundThread {
            try {
                val favoritePaths = favoritesDB.getValidFavoritePaths()
                // Keep the legacy one-path-per-line bytes unchanged for stock Fossify.
                outputStream.bufferedWriter().use { out ->
                    favoritePaths.forEach { path ->
                        out.writeLn(path)
                    }
                }
                toast(if (favoritePaths.isNotEmpty())
                    org.fossify.commons.R.string.exporting_successful
                else org.fossify.commons.R.string.no_items_found)
            } catch (error: Exception) {
                android.util.Log.e("GalleryFavoritesBackup", "Media favorites export failed", error)
                toast(getString(R.string.favorite_backup_export_failed))
            }
        }
    }

    private fun getExportFavoritesFilename(): String {
        val appName = baseConfig.appId.removeSuffix(".debug").removePrefix("org.fossify.")
        return "$appName-favorites_${getCurrentFormattedDateTime()}"
    }

    private fun setupImportFavorites() {
        binding.settingsImportFavoritesHolder.setOnClickListener {
            if (isQPlus()) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    startActivityForResult(this, SELECT_IMPORT_FAVORITES_FILE_INTENT)
                }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) {
                    if (it) {
                        FilePickerDialog(this) {
                            ensureBackgroundThread {
                                importFavorites(File(it).inputStream())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importFavorites(inputStream: InputStream?) {
        if (inputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        ensureBackgroundThread {
            try {
                val paths = FavoriteBackupCodec.decodeMediaPaths(FavoriteBackupCodec.readLimited(inputStream))
                val candidates = paths.filter { path ->
                    runCatching { getDoesFilePathExist(path) && !File(path).isDirectory }
                        .getOrDefault(false)
                }
                val unlockedCandidates = candidates.filter { path ->
                    runCatching {
                        FavoriteAccessSessionManager.captureSnapshot(
                            listOf(path), applicationContext::favoriteProtection
                        ).entries[path]?.locks?.isEmpty() == true
                    }.getOrDefault(false)
                }
                val protectedSourceSkipped = candidates.size - unlockedCandidates.size
                var protectionChangedDuringImport = 0
                var skipped = paths.size - unlockedCandidates.size
                ensureBackgroundThread {
                    val database = GalleryDatabase.getInstance(applicationContext)
                    var added = 0
                    database.runInTransaction {
                        val stillUnlocked = unlockedCandidates.filter { path ->
                            runCatching {
                                FavoriteAccessSessionManager.captureSnapshot(
                                    listOf(path), applicationContext::favoriteProtection
                                ).entries[path]?.locks?.isEmpty() == true
                            }.getOrDefault(false)
                        }
                        skipped += unlockedCandidates.size - stillUnlocked.size
                        val existing = favoritesDB.getValidFavoritePaths().toHashSet()
                        val stillNew = stillUnlocked.filterNot { it in existing }
                        added = favoritesDB.insertAllIgnore(stillNew.map { getFavoriteFromPath(it) })
                            .count { it >= 0L }
                        stillUnlocked.forEach { mediaDB.updateFavorite(it, true) }
                    }
                    runOnUiThread {
                        if (protectedSourceSkipped > 0 || protectionChangedDuringImport > 0) {
                            toast(R.string.favorite_import_protected_skipped)
                        }
                        toast(getString(R.string.favorite_media_import_summary, added,
                            unlockedCandidates.size - added, skipped))
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("GalleryFavoritesBackup", "Media favorites import failed", error)
                toast(getString(R.string.favorite_backup_import_failed))
            }
        }
    }

    private fun setupExportFavoriteFolders() {
        binding.settingsExportFavoriteFoldersHolder.setOnClickListener {
            if (isQPlus()) {
                ExportFavoritesDialog(this, getExportFavoriteFoldersFilename(), true,
                    ".json", R.string.export_favorite_folder_paths) { _, filename ->
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "application/json"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, filename)
                        try {
                            startActivityForResult(this, SELECT_EXPORT_FAVORITE_FOLDERS_INTENT)
                        } catch (error: ActivityNotFoundException) {
                            toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                        } catch (error: Exception) {
                            showErrorToast(error)
                        }
                    }
                }
            } else {
                handlePermission(PERMISSION_WRITE_STORAGE) { granted ->
                    if (granted) {
                        ExportFavoritesDialog(this, getExportFavoriteFoldersFilename(), false,
                            ".json", R.string.export_favorite_folder_paths) { path, _ ->
                            getFileOutputStream(File(path).toFileDirItem(this), true) {
                                exportFavoriteFoldersTo(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getExportFavoriteFoldersFilename(): String {
        val appName = baseConfig.appId.removeSuffix(".debug").removePrefix("org.fossify.")
        return "$appName-favorite-folders_${getCurrentFormattedDateTime()}"
    }

    private fun exportFavoriteFoldersTo(outputStream: OutputStream?) {
        if (outputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }
        ensureBackgroundThread {
            try {
                val records = favoriteFoldersDB.getAll().map {
                    FavoriteBackupCodec.FolderRecord(it.path, it.addedAt)
                }
                outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(FavoriteBackupCodec.encodeFolders(records))
                }
                toast(getString(R.string.favorite_folders_export_summary, records.size))
            } catch (error: Exception) {
                android.util.Log.e("GalleryFavoritesBackup", "Favorite folders export failed", error)
                toast(getString(R.string.favorite_backup_export_failed))
            }
        }
    }

    private fun setupImportFavoriteFolders() {
        binding.settingsImportFavoriteFoldersHolder.setOnClickListener {
            if (isQPlus()) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    try {
                        startActivityForResult(this, SELECT_IMPORT_FAVORITE_FOLDERS_INTENT)
                    } catch (error: Exception) {
                        showErrorToast(error)
                    }
                }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) { granted ->
                    if (granted) {
                        FilePickerDialog(this) { path ->
                            ensureBackgroundThread {
                                try {
                                    importFavoriteFolders(File(path).inputStream())
                                } catch (error: Exception) {
                                    showErrorToast(error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importFavoriteFolders(inputStream: InputStream?) {
        if (inputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }
        ensureBackgroundThread {
            try {
                // Parse and validate the entire backup before touching Room.
                val records = FavoriteBackupCodec.decodeFolders(FavoriteBackupCodec.readLimited(inputStream))
                val valid = records.filterNot { File(it.path).isFile }
                val rejected = records.size - valid.size
                val existing = favoriteFoldersDB.getPaths()
                    .map(FavoriteFolderRules::normalizedPath).toHashSet()
                val newRecords = valid.filterNot {
                    FavoriteFolderRules.normalizedPath(it.path) in existing
                }
                val database = GalleryDatabase.getInstance(applicationContext)
                var added = 0
                database.runInTransaction {
                    added = favoriteFoldersDB.insertAllIgnore(newRecords.map {
                        FavoriteFolder(it.path, it.addedAt)
                    }).count { it >= 0L }
                }
                toast(getString(R.string.favorite_folders_import_summary, added,
                    valid.size - added, rejected))
            } catch (error: Exception) {
                android.util.Log.e("GalleryFavoritesBackup", "Favorite folders import failed", error)
                toast(getString(R.string.favorite_backup_import_failed))
            }
        }
    }

    private fun setupExportSettings() {
        binding.settingsExportHolder.setOnClickListener {
            val configItems = LinkedHashMap<String, Any>().apply {
                put(TEXT_COLOR, config.textColor)
                put(BACKGROUND_COLOR, config.backgroundColor)
                put(PRIMARY_COLOR, config.primaryColor)
                put(ACCENT_COLOR, config.accentColor)
                put(APP_ICON_COLOR, config.appIconColor)
                put(USE_ENGLISH, config.useEnglish)
                put(WAS_USE_ENGLISH_TOGGLED, config.wasUseEnglishToggled)
                put(WIDGET_BG_COLOR, config.widgetBgColor)
                put(WIDGET_TEXT_COLOR, config.widgetTextColor)
                put(DATE_FORMAT, config.dateFormat)
                put(USE_24_HOUR_FORMAT, config.use24HourFormat)
                put(INCLUDED_FOLDERS, TextUtils.join(",", config.includedFolders))
                put(EXCLUDED_FOLDERS, TextUtils.join(",", config.excludedFolders))
                put(SHOW_HIDDEN_MEDIA, config.showHiddenMedia)
                put(FILE_LOADING_PRIORITY, config.fileLoadingPriority)
                put(AUTOPLAY_VIDEOS, config.autoplayVideos)
                put(REMEMBER_LAST_VIDEO_POSITION, config.rememberLastVideoPosition)
                put(LOOP_VIDEOS, config.loopVideos)
                put(GESTURE_VIDEO_PLAYER, config.gestureVideoPlayer)
                put(VIDEO_PLAYER_TYPE, config.videoPlayerType)
                put(ALLOW_VIDEO_GESTURES, config.allowVideoGestures)
                put(ANIMATE_GIFS, config.animateGifs)
                put(CROP_THUMBNAILS, config.cropThumbnails)
                put(SHOW_THUMBNAIL_VIDEO_DURATION, config.showThumbnailVideoDuration)
                put(SHOW_THUMBNAIL_FILE_TYPES, config.showThumbnailFileTypes)
                put(MARK_FAVORITE_ITEMS, config.markFavoriteItems)
                put(SCROLL_HORIZONTALLY, config.scrollHorizontally)
                put(ENABLE_PULL_TO_REFRESH, config.enablePullToRefresh)
                put(MAX_BRIGHTNESS, config.maxBrightness)
                put(ULTRA_HDR_RENDERING, config.ultraHdrRendering)
                put(BLACK_BACKGROUND, config.blackBackground)
                put(HIDE_SYSTEM_UI, config.hideSystemUI)
                put(ALLOW_INSTANT_CHANGE, config.allowInstantChange)
                put(KEEP_SCREEN_ON, config.keepScreenOn)
                put(ALLOW_PHOTO_GESTURES, config.allowPhotoGestures)
                put(ALLOW_DOWN_GESTURE, config.allowDownGesture)
                put(ALLOW_ROTATING_WITH_GESTURES, config.allowRotatingWithGestures)
                put(SHOW_NOTCH, config.showNotch)
                put(SCREEN_ROTATION, config.screenRotation)
                put(ALLOW_ZOOMING_IMAGES, config.allowZoomingImages)
                put(SHOW_HIGHEST_QUALITY, config.showHighestQuality)
                put(ALLOW_ONE_TO_ONE_ZOOM, config.allowOneToOneZoom)
                put(SHOW_EXTENDED_DETAILS, config.showExtendedDetails)
                put(HIDE_EXTENDED_DETAILS, config.hideExtendedDetails)
                put(EXTENDED_DETAILS, config.extendedDetails)
                put(DELETE_EMPTY_FOLDERS, config.deleteEmptyFolders)
                put(KEEP_LAST_MODIFIED, config.keepLastModified)
                put(SKIP_DELETE_CONFIRMATION, config.skipDeleteConfirmation)
                put(BOTTOM_ACTIONS, config.bottomActions)
                put(VISIBLE_BOTTOM_ACTIONS, config.visibleBottomActions)
                put(USE_RECYCLE_BIN, config.useRecycleBin)
                put(SHOW_RECYCLE_BIN_AT_FOLDERS, config.showRecycleBinAtFolders)
                put(SHOW_RECYCLE_BIN_LAST, config.showRecycleBinLast)
                put(SORT_ORDER, config.sorting)
                put(DIRECTORY_SORT_ORDER, config.directorySorting)
                put(GROUP_BY, config.groupBy)
                put(GROUP_DIRECT_SUBFOLDERS, config.groupDirectSubfolders)
                put(PINNED_FOLDERS, TextUtils.join(",", config.pinnedFolders))
                put(DISPLAY_FILE_NAMES, config.displayFileNames)
                put(FILTER_MEDIA, config.filterMedia)
                put(DIR_COLUMN_CNT, config.dirColumnCnt)
                put(MEDIA_COLUMN_CNT, config.mediaColumnCnt)
                put(SHOW_ALL, config.showAll)
                put(SHOW_WIDGET_FOLDER_NAME, config.showWidgetFolderName)
                put(VIEW_TYPE_FILES, config.viewTypeFiles)
                put(VIEW_TYPE_FOLDERS, config.viewTypeFolders)
                put(SLIDESHOW_INTERVAL, config.slideshowInterval)
                put(SLIDESHOW_INCLUDE_VIDEOS, config.slideshowIncludeVideos)
                put(SLIDESHOW_INCLUDE_GIFS, config.slideshowIncludeGIFs)
                put(SLIDESHOW_RANDOM_ORDER, config.slideshowRandomOrder)
                put(SLIDESHOW_MOVE_BACKWARDS, config.slideshowMoveBackwards)
                put(SLIDESHOW_LOOP, config.loopSlideshow)
                put(LAST_EDITOR_CROP_ASPECT_RATIO, config.lastEditorCropAspectRatio)
                put(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, config.lastEditorCropOtherAspectRatioX)
                put(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, config.lastEditorCropOtherAspectRatioY)
                put(LAST_CONFLICT_RESOLUTION, config.lastConflictResolution)
                put(LAST_CONFLICT_APPLY_TO_ALL, config.lastConflictApplyToAll)
                put(EDITOR_BRUSH_COLOR, config.editorBrushColor)
                put(EDITOR_BRUSH_HARDNESS, config.editorBrushHardness)
                put(EDITOR_BRUSH_SIZE, config.editorBrushSize)
                put(ALBUM_COVERS, config.albumCovers)
                put(FOLDER_THUMBNAIL_STYLE, config.folderStyle)
                put(FOLDER_MEDIA_COUNT, config.showFolderMediaCount)
                put(LIMIT_FOLDER_TITLE, config.limitFolderTitle)
                put(THUMBNAIL_SPACING, config.thumbnailSpacing)
                put(FILE_ROUNDED_CORNERS, config.fileRoundedCorners)
                put(SEARCH_ALL_FILES_BY_DEFAULT, config.searchAllFilesByDefault)
                // Favorite credential hashes and biometric fallback remain device-local and are never exported.
            }

            exportSettings(configItems)
        }
    }

    private fun setupImportSettings() {
        binding.settingsImportHolder.setOnClickListener {
            if (isQPlus()) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) {
                    if (it) {
                        FilePickerDialog(this) {
                            ensureBackgroundThread {
                                parseFile(File(it).inputStream())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseFile(inputStream: InputStream?) {
        if (inputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        var importedItems = 0
        val configValues = LinkedHashMap<String, Any>()
        inputStream.bufferedReader().use {
            while (true) {
                try {
                    val line = it.readLine() ?: break
                    val split = line.split("=".toRegex(), 2)
                    if (split.size == 2) {
                        configValues[split[0]] = split[1]
                    }
                    importedItems++
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }

        for ((key, value) in configValues) {
            when (key) {
                TEXT_COLOR -> config.textColor = value.toInt()
                BACKGROUND_COLOR -> config.backgroundColor = value.toInt()
                PRIMARY_COLOR -> config.primaryColor = value.toInt()
                ACCENT_COLOR -> config.accentColor = value.toInt()
                APP_ICON_COLOR -> {
                    if (getAppIconColors().contains(value.toInt())) {
                        config.appIconColor = value.toInt()
                        checkAppIconColor()
                    }
                }

                USE_ENGLISH -> config.useEnglish = value.toBoolean()
                WAS_USE_ENGLISH_TOGGLED -> config.wasUseEnglishToggled = value.toBoolean()
                WIDGET_BG_COLOR -> config.widgetBgColor = value.toInt()
                WIDGET_TEXT_COLOR -> config.widgetTextColor = value.toInt()
                DATE_FORMAT -> config.dateFormat = value.toString()
                USE_24_HOUR_FORMAT -> config.use24HourFormat = value.toBoolean()
                INCLUDED_FOLDERS -> config.addIncludedFolders(value.toStringSet())
                EXCLUDED_FOLDERS -> config.addExcludedFolders(value.toStringSet())
                SHOW_HIDDEN_MEDIA -> config.showHiddenMedia = value.toBoolean()
                FILE_LOADING_PRIORITY -> config.fileLoadingPriority = value.toInt()
                AUTOPLAY_VIDEOS -> config.autoplayVideos = value.toBoolean()
                REMEMBER_LAST_VIDEO_POSITION -> config.rememberLastVideoPosition = value.toBoolean()
                LOOP_VIDEOS -> config.loopVideos = value.toBoolean()
                GESTURE_VIDEO_PLAYER -> config.gestureVideoPlayer = value.toBoolean()
                VIDEO_PLAYER_TYPE -> config.videoPlayerType = value.toInt()
                ALLOW_VIDEO_GESTURES -> config.allowVideoGestures = value.toBoolean()
                ANIMATE_GIFS -> config.animateGifs = value.toBoolean()
                CROP_THUMBNAILS -> config.cropThumbnails = value.toBoolean()
                SHOW_THUMBNAIL_VIDEO_DURATION -> config.showThumbnailVideoDuration = value.toBoolean()
                SHOW_THUMBNAIL_FILE_TYPES -> config.showThumbnailFileTypes = value.toBoolean()
                MARK_FAVORITE_ITEMS -> config.markFavoriteItems = value.toBoolean()
                SCROLL_HORIZONTALLY -> config.scrollHorizontally = value.toBoolean()
                ENABLE_PULL_TO_REFRESH -> config.enablePullToRefresh = value.toBoolean()
                MAX_BRIGHTNESS -> config.maxBrightness = value.toBoolean()
                ULTRA_HDR_RENDERING -> config.ultraHdrRendering = value.toBoolean()
                BLACK_BACKGROUND -> config.blackBackground = value.toBoolean()
                HIDE_SYSTEM_UI -> config.hideSystemUI = value.toBoolean()
                ALLOW_INSTANT_CHANGE -> config.allowInstantChange = value.toBoolean()
                KEEP_SCREEN_ON -> config.keepScreenOn = value.toBoolean()
                ALLOW_PHOTO_GESTURES -> config.allowPhotoGestures = value.toBoolean()
                ALLOW_DOWN_GESTURE -> config.allowDownGesture = value.toBoolean()
                ALLOW_ROTATING_WITH_GESTURES -> config.allowRotatingWithGestures = value.toBoolean()
                SHOW_NOTCH -> config.showNotch = value.toBoolean()
                SCREEN_ROTATION -> config.screenRotation = value.toInt()
                ALLOW_ZOOMING_IMAGES -> config.allowZoomingImages = value.toBoolean()
                SHOW_HIGHEST_QUALITY -> config.showHighestQuality = value.toBoolean()
                ALLOW_ONE_TO_ONE_ZOOM -> config.allowOneToOneZoom = value.toBoolean()
                SHOW_EXTENDED_DETAILS -> config.showExtendedDetails = value.toBoolean()
                HIDE_EXTENDED_DETAILS -> config.hideExtendedDetails = value.toBoolean()
                EXTENDED_DETAILS -> config.extendedDetails = value.toInt()
                DELETE_EMPTY_FOLDERS -> config.deleteEmptyFolders = value.toBoolean()
                KEEP_LAST_MODIFIED -> config.keepLastModified = value.toBoolean()
                SKIP_DELETE_CONFIRMATION -> config.skipDeleteConfirmation = value.toBoolean()
                BOTTOM_ACTIONS -> config.bottomActions = value.toBoolean()
                VISIBLE_BOTTOM_ACTIONS -> config.visibleBottomActions = value.toInt()
                USE_RECYCLE_BIN -> config.useRecycleBin = value.toBoolean()
                SHOW_RECYCLE_BIN_AT_FOLDERS -> config.showRecycleBinAtFolders = value.toBoolean()
                SHOW_RECYCLE_BIN_LAST -> config.showRecycleBinLast = value.toBoolean()
                SORT_ORDER -> config.sorting = value.toInt()
                DIRECTORY_SORT_ORDER -> config.directorySorting = value.toInt()
                GROUP_BY -> config.groupBy = value.toInt()
                GROUP_DIRECT_SUBFOLDERS -> config.groupDirectSubfolders = value.toBoolean()
                PINNED_FOLDERS -> config.addPinnedFolders(value.toStringSet())
                DISPLAY_FILE_NAMES -> config.displayFileNames = value.toBoolean()
                FILTER_MEDIA -> config.filterMedia = value.toInt()
                DIR_COLUMN_CNT -> config.dirColumnCnt = value.toInt()
                MEDIA_COLUMN_CNT -> config.mediaColumnCnt = value.toInt()
                SHOW_ALL -> config.showAll = value.toBoolean()
                SHOW_WIDGET_FOLDER_NAME -> config.showWidgetFolderName = value.toBoolean()
                VIEW_TYPE_FILES -> config.viewTypeFiles = value.toInt()
                VIEW_TYPE_FOLDERS -> config.viewTypeFolders = value.toInt()
                SLIDESHOW_INTERVAL -> config.slideshowInterval = value.toInt()
                SLIDESHOW_INCLUDE_VIDEOS -> config.slideshowIncludeVideos = value.toBoolean()
                SLIDESHOW_INCLUDE_GIFS -> config.slideshowIncludeGIFs = value.toBoolean()
                SLIDESHOW_RANDOM_ORDER -> config.slideshowRandomOrder = value.toBoolean()
                SLIDESHOW_MOVE_BACKWARDS -> config.slideshowMoveBackwards = value.toBoolean()
                SLIDESHOW_LOOP -> config.loopSlideshow = value.toBoolean()
                LAST_EDITOR_CROP_ASPECT_RATIO -> config.lastEditorCropAspectRatio = value.toInt()
                LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X -> config.lastEditorCropOtherAspectRatioX = value.toString().toFloat()
                LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y -> config.lastEditorCropOtherAspectRatioY = value.toString().toFloat()
                LAST_CONFLICT_RESOLUTION -> config.lastConflictResolution = value.toInt()
                LAST_CONFLICT_APPLY_TO_ALL -> config.lastConflictApplyToAll = value.toBoolean()
                EDITOR_BRUSH_COLOR -> config.editorBrushColor = value.toInt()
                EDITOR_BRUSH_HARDNESS -> config.editorBrushHardness = value.toString().toFloat()
                EDITOR_BRUSH_SIZE -> config.editorBrushSize = value.toString().toFloat()
                FOLDER_THUMBNAIL_STYLE -> config.folderStyle = value.toInt()
                FOLDER_MEDIA_COUNT -> config.showFolderMediaCount = value.toInt()
                LIMIT_FOLDER_TITLE -> config.limitFolderTitle = value.toBoolean()
                THUMBNAIL_SPACING -> config.thumbnailSpacing = value.toInt()
                FILE_ROUNDED_CORNERS -> config.fileRoundedCorners = value.toBoolean()
                SEARCH_ALL_FILES_BY_DEFAULT -> config.searchAllFilesByDefault = value.toBoolean()
                ALBUM_COVERS -> {
                    val existingCovers = config.parseAlbumCovers()
                    val existingCoverPaths = existingCovers.map { it.path }.toMutableList() as ArrayList<String>

                    val listType = object : TypeToken<List<AlbumCover>>() {}.type
                    val covers = Gson().fromJson<ArrayList<AlbumCover>>(value.toString(), listType) ?: ArrayList(1)
                    covers.filter { !existingCoverPaths.contains(it.path) && getDoesFilePathExist(it.tmb) }.forEach {
                        existingCovers.add(it)
                    }

                    config.albumCovers = Gson().toJson(existingCovers)
                }
            }
        }

        toast(if (configValues.size > 0) org.fossify.commons.R.string.settings_imported_successfully else org.fossify.commons.R.string.no_entries_for_importing)
        runOnUiThread {
            setupSettingItems()
        }
    }
}

