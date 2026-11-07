package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ClassicGlowPulse
import org.fossify.gallery.helpers.OxygenThemeEngine

/** ScrollView variant with a bounded theme-aware glow instead of platform stretch. */
open class ClassicGlowScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {
    private val glow = ClassicGlowPulse(this, accent = {
        OxygenThemeEngine.snapshot(context, context.config).interactiveAccent
    })

    init { overScrollMode = View.OVER_SCROLL_NEVER }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && computeVerticalScrollRange() > height) {
            val end = (computeVerticalScrollRange() - height).coerceAtLeast(0)
            when {
                scrollY <= 0 -> glow.pulse(false)
                scrollY >= end -> glow.pulse(true)
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
}

/** NestedScrollView variant matching the same bounded classic edge feedback. */
class ClassicGlowNestedScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {
    private val glow = ClassicGlowPulse(this, accent = {
        OxygenThemeEngine.snapshot(context, context.config).interactiveAccent
    })

    init { overScrollMode = View.OVER_SCROLL_NEVER }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && computeVerticalScrollRange() > height) {
            val end = (computeVerticalScrollRange() - height).coerceAtLeast(0)
            when {
                scrollY <= 0 -> glow.pulse(false)
                scrollY >= end -> glow.pulse(true)
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
}
