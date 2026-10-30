package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Focused functional contracts for the folder-picker presentation layer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FolderPickerPolishTest {
    private fun activity(): AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).setup().get().apply {
            baseConfig.isSystemThemeEnabled = false
            baseConfig.backgroundColor = Color.WHITE
            baseConfig.textColor = Color.BLACK
            baseConfig.primaryColor = Color.rgb(33, 150, 243)
        }

    @Test fun commonsHiddenActionRemainsAvailableAsMenuAfterReveal() {
        val activity = activity()
        val host = FrameLayout(activity)
        val original = View(activity).apply {
            id = org.fossify.commons.R.id.filepicker_fab_show_hidden
            visibility = View.VISIBLE
        }
        val menu = ClassicFilePickerOverflowButton(activity).apply { visibility = View.GONE }
        host.addView(original)
        host.addView(menu)
        activity.setContentView(host)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, menu.visibility)
        assertEquals(0f, original.alpha, 0f)
        original.visibility = View.GONE
        menu.syncVisibility()
        assertEquals(View.VISIBLE, menu.visibility)
        assertEquals(R.string.picker_hidden_folders_visible, menu.currentActionLabel())
    }

    @Test fun hiddenActionMenuIsAbsentWhenCommonsNeverOffersReveal() {
        val activity = activity()
        val host = FrameLayout(activity)
        host.addView(View(activity).apply {
            id = org.fossify.commons.R.id.filepicker_fab_show_hidden
            visibility = View.GONE
        })
        val menu = ClassicFilePickerOverflowButton(activity).apply { visibility = View.GONE }
        host.addView(menu)
        activity.setContentView(host)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        menu.syncVisibility()
        assertEquals(View.GONE, menu.visibility)
    }

    @Test fun popupThemesKeepReadableTextAgainstTheirActualSurfaces() {
        val activity = activity()
        for ((style, surface) in listOf(
            R.style.OxygenClassicPopupTheme_Light to Color.WHITE,
            R.style.OxygenClassicPopupTheme_Dark to Color.rgb(48, 48, 48)
        )) {
            val themed = ContextThemeWrapper(activity, style)
            val attrs = themed.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
            val foreground = try { attrs.getColor(0, Color.TRANSPARENT) } finally { attrs.recycle() }
            assertTrue("Insufficient menu contrast for style $style",
                ColorUtils.calculateContrast(foreground, surface) >= 4.5)
        }
    }

    @Test fun shortClassicPickerDoesNotOccupyTheWholeDialogViewport() {
        val activity = activity()
        val picker = ClassicDialogPickerLayout(activity)
        picker.addView(View(activity), androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(200, 120))
        picker.measure(
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST)
        )
        assertTrue("Short picker must wrap its content", picker.measuredHeight in 120..250)
    }
}
