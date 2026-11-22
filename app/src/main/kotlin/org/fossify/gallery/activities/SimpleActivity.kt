package org.fossify.gallery.activities

import android.database.ContentObserver
import android.os.Bundle
import android.content.res.Configuration
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenThemeTokens
import org.fossify.gallery.helpers.OxygenThemeViewState
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.gallery.helpers.OxygenSystemBars
import android.net.Uri
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.view.WindowManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isPiePlus
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.StoragePermissionRequiredDialog
import org.fossify.gallery.extensions.addPathToDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.updateDirectoryPath
import org.fossify.gallery.helpers.getPermissionsToRequest

open class SimpleActivity : BaseSimpleActivity() {
    private var inflatedTheme: OxygenThemeTokens? = null
    private var inflatedAppearance: Pair<Boolean, String>? = null
    private var themeRecreationRequested = false
    private var restoredThemeState: Bundle? = null
    private val themeViewState = OxygenThemeViewState()

    // Fullscreen viewers and editors keep their media/playback/edit state and dedicated themes.
    private val recreatesForTheme: Boolean
        get() = this is MainActivity || this is MediaActivity || this is SearchActivity ||
            this is SettingsActivity || this is DiagnosticsActivity || this is PhotoProfileEditActivity || this is PhotoProfileManageActivity || this is IncludedFoldersActivity ||
            this is ExcludedFoldersActivity || this is HiddenFoldersActivity ||
            this is OxygenThemeActivity

    private val usesSharedToolbar: Boolean
        get() = recreatesForTheme || this is SetWallpaperActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        // Viewer/editor starting windows share the same black media canvas as their roots.
        val photoSharedTransition = this is ViewPagerActivity &&
            intent.hasExtra(org.fossify.gallery.helpers.PhotoViewerTransition.EXTRA_PATH) &&
            org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this)
        if (this is EditActivity || this is ViewPagerActivity || this is PhotoVideoActivity) {
            window.setBackgroundDrawable(ColorDrawable(
                if (photoSharedTransition) Color.TRANSPARENT else Color.BLACK
            ))
        }
        // Commons resolves its base theme in super.onCreate(), before Gallery inflates controls.
        OxygenThemeEngine.apply(this, config)
        // AppCompat remembers localNightMode across recreate(). Explicitly clear it
        // when restoring Commons colors, rather than inheriting the previous forced mode.
        delegate.localNightMode = when {
            !config.oxygenThemeActivated -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
            config.oxygenAppearanceMode == "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            config.oxygenAppearanceMode == "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        org.fossify.gallery.helpers.GalleryBottomBar.traceWindow(this, "create_before_commons")
        super.onCreate(savedInstanceState)
        (this as? ViewPagerActivity)?.traceEntryCanvas("commons_create_after")
        if (this is EditActivity || this is ViewPagerActivity || this is PhotoVideoActivity) {
            window.setBackgroundDrawable(ColorDrawable(
                if (photoSharedTransition) Color.TRANSPARENT else Color.BLACK
            ))
        }
        applyClassicOverlay()
        org.fossify.gallery.helpers.GalleryBottomBar.restoreRootWindow(this)
        applyPageWindowSurface()
        org.fossify.gallery.helpers.GalleryBottomBar.traceWindow(this, "create_after_commons")
        inflatedTheme = OxygenThemeEngine.snapshot(this, config)
        inflatedAppearance = config.oxygenThemeActivated to config.oxygenAppearanceMode
        restoredThemeState = savedInstanceState
        // Commons registers one permanently enabled back callback for every page.
        // Pages that have no in-app step must drop it: an enabled callback takes the
        // gesture away from the system and suppresses its predictive exit preview.
        // Pages that do own a step re-enable it through [setAppBackStepAvailable].
        setAppBackStepAvailable(false)
        // A root handoff or a staged page fade may have installed a zero animation on
        // this Activity instance. It is persistent, so an ordinary back taken later
        // would inherit it instead of the platform's cross-Activity preview.
        org.fossify.gallery.helpers.PredictiveBackSupport.clearPageTransitionOverrides(this)
    }

    /**
     * The platform scales and clips the window while a predictive-back gesture runs.
     * Anything the page itself does not paint - the inset corners and the strip behind
     * the rounded card - would otherwise show the platform default, which is black on
     * this device and reads as an unthemed background.
     */
    private fun applyPageWindowSurface() {
        if (!usesSharedToolbar) return
        window.setBackgroundDrawable(ColorDrawable(
            OxygenThemeEngine.snapshot(this, config).pageBackground))
    }

    /**
     * True only while this page still has an in-app back step it must animate itself
     * (inline search, nested folder, unsaved edits, the Viewer's own return).
     * Every other page leaves back to the system so the predictive exit/return
     * preview is the only motion on screen.
     */
    protected fun setAppBackStepAvailable(available: Boolean) {
        setBackHandlingEnabled(available)
    }

    override fun onSupportActionModeStarted(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeStarted(mode)
        org.fossify.gallery.helpers.OxygenSelectionMode.completePresentation(this, true)
    }

    override fun onSupportActionModeFinished(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeFinished(mode)
        org.fossify.gallery.helpers.OxygenSelectionMode.completePresentation(this, false)
    }

    override fun onResume() {
        OxygenThemeEngine.apply(this, config)
        val viewer = this as? ViewPagerActivity
        viewer?.traceEntryCanvas("commons_resume_before")
        super.onResume()
        viewer?.traceEntryCanvas("commons_resume_after")
        // Commons' onResume calls setTheme(); restore the overlay after that call.
        applyClassicOverlay()
        org.fossify.gallery.helpers.GalleryBottomBar.restoreRootWindow(this)
        viewer?.traceEntryCanvas("classic_resume_after")
        org.fossify.gallery.helpers.GalleryBottomBar.traceWindow(this, "resume_after_commons")
    }

    override fun onPostResume() {
        super.onPostResume()
        if (refreshThemeIfNeeded()) return
        if (usesSharedToolbar) {
            OxygenToolbarStyle.refresh(this)
            OxygenSystemBars.apply(this)
        }
        themeViewState.restore(this, restoredThemeState)
        restoredThemeState = null
    }

    /**
     * Keep the task background on the page surface for every theme application.
     *
     * Android resolves `android:colorBackground` from the effective theme into the
     * Activity TaskDescription inside [onApplyThemeResource] (and the Shell back
     * animation paints its reveal background from the *entering* task description).
     * Commons re-applies its own Material3-Dark based themes in onCreate/onResume, so
     * a value declared only in styles.xml would be replaced before the TaskDescription
     * write happens.
     *
     * Android 16's Activity.onApplyThemeResource (aosp 99b01a65, Activity.java:5569)
     * does, in order: super.onApplyThemeResource -> theme.applyStyle(resid, true),
     * then reads ActivityTaskDescription_colorBackground into the TaskDescription.
     * Injecting the token *before* super is therefore lost to applyStyle(resid). Instead
     * we apply the real theme ourselves and pass the token AS the resid to super: the
     * token only overrides colorBackground (values-night carries the dark page), so
     * every other attribute stays from the real theme, and the framework reads the
     * page surface into the TaskDescription on the very next lines. Public API only.
     */
    override fun onApplyThemeResource(theme: android.content.res.Resources.Theme, resid: Int, first: Boolean) {
        theme.applyStyle(resid, true)
        super.onApplyThemeResource(theme, R.style.OxygenClassicTaskBackground, first)
    }

    private fun applyClassicOverlay() {
        theme.applyStyle(
            if (OxygenThemeEngine.snapshot(this, config).dark)
                R.style.OxygenClassicActivityOverlay_Dark
            else R.style.OxygenClassicActivityOverlay_Light,
            true
        )
    }

    /** Returns true only while a single recreation is pending; never rewrite seeds here. */
    protected fun refreshThemeIfNeeded(): Boolean {
        OxygenThemeEngine.apply(this, config)
        val current = OxygenThemeEngine.snapshot(this, config)
        val appearance = config.oxygenThemeActivated to config.oxygenAppearanceMode
        if (!recreatesForTheme || (inflatedTheme == current && inflatedAppearance == appearance)) return false
        // Theme picker owns and recolors its existing Views. Accent-only changes do not
        // need resource reinflation; appearance/activation changes still recreate normally.
        if (this is OxygenThemeActivity && inflatedAppearance == appearance && inflatedTheme?.dark == current.dark) {
            inflatedTheme = current
            applyClassicOverlay()
            return false
        }
        if (!themeRecreationRequested) {
            themeRecreationRequested = true
            window.decorView.setBackgroundColor(current.pageBackground)
            recreate()
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        themeViewState.save(this, outState)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        OxygenThemeEngine.apply(this, config)
        applyClassicOverlay()
        if (!refreshThemeIfNeeded()) {
            if (usesSharedToolbar) {
                OxygenToolbarStyle.refresh(this)
                OxygenSystemBars.apply(this)
            }
        }
    }

    override fun onDestroy() {
        themeViewState.clear()
        super.onDestroy()
    }

    private var dialog: AlertDialog? = null

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (uri != null) {
                val path = getRealPathFromURI(uri)
                if (path != null) {
                    updateDirectoryPath(path.getParentPath())
                    addPathToDB(path)
                }
            }
        }
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Gallery"

    protected fun checkNotchSupport() {
        if (isPiePlus()) {
            val cutoutMode = when {
                config.showNotch -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            window.attributes.layoutInDisplayCutoutMode = cutoutMode
            if (config.showNotch) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }
        }
    }

    protected fun registerFileUpdateListener() {
        try {
            contentResolver.registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            contentResolver.registerContentObserver(Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (ignored: Exception) {
        }
    }

    protected fun unregisterFileUpdateListener() {
        try {
            contentResolver.unregisterContentObserver(observer)
        } catch (ignored: Exception) {
        }
    }

    protected fun showAddIncludedFolderDialog(callback: () -> Unit) {
        FilePickerDialog(this, config.lastFilepickerPath, false, config.shouldShowHidden, false, true) {
            config.lastFilepickerPath = it
            config.addIncludedFolder(it)
            callback()
            ensureBackgroundThread {
                scanPathRecursively(it)
            }
        }
    }

    protected fun requestMediaPermissions(enableRationale: Boolean = false, onGranted: () -> Unit) {
        when {
            hasAllPermissions(getPermissionsToRequest()) -> onGranted()
            config.showPermissionRationale -> {
                if (enableRationale) {
                    showPermissionRationale()
                } else {
                    onPermissionDenied()
                }
            }

            else -> {
                handlePartialMediaPermissions(getPermissionsToRequest(), force = true) { granted ->
                    if (granted) {
                        onGranted()
                    } else {
                        config.showPermissionRationale = true
                        showPermissionRationale()
                    }
                }
            }
        }
    }

    private fun showPermissionRationale() {
        dialog?.dismiss()
        StoragePermissionRequiredDialog(
            activity = this,
            onOkay = ::openDeviceSettings,
            onCancel = ::onPermissionDenied
        ) { dialog ->
            this.dialog = dialog
        }
    }

    private fun onPermissionDenied() {
        toast(org.fossify.commons.R.string.no_storage_permissions)
        finish()
    }
}
