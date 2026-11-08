package org.fossify.gallery.helpers

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.graphics.ColorUtils
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
    /** Robolectric's Canvas does not reliably rasterize ColorDrawable tint.
     * Record tint API calls instead of making false assertions about pixels. */
    private class TintProbeDrawable : ColorDrawable(Color.WHITE) {
        private var mostRecentTint: ColorStateList? = null

        override fun setTint(tintColor: Int) {
            super.setTint(tintColor)
            mostRecentTint = ColorStateList.valueOf(tintColor)
        }

        override fun setTintList(tint: ColorStateList?) {
            super.setTintList(tint)
            mostRecentTint = tint
        }

        fun tintColor(enabled: Boolean = true): Int {
            val state = if (enabled) intArrayOf(android.R.attr.state_enabled) else intArrayOf()
            return checkNotNull(mostRecentTint).getColorForState(state, Color.TRANSPARENT)
        }
    }

    private fun menu(): MenuBuilder = MenuBuilder(RuntimeEnvironment.getApplication())

    private fun addIcon(menu: MenuBuilder, id: Int): Pair<MenuItem, TintProbeDrawable> {
        val probe = TintProbeDrawable()
        val item = menu.add(Menu.NONE, id, menu.size(), "Action")
        item.icon = probe
        return item to probe
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
        val entries = ids.map { addIcon(menu, it) }
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        val icons = entries.map { it.first.icon }

        // Exact call pattern used by Commons' EdgeToEdgeActivity.
        entries.forEach { (item, probe) ->
            item.icon!!.setTint(Color.BLACK)
            assertEquals(Color.BLACK, probe.tintColor())
        }
        repeat(3) { iteration ->
            OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)
            entries.forEachIndexed { index, (item, probe) ->
                assertSame(icons[index], item.icon)
                assertEquals(Color.WHITE, probe.tintColor())
                assertEquals(ids[index], item.itemId)
                assertEquals(true, item.isVisible)
            }
            // A subsequent Commons refresh must remain recoverable in-place.
            if (iteration < 2) entries.forEach { (item, _) -> item.icon!!.setTint(Color.BLACK) }
        }
    }

    @Test fun disabledSlideshowRetainsTransparentWhiteStateAndIconIdentity() {
        val menu = menu()
        val (slideshow, probe) = addIcon(menu, R.id.quick_toolbar_slot_0)
        slideshow.isEnabled = false
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        val originalIcon = slideshow.icon
        slideshow.icon!!.setTint(Color.BLACK)

        OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)

        assertSame(originalIcon, slideshow.icon)
        assertEquals(false, slideshow.isEnabled)
        assertEquals(ColorUtils.setAlphaComponent(Color.WHITE, 110), probe.tintColor(enabled = false))
        assertEquals(Color.WHITE, probe.tintColor(enabled = true))
    }

    @Test fun onlyRealToolbarActionSlotsRecoverAndReinflatedIconsAreBound() {
        val menu = menu()
        val (action, actionProbe) = addIcon(menu, R.id.quick_toolbar_slot_0)
        val (overflow, overflowProbe) = addIcon(menu, R.id.settings)
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        val originalAction = action.icon
        val originalOverflow = overflow.icon
        action.icon!!.setTint(Color.BLACK)
        overflow.icon!!.setTint(Color.BLACK)

        OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)

        assertEquals(Color.WHITE, actionProbe.tintColor())
        assertEquals(Color.BLACK, overflowProbe.tintColor())
        assertSame(originalOverflow, overflow.icon)

        // When a different action is selected, QuickActions really can rebind.
        val newProbe = TintProbeDrawable()
        action.icon = newProbe
        OxygenToolbarStyle.tintMenu(menu, Color.WHITE)
        OxygenToolbarStyle.reassertActionTints(menu, Color.WHITE)
        assertNotSame(originalAction, action.icon)
        assertEquals(Color.WHITE, newProbe.tintColor())
    }
}
