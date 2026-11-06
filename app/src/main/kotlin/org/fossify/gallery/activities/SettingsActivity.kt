Warning: truncated output (original token count: 23918)
Total output lines: 1850

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
import androidx.core.view.doOnLayout
import org.fossify.gallery.helpers.OxygenThemeEngine
import android.widget.Toast
import org.fossify.commons.dialogs.*
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivitySettingsBinding
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.dialogs.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.*
import org.fossify.gallery.helpers.backup.*
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
        private const val SELECT_EXPORT_PHOTO_PROFILES_INTENT = 6
        private const val SELECT_IMPORT_PHOTO_PROFILES_INTENT = 7
        private const val SELECT_EXPORT_FULL_BACKUP_INTENT = 8
        private const val SELECT_RESTORE_FULL_BACKUP_INTENT = 9
    }

    private val fullBackupBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private var restoreOperation: GalleryRestoreOperation? = null
    private var mRecycleBinContentSize = 0L
    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private var activeSettingsCategory: String? = null
    private val settingsPageViews = mutableListOf<View>()
    private val settingsPageInitialVisibility = mutableListOf<Int>()
    private lateinit var settingsHub: android.widget.LinearLayout
    private lateinit var settingsBackCallback: OnBackPressedCallback
    private var pendingSettingsCategory: String? = null
    private var settingsTransitionEpoch = 0L
    private val settingsScrollPositions = mutableMapOf<String, Int>()
    private val settingsHubDividers = mutableListOf<View>()

    private data class SettingsCategory(
        val id: String, val title: Int, val summary: Int, val icon: Int, val itemIds: Set<Int>
    )

    // The eight pages and their visible section headings share the same registry.
    private val settingsCategories by lazy {
        listOf(
            SettingsCategory("appearance", R.string.classic_settings_appearance, R.string.settings_hub_appearance_summary, R.drawable.ic_photo_filter_vector, setOf(
                R.id.settings_section_appearance_theme, R.id.settings_color_customization_holder, R.id.settings_bottom_navigation_order_holder,
                R.id.settings_app_icon_holder, R.id.settings_section_appearance_thumbnails, R.id.settings_crop_thumbnails_holder,
                R.id.settings_file_thumbnail_style_holder, R.id.settings_folder_thumbnail_style_holder
            )),
            SettingsCategory("gallery", R.string.settings_hub_gallery, R.string.settings_hub_gallery_summary, R.drawable.ic_folders_vector, setOf(
                R.id.settings_section_gallery_browsing, R.id.settings_scroll_horizontally_holder, R.id.settings_enable_pull_to_refresh_holder
            )),
            SettingsCategory("photos", R.string.settings_hub_photos, R.string.settings_hub_photos_summary, R.drawable.ic_portrait_photo_vector, setOf(
                R.id.settings_section_photos_display, R.id.settings_max_brightness_holder, R.id.settings_ultra_hdr_rendering_holder,
                R.id.settings_black_background_holder, R.id.settings_hide_system_ui_holder, R.id.settings_section_photos_screen,
                R.id.settings_keep_screen_on_fullscreen_photos_holder, R.id.settings_show_notch_holder, R.id.settings_screen_rotation_holder,
                R.id.settings_section_photos_gestures, R.id.settings_allow_instant_change_holder, R.id.settings_allow_photo_gestures_holder,
                R.id.settings_allow_down_gesture_holder, R.id.settings_allow_zooming_images_holder, R.id.settings_allow_rotating_with_gestures_holder,
                R.id.settings_show_highest_quality_holder, R.id.settings_allow_one_to_one_zoom_holder
            )),
            SettingsCategory("controls", R.string.settings_hub_controls, R.string.settings_hub_controls_summary, R.drawable.ic_aspect_ratio_vector, setOf(
                R.id.settings_section_controls_info, R.id.settings_show_extended_details_holder, R.id.settings_hide_extended_details_holder,
                R.id.settings_manage_extended_details_holder, R.id.settings_section_controls_actions, R.id.settings_bottom_actions_checkbox_holder,
                R.id.settings_manage_bottom_actions_holder, R.id.settings_toolbar_quick_actions_holder
            )),
            SettingsCategory("videos", R.string.classic_settings_video, R.string.settings_hub_videos_summary, R.drawable.ic_play_vector, setOf(
                R.id.settings_section_videos_playback, R.id.settings_autoplay_videos_holder, R.id.settings_remember_last_video_position_holder,
                R.id.settings_loop_videos_holder, R.id.settings_animate_gifs_holder, R.id.settings_section_videos_player,
                R.id.settings_open_videos_on_separate_screen_holder, R.id.settings_on_video_tap_holder, R.id.settings_allow_video_gestures_holder
            )),
            SettingsCategory("files", R.string.classic_settings_files, R.string.settings_hub_files_summary, R.drawable.ic_files_vector, setOf(
                R.id.settings_section_files_sources, R.id.settings_manage_included_folders_holder, R.id.settings_manage_excluded_folders_holder,
                R.id.settings_manage_hidden_folders_holder, R.id.settings_show_hidden_items_holder, R.id.settings_search_all_files_holder,
                R.id.settings_section_files_operations, R.id.settings_delete_empty_folders_holder, R.id.settings_keep_last_modified_holder,
                R.id.settings_skip_delete_confirmation_holder, R.id.settings_section_files_recycle, R.id.settings_use_recycle_bin_holder,
                R.id.settings_show_recycle_bin_holder, R.id.settings_show_recycle_bin_last_holder, R.id.settings_empty_recycle_bin_holder
            )),
            SettingsCategory("security", R.string.classic_settings_security, R.string.settings_hub_security_summary, org.fossify.commons.R.drawable.ic_lock_vector, setOf(
                R.id.settings_section_security_app, R.id.settings_app_password_protection_holder, R.id.settings_file_deletion_password_protection_holder,
                R.id.settings_section_security_media, R.id.settings_hidden_item_password_protection_holder, R.id.settings_excluded_item_password_protection_holder,
                R.id.settings_favorite_password_holder, R.id.settings_photo_password_holder
            )),
            SettingsCategory("advanced", R.string.classic_settings_advanced, R.string.settings_hub_advanced_summary, org.fossify.commons.R.drawable.ic_settings_cog_vector, setOf(
                R.id.settings_section_advanced_language, R.id.settings_use_english_holder, R.id.settings_language_holder,
                R.id.settings_change_date_time_format_holder, R.id.settings_section_advanced_performance, R.id.settings_file_loading_priority_holder,
                R.id.settings_refresh_diagnostics_holder, R.id.settings_diagnostics_logs_holder, R.id.settings_clear_cache_holder, R.id.settings_section_advanced_backup, R.id.settings_export_favorites_holder,
                R.id.settings_import_favorites_holder, R.id.settings_export_favorite_folders_holder, R.id.settings_import_favorite_folders_holder,
                R.id.settings_export_holder, R.id.settings_import_holder, R.id.settings_export_photo_profiles_holder, R.id.settings_import_photo_profiles_holder, R.id.settings_export_full_backup_holder, R.id.settings_restore_full_backup_holder, R.id.settings_purchase_thank_you_holder
            ))
        )
    }

    private val settingsSectionRows: Map<Int, Set<Int>> = mapOf(
        R.id.settings_section_appearance_theme to setOf(R.id.settings_color_customization_holder, R.id.settings_bottom_navigation_order_holder, R.id.settings_app_icon_holder),
        R.id.settings_section_appearance_thumbnails to setOf(R.id.settings_crop_thumbnails_holder, R.id.settings_file_thumbnail_style_holder, R.id.settings_folder_thumbnail_style_holder),
        R.id.settings_section_gallery_browsing to setOf(R.id.settings_scroll_horizontally_holder, R.id.settings_enable_pull_to_refresh_holder),
        R.id.settings_section_photos_display to setOf(R.id.settings_max_brightness_holder, R.id.settings_ultra_hdr_rendering_holder, R.id.settings_black_background_holder, R.id.settings_hide_system_ui_holder),
        R.id.settings_section_photos_screen to setOf(R.id.settings_keep_screen_on_fullscreen_photos_holder, R.id.settings_show_notch_holder, R.id.settings_screen_rotation_holder),
        R.id.settings_section_photos_gestures to setOf(R.id.settings_allow_instant_change_holder, R.id.settings_allow_photo_gestures_holder, R.id.settings_allow_down_gesture_holder, R.id.settings_allow_zooming_images_holder, R.id.settings_allow_rotating_with_gestures_holder, R.id.settings_show_highest_quality_holder, R.id.settings_allow_one_to_one_zoom_holder),
        R.id.settings_section_controls_info to setOf(R.id.settings_show_extended_details_holder, R.id.settings_hide_extended_details_holder, R.id.settings_manage_extended_details_holder),
        R.id.settings_section_controls_actions to setOf(R.id.settings_bottom_actions_checkbox_holder, R.id.settings_manage_bottom_actions_holder, R.id.settings_toolbar_quick_actions_holder),
        R.id.settings_section_videos_playback to setOf(R.id.settings_autoplay_videos_holder, R.id.settings_remember_last_video_position_holder, R.id.settings_loop_videos_holder, R.id.settings_animate_gifs_holder),
        R.id.settings_section_videos_player to setOf(R.id.settings_open_videos_on_separate_screen_holder, R.id.settings_on_video_tap_holder, R.id.settings_allow_video_gestures_holder),
        R.id.settings_section_files_sources to setOf(R.id.settings_manage_included_folders_holder, R.id.settings_manage_excluded_folders_holder, R.id.settings_manage_hidden_folders_holder, R.id.settings_show_hidden_items_holder, R.id.settings_search_all_files_holder),
        R.id.settings_section_files_operations to setOf(R.id.settings_delete_empty_folders_holder, R.id.settings_keep_last_modified_holder, R.id.settings_skip_delete_confirmation_holder),
        R.id.settings_section_files_recycle to setOf(R.id.settings_use_recycle_bin_holder, R.id.settings_show_recycle_bin_holder, R.id.settings_show_recycle_bin_last_holder, R.id.settings_empty_recycle_bin_holder),
        R.id.settings_section_security_app to setOf(R.id.settings_app_password_protection_holder, R.id.settings_file_deletion_password_protection_holder),
        R.id.settings_section_security_media to setOf(R.id.settings_hidden_item_password_protection_holder, R.id.settings_excluded_item_password_protection_holder, R.id.settings_favorite_password_holder, R.id.settings_photo_password_holder),
        R.id.settings_section_advanced_language to setOf(R.id.settings_use_english_holder, R.id.settings_language_holder, R.id.settings_change_date_time_format_holder),
        R.id.settings_section_advanced_performance to setOf(R.id.settings_file_loading_priority_holder, R.id.settings_refresh_diagnostics_holder, R.id.settings_diagnostics_logs_holder, R.id.settings_clear_cache_holder),
        R.id.settings_section_advanced_backup to setOf(R.id.settings_export_favorites_holder, R.id.settings_import_favorites_holder, R.id.settings_export_favorite_folders_holder, R.id.settings_import_favorite_folders_holder, R.id.settings_export_holder, R.id.settings_import_holder, R.id.settings_export_photo_profiles_holder, R.id.settings_import_photo_profiles_holder, R.id.settings_export_full_backup_holder, R.id.settings_restore_full_backup_holder)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        activeSettingsCategory = savedInstanceState?.getString("settings_category")
            ?.takeIf { id -> settingsCategories.any { it.id == id } }
        savedInstanceState?.getBundle("settings_scroll_positions")?.let { positions ->
            positions.keySet().forEach { key -> settingsScrollPositions[key] = positions.getInt(key) }
        }

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

    override fun onDestroy() {
        settingsTransitionEpoch++
        ClassicSettingsPageTransition.cancel(binding.settingsNestedScrollview, binding.settingsHolder)
        if (restoreOperation != null) PhotoAuthentication.requests.cancelOwner(this)
        restoreOperation?.close()
        restoreOperation = null
        super.onDestroy()
    }

    override fun onResume() {
       …16918 tokens truncated…PREFERENCES -> R.string.full_backup_preferences_rolled_back
                else -> R.string.full_backup_rolled_back
            }
            is InvalidGalleryBackup -> if (error.reason == InvalidGalleryBackupReason.UNSUPPORTED_VERSION)
                R.string.full_backup_unsupported_version else R.string.full_backup_invalid
            is IllegalArgumentException -> R.string.full_backup_invalid
            else -> R.string.full_backup_io_failed
        })
    }

    /** Compute under the existing backup mutex; release it before any authentication UI. */
    private fun <T> restoreStep(operation: GalleryRestoreOperation, action: () -> T, ready: (T) -> Unit) {
        if (!isRestoreCurrent(operation)) return
        if (!fullBackupBusy.compareAndSet(false, true)) { finishRestore(operation); toast(R.string.full_backup_busy); return }
        BackupOperationRunner.execute({
            val value = try {
                if (!operation.isActive()) throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
                action()
            } finally { fullBackupBusy.set(false) }
            backupUi { if (isRestoreCurrent(operation)) ready(value) }
        }) { error ->
            fullBackupBusy.set(false)
            operation.close()
            backupUi {
                if (restoreOperation === operation) {
                    restoreOperation = null
                    showFullBackupError(error)
                }
            }
        }
    }

    private fun previewFullBackup(open: () -> InputStream?) {
        if (restoreOperation != null || fullBackupBusy.get()) { toast(R.string.full_backup_busy); return }
        val operation = GalleryRestoreOperation { !isFinishing && !isDestroyed }
        restoreOperation = operation
        val service = GalleryRestoreService(applicationContext)
        toast(R.string.full_backup_working)
        restoreStep(operation, {
            service.recoverPending()
            service.preview(GalleryBackupZipCodec.decode(open(), cacheDir))
        }) { plan -> showRestorePlan(operation, service, plan, GalleryRestoreMode.MERGE) }
    }

    private fun showRestorePlan(operation: GalleryRestoreOperation, service: GalleryRestoreService,
                                plan: GalleryRestorePlan, initialMode: GalleryRestoreMode) {
        if (!isRestoreCurrent(operation)) return
        operation.awaitConfirmation()
        GalleryRestorePreviewDialog.show(this, plan, initialMode, onCancelled = {
            if (isRestoreCurrent(operation)) {
                finishRestore(operation)
                toast(R.string.full_backup_cancelled)
            }
        }, onConfirmationCancelled = { plan.authorization?.invalidate() }) { mode ->
            if (!isRestoreCurrent(operation) || !operation.advance()) return@show
            if (plan.requiresFavoriteAuthorization(mode) && plan.authorization?.current(applicationContext, plan, mode) != true) {
                if (plan.securityVersion != GalleryRestoreAuthorization.securityVersion(applicationContext) ||
                    plan.sessionGeneration != FavoriteAccessSessionManager.currentGeneration()) {
                    finishRestore(operation)
                    toast(R.string.full_backup_authorization_changed)
                    return@show
                }
                if (config.isFolderProtected(FAVORITES)) {
                    finishRestore(operation)
                    toast(R.string.full_backup_auth_migration_required)
                    return@show
                }
                if (!config.isFavoriteCredentialCurrent(config.favoriteCredentialSnapshot())) {
                    finishRestore(operation)
                    toast(R.string.full_backup_auth_not_completed)
                    return@show
                }
                val started = authenticateFavoritePassword(allowLegacyMigration = false) { proof ->
                    if (!isRestoreCurrent(operation)) return@authenticateFavoritePassword
                    if (proof == null) {
                        finishRestore(operation)
                        toast(R.string.full_backup_auth_not_completed)
                    } else restoreStep(operation, { service.authorize(plan, mode, proof, operation) }) { updated ->
                        toast(R.string.full_backup_preview_updated)
                        // Always re-confirm the newly authorized scope, including replacement's deletion count.
                        showRestorePlan(operation, service, updated, mode)
                    }
                }
                if (!started && isRestoreCurrent(operation)) { finishRestore(operation); toast(R.string.full_backup_busy) }
            } else restoreStep(operation, { service.restore(plan, mode, operation) }) { result ->
                finishRestore(operation)
                if (result.settings.changed > 0 || result.profiles.appliedAdded > 0 || result.profiles.appliedRemoved > 0 || result.profiles.conflicts > 0) {
                    checkAppIconColor()
                    OxygenThemeEngine.apply(this, config)
                    setupSettingItems()
                    renderSettingsPage()
                    setupBottomNavigationOrder()
                    setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow,
                        topBarColor = OxygenThemeEngine.toolbarSurface(this, config))
                    OxygenToolbarStyle.applyToolbar(this, binding.settingsToolbar)
                    OxygenSystemBars.apply(this)
                    refreshThemeIfNeeded()
                }
                showRestoreResult(result)
            }
        }
    }

    private fun showRestoreResult(result: GalleryRestoreResult) {
        if (result.completion == GalleryRestoreCompletion.NO_CHANGES) { toast(R.string.full_backup_no_changes); return }
        val summary = getString(R.string.full_backup_applied_summary,
            result.media.appliedAdded, result.media.appliedRemoved, result.media.skippedCount, result.media.retained,
            result.folders.appliedAdded, result.folders.appliedRemoved, result.profiles.appliedAdded,
            result.profiles.appliedRemoved, result.settings.changed, result.media.cacheRepaired)
        if (result.completion == GalleryRestoreCompletion.COMPLETE) {
            toast(getString(R.string.full_backup_completed_summary, summary))
        } else {
            val warnings = result.media.skipped.entries.joinToString("\n") { (reason, count) ->
                val label = when (reason) {
                    RestoreSkipReason.FAVORITE_AUTHORIZATION -> R.string.full_backup_skip_favorite_auth
                    RestoreSkipReason.SOURCE_PROTECTION -> R.string.full_backup_skip_source_protection
                    RestoreSkipReason.OTHER_PROTECTION -> R.string.full_backup_path_protected
                    RestoreSkipReason.MISSING -> R.string.full_backup_path_missing
                    RestoreSkipReason.OFFLINE -> R.string.full_backup_path_offline
                    RestoreSkipReason.SAF_PERMISSION -> R.string.full_backup_path_saf
                    RestoreSkipReason.MEDIA_PERMISSION -> R.string.full_backup_path_permission
                    RestoreSkipReason.UNCONFIRMED -> R.string.full_backup_path_unconfirmed
                }
                getString(R.string.full_backup_status_line, getString(label), count)
            }
            val retained = getString(R.string.full_backup_retained_summary, result.media.retainedUnsafe,
                result.folders.offlineRetained, result.profiles.inaccessibleSourcesRetained,
                result.settings.securityRetained, result.unresolvedPaths)
            OxygenClassicDialog.show(this, OxygenClassicDialog.builder(this, getString(R.string.full_backup_partial_title))
                .setMessage(listOf(summary, warnings, retained).filter { it.isNotEmpty() }.joinToString("\n\n"))
                .setPositiveButton(org.fossify.commons.R.string.ok, null))
        }
    }

    private fun setupPhotoProfileBackup() {
        binding.settingsExportPhotoProfilesHolder.setOnClickListener {
            fun chooseExport() {
                ExportFavoritesDialog(this, "gallery-photo-profiles_${getCurrentFormattedDateTime()}", isQPlus(),
                    ".json", R.string.photo_profiles_export) { path, filename ->
                    if (isQPlus()) {
                        try {
                            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/json"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, filename)
                            }, SELECT_EXPORT_PHOTO_PROFILES_INTENT)
                        } catch (error: Exception) { showErrorToast(error) }
                    } else {
                        legacyBackupOutput(path, "application/json", ::exportPhotoProfilesTo)
                    }
                }
            }
            if (isQPlus()) chooseExport() else handlePermission(PERMISSION_WRITE_STORAGE) { if (it) chooseExport() }
        }
        binding.settingsImportPhotoProfilesHolder.setOnClickListener {
            if (isQPlus()) {
                try {
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/json"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }, SELECT_IMPORT_PHOTO_PROFILES_INTENT)
                } catch (error: Exception) { showErrorToast(error) }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) { granted ->
                    if (granted) FilePickerDialog(this) { path -> backupWork { importPhotoProfiles(File(path).inputStream()) } }
                }
            }
        }
    }

    private fun exportPhotoProfilesTo(outputStream: OutputStream?) = backupWork {
        BackupIo.writeText(outputStream) {
            PhotoProfileBackupCodec.encodeStored(applicationContext.getSharedPrefs().getString(PhotoProfileRepository.KEY, "").orEmpty())
        }
        backupUi { toast(R.string.photo_profiles_export_success) }
    }

    private fun importPhotoProfiles(inputStream: InputStream?) = backupWork {
        val incoming = PhotoProfileBackupCodec.decodeStrict(BackupIo.readText(inputStream))
        val result = PhotoProfileRepository.of(applicationContext).importState(incoming).result
        backupUi {
            toast(getString(R.string.photo_profiles_import_success, result.imported, result.existing, result.conflicts))
        }
    }

    private fun setupExportSettings() {
        binding.settingsExportHolder.setOnClickListener {
            val snapshot = collectLegacySettings(config)
            if (isQPlus()) {
                configItemsToExport = snapshot
                ExportFavoritesDialog(this, "${baseConfig.appId.removeSuffix(".debug").removePrefix("org.fossify.")}-settings_${getCurrentFormattedDateTime()}", true,
                    dialogTitle = org.fossify.commons.R.string.export_settings) { _, filename ->
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, filename)
                        addCategory(Intent.CATEGORY_OPENABLE)
                        try { startActivityForResult(this, SELECT_EXPORT_SETTINGS_FILE_INTENT) }
                        catch (error: Exception) { showErrorToast(error) }
                    }
                }
            } else {
                handlePermission(PERMISSION_WRITE_STORAGE) { granted ->
                    if (granted) {
                        val appName = baseConfig.appId.removeSuffix(".debug").removePrefix("org.fossify.")
                        ExportFavoritesDialog(this, "$appName-settings_${getCurrentFormattedDateTime()}", false,
                            dialogTitle = org.fossify.commons.R.string.export_settings) { path, _ ->
                            legacyBackupOutput(path, "text/plain") { output ->
                                backupWork {
                                    BackupIo.writeText(output) { LegacySettingsCodec.encode(snapshot) }
                                    backupUi { toast(org.fossify.commons.R.string.settings_exported_successfully) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupImportSettings() {
        binding.settingsImportHolder.setOnClickListener {
            if (isQPlus()) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    try { startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT) }
                    catch (error: Exception) { showErrorToast(error) }
                }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) {
                    if (it) {
                        FilePickerDialog(this) {
                            backupWork {
                                parseFile(File(it).inputStream())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseFile(inputStream: InputStream?) = backupWork {
        val plan = LegacySettingsCodec.decode(BackupIo.readText(inputStream))
        require(plan.applicable) {
            "Invalid settings: ${plan.invalidKeys}; duplicate keys: ${plan.duplicateKeys}; supported values: ${plan.values.size}"
        }
        LegacySettingsRestore.apply(applicationContext, config, plan)
        backupUi {
            if (APP_ICON_COLOR in plan.values) checkAppIconColor()
            setupSettingItems()
            renderSettingsPage()
            toast(org.fossify.commons.R.string.settings_imported_successfully)
        }
    }
}
