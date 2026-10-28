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
            this is SettingsActivity || this is IncludedFoldersActivity ||
            this is ExcludedFoldersActivity || this is HiddenFoldersActivity ||
            this is OxygenThemeActivity

    private val usesSharedToolbar: Boolean
        get() = recreatesForTheme || this is EditActivity || this is SetWallpaperActivity

    override fun onCreate(savedInstanceState: Bundle?) {
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
        super.onCreate(savedInstanceState)
        applyClassicOverlay()
        inflatedTheme = OxygenThemeEngine.snapshot(this, config)
        inflatedAppearance = config.oxygenThemeActivated to config.oxygenAppearanceMode
        restoredThemeState = savedInstanceState
    }

    override fun onResume() {
        OxygenThemeEngine.apply(this, config)
        super.onResume()
        // Commons' onResume calls setTheme(); restore the overlay after that call.
        applyClassicOverlay()
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
