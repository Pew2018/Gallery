package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Lightweight navigation shell. Existing media/picker Activities remain responsible for
 * their own result contracts and permission workflows. This bar is for normal launches.
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
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(6).toFloat()
            minimumHeight = dp(58)
        }

        listOf(
            Tab.ALL to "所有图片",
            Tab.FAVORITE_FOLDERS to "收藏",
            Tab.PRIVATE to "隐私"
        ).forEach { (tab, label) ->
            val isSelected = tab == active
            val item = TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(if (isSelected) Color.rgb(55, 133, 83) else Color.rgb(96, 96, 96))
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { navigate(tab) }
            }
            bar.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f))
        }

        parent.addView(bar, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, dp(58)
        ).apply { gravity = Gravity.BOTTOM })

        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, windowInsets ->
            val bottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.layoutParams = (v.layoutParams as CoordinatorLayout.LayoutParams).apply {
                height = dp(58) + bottom
            }
            v.setPadding(0, 0, 0, bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(bar)
    }
}
