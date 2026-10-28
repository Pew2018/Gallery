package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import org.fossify.commons.views.MyMaterialSwitch
import org.fossify.gallery.R

/**
 * Classic Material 1 switch presentation on top of Commons' MaterialSwitch.
 *
 * Keep the existing switch instances, IDs, checked state, holder click handlers,
 * keyboard accessibility and theming. Never replace the interactive widget.
 */
object OxygenClassicControls {
    fun applySwitches(root: View, accent: Int, text: Int, surface: Int) {
        val uncheckedThumb = ColorUtils.blendARGB(surface, text, 0.10f)
        val uncheckedTrack = ColorUtils.blendARGB(surface, text, 0.36f)
        val checkedTrack = ColorUtils.blendARGB(surface, accent, 0.40f)
        val disabledThumb = ColorUtils.blendARGB(surface, text, 0.06f)
        val disabledTrack = ColorUtils.blendARGB(surface, text, 0.12f)

        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val thumbs = ColorStateList(states, intArrayOf(disabledThumb, accent, uncheckedThumb))
        val tracks = ColorStateList(states, intArrayOf(disabledTrack, checkedTrack, uncheckedTrack))

        fun visit(view: View) {
            if (view is MyMaterialSwitch) {
                // The Commons preference can enable an M3 checkmark listener.
                // Settings rows use their existing holder click handlers instead.
                view.setShowCheckmark(false)
                view.thumbIconDrawable = null
                view.trackDecorationDrawable = null
                view.thumbDrawable = AppCompatResources.getDrawable(view.context, R.drawable.oxygen_classic_switch_thumb)
                view.trackDrawable = AppCompatResources.getDrawable(view.context, R.drawable.oxygen_classic_switch_track)
                view.thumbTintList = thumbs
                view.trackTintList = tracks
                view.switchMinWidth = view.resources.getDimensionPixelSize(R.dimen.oxygen_classic_switch_width)
                view.showText = false
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
    }
}
