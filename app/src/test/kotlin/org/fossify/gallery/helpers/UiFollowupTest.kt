package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.ListMenuItemView
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.ClassicPasswordMethodDialog
import org.fossify.gallery.extensions.showPhotoPasswordDialog
import org.fossify.gallery.extensions.showFavoritePasswordDialog
import org.fossify.gallery.views.ClassicToolbarChoiceBody
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class, qualifiers = "w411dp-h800dp-mdpi")
class UiFollowupTest {
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun host() = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(R.style.OxygenClassicAlertDialog_Light)
        config.oxygenThemeActivated = true
        config.oxygenAppearanceMode = "light"
    }

    @Suppress("RestrictedApi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun favoriteMenuOptionsArePlainTextAndOtherCheckableRowsKeepTheirCheckbox() {
        val activity = host()
        val themed = androidx.appcompat.view.ContextThemeWrapper(activity, OxygenClassicMenus.theme(activity))
        val menu = MenuBuilder(themed)
        androidx.appcompat.view.SupportMenuInflater(themed).inflate(R.menu.menu_main, menu)
        for (id in listOf(R.id.favorite_always_show_hidden, R.id.favorite_always_show_excluded)) {
            val xmlItem = menu.findItem(id) as MenuItemImpl
            assertFalse("favorite action is not a checked state", xmlItem.isCheckable)
            assertEquals(if (id == R.id.favorite_always_show_excluded) 40 else 41, xmlItem.order)
        }
        menu.clear()
        for (id in listOf(R.id.favorite_always_show_hidden, R.id.favorite_always_show_excluded)) {
            val item = menu.add(0, id, 0, "Favorite option") as MenuItemImpl
            assertFalse(item.isCheckable)
            val row = LayoutInflater.from(themed).inflate(R.layout.abc_popup_menu_item_layout, null) as ListMenuItemView
            row.initialize(item, 0)
            assertTrue(all(row).filterIsInstance<CheckBox>().isEmpty())
            assertEquals("Favorite option", row.findViewById<TextView>(androidx.appcompat.R.id.title).text)
        }

        val other = menu.add(0, 90001, 0, "Other checkable option").setCheckable(true).setChecked(true) as MenuItemImpl
        val otherRow = LayoutInflater.from(themed).inflate(R.layout.abc_popup_menu_item_layout, null) as ListMenuItemView
        otherRow.initialize(other, 0)
        assertTrue(all(otherRow).filterIsInstance<CheckBox>().isNotEmpty())
        assertTrue(all(otherRow).filterIsInstance<CheckBox>().any { it.isChecked })
        activity.finish()
    }

    @Test fun favoriteActionTitlesCoverAllIndependentBooleanCombinationsAndLocales() {
        val context = host()
        val titleIds = listOf(
            FavoriteMenuActionTitles.excluded(false) to "Show excluded items",
            FavoriteMenuActionTitles.excluded(true) to "Do not show excluded items",
            FavoriteMenuActionTitles.hidden(false) to "Show hidden items",
            FavoriteMenuActionTitles.hidden(true) to "Do not show hidden items"
        )
        titleIds.forEach { (resId, expected) -> assertEquals(expected, context.getString(resId)) }
        val chinese = context.createConfigurationContext(android.content.res.Configuration().apply {
            setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        })
        assertEquals("显示已排除项目", chinese.getString(FavoriteMenuActionTitles.excluded(false)))
        assertEquals("不显示已排除项目", chinese.getString(FavoriteMenuActionTitles.excluded(true)))
        assertEquals("显示隐藏项目", chinese.getString(FavoriteMenuActionTitles.hidden(false)))
        assertEquals("不显示隐藏项目", chinese.getString(FavoriteMenuActionTitles.hidden(true)))
    }

    @Test fun toolbarUsesOneBodyRippleTrailingChecksAndIndependentHandle() {
        val activity = host()
        val before = activity.config.toolbarHomeActions.toList()
        val dialog = ToolbarQuickActionsDialog.show(activity)
        val list = all(dialog.window!!.decorView).filterIsInstance<androidx.recyclerview.widget.RecyclerView>().single()
        @Suppress("UNCHECKED_CAST")
        val adapter = list.adapter as androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>
        val holder = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(holder, 0)
        val row = holder.itemView as ViewGroup
        val body = row.getChildAt(0) as ClassicToolbarChoiceBody
        val check = all(body).filterIsInstance<CheckBox>().single()
        val mark = body.getChildAt(1) as ImageView
        val drag = row.getChildAt(1)
        assertNull(row.background)
        assertTrue(body.background is android.graphics.drawable.RippleDrawable)
        assertNull(check.background)
        assertNull(check.buttonDrawable)
        val initial = check.isChecked
        body.performClick()
        assertEquals(!initial, check.isChecked)
        assertEquals(if (check.isChecked) View.VISIBLE else View.INVISIBLE, mark.visibility)
        val after = check.isChecked
        drag.performClick()
        assertEquals("handle is not a selection toggle", after, check.isChecked)
        val fixed = all(dialog.window!!.decorView).filterIsInstance<CheckBox>().first {
            it.text == activity.getString(R.string.quick_toolbar_browse_view)
        }
        val fixedBody = fixed.parent as ClassicToolbarChoiceBody
        assertNull(fixedBody.background)
        assertFalse(fixedBody.isClickable)
        assertEquals(View.GONE, fixedBody.getChildAt(1).visibility)
        fixedBody.performClick()
        assertTrue(fixed.isChecked)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(before, activity.config.toolbarHomeActions)
        activity.finish()
    }

    @Test fun bothSecurityManagementWindowsMatchTheirNextChoiceWithoutWideningGenericLists() {
        for (width in listOf(320, 360, 411, 600, 840)) {
            RuntimeEnvironment.setQualifiers("w${width}dp-h900dp-mdpi")
            val activity = host()
            for (photo in listOf(false, true)) {
                val build: AlertDialog.Builder.() -> Unit = { setItems(arrayOf("Change", "Disable")) { _, _ -> } }
                val manage = if (photo) activity.showPhotoPasswordDialog(R.string.photo_password_title, build)
                    else activity.showFavoritePasswordDialog(R.string.favorite_password_title, build)
                val first = manage.window!!.attributes.width
                assertEquals(OxygenClassicDialog.widthForContent(OxygenClassicDialog.availableWidth(activity), 1f, 380), first)
                assertTrue(manage.listView.adapter.count == 2)
                manage.dismiss()
                val next = ClassicPasswordMethodDialog.show(activity, R.string.photo_password_title,
                    listOf("Pattern", "PIN"), onCancel = {}, onChoice = {})
                assertEquals(first, next.window!!.attributes.width)
                next.dismiss()
            }
            val ordinary = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Generic")
                .setItems(arrayOf("A", "B"), null))
            assertEquals(OxygenClassicDialog.widthFor(OxygenClassicDialog.availableWidth(activity), 1f), ordinary.window!!.attributes.width)
            ordinary.dismiss(); activity.finish()
        }
    }
}
