package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuAdapter
import androidx.appcompat.view.menu.MenuBuilder
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ClassicToolbarLayoutTest {
    private fun descendants(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount)
            .flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun folderToolbarHasOneActiveBackTargetAfterRepeatedRebinds() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val page = LayoutInflater.from(activity).inflate(R.layout.activity_media, null)
        val menu = page.findViewById<MySearchMenu>(R.id.media_menu)
        menu.requireToolbar().inflateMenu(R.menu.menu_media)
        menu.setupMenu()
        var backCount = 0
        menu.onNavigateBackClickListener = { backCount++ }
        OxygenToolbarStyle.apply(activity, menu, "First folder", backButton = true)
        OxygenToolbarStyle.apply(activity, menu, "Second folder", backButton = true)
        val children = descendants(menu.binding.toolbarContainer)
        val backs = children.filterIsInstance<ImageView>().filter {
            it.tag == "oxygen_classic_toolbar_back"
        }
        assertEquals(1, backs.size)
        assertEquals(View.VISIBLE, backs.single().visibility)
        assertEquals("Second folder", children.filterIsInstance<TextView>()
            .single { it.tag == "oxygen_classic_toolbar_title" }.text.toString())
        assertFalse(menu.useArrowIcon)
        backs.single().performClick()
        assertEquals(1, backCount)
        OxygenToolbarStyle.apply(activity, menu, "Root", backButton = false)
        assertEquals(View.GONE, backs.single().visibility)
        assertEquals(1, descendants(menu.binding.toolbarContainer)
            .count { it.tag == "oxygen_classic_toolbar_back" })
    }

    @Test fun photoToolbarUsesPlainNonClickableTitleAndOneFixedManageAction() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val page = LayoutInflater.from(activity).inflate(R.layout.activity_media, null)
        val menu = page.findViewById<MySearchMenu>(R.id.media_menu)
        menu.requireToolbar().inflateMenu(R.menu.menu_media)
        menu.setupMenu()

        OxygenToolbarStyle.apply(activity, menu, activity.getString(R.string.gallery_nav_photos))
        val children = descendants(menu.binding.toolbarContainer)
        val title = children.filterIsInstance<TextView>()
            .single { it.tag == "oxygen_classic_toolbar_title" }
        assertEquals(activity.getString(R.string.gallery_nav_photos), title.text.toString())
        assertFalse(title.isClickable)
        assertFalse(title.isFocusable)
        assertEquals(20f, title.textSize / activity.resources.displayMetrics.scaledDensity, 0.2f)
        assertEquals(0, children.count { it.tag == "oxygen_photo_profile_title_control" })
        assertFalse(title.performClick())

        val manager = menu.requireToolbar().menu.findItem(R.id.photo_profiles_manage)
        assertEquals(7, manager.order)
        assertNotNull(manager.icon)
        manager.isVisible = true // Page policy keeps it visible even when no profile exists.
        assertTrue(manager.isVisible)
    }

    @Test fun settingsThemeRowSharesTheLeadingTextAlignment() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val page = LayoutInflater.from(activity).inflate(R.layout.activity_settings, null)
        page.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST))
        page.layout(0, 0, 360, page.measuredHeight)
        val theme = page.findViewById<TextView>(R.id.settings_color_customization_label)
        val navigation = page.findViewById<TextView>(R.id.settings_bottom_navigation_order)
        assertEquals(navigation.left, theme.left)
        assertEquals(16f, theme.textSize / activity.resources.displayMetrics.scaledDensity, 0.2f)
    }

    @Test fun viewerPopupTextIsReadableInBothAppearanceModes() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val toolbar = MaterialToolbar(activity)
        activity.config.oxygenThemeActivated = true
        for (mode in listOf("light", "dark")) {
            activity.config.oxygenAppearanceMode = mode
            OxygenToolbarStyle.applyViewerPopup(activity, toolbar)
            val attributes = ContextThemeWrapper(activity, toolbar.popupTheme).obtainStyledAttributes(
                intArrayOf(android.R.attr.textColorPrimary))
            val text = attributes.getColorStateList(0)!!.defaultColor
            attributes.recycle()
            val surface = if (mode == "light") 0xFFFFFFFF.toInt() else 0xFF303030.toInt()
            assertTrue(OxygenThemeColors.contrastRatio(text, surface) >= 4.5)
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    @Test fun classicOverflowMeasuresLongCheckedLabelsWithoutControlOverlap() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.config.oxygenThemeActivated = true
        for (mode in listOf("light", "dark")) {
            activity.config.oxygenAppearanceMode = mode
            val context = ContextThemeWrapper(activity, OxygenClassicMenus.theme(activity))
            val popup = OxygenClassicMenus.createPopup(activity, View(activity))
            popup.menu.add(0, 1, 0, "始终显示已排除项目").apply {
                isCheckable = true
                isChecked = true
            }
            val adapter = MenuAdapter(popup.menu as MenuBuilder, LayoutInflater.from(context),
                false, androidx.appcompat.R.layout.abc_popup_menu_item_layout)
            val row = adapter.getView(0, null, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            row.measure(View.MeasureSpec.makeMeasureSpec((256 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            val title = descendants(row).filterIsInstance<TextView>()
                .single { it.text.toString() == "始终显示已排除项目" }
            val check = descendants(row).filterIsInstance<CheckBox>().single()
            assertTrue(check.isChecked)
            assertEquals(16f, title.textSize / context.resources.displayMetrics.scaledDensity, 0.2f)
            assertTrue(row.measuredHeight >= (48 * density).toInt())
            val titleBounds = Rect().also { title.getDrawingRect(it) }
            val checkBounds = Rect().also { check.getDrawingRect(it) }
            (row as ViewGroup).offsetDescendantRectToMyCoords(title, titleBounds)
            row.offsetDescendantRectToMyCoords(check, checkBounds)
            assertTrue("Menu title overlaps its checked control", titleBounds.right <= checkBounds.left)
        }
    }
}
