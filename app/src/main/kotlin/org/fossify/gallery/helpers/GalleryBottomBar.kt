package org.fossify.gallery.helpers

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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
 * OxygenOS 7.1.1-inspired unobtrusive navigation for ordinary gallery launches.
 * The host Activities keep their original picker, viewer, and result contracts.
 * Colors come from Fossify's theme instead of forcing white in dark mode.
 */
object GalleryBottomBar {
    enum class Tab { ALL, FAVORITE_FOLDERS, PRIVATE }

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
        val quiet = Color.argb(180, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
        val selectedSurface = Color.argb(30, Color.red(primary), Color.green(primary), Color.blue(primary))

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(background)
                setStroke(dp(1), Color.argb(24, Color.red(foreground), Color.green(foreground), Color.blue(foreground)))
            }
            elevation = dp(4).toFloat()
            minimumHeight = dp(64)
            setPadding(dp(6), 0, dp(6), 0)
        }

        val tabs = listOf(
            Triple(Tab.ALL, "所有图片", R.drawable.ic_files_vector),
            Triple(Tab.FAVORITE_FOLDERS, "收藏", R.drawable.ic_folders_vector),
            Triple(Tab.PRIVATE, "隐私", org.fossify.commons.R.drawable.ic_lock_vector)
        )
        tabs.forEach { (tab, label, icon) ->
            val selected = tab == active
            val tint = if (selected) primary else quiet
            val item = TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12.5f
                compoundDrawablePadding = dp(4)
                setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(tint)
                setTextColor(tint)
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                contentDescription = if (selected) "$label，当前页面" else label
                isSelected = selected
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (selected) selectedSurface else Color.TRANSPARENT)
                }
                setOnClickListener { navigate(tab) }
            }
            val params = LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            bar.addView(item, params)
        }

        parent.addView(bar, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, dp(64)
        ).apply { gravity = Gravity.BOTTOM })

        // Respect system gesture/navigation insets and avoid icon overlap.
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, windowInsets ->
            val bottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.layoutParams = (v.layoutParams as CoordinatorLayout.LayoutParams).apply {
                height = dp(64) + bottom
            }
            v.setPadding(dp(6), 0, dp(6), bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(bar)
    }
}
