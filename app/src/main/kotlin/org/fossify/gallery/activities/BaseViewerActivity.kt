package org.fossify.gallery.activities

import org.fossify.gallery.helpers.OxygenClassicActivityMotion

import android.os.Bundle
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
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

    override fun onResume() {
        super.onResume()
        applyViewerSystemBars()
    }

    private fun applyViewerSystemBars() {
        val decor = window.decorView
        val bars = WindowCompat.getInsetsController(window, decor)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = getColor(org.fossify.gallery.R.color.viewer_status_surface)
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.BLACK
        } else {
            val host = decor as? FrameLayout ?: return
            val underlay = host.findViewWithTag<View>(VIEWER_STATUS_UNDERLAY) ?: View(this).also { view ->
                view.tag = VIEWER_STATUS_UNDERLAY
                view.isClickable = false
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                host.addView(view, 0, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    0,
                    android.view.Gravity.TOP
                ))
                ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
                    val top = insets.getInsets(
                        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                    ).top
                    target.layoutParams = (target.layoutParams as FrameLayout.LayoutParams).apply { height = top }
                    insets
                }
            }
            underlay.setBackgroundColor(getColor(org.fossify.gallery.R.color.viewer_status_surface))
            ViewCompat.getRootWindowInsets(host)?.let { insets ->
                val top = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                ).top
                underlay.layoutParams = (underlay.layoutParams as FrameLayout.LayoutParams).apply { height = top }
            }
            ViewCompat.requestApplyInsets(underlay)
        }
    }

    private companion object {
        const val VIEWER_STATUS_UNDERLAY = "gallery_viewer_status_underlay"
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
