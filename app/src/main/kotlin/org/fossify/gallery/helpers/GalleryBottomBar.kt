package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import org.fossify.gallery.extensions.config
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R

/**
 * Stable, unobtrusive three-item navigation. No moving selection pill or
 * animated tab translation; selection is expressed by icon and label color.
 */
object GalleryBottomBar {
    enum class Tab { ALL, FAVORITE_FOLDERS, PRIVATE }

    @Suppress("DEPRECATION")
    fun open(activity: Activity, intent: Intent, finishCurrent: Boolean = false) {
        activity.startActivity(intent)
        if (finishCurrent) activity.finish()
        // Suppress the system Activity slide/fade during bottom-bar navigation.
        activity.overridePendingTransition(0, 0)
    }

    fun attach(
        activity: Activity,
        parent: CoordinatorLayout,
        active: Tab,
        navigate: (Tab) -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val background = activity.getProperBackgroundColor()
        val foreground = activity.getProperTextColor()
        val primary = activity.getProperPrimaryColor()
        val quiet = Color.argb(138, Color.red(foreground), Color.green(foreground), Color.blue(foreground))

        // Reattaching after a configuration-driven rebuild must not duplicate the bar.
        parent.findViewWithTag<android.view.View>("oxygen_classic_bottom_bar")?.let(parent::removeView)
        var navigating = false
        val barHeight = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height)
        val itemHeight = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_item_height)
        val iconSize = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_icon_size)
        val bar = LinearLayout(activity).apply {
            tag = "oxygen_classic_bottom_bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(background)
            elevation = 0f
            setPadding(dp(6), dp(2), dp(6), 0)
        }

        val byId = mapOf(
            Tab.ALL to Triple(Tab.ALL, "所有图片", R.drawable.ic_files_vector),
            Tab.FAVORITE_FOLDERS to Triple(Tab.FAVORITE_FOLDERS, "收藏", R.drawable.ic_folders_vector),
            Tab.PRIVATE to Triple(Tab.PRIVATE, "隐私", org.fossify.commons.R.drawable.ic_lock_vector)
        )
        val tabs = activity.config.bottomNavigationOrder.mapNotNull { id ->
            Tab.values().firstOrNull { it.name == id }?.let(byId::get)
        }
        tabs.forEach { (tab, label, icon) ->
            val selected = tab == active
            val tint = if (selected) primary else quiet
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = if (selected) "$label，当前页面" else label
                this.background = RippleDrawable(
                    ColorStateList.valueOf(Color.argb(28, Color.red(primary), Color.green(primary), Color.blue(primary))),
                    ColorDrawable(Color.TRANSPARENT),
                    GradientDrawable().apply { setColor(Color.WHITE) }
                )
                setOnClickListener {
                    if (tab != active && !navigating) {
                        navigating = true
                        navigate(tab)
                    }
                }
            }
            item.addView(ImageView(activity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(tint)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(iconSize, iconSize))
            item.addView(TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(tint)
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                includeFontPadding = false
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(22)))
            bar.addView(item, LinearLayout.LayoutParams(0, itemHeight, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }

        parent.addView(bar, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, barHeight
        ).apply { gravity = Gravity.BOTTOM })

        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val params = view.layoutParams as CoordinatorLayout.LayoutParams
            val desiredHeight = barHeight + bottom
            if (params.height != desiredHeight) {
                params.height = desiredHeight
                view.layoutParams = params
            }
            view.setPadding(dp(6), dp(2), dp(6), bottom)
            insets
        }
        ViewCompat.requestApplyInsets(bar)
    }
}
