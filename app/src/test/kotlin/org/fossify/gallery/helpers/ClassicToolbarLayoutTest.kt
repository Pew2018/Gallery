package org.fossify.gallery.helpers

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
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
}
