package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class OxygenToolbarActionTintTest {
    private fun menu(): MenuBuilder = MenuBuilder(RuntimeEnvironment.getApplication())

    private fun addIcon(menu: MenuBuilder, id: Int): MenuItem =
        menu.add(Menu.NONE, id, menu.size(), "Action").apply {
            icon = ColorDrawable(Color.WHITE)
        }

    /** Read actual rendered pixels, not MenuItem.iconTintList's cached target. */
    private fun pixel(drawable: Drawable, enabled: Boolean = true): Int {
        drawable.state = if (enabled) intArrayOf(android.R.attr.state_enabled) else intArrayOf()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 16, 16)
        drawable.draw(Canvas(bitmap))
        return bitmap.getPixel(8, 8)
    }

    @Test fun commonsBlackTintIsRestoredWithoutRebindingPhotoProfileOrQuickActions() {
        val menu = menu()
        val ids = listOf(
            R.id.photo_profiles_manage,
            R.id.quick_toolbar_slot_0,
            R.id.quick_toolbar_slot_1,
            R.id.quick_toolbar_slot_2,
            R.id.quick_toolbar_slot_3,
            R.id.quick_toolbar_slot_4,
            R.id.folder_view
        )
        val items = ids.map { addIcon(menu, it) }
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        val originalIcons = items.map { it.icon }

        // Exact conflict: Commons' EdgeToEdgeActivity mutates the same icons.
        items.forEach { item ->
            item.icon!!.setTint(Color.BLACK)
            assertEquals(Color.BLACK, pixel(item.icon!!))
        }
        repeat(3) {
            OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)
            items.forEachIndexed { index, item ->
                assertSame(originalIcons[index], item.icon)
                assertEquals(Color.WHITE, pixel(item.icon!!))
                assertEquals(ids[index], item.itemId)
                assertEquals(true, item.isVisible)
            }
            // A later Commons pass must also be recoverable with identical icons.
            if (it < 2) items.forEach { item -> item.icon!!.setTint(Color.BLACK) }
        }
    }

    @Test fun disabledSlideshowUsesTranslucentWhiteWithoutChangingEnabledState() {
        val menu = menu()
        val slideshow = addIcon(menu, R.id.quick_toolbar_slot_0)
        slideshow.isEnabled = false
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        val originalIcon = slideshow.icon
        slideshow.icon!!.setTint(Color.BLACK)
        OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)

        assertSame(originalIcon, slideshow.icon)
        assertEquals(false, slideshow.isEnabled)
        assertEquals(110, Color.alpha(pixel(slideshow.icon!!, enabled = false)))
        assertEquals(Color.WHITE, pixel(slideshow.icon!!, enabled = true))
    }

    @Test fun onlyActualActionSlotsAreRestoredAndNewIconsAreTinted() {
        val menu = menu()
        val action = addIcon(menu, R.id.quick_toolbar_slot_0)
        val overflowOnly = addIcon(menu, R.id.settings)
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        val oldAction = action.icon!!
        val overflowIcon = overflowOnly.icon!!
        action.icon!!.setTint(Color.BLACK)
        overflowOnly.icon!!.setTint(Color.BLACK)

        OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)
        assertEquals(Color.WHITE, pixel(action.icon!!))
        assertEquals(Color.BLACK, pixel(overflowOnly.icon!!))
        assertSame(overflowIcon, overflowOnly.icon)

        // A genuinely changed action icon may be rebound by QuickActions.
        action.icon = ColorDrawable(Color.BLACK)
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)
        assertNotSame(oldAction, action.icon)
        assertEquals(Color.WHITE, pixel(action.icon!!))
    }
}
