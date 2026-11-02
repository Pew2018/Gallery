package org.fossify.gallery.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.InsetDrawable
import android.widget.CheckBox
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.widget.TintableCompoundButton
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenClassicControls
import org.fossify.gallery.helpers.OxygenThemeEngine
import androidx.appcompat.view.menu.ListMenuItemView
import kotlin.math.roundToInt

/** Bound intrinsic menu measurement too: AppCompat otherwise permits 320dp or half a tablet. */
@SuppressLint("RestrictedApi")
class TiebaMenuItemView(context: Context, attrs: AttributeSet?) : ListMenuItemView(context, attrs) {
    override fun initialize(itemData: MenuItemImpl, menuType: Int) {
        super.initialize(itemData, menuType)
        if (itemData.itemId != R.id.favorite_always_show_excluded &&
            itemData.itemId != R.id.favorite_always_show_hidden) return

        // Keep translations untouched: the trailing checkbox owns its own fixed spacing.
        setTitle(itemData.title.toString().trimEnd())

        // AppCompat creates the check view lazily during initialize(). Resolve the
        // current palette on each bind, including after an accent/appearance change.
        val palette = OxygenThemeEngine.snapshot(context, context.config)
        val colors = palette.copy(
            pageBackground = palette.dialogBackground,
            interactiveAccent = org.fossify.gallery.helpers.OxygenClassicPalette.readableAccent(
                palette.rawAccent, palette.dialogBackground
            )
        )
        val dp = resources.displayMetrics.density
        fun applyCheck(view: View) {
            if (view is CheckBox) {
                // A whole-drawable tint would also recolor the contrasting tick.
                view.buttonTintList = null
                if (view is TintableCompoundButton) view.supportButtonTintList = null
                // 24dp drawable plus a 12dp leading and 8dp trailing control gutter.
                // This preserves the 20dp square while ensuring stable text separation.
                view.buttonDrawable = InsetDrawable(
                    OxygenClassicControls.selectionDrawable(
                        radio = false, density = dp, colors = colors, surfaceAccent = true
                    ), (12 * dp + 0.5f).toInt(), 0, (8 * dp + 0.5f).toInt(), 0
                )
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) applyCheck(view.getChildAt(index))
            }
        }
        applyCheck(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maximum = (280 * resources.displayMetrics.density).roundToInt()
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        if (mode == MeasureSpec.UNSPECIFIED) {
            // Measure natural content first; imposing AT_MOST immediately makes
            // the MATCH_PARENT content group expand even for a three-item menu.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (measuredWidth <= maximum) return
        }
        val bounded = if (mode == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST)
        } else MeasureSpec.makeMeasureSpec(minOf(size, maximum), mode)
        super.onMeasure(bounded, heightMeasureSpec)
    }
}
