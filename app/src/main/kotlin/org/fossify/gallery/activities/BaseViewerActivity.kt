package org.fossify.gallery.activities

import org.fossify.gallery.helpers.OxygenClassicActivityMotion

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.updateMarginWithBase
import org.fossify.commons.extensions.updatePaddingWithBase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenThemeTokens

abstract class BaseViewerActivity : SimpleActivity() {
    override val padCutout: Boolean = false
    abstract val contentHolder: View
    abstract val appBarLayout: AppBarLayout

    override fun finish() {
        super.finish()
        OxygenClassicActivityMotion.applyClose(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OxygenClassicActivityMotion.install(this)
        val contentRoot = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { _, insets ->
            setupEdgeToEdge(insets)
            insets
        }
        registerShowNotchCollector(contentRoot)
    }

    protected open val viewerSystemBarProgress: Float get() = 1f
    protected open val viewerStatusSurfaceVisible: Boolean get() = true

    private lateinit var viewerBarColors: OxygenThemeTokens

    override fun onResume() {
        super.onResume()
        refreshViewerSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshViewerSystemBars()
    }

    protected fun refreshViewerSystemBars() {
        viewerBarColors = OxygenThemeEngine.snapshot(this, config)
        updateViewerSystemBars()
    }

    protected fun updateViewerSystemBars() {
        val changed = OxygenSystemBars.applyViewer(
            this, viewerBarColors, viewerSystemBarProgress, viewerStatusSurfaceVisible)
        // Only actual changes are recorded: the entry clock calls this per frame and a
        // per-frame log line would both cost time and hide the interesting frames.
        if (changed && org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) {
            val underlay = window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            val underlayColor = (underlay?.background as? android.graphics.drawable.ColorDrawable)?.color
            @Suppress("DEPRECATION")
            val legacyColor = window.statusBarColor
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "Viewer", "viewer_status_appearance_changed",
                null,
                mapOf(
                    "progress" to viewerSystemBarProgress,
                    "statusBarVisible" to viewerStatusSurfaceVisible,
                    "iconAppearance" to if (bars.isAppearanceLightStatusBars) "DARK" else "LIGHT",
                    "navigationIconAppearance" to if (bars.isAppearanceLightNavigationBars) "DARK" else "LIGHT",
                    "underlayVisibility" to (underlay?.visibility == View.VISIBLE),
                    "backgroundColor" to (underlayColor ?: legacyColor),
                )
            )
        }
    }

    private fun registerShowNotchCollector(view: View) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                config.showNotchFlow.collect {
                    view.requestApplyInsets()
                }
            }
        }
    }

    private fun setupEdgeToEdge(insets: WindowInsetsCompat) {
        if (config.showNotch) {
            val systemAndCutout =
                insets.getInsetsIgnoringVisibility(Type.systemBars() or Type.displayCutout())
            appBarLayout.updatePaddingWithBase(
                top = systemAndCutout.top,
                left = systemAndCutout.left,
                right = systemAndCutout.right
            )

            contentHolder.updatePaddingWithBase(left = 0, top = 0, right = 0, bottom = 0)
        } else {
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
            val cutout = insets.getInsetsIgnoringVisibility(Type.displayCutout())
            appBarLayout.updatePaddingWithBase(
                top = if (cutout.top > 0) 0 else system.top,
                left = if (cutout.left > 0) 0 else system.left,
                right = if (cutout.right > 0) 0 else system.right
            )

            contentHolder.updatePaddingWithBase(
                left = cutout.left,
                top = cutout.top,
                right = cutout.right,
                bottom = cutout.bottom
            )
        }
    }

    fun applyProperHorizontalInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            if (config.showNotch) {
                val systemAndCutout =
                    insets.getInsetsIgnoringVisibility(Type.systemBars() or Type.displayCutout())
                view.updateMarginWithBase(
                    left = systemAndCutout.left,
                    right = systemAndCutout.right
                )
            } else {
                val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
                val cutout = insets.getInsetsIgnoringVisibility(Type.displayCutout())
                view.updateMarginWithBase(
                    left = if (cutout.left > 0) 0 else system.left,
                    right = if (cutout.right > 0) 0 else system.right
                )
            }
            insets
        }
    }
}
