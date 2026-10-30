package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R

/** Bounds only the scrolling content, leaving title/actions available at large font scales. */
class ClassicDialogScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visible = Rect()
        getWindowVisibleDisplayFrame(visible)
        val available = visible.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val limit = (available * 0.60f).toInt().coerceAtLeast(1)
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}

/**
 * Shared viewport for the Commons FilePickerDialog and Gallery PickDirectoryDialog.
 * Short folders wrap their content; long folders are constrained to a scrollable
 * viewport. The FAB clearance is inside the scrolling list, never over its rows.
 * This is presentation-only; Commons retains file/SAF/security callbacks.
 */
class ClassicDialogPickerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout(context, attrs) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun reserveFabSpace(listId: Int, vararg fabIds: Int) {
        val list = findViewById<View>(listId) ?: return
        val hasVisibleFab = fabIds.any { findViewById<View>(it)?.visibility == View.VISIBLE }
        val bottom = dp(if (hasVisibleFab) 80 else 12)
        if (list.paddingBottom != bottom) {
            list.setPaddingRelative(list.paddingStart, list.paddingTop, list.paddingEnd, bottom)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        reserveFabSpace(
            org.fossify.commons.R.id.filepicker_list,
            org.fossify.commons.R.id.filepicker_fab_show_hidden,
            org.fossify.commons.R.id.filepicker_fab_show_favorites,
            org.fossify.commons.R.id.filepicker_fab
        )
        reserveFabSpace(R.id.directories_grid, R.id.directories_show_hidden)

        val visible = Rect()
        getWindowVisibleDisplayFrame(visible)
        val available = visible.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        // The alert's title and action buttons live outside this view.
        val limit = minOf((available * 0.62f).toInt(), available - dp(136)).coerceAtLeast(1)
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}
