package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.gallery.R

/**
 * Presentation-only proxy for Commons 7.1.3 FilePickerDialog's hidden-item FAB.
 * The original view keeps its password/SAF/list-refresh listener; no Commons
 * internal state or callbacks are reimplemented here.
 */
class ClassicFilePickerOverflowButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs), ViewTreeObserver.OnGlobalLayoutListener {
    private var delegate: View? = null
    private var observer: ViewTreeObserver? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            if (!isAttachedToWindow) return@post
            val original = rootView.findViewById<View>(org.fossify.commons.R.id.filepicker_fab_show_hidden)
                ?: return@post
            delegate = original
            original.alpha = 0f
            original.isClickable = false
            original.isFocusable = false
            original.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            val light = ColorUtils.calculateLuminance(context.getProperBackgroundColor()) >= 0.5
            imageTintList = ColorStateList.valueOf(if (light) Color.rgb(33, 33, 33) else Color.WHITE)
            setOnClickListener { openOptions(original, light) }
            observer = viewTreeObserver.also { it.addOnGlobalLayoutListener(this) }
            syncVisibility()
        }
    }

    private fun openOptions(original: View, light: Boolean) {
        if (original.visibility != View.VISIBLE) return
        val style = if (light) R.style.OxygenClassicPopupTheme_Light else R.style.OxygenClassicPopupTheme_Dark
        PopupMenu(ContextThemeWrapper(context, style), this, Gravity.END).apply {
            menu.add(0, 1, 0, R.string.picker_show_hidden_folders)
            setOnMenuItemClickListener {
                // performClick() deliberately invokes Commons' original protected reveal.
                original.performClick()
                post { syncVisibility() }
                true
            }
            show()
        }
    }

    private fun syncVisibility() {
        val target = if (delegate?.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        if (visibility != target) visibility = target
    }

    override fun onGlobalLayout() = syncVisibility()

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
        observer = null
        delegate = null
        super.onDetachedFromWindow()
    }
}
