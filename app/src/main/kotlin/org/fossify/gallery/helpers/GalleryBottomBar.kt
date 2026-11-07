package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import org.fossify.gallery.extensions.config
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
    // WindowManager owns the cross-Activity handoff so the old opaque root remains
    // visible underneath the incoming surface while its alpha is animated.
    internal const val ROOT_ENTER_DURATION_MS = 180L
    internal const val FIRST_DRAW_LIMIT_MS = 250L
    internal const val ROOT_FINISH_FALLBACK_MS = FIRST_DRAW_LIMIT_MS + 280L
    private const val EXTRA_ROOT_TOKEN = "org.fossify.gallery.ROOT_TRANSITION_TOKEN"
    private data class PendingRoot(
        val source: java.lang.ref.WeakReference<Activity>,
        val startedAt: Long,
        val timeout: Runnable,
        var acknowledged: Boolean = false
    )
    private val pendingRoots = mutableMapOf<String, PendingRoot>()
    enum class RootContent { LOADING, CONTENT, EMPTY, AUTHORIZATION, FAILED }
    private val readyRoots = java.util.WeakHashMap<Activity, RootContent>()
    private val rootCleanups = java.util.WeakHashMap<Activity, () -> Unit>()
    internal val pendingRootCount: Int get() = pendingRoots.size
    private val mainHandler = Handler(Looper.getMainLooper())

    private val rippleColors = java.util.WeakHashMap<LinearLayout, Int>()

    /** The public RippleDrawable API is write-only; track our last applied tint. */
    internal fun currentRippleColor(item: LinearLayout): Int? = rippleColors[item]

    internal fun mayDrawRoot(contentCommitted: Boolean, hasBoundRows: Boolean,
        confirmedEmpty: Boolean, elapsedMs: Long): Boolean =
        elapsedMs >= FIRST_DRAW_LIMIT_MS || (contentCommitted && (hasBoundRows || confirmedEmpty))

    fun prepareRootSurface(activity: Activity, parent: CoordinatorLayout) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        val surface = OxygenThemeEngine.snapshot(activity, activity.config).pageBackground
        activity.window.setBackgroundDrawable(ColorDrawable(surface))
        parent.setBackgroundColor(surface)
        OxygenSystemBars.apply(activity)
    }

    fun markRootContentReady(activity: Activity, content: RootContent = RootContent.CONTENT) {
        if (readyRoots.containsKey(activity)) readyRoots[activity] = content
    }

    /** Activity teardown cancels callbacks rather than leaving a retained window/view. */
    fun dispose(activity: Activity) {
        rootCleanups.remove(activity)?.invoke()
        readyRoots.remove(activity)
        bars.remove(activity)?.get()?.let { bar ->
            for (i in 0 until bar.childCount) {
                val item = bar.getChildAt(i) as? LinearLayout ?: continue
                rippleColors.remove(item)
            }
        }
        pendingRoots.entries.filter { it.value.source.get() == null || it.value.source.get() === activity }
            .map { it.key }.forEach { token ->
                pendingRoots.remove(token)?.let { mainHandler.removeCallbacks(it.timeout) }
            }
    }

    private fun finishOutgoing(token: String?) {
        if (token == null) return
        val pending = pendingRoots[token] ?: return
        if (pending.acknowledged) return
        pending.acknowledged = true
        mainHandler.removeCallbacks(pending.timeout)
        // Keep the source alive beneath the incoming window until the WindowManager
        // cross-fade has had its full duration. Navigation itself is never delayed.
        val elapsed = android.os.SystemClock.uptimeMillis() - pending.startedAt
        mainHandler.postDelayed(pending.timeout, (ROOT_ENTER_DURATION_MS - elapsed).coerceAtLeast(0L))
    }

    /**
     * Keep the pressed tint semantic and legible on the current surface. Strong,
     * already-readable accents use a lighter touch; borderline accents get a
     * little more opacity so the native ripple remains visible in both themes.
     */
    internal fun rippleColor(accent: Int, surface: Int): Int {
        val contrast = OxygenThemeColors.contrastRatio(accent, surface)
        val alpha = when {
            // Keep even very legible accents perceptible on a quick tap. Softer
            // contrast receives a little more opacity while staying below 16%.
            contrast >= 10.0 -> 32
            contrast >= 7.0 -> 36
            else -> 40
        }
        return Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
    }

    /**
     * RippleDrawable owns its pressed/released animation. Replacing its background
     * (even with the same color) cancels that native state and cuts off its fade.
     */
    private fun updateRipple(item: LinearLayout, colors: OxygenThemeTokens) {
        val color = rippleColor(colors.interactiveAccent, colors.pageBackground)
        val ripple = item.background as? RippleDrawable
        if (ripple == null) {
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = item.resources.getDimension(R.dimen.oxygen_bottom_bar_ripple_corner_radius)
            }
            item.background = RippleDrawable(
                ColorStateList.valueOf(color),
                ColorDrawable(Color.TRANSPARENT),
                InsetDrawable(mask,
                    item.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_ripple_inset_horizontal),
                    item.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_ripple_inset_vertical),
                    item.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_ripple_inset_horizontal),
                    item.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_ripple_inset_vertical)
                )
            )
        } else if (rippleColors[item] != color) {
            ripple.setColor(ColorStateList.valueOf(color))
        }
        rippleColors[item] = color
    }

    /** Both windows use the same final tab colors, without competing tint timelines. */
    private fun updateTint(item: LinearLayout, tint: Int) {
        val icon = item.getChildAt(0) as? ImageView ?: return
        val label = item.getChildAt(1) as? TextView ?: return
        if (icon.imageTintList?.defaultColor != tint) icon.imageTintList = ColorStateList.valueOf(tint)
        if (label.currentTextColor != tint) label.setTextColor(tint)
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
            updateTint(item, if (selected) colors.interactiveAccent else colors.secondaryText)
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
        val selected = tokens.interactiveAccent
        val quiet = tokens.secondaryText
        if ((bar.background as? ColorDrawable)?.color != surface) bar.setBackgroundColor(surface)
        for (i in 0 until bar.childCount) {
            val item = bar.getChildAt(i) as? LinearLayout ?: continue
            updateTint(item, if (item.isSelected) selected else quiet)
            // setColor() preserves the same Drawable and any in-flight pressed ripple.
            updateRipple(item, tokens)
        }
    }

    enum class Tab { ALL, FAVORITES, PHOTOS }

    @Suppress("DEPRECATION")
    fun open(activity: Activity, intent: Intent, finishCurrent: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed ||
            pendingRoots.values.any { it.source.get() === activity }) return
        intent.putExtra(EXTRA_ROOT_TRANSITION, true)
        val token = if (finishCurrent) java.util.UUID.randomUUID().toString() else null
        if (token != null) {
            intent.putExtra(EXTRA_ROOT_TOKEN, token)
            val startedAt = android.os.SystemClock.uptimeMillis()
            val timeout = Runnable {
                pendingRoots.remove(token)?.source?.get()?.let {
                    if (!it.isFinishing && !it.isDestroyed) {
                        it.finish()
                    }
                }
            }
            pendingRoots[token] = PendingRoot(java.lang.ref.WeakReference(activity), startedAt, timeout)
            mainHandler.postDelayed(timeout, ROOT_FINISH_FALLBACK_MS)
        }
        try {
            activity.startActivity(intent)
            // The opaque outgoing surface remains underneath the incoming one. A
            // paired WindowManager alpha transition makes the page handoff visible
            // without setting the destination's first content frame to alpha=0.
            activity.overridePendingTransition(R.anim.oxygen_classic_root_enter,
                R.anim.oxygen_classic_root_exit)
        } catch (error: RuntimeException) {
            token?.let { pendingRoots.remove(it)?.let { pending -> mainHandler.removeCallbacks(pending.timeout) } }
            throw error
        }
    }

    private const val EXTRA_ROOT_TRANSITION = "org.fossify.gallery.ROOT_TRANSITION"
    /** Allow the destination's first content-backed frame before releasing its source. */
    internal fun coordinateFirstDraw(activity: Activity, parent: CoordinatorLayout,
        loadingFrame: () -> Unit = {}) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        rootCleanups.remove(activity)?.invoke()
        activity.intent.removeExtra(EXTRA_ROOT_TRANSITION)
        val token = activity.intent.getStringExtra(EXTRA_ROOT_TOKEN)
        activity.intent.removeExtra(EXTRA_ROOT_TOKEN)
        readyRoots[activity] = RootContent.LOADING
        val started = android.os.SystemClock.uptimeMillis()
        val wake = Runnable { parent.invalidate() }
        var acknowledged = false
        var waitingForDraw = false
        var fallbackPrepared = false
        lateinit var cleanup: () -> Unit
        lateinit var preDraw: android.view.ViewTreeObserver.OnPreDrawListener
        lateinit var drawn: android.view.ViewTreeObserver.OnDrawListener
        lateinit var attachment: android.view.View.OnAttachStateChangeListener
        val afterDraw = Runnable {
            if (acknowledged) return@Runnable
            acknowledged = true
            cleanup()
            rootCleanups.remove(activity)
            finishOutgoing(token)
        }
        drawn = android.view.ViewTreeObserver.OnDrawListener {
            // A pre-draw pass can still be cancelled by another listener. Acknowledge
            // the actual draw, without mistaking laid-out rows for decoded images.
            mainHandler.post(afterDraw)
        }
        cleanup = {
            if (parent.viewTreeObserver.isAlive) {
                parent.viewTreeObserver.removeOnPreDrawListener(preDraw)
                parent.viewTreeObserver.removeOnDrawListener(drawn)
            }
            parent.removeCallbacks(wake)
            mainHandler.removeCallbacks(afterDraw)
            readyRoots.remove(activity)
            parent.removeOnAttachStateChangeListener(attachment)
        }
        preDraw = android.view.ViewTreeObserver.OnPreDrawListener {
            val directories: androidx.recyclerview.widget.RecyclerView? = parent.findViewById(R.id.directories_grid)
            val media: androidx.recyclerview.widget.RecyclerView? = parent.findViewById(R.id.media_grid)
            val empty: android.view.View? = parent.findViewById(if (media != null)
                R.id.media_empty_text_placeholder else R.id.directories_empty_placeholder)
            val grid = media ?: directories
            val count = grid?.adapter?.itemCount ?: 0
            val content = readyRoots[activity] ?: RootContent.LOADING
            val elapsed = android.os.SystemClock.uptimeMillis() - started
            val bound = count > 0 && (grid?.childCount ?: 0) > 0 &&
                !grid!!.isLayoutRequested && !grid.isComputingLayout
            val confirmedEmpty = count == 0 && empty?.visibility == android.view.View.VISIBLE
            val ready = mayDrawRoot(content != RootContent.LOADING, bound, confirmedEmpty, elapsed)
            if (!ready && !activity.isFinishing && !activity.isDestroyed) false
            else {
                if (!fallbackPrepared && elapsed >= FIRST_DRAW_LIMIT_MS &&
                    !(content != RootContent.LOADING && (bound || confirmedEmpty))) {
                    // Bounded waiting falls back to a fully opaque, themed loading
                    // surface, never a fade through the system window background.
                    fallbackPrepared = true
                    val surface = OxygenThemeEngine.snapshot(activity, activity.config).pageBackground
                    activity.window.setBackgroundDrawable(ColorDrawable(surface))
                    parent.setBackgroundColor(surface)
                    loadingFrame()
                }
                if (!waitingForDraw) {
                    waitingForDraw = true
                    parent.viewTreeObserver.addOnDrawListener(drawn)
                }
                true
            }
        }
        attachment = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) = Unit
            override fun onViewDetachedFromWindow(view: android.view.View) {
                cleanup()
                rootCleanups.remove(activity)
                // The tracked source timeout still bounds failed destination startup.
            }
        }
        rootCleanups[activity] = cleanup
        parent.viewTreeObserver.addOnPreDrawListener(preDraw)
        parent.addOnAttachStateChangeListener(attachment)
        parent.postDelayed(wake, FIRST_DRAW_LIMIT_MS)
    }

    fun attach(
        activity: Activity,
        parent: CoordinatorLayout,
        active: Tab,
        loadingFrame: () -> Unit = {},
        navigate: (Tab) -> Unit
    ) {
        coordinateFirstDraw(activity, parent, loadingFrame)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
        val background = tokens.pageBackground
        val primary = tokens
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
                setOnTouchListener { view, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        // CoordinatorLayout can defer child pressed feedback while
                        // deciding whether a gesture belongs to a scrolling parent.
                        // Set the native Drawable state immediately; leave click,
                        // release, cancel, and long-press handling to the View.
                        view.drawableHotspotChanged(event.x, event.y)
                        view.isPressed = true
                    }
                    false
                }
                val accessibilityLabel = label
                contentDescription = if (selected) "$accessibilityLabel, ${activity.getString(R.string.classic_selected)}"
                    else accessibilityLabel
                updateRipple(this, primary)
                setOnClickListener {
                    if (tab != active && !navigating) {
                        navigating = true
                        // Match the destination initial state without a second color animation.
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
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_label_gap)
            })
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

