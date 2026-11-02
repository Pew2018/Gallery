package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.views.MyDialogViewPager
import org.fossify.gallery.views.ClassicSecurityViewPager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ClassicSecurityViewPagerTest {
    @Test fun commonsSecurityLayoutKeepsItsBindingCompatibleAndUsesSelectedPageHeight() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(org.fossify.gallery.R.style.OxygenClassicAlertDialog_Light)
        val root = LayoutInflater.from(activity).inflate(org.fossify.commons.R.layout.dialog_security, null)
        val pager = root.findViewById<MyDialogViewPager>(org.fossify.commons.R.id.dialog_tab_view_pager)
        assertTrue(pager is ClassicSecurityViewPager)

        val pages = listOf(
            View(activity).apply {
                id = org.fossify.commons.R.id.pattern_lock_holder
                layoutParams = androidx.viewpager.widget.ViewPager.LayoutParams().apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 240
                }
            },
            View(activity).apply {
                id = org.fossify.commons.R.id.pin_lock_holder
                layoutParams = androidx.viewpager.widget.ViewPager.LayoutParams().apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 370
                }
            }
        )
        val adapter = object : PagerAdapter() {
            override fun getCount() = pages.size
            override fun isViewFromObject(view: View, item: Any) = view === item
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val child = pages[position]
                if (child.parent == null) container.addView(child)
                return child
            }
            override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
                container.removeView(item as View)
            }
        }
        pager.adapter = adapter
        // Robolectric does not populate a wrap-content ViewPager automatically
        // before its first nonzero measurement. Supply the two adapter pages
        // explicitly; production SecurityDialog populates them when shown.
        adapter.instantiateItem(pager, 0)
        adapter.instantiateItem(pager, 1)

        // Verify the measured height after the parent attaches and lays out.
        activity.setContentView(root)
        val width = View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.AT_MOST)
        root.measure(width, height)
        root.layout(0, 0, 340, root.measuredHeight)

        fun measuredHeight(): Int {
            pager.measure(width, height)
            pager.layout(0, 0, 340, pager.measuredHeight)
            pager.measure(width, height)
            return pager.measuredHeight
        }

        assertEquals(240, measuredHeight())
        pager.setCurrentItem(1, false)
        assertEquals(370, measuredHeight())
    }
}
