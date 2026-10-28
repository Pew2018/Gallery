package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.R
import java.util.WeakHashMap

/**
 * Classic SwitchCompat geometry and stateful tints across the settings page.
 * The SettingsSwitchStyle keeps the switch itself non-clickable, so the
 * existing whole-row click listeners are the single source of persistence.
 */
object OxygenClassicControls {
    private val applied = WeakHashMap<SwitchCompat, Triple<Int, Int, Int>>()

    fun applySwitches(root: View, accent: Int, text: Int, surface: Int) {
        val palette = Triple(accent, text, surface)
        val offThumb = ColorUtils.blendARGB(surface, text, 0.10f)
        val offTrack = ColorUtils.blendARGB(surface, text, 0.38f)
        val onTrack = ColorUtils.blendARGB(surface, accent, 0.52f)
        val disabledThumb = ColorUtils.blendARGB(surface, text, 0.06f)
        val disabledTrack = ColorUtils.blendARGB(surface, text, 0.13f)
        val selectors = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val thumbs = ColorStateList(selectors, intArrayOf(disabledThumb, accent, offThumb))
        val tracks = ColorStateList(selectors, intArrayOf(disabledTrack, onTrack, offTrack))

        fun visit(view: View) {
            if (view is SwitchCompat && applied[view] != palette) {
                view.thumbDrawable = AppCompatResources.getDrawable(view.context, R.drawable.oxygen_classic_switch_thumb)
                view.trackDrawable = AppCompatResources.getDrawable(view.context, R.drawable.oxygen_classic_switch_track)
                view.thumbTintList = thumbs
                view.trackTintList = tracks
                view.switchMinWidth = view.resources.getDimensionPixelSize(R.dimen.oxygen_classic_switch_width)
                view.showText = false
                view.splitTrack = false
                applied[view] = palette
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
    }
}
