package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.extensions.config
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.animation.PathInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.R

/**
 * Stable, unobtrusive three-item navigation. No moving selection pill or
 * animated tab translation; selection is expressed by icon and label color.
 */
object GalleryBottomBar {
    private val bars = java.util.WeakHashMap<Activity, java.lang.ref.WeakReference<LinearLayout>>()
    private const val SELECTION_TINT_DURATION_MS = 140L
    // Match the existing gallery_root_enter / gallery_root_hold window animations.
    private const val ROOT_WINDOW_HOLD_MS = 160L
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class TintTransition(val target: Int, val animator: ValueAnimator)
    private val tintTransitions = java.util.WeakHashMap<LinearLayout, TintTransition>()

    private fun rippleColor(accent: Int): Int =
        Color.argb(32, Color.red(accent), Color.green(accent), Color.blue(accent))

    /**
     * RippleDrawable owns its pressed/released animation. Replacing its background
     * (even with the same color) cancels that native state and cuts off its fade.
     */
    private fun updateRipple(item: LinearLayout, accent: Int) {
        val color = rippleColor(accent)
        val ripple = item.background as? RippleDrawable
        if (ripple == null) {
            item.background = RippleDrawable(
                ColorStateList.valueOf(color),
                ColorDrawable(Color.TRANSPARENT),
                ColorDrawable(Color.WHITE)
            )
        } else if (ripple.color?.defaultColor != color) {
            ripple.setColor(ColorStateList.valueOf(color))
        }
    }

    /** Only colors are animated: no scale, movement, elevation or selection pill. */
    private fun updateTint(item: LinearLayout, tint: Int, animate: Boolean) {
        val icon = item.getChildAt(0) as? ImageView ?: return
        val label = item.getChildAt(1) as? TextView ?: return
        val pending = tintTransitions[item]
        if (pending?.target == tint) return
        tintTransitions.remove(item)?.animator?.cancel()
        val fromIcon = icon.imageTintList?.defaultColor ?: tint
        val fromLabel = label.currentTextColor
        if (!animate || (fromIcon == tint && fromLabel == tint)) {
            if (fromIcon != tint) icon.imageTintList = ColorStateList.valueOf(tint)
            if (fromLabel != tint) label.setTextColor(tint)
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SELECTION_TINT_DURATION_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                val progress = it.animatedValue as Float
                icon.imageTintList = ColorStateList.valueOf(ColorUtils.blendARGB(fromIcon, tint, progress))
                label.setTextColor(ColorUtils.blendARGB(fromLabel, tint, progress))
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (tintTransitions[item]?.animator === animation) {
                        icon.imageTintList = ColorStateList.valueOf(tint)
                        label.setTextColor(tint)
                        tintTransitions.remove(item)
                    }
                }
            })
        }
        tintTransitions[item] = TintTransition(tint, animator)
        animator.start()
    }

    private fun selectTab(activity: Activity, bar: LinearLayout, tab: Tab) {
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        for (i in 0 until bar.childCount) {
            val item = bar.getChildAt(i) as? LinearLayout ?: continue
            val selected = item.tag == tab.name
            if (item.isSelected == selected) continue
            item.isSelected = selected
            val label = item.getChildAt(1) as? TextView ?: continue
            label.typeface = Typeface.create(
                if (selected) "sans-serif-medium" else "sans-serif", Typeface.NORMAL
            )
            item.contentDescription = if (selected) "${label.text}, ${activity.getString(R.string.classic_selected)}"
                else label.text.toString()
            updateTint(item, if (selected) colors.interactiveAccent else colors.secondaryText, animate = true)
        }
    }

    fun refreshAll() {
        bars.entries.toList().forEach { (activity, reference) ->
            val bar = reference.get() ?: return@forEach
            if (activity.isFinishing || activity.isDestroyed) return@forEach
            val children = (0 until bar.childCount).map { bar.getChildAt(it) }.associateBy { it.tag }
            // Avoid detach/reattach when ordering is unchanged: it can abort a held ripple.
            val order = activity.config.bottomNavigationOrder
            val currentOrder = (0 until bar.childCount).map { bar.getChildAt(it).tag }
            if (currentOrder != order) {
                bar.removeAllViews()
                order.forEach { id -> children[id]?.let { bar.addView(it) } }
            }
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
        if ((bar.background as? ColorDrawable)?.color != surface) bar.setBackgroundColor(surface)
        for (i in 0 until bar.childCount) {
            val item = bar.getChildAt(i) as? LinearLayout ?: continue
            updateTint(item, if (item.isSelected) selected else quiet, animate = false)
            // setColor() preserves the same Drawable and any in-flight pressed ripple.
            updateRipple(item, accent)
        }
    }

    enum class Tab { ALL, FAVORITES, PHOTOS }

    @Suppress("DEPRECATION")
    fun open(activity: Activity, intent: Intent, finishCurrent: Boolean = false) {
        intent.putExtra(EXTRA_ROOT_TRANSITION, true)
        activity.startActivity(intent)
        // Launch immediately. Keep the outgoing Activity alive for the existing 160ms
        // window cross-fade, so its released ripple is not torn down on the click frame.
        // This defers teardown, never navigation or touch handling.
        if (finishCurrent) {
            mainHandler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) activity.finish()
            }, ROOT_WINDOW_HOLD_MS)
        }
        // The outgoing window stays opaque while the new root fades in.
        activity.overridePendingTransition(R.anim.gallery_root_enter, R.anim.gallery_root_hold)
    }

    private const val EXTRA_ROOT_TRANSITION = "org.fossify.gallery.ROOT_TRANSITION"
    internal const val FIRST_DRAW_LIMIT_MS = 250L

    /** Let a warm cache reach the first layout before starting the window animation. */
    private fun coordinateFirstDraw(activity: Activity, parent: CoordinatorLayout) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        activity.intent.removeExtra(EXTRA_ROOT_TRANSITION)
        val started = android.os.SystemClock.uptimeMillis()
        val wake = Runnable { parent.invalidate() }
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val directories: androidx.recyclerview.widget.RecyclerView? = parent.findViewById(R.id.directories_grid)
                val media: androidx.recyclerview.widget.RecyclerView? = parent.findViewById(R.id.media_grid)
                val directoryEmpty: android.view.View? = parent.findViewById(R.id.directories_empty_placeholder)
                val mediaEmpty: android.view.View? = parent.findViewById(R.id.media_empty_text_placeholder)
                val grid = directories ?: media
                val empty = directoryEmpty ?: mediaEmpty
                val ready = (grid?.childCount ?: 0) > 0 || empty?.visibility == android.view.View.VISIBLE
                if (!ready && !activity.isFinishing && !activity.isDestroyed &&
                    android.os.SystemClock.uptimeMillis() - started < FIRST_DRAW_LIMIT_MS) return false
                if (parent.viewTreeObserver.isAlive) parent.viewTreeObserver.removeOnPreDrawListener(this)
                parent.removeCallbacks(wake)
                return true
            }
        }
        parent.viewTreeObserver.addOnPreDrawListener(listener)
        parent.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) = Unit
            override fun onViewDetachedFromWindow(view: android.view.View) {
                if (parent.viewTreeObserver.isAlive) parent.viewTreeObserver.removeOnPreDrawListener(listener)
                parent.removeCallbacks(wake)
                parent.removeOnAttachStateChangeListener(this)
            }
        })
        // A failed/slow query must never indefinitely block the first frame.
        parent.postDelayed(wake, FIRST_DRAW_LIMIT_MS)
    }

    fun attach(
        activity: Activity,
        parent: CoordinatorLayout,
        active: Tab,
        navigate: (Tab) -> Unit
    ) {
        coordinateFirstDraw(activity, parent)
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
            Tab.ALL to Triple(Tab.ALL, activity.getString(R.string.gallery_nav_albums), R.drawable.ic_files_vector),
            Tab.PHOTOS to Triple(Tab.PHOTOS, activity.getString(R.string.gallery_nav_photos), R.drawable.ic_photo_profile_vector),
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
                updateRipple(this, primary)
                setOnClickListener {
                    if (tab != active && !navigating) {
                        navigating = true
                        // Selection feedback starts on release; navigation is immediate.
                        selectTab(activity, bar, tab)
                        try {
                            navigate(tab)
                        } catch (error: RuntimeException) {
                            navigating = false
                            selectTab(activity, bar, active)
                            throw error
                        }
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

