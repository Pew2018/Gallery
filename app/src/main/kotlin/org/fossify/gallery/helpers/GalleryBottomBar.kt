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
import org.fossify.gallery.R

/**
 * Stable, unobtrusive two-item navigation. No moving selection pill or
 * animated tab translation; selection is expressed by icon and label color.
 */
object GalleryBottomBar {
    private val bars = java.util.WeakHashMap<Activity, java.lang.ref.WeakReference<LinearLayout>>()

    fun refreshAll() {
        bars.entries.toList().forEach { (activity, reference) ->
            val bar = reference.get() ?: return@forEach
            if (activity.isFinishing || activity.isDestroyed) return@forEach
            val children = (0 until bar.childCount).map { bar.getChildAt(it) }.associateBy { it.tag }
            bar.removeAllViews()
            activity.config.bottomNavigationOrder.forEach { id -> children[id]?.let { bar.addView(it) } }
            recolor(activity)
        }
    }

    /** Refresh the current colors without recreating views or losing selection. */
    fun recolor(activity: Activity) {
        val bar = bars[activity]?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
        val surface = tokens.pageBackground
        val accent = tokens.rawAccent
        val selected = tokens.interactiveAccent
        val quiet = tokens.secondaryText
        bar.setBackgroundColor(surface)
        for (i in 0 until bar.childCount) {
            val item = bar.getChildAt(i) as? LinearLayout ?: continue
            val tint = if (item.isSelected) selected else quiet
            (item.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(tint)
            (item.getChildAt(1) as? TextView)?.setTextColor(tint)
            // Keep the touch ripple in sync after live theme changes.
            item.background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(32, Color.red(accent), Color.green(accent), Color.blue(accent))),
                ColorDrawable(Color.TRANSPARENT), ColorDrawable(Color.WHITE)
            )
        }
    }

    enum class Tab { ALL, FAVORITES }

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
        val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
        val background = tokens.pageBackground
        val primary = tokens.rawAccent
        val selectedText = tokens.interactiveAccent
        val quiet = tokens.secondaryText

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
            Tab.ALL to Triple(Tab.ALL, activity.getString(R.string.gallery_nav_all_images), R.drawable.ic_files_vector),
            Tab.FAVORITES to Triple(Tab.FAVORITES, activity.getString(org.fossify.commons.R.string.favorites), org.fossify.commons.R.drawable.ic_star_outline_vector)
        )
        val tabs = activity.config.bottomNavigationOrder.mapNotNull { id ->
            Tab.values().firstOrNull { it.name == id }?.let(byId::get)
        }
        tabs.forEach { (tab, label, icon) ->
            val selected = tab == active
            val tint = if (selected) selectedText else quiet
            val item = LinearLayout(activity).apply {
                tag = tab.name
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                isSelected = selected
                val accessibilityLabel = label
                contentDescription = if (selected) "$accessibilityLabel, ${activity.getString(R.string.classic_selected)}"
                    else accessibilityLabel
                this.background = RippleDrawable(
                    ColorStateList.valueOf(Color.argb(32, Color.red(primary), Color.green(primary), Color.blue(primary))),
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
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(tint)
                typeface = Typeface.create(if (selected) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
                includeFontPadding = false
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(20)).apply { topMargin = dp(3) })
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
        bars[activity] = java.lang.ref.WeakReference(bar)
        ViewCompat.requestApplyInsets(bar)
    }
}
