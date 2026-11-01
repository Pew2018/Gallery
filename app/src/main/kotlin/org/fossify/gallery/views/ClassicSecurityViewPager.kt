package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.viewpager.widget.ViewPager
import org.fossify.commons.views.MyDialogViewPager
import org.fossify.commons.R as CommonsR

/**
 * Commons measures all offscreen security tabs and selects the tallest.
 * Retain its no-swipe behavior and security callbacks, changing only sizing
 * to fit the selected tab (PIN, pattern, or biometric).
 */
class ClassicSecurityViewPager : MyDialogViewPager {
    constructor(context: Context) : super(context) { monitorPage() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { monitorPage() }

    private fun monitorPage() {
        addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                requestLayout()
                (parent as? View)?.requestLayout()
            }
        })
    }

    private fun activePage(): View? {
        val ids = when (currentItem) {
            0 -> intArrayOf(CommonsR.id.pattern_lock_holder)
            1 -> intArrayOf(CommonsR.id.pin_lock_holder)
            2 -> intArrayOf(CommonsR.id.biometric_lock_holder, CommonsR.id.fingerprint_lock_holder)
            else -> return null
        }
        return (0 until childCount).map { getChildAt(it) }.firstOrNull { it.id in ids }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val pageWidthSpec = View.MeasureSpec.makeMeasureSpec(
            (width - paddingLeft - paddingRight).coerceAtLeast(1),
            View.MeasureSpec.EXACTLY
        )
        fun naturalHeight(page: View?): Int {
            if (page == null) return 0
            page.measure(pageWidthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return page.measuredHeight
        }

        // Capture current tab's natural height before Commons measures all tabs.
        var selected = activePage()
        var natural = naturalHeight(selected)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Pages can first be instantiated during super.onMeasure.
        if (selected == null) {
            selected = activePage()
            natural = naturalHeight(selected)
        }
        if (natural > 0) {
            val desired = natural + paddingTop + paddingBottom
            if (desired != measuredHeight) {
                setMeasuredDimension(measuredWidth, desired)
                // Re-measure the selected ConstraintLayout against the new
                // viewport so its bottom-constrained keypad stays visible.
                selected?.measure(
                    pageWidthSpec,
                    View.MeasureSpec.makeMeasureSpec(natural, View.MeasureSpec.EXACTLY)
                )
            }
        }
    }
}
