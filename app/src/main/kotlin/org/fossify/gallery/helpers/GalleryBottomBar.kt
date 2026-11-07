package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.fossify.gallery.extensions.config
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
    // Root windows stay opaque. Feedback continues using a value-only clock snapshot.
    internal const val FIRST_DRAW_LIMIT_MS = 250L
    internal const val ROOT_FINISH_FALLBACK_MS = 5_000L
    private const val EXTRA_ROOT_TOKEN = "org.fossify.gallery.ROOT_TRANSITION_TOKEN"
    private data class PendingRoot(
        val source: java.lang.ref.WeakReference<Activity>,
        val feedback: Map<String, NavigationRippleDrawable.Pulse>,
        val timeout: Runnable,
        var acknowledged: Boolean = false
    )
    private val pendingRoots = mutableMapOf<String, PendingRoot>()
    private val navigationResets = java.util.WeakHashMap<Activity, () -> Unit>()
    private val rootCleanups = java.util.WeakHashMap<Activity, () -> Unit>()
    internal val pendingRootCount: Int get() = pendingRoots.size
    private val mainHandler = Handler(Looper.getMainLooper())

    private val rippleColors = java.util.WeakHashMap<LinearLayout, Int>()

    /** Track the resolved theme tint without replacing feedback drawables. */
    internal fun currentRippleColor(item: LinearLayout): Int? = rippleColors[item]

    fun prepareRootSurface(activity: Activity, parent: CoordinatorLayout) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        val surface = OxygenThemeEngine.snapshot(activity, activity.config).pageBackground
        disableRootWindowMotion(activity)
        activity.window.setBackgroundDrawable(ColorDrawable(surface))
        parent.setBackgroundColor(surface)
        OxygenSystemBars.apply(activity)
    }

    @Suppress("DEPRECATION")
    private fun disableRootWindowMotion(activity: Activity) {
        activity.window.setWindowAnimations(0)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }

    /** Activity teardown cancels callbacks rather than leaving a retained window/view. */
    fun dispose(activity: Activity) {
        rootCleanups.remove(activity)?.invoke()
        navigationResets.remove(activity)
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

    @Suppress("DEPRECATION")
    private fun finishOutgoing(token: String?, captured: PendingRoot?) {
        val pending = pendingRoots[token] ?: captured ?: return
        if (pending.acknowledged) return
        pending.acknowledged = true
        mainHandler.removeCallbacks(pending.timeout)
        if (token != null) pendingRoots.remove(token)
        val source = pending.source.get() ?: return
        // A late frame after the watchdog must not retire the source of a newer request.
        if (pendingRoots.values.any { it.source.get() === source }) return
        if (!source.isFinishing && !source.isDestroyed) {
            source.finish()
            source.overridePendingTransition(0, 0)
        }
    }

    /**
     * Keep the pressed tint semantic and legible on the current surface. Strong,
     * already-readable accents use a lighter touch; borderline accents get a
     * little more opacity so the circular ripple remains visible in both themes.
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

    private fun updateRipple(item: LinearLayout, colors: OxygenThemeTokens) {
        val color = rippleColor(colors.interactiveAccent, colors.pageBackground)
        val ripple = item.background as? NavigationRippleDrawable
        val scale = animationScale(item)
        if (ripple == null) {
            item.background = NavigationRippleDrawable(color,
                item.resources.getDimension(R.dimen.oxygen_bottom_bar_ripple_radius),
                scale)
        } else if (rippleColors[item] != color) {
            ripple.color = color
            ripple.invalidateSelf()
        }
        (item.background as? NavigationRippleDrawable)?.durationScale = scale
        rippleColors[item] = color
    }

    private fun animationScale(item: LinearLayout): Float = runCatching {
        Settings.Global.getFloat(item.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    /** Both windows use the same final tab colors, without competing tint timelines. */
    private fun updateTint(item: LinearLayout, tint: Int) {
        val icon = item.getChildAt(0) as? ImageView ?: return
        val label = item.getChildAt(1) as? TextView ?: return
        if (icon.imageTintList?.defaultColor != tint) icon.imageTintList = ColorStateList.valueOf(tint)
        if (label.currentTextColor != tint) label.setTextColor(tint)
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
            // Preserve the same Drawable and any in-flight pressed ripple.
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
            val feedback = bars[activity]?.get()?.let { bar ->
                (0 until bar.childCount).mapNotNull { index ->
                    val item = bar.getChildAt(index)
                    val pulse = (item.background as? NavigationRippleDrawable)?.pulse ?: return@mapNotNull null
                    // Snapshot release even if View posts its unset-pressed callback after click.
                    item.tag.toString() to pulse.copy(releasedAt = pulse.releasedAt
                        ?: android.os.SystemClock.uptimeMillis())
                }.toMap()
            }.orEmpty()
            val timeout = Runnable {
                val pending = pendingRoots.remove(token) ?: return@Runnable
                // A deadline cannot prove a destination frame. Release the request
                // lock; a later frame may still retire this weakly held source.
                pending.source.get()?.let { navigationResets[it]?.invoke() }
            }
            pendingRoots[token] = PendingRoot(java.lang.ref.WeakReference(activity), feedback, timeout)
            mainHandler.postDelayed(timeout, ROOT_FINISH_FALLBACK_MS)
        }
        try {
            disableRootWindowMotion(activity)
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
        } catch (error: RuntimeException) {
            token?.let { pendingRoots.remove(it)?.let { pending -> mainHandler.removeCallbacks(pending.timeout) } }
            throw error
        }
    }

    private const val EXTRA_ROOT_TRANSITION = "org.fossify.gallery.ROOT_TRANSITION"
    /** Finish the source on a real destination frame, without blocking that frame. */
    internal fun coordinateFirstDraw(activity: Activity, parent: CoordinatorLayout,
        loadingFrame: () -> Unit = {}) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        rootCleanups.remove(activity)?.invoke()
        activity.intent.removeExtra(EXTRA_ROOT_TRANSITION)
        val token = activity.intent.getStringExtra(EXTRA_ROOT_TOKEN)
        val captured = pendingRoots[token]
        activity.intent.removeExtra(EXTRA_ROOT_TOKEN)
        var acknowledged = false
        lateinit var cleanup: () -> Unit
        lateinit var drawn: android.view.ViewTreeObserver.OnDrawListener
        lateinit var attachment: android.view.View.OnAttachStateChangeListener
        val afterDraw = Runnable {
            if (acknowledged) return@Runnable
            acknowledged = true
            cleanup()
            rootCleanups.remove(activity)
            finishOutgoing(token, captured)
        }
        val revealLoading = Runnable { if (!acknowledged) loadingFrame() }
        var commitRequested = false
        drawn = android.view.ViewTreeObserver.OnDrawListener {
            if (!commitRequested) {
                commitRequested = true
                if (android.os.Build.VERSION.SDK_INT >= 29 && parent.isHardwareAccelerated) {
                    parent.viewTreeObserver.registerFrameCommitCallback(afterDraw)
                } else {
                    // Compatibility approximation; OnDraw alone is not an on-screen proof.
                    parent.postOnAnimation(afterDraw)
                }
            }
        }
        cleanup = {
            if (!acknowledged && token != null) {
                pendingRoots.remove(token)?.let { pending ->
                    mainHandler.removeCallbacks(pending.timeout)
                    pending.source.get()?.let { navigationResets[it]?.invoke() }
                }
            }
            if (parent.viewTreeObserver.isAlive) parent.viewTreeObserver.removeOnDrawListener(drawn)
            mainHandler.removeCallbacks(afterDraw)
            parent.removeCallbacks(afterDraw)
            if (android.os.Build.VERSION.SDK_INT >= 29 && parent.viewTreeObserver.isAlive) {
                parent.viewTreeObserver.unregisterFrameCommitCallback(afterDraw)
            }
            parent.removeCallbacks(revealLoading)
            parent.removeOnAttachStateChangeListener(attachment)
        }
        attachment = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) = Unit
            override fun onViewDetachedFromWindow(view: android.view.View) {
                cleanup()
                rootCleanups.remove(activity)
                // The fallback still bounds a destination that never draws.
            }
        }
        rootCleanups[activity] = cleanup
        parent.viewTreeObserver.addOnDrawListener(drawn)
        parent.addOnAttachStateChangeListener(attachment)
        parent.postDelayed(revealLoading, FIRST_DRAW_LIMIT_MS)
    }

    fun attach(
        activity: Activity,
        parent: CoordinatorLayout,
        active: Tab,
        loadingFrame: () -> Unit = {},
        navigate: (Tab) -> Unit
    ) {
        val feedback = pendingRoots[activity.intent.getStringExtra(EXTRA_ROOT_TOKEN)]?.feedback.orEmpty()
        coordinateFirstDraw(activity, parent, loadingFrame)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
        val surfaceColor = tokens.pageBackground
        val primary = tokens
        val selectedText = tokens.interactiveAccent
        val quiet = tokens.secondaryText

        // Reattaching after a configuration-driven rebuild must not duplicate the bar.
        parent.findViewWithTag<android.view.View>("oxygen_classic_bottom_bar")?.let(parent::removeView)
        var navigating = false
        navigationResets[activity] = { navigating = false }
        val barHeight = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height)
        val itemHeight = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_item_height)
        val iconSize = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_icon_size)
        val bar = LinearLayout(activity).apply {
            tag = "oxygen_classic_bottom_bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surfaceColor)
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
                        // Set the Drawable state immediately; leave click,
                        // release, cancel, and long-press handling to the View.
                        (view.background as? NavigationRippleDrawable)?.durationScale = animationScale(this)
                        view.drawableHotspotChanged(event.x, event.y)
                        view.isPressed = true
                    }
                    false
                }
                val accessibilityLabel = label
                contentDescription = if (selected) "$accessibilityLabel, ${activity.getString(R.string.classic_selected)}"
                    else accessibilityLabel
                updateRipple(this, primary)
                (this.background as? NavigationRippleDrawable)?.continuePulse(feedback[tab.name])
                setOnClickListener {
                    if (tab != active && !navigating) {
                        navigating = true
                        // Keep outgoing selection aligned with outgoing content. Destination
                        // attach applies its own selection on the same frame as its content.
                        try {
                            navigate(tab)
                        } catch (error: RuntimeException) {
                            navigating = false
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

