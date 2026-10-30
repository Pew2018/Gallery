package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Commons' FilePickerDialog doesn't call Gallery's shared scroller skin.
        // Install the same thumb hit target after Commons has attached its
        // original touch listeners, without replacing those listeners.
        post {
            if (!isAttachedToWindow) return@post
            findViewById<RecyclerViewFastScroller>(org.fossify.commons.R.id.filepicker_fastscroller)
                ?.let(OxygenFastScroller::ensureTouchableHandle)
        }
    }

    private fun reserveFabSpace(listId: Int, primaryFabId: Int?, vararg secondaryFabIds: Int) {
        val list = findViewById<View>(listId) ?: return
        val secondaryCount = secondaryFabIds.count { findViewById<View>(it)?.visibility == View.VISIBLE }
        val primaryVisible = primaryFabId?.let { findViewById<View>(it)?.visibility == View.VISIBLE } == true
        // Commons stacks secondary buttons vertically and lifts them above the
        // create-folder FAB. Reserve the whole stack, not just the bottom button.
        val clearance = when {
            primaryVisible && secondaryCount > 0 -> 96 + secondaryCount * 72
            primaryVisible -> 80
            secondaryCount > 0 -> 16 + secondaryCount * 72
            else -> 12
        }
        val bottom = dp(clearance)
        if (list.paddingBottom != bottom) {
            list.setPaddingRelative(list.paddingStart, list.paddingTop, list.paddingEnd, bottom)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The Commons hidden-folder FAB is a 1dp invisible action delegate for
        // the compact toolbar menu, not an overlay that needs scrolling clearance.
        val filePickerFabs = intArrayOf(
            org.fossify.commons.R.id.filepicker_fab_show_favorites
        )
        reserveFabSpace(
            org.fossify.commons.R.id.filepicker_list,
            org.fossify.commons.R.id.filepicker_fab,
            *filePickerFabs
        )
        reserveFabSpace(
            org.fossify.commons.R.id.filepicker_favorites_list,
            org.fossify.commons.R.id.filepicker_fab,
            *filePickerFabs
        )
        reserveFabSpace(R.id.directories_grid, null)

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
