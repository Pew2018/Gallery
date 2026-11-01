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

        pager.adapter = object : PagerAdapter() {
            override fun getCount() = 2
            override fun isViewFromObject(view: View, item: Any) = view === item
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val child = View(activity).apply {
                    id = if (position == 0) org.fossify.commons.R.id.pattern_lock_holder
                        else org.fossify.commons.R.id.pin_lock_holder
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        if (position == 0) 240 else 370)
                }
                container.addView(child)
                return child
            }
            override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
                container.removeView(item as View)
            }
        }

        fun measuredHeight(): Int {
            pager.measure(
                View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.AT_MOST)
            )
            return pager.measuredHeight
        }

        assertEquals(240, measuredHeight())
        pager.setCurrentItem(1, false)
        assertEquals(370, measuredHeight())
    }
}
