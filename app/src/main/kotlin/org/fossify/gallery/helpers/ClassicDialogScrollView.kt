package org.fossify.gallery.helpers

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.view.ViewCompat
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.graphics.Canvas
import android.widget.ScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ClassicGlowPulse
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller

/** Bounds only the scrolling content, leaving title/actions available at large font scales. */
class ClassicDialogScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ScrollView(context, attrs) {
    private val glow = ClassicGlowPulse(this, accent = {
        OxygenThemeEngine.snapshot(context, context.config).interactiveAccent
    }, maxIntensity = 0.28f)

    init {
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && computeVerticalScrollRange() > height) {
            val end = (computeVerticalScrollRange() - height).coerceAtLeast(0)
            when {
                scrollY <= 0 -> glow.pulse(atBottom = false)
                scrollY >= end -> glow.pulse(atBottom = true)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) glow.release()
        return handled
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        glow.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        glow.finish()
        super.onDetachedFromWindow()
    }

    private fun hostActivity(): Activity? {
        var owner = context
        while (owner is ContextWrapper && owner !is Activity) {
            val base = owner.baseContext
            if (base === owner) break
            owner = base
        }
        return owner as? Activity
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Restrict scrolling to the host window (including IME and multi-window),
        // with AppCompat title and action bar outside the scroller.
        val available = hostActivity()?.let(OxygenClassicDialog::availableHeight)
            ?: resources.displayMetrics.heightPixels
        val limit = OxygenClassicDialog.scrollViewportHeight(this, available)
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
    private var imeVisible = false
    private var normalViewportHeight = 0

    /** Use the host window, not the resized dialog's visible frame. */
    private fun hostViewportHeight(): Int {
        var owner: Context = context
        while (owner is ContextWrapper && owner !is Activity) {
            val base = owner.baseContext
            if (base === owner) break
            owner = base
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && owner is Activity) {
            val metrics = owner.windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return (metrics.bounds.height() - bars.top - bars.bottom).coerceAtLeast(1)
        }
        return resources.displayMetrics.heightPixels.coerceAtLeast(1)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val nowVisible = insets.isVisible(WindowInsets.Type.ime())
            if (imeVisible != nowVisible) {
                imeVisible = nowVisible
                // A wrap-content AlertDialog can retain its previous compact height.
                // Re-measure both the picker and its parent after an IME transition.
                post {
                    requestLayout()
                    (parent as? View)?.requestLayout()
                }
            }
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        // Commons' FilePickerDialog doesn't call Gallery's shared scroller skin.
        // Install the same thumb hit target after Commons has attached its
        // original touch listeners, without replacing those listeners.
        post {
            if (!isAttachedToWindow) return@post
            OxygenClassicEdgeGlow.install(this, context, OxygenClassicEdgeGlow.DIALOG_INTENSITY_SCALE)
            val scroller = findViewById<RecyclerViewFastScroller>(org.fossify.commons.R.id.filepicker_fastscroller)
            val list = findViewById<androidx.recyclerview.widget.RecyclerView>(org.fossify.commons.R.id.filepicker_list)
            if (scroller != null && list != null) {
                OxygenFastScroller.ensureTouchableHandle(scroller)
                OxygenFastScroller.installPickerTouchHandling(scroller, list)
            }
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

        // IME resizing is already reflected by the incoming AT_MOST parent spec.
        // Measuring against getWindowVisibleDisplayFrame() here caused a second,
        // sometimes sticky, shrink when the soft keyboard opened and closed.
        val hostHeight = hostViewportHeight()
        if (!imeVisible || normalViewportHeight == 0) normalViewportHeight = hostHeight
        val available = normalViewportHeight
        // The alert's title and action buttons live outside this view.
        val limit = OxygenClassicDialog.scrollViewportHeight(this, available)
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}
