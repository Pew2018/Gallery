package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        val quiet = Color.argb(195, Color.red(foreground), Color.green(foreground), Color.blue(foreground))

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(background)
            elevation = dp(2).toFloat()
            setPadding(dp(6), dp(2), dp(6), 0)
        }

        val tabs = listOf(
            Triple(Tab.ALL, "所有图片", R.drawable.ic_files_vector),
            Triple(Tab.FAVORITE_FOLDERS, "收藏", R.drawable.ic_folders_vector),
            Triple(Tab.PRIVATE, "隐私", org.fossify.commons.R.drawable.ic_lock_vector)
        )
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
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.argb(28, Color.red(primary), Color.green(primary), Color.blue(primary))),
                    null,
                    GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.WHITE) }
                )
                setOnClickListener { navigate(tab) }
            }
            item.addView(ImageView(activity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(tint)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(23), dp(23)))
            item.addView(TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(tint)
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                includeFontPadding = false
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(22)))
            bar.addView(item, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }

        parent.addView(bar, CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, dp(60)
        ).apply { gravity = Gravity.BOTTOM })

        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val params = view.layoutParams as CoordinatorLayout.LayoutParams
            val desiredHeight = dp(60) + bottom
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
