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
import androidx.core.view.doOnPreDraw
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
        val selection: String?,
        val timeout: Runnable,
        var acknowledged: Boolean = false
    )
    private val pendingRoots = mutableMapOf<String, PendingRoot>()
    private val latestRequests = java.util.WeakHashMap<Activity, String>()
    private val touchTokens = java.util.WeakHashMap<Activity, String>()
    private val retiringSources = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())
    private val navigationResets = java.util.WeakHashMap<Activity, () -> Unit>()
    private val rootCleanups = java.util.WeakHashMap<Activity, () -> Unit>()
    internal val pendingRootCount: Int get() = pendingRoots.size
    private val mainHandler = Handler(Looper.getMainLooper())

    private val tintAnimations = java.util.WeakHashMap<Activity, android.animation.ValueAnimator>()
    private val rippleColors = java.util.WeakHashMap<LinearLayout, Int>()

    /** Track the resolved theme tint without replacing feedback drawables. */
    internal fun currentRippleColor(item: LinearLayout): Int? = rippleColors[item]

    internal fun isRetiringRoot(activity: Activity): Boolean = activity in retiringSources

    internal fun isRootTransition(activity: Activity): Boolean =
        activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false) || activity in retiringSources

    internal fun trace(activity: Activity, event: String, fields: Map<String, Any?> = emptyMap()) {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        val inputEvent = event == "touch" || event == "pulse_created" || event == "pulse_released" || event == "click"
        val token = (if (inputEvent) touchTokens[activity] ?: latestRequests[activity]
            else latestRequests[activity] ?: activity.intent.getStringExtra(EXTRA_ROOT_TOKEN)) ?: return
        GalleryDiagnosticLogger.event(module = "RootNavigation", name = event, requestId = token,
            fields = mapOf("activity" to activity.javaClass.simpleName,
                "uptimeMs" to android.os.SystemClock.uptimeMillis()) + fields)
    }

    internal fun traceWindow(activity: Activity, event: String) {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        fun attribute(id: Int): Int {
            val value = android.util.TypedValue()
            activity.theme.resolveAttribute(id, value, true)
            return if (value.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT)
                value.data else value.resourceId
        }
        trace(activity, event, mapOf(
            "themeBackground" to attribute(android.R.attr.windowBackground),
            "themeAnimation" to attribute(android.R.attr.windowAnimationStyle),
            "windowAnimations" to activity.window.attributes.windowAnimations,
            "windowAlpha" to activity.window.attributes.alpha,
            "flags" to activity.window.attributes.flags,
            "decorColor" to (activity.window.peekDecorView()?.background as? ColorDrawable)?.color))
    }

    internal fun restoreRootWindow(activity: Activity) {
        if (!isRootTransition(activity)) return
        disableRootWindowMotion(activity)
        activity.window.setBackgroundDrawable(ColorDrawable(
            OxygenThemeEngine.snapshot(activity, activity.config).pageBackground))
        traceWindow(activity, "window_restored")
    }

    fun prepareRootSurface(activity: Activity, parent: CoordinatorLayout) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        val surface = OxygenThemeEngine.snapshot(activity, activity.config).pageBackground
        disableRootWindowMotion(activity)
        activity.window.setBackgroundDrawable(ColorDrawable(surface))
        parent.setBackgroundColor(surface)
        OxygenSystemBars.apply(activity)
        traceWindow(activity, "prepare_surface")
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
        trace(activity, "destroy_or_dispose")
        tintAnimations.remove(activity)?.cancel()
        touchTokens.remove(activity)
        latestRequests.remove(activity)
        retiringSources.remove(activity)
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
        if (latestRequests[source] != token) return
        if (!source.isFinishing && !source.isDestroyed) {
            retiringSources.add(source)
            trace(source, "source_finish")
            source.finish()
            source.overridePendingTransition(0, 0)
        }
    }

    /** Use the resolved theme accent unchanged, with a consistent 8% ripple opacity. */
    internal fun rippleColor(accent: Int): Int =
        Color.argb(20, Color.red(accent), Color.green(accent), Color.blue(accent))

    private fun updateRipple(item: LinearLayout, colors: OxygenThemeTokens) {
        val color = rippleColor(colors.interactiveAccent)
        val ripple = item.background as? NavigationRippleDrawable
        val scale = animationScale(item)
        if (ripple == null) {
            item.background = NavigationRippleDrawable(color, scale)
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

    internal fun applySelectionBlend(bar: LinearLayout, previous: String, fraction: Float, colors: OxygenThemeTokens) {
        for (index in 0 until bar.childCount) {
            val item = bar.getChildAt(index) as? LinearLayout ?: continue
            val from = if (item.tag == previous) colors.interactiveAccent else colors.secondaryText
            val to = if (item.isSelected) colors.interactiveAccent else colors.secondaryText
            updateTint(item, androidx.core.graphics.ColorUtils.blendARGB(from, to, fraction))
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
        tintAnimations.remove(activity)?.cancel()
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
        val token = if (finishCurrent) touchTokens.remove(activity) ?: java.util.UUID.randomUUID().toString() else null
        if (token != null) {
            intent.putExtra(EXTRA_ROOT_TOKEN, token)
            latestRequests[activity] = token
            val feedback = bars[activity]?.get()?.let { bar ->
                (0 until bar.childCount).mapNotNull { index ->
                    val item = bar.getChildAt(index)
                    val drawable = item.background as? NavigationRippleDrawable ?: return@mapNotNull null
                    val pulse = drawable.pulse ?: return@mapNotNull null
                    if (drawable.progress(android.os.SystemClock.uptimeMillis()) == null) return@mapNotNull null
                    // Snapshot release even if View posts its unset-pressed callback after click.
                    item.tag.toString() to pulse.copy(releasedAt = pulse.releasedAt
                        ?: android.os.SystemClock.uptimeMillis())
                }.toMap()
            }.orEmpty()
            trace(activity, "pulse_snapshot", mapOf("count" to feedback.size))
            val timeout = Runnable {
                val pending = pendingRoots.remove(token) ?: return@Runnable
                // A deadline cannot prove a destination frame. Release the request
                // lock; a later frame may still retire this weakly held source.
                pending.source.get()?.let { navigationResets[it]?.invoke() }
            }
            val selection = bars[activity]?.get()?.let { bar ->
                (0 until bar.childCount).map { bar.getChildAt(it) }.firstOrNull { it.isSelected }?.tag as? String
            }
            pendingRoots[token] = PendingRoot(java.lang.ref.WeakReference(activity), feedback, selection, timeout)
            mainHandler.postDelayed(timeout, ROOT_FINISH_FALLBACK_MS)
        }
        try {
            disableRootWindowMotion(activity)
            traceWindow(activity, "start_activity")
            activity.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle())
            activity.overridePendingTransition(0, 0)
        } catch (error: RuntimeException) {
            token?.let { pendingRoots.remove(it)?.let { pending -> mainHandler.removeCallbacks(pending.timeout) } }
            throw error
        }
    }

    private const val EXTRA_ROOT_TRANSITION = "org.fossify.gallery.ROOT_TRANSITION"
    /** Finish the source on a real destination frame, without blocking that frame. */
    internal fun coordinateFirstDraw(activity: Activity, parent: CoordinatorLayout,
        loadingFrame: () -> Unit = {},
        frameState: () -> String? = { null }) {
        if (!activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false)) return
        rootCleanups.remove(activity)?.invoke()
        // Keep root-window identity through Commons onResume and eventual finish.
        val token = activity.intent.getStringExtra(EXTRA_ROOT_TOKEN)
        val captured = pendingRoots[token]
        // Keep token for lifecycle diagnostics after the first frame.
        var acknowledged = false
        // A blank target must not replace the still-valid source surface. Production
        // attach publishes loading synchronously, so this is a state gate, not a delay.
        val readyToDraw = android.view.ViewTreeObserver.OnPreDrawListener { frameState() != null }
        lateinit var cleanup: () -> Unit
        lateinit var drawn: android.view.ViewTreeObserver.OnDrawListener
        lateinit var attachment: android.view.View.OnAttachStateChangeListener
        val afterDraw = Runnable {
            if (acknowledged) return@Runnable
            acknowledged = true
            traceWindow(activity, "frame_commit")
            cleanup()
            rootCleanups.remove(activity)
            finishOutgoing(token, captured)
        }
        val revealLoading = Runnable { if (!acknowledged) loadingFrame() }
        var commitRequested = false
        var firstDraw = true
        drawn = android.view.ViewTreeObserver.OnDrawListener {
            val state = frameState()
            if (firstDraw) {
                firstDraw = false
                traceWindow(activity, "first_root_draw")
                trace(activity, "first_page_state", mapOf("state" to (state ?: "blank")))
            }
            if (!commitRequested && state != null) {
                trace(activity, "valid_frame_draw", mapOf("state" to state))
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
            if (parent.viewTreeObserver.isAlive) {
                parent.viewTreeObserver.removeOnDrawListener(drawn)
                parent.viewTreeObserver.removeOnPreDrawListener(readyToDraw)
            }
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
        parent.viewTreeObserver.addOnPreDrawListener(readyToDraw)
        parent.viewTreeObserver.addOnDrawListener(drawn)
        parent.addOnAttachStateChangeListener(attachment)
        parent.postDelayed(revealLoading, FIRST_DRAW_LIMIT_MS)
    }

    fun attach(
        activity: Activity,
        parent: CoordinatorLayout,
        active: Tab,
        loadingFrame: () -> Unit = {},
        frameState: () -> String? = { null },
        navigate: (Tab) -> Unit
    ) {
        val pending = pendingRoots[activity.intent.getStringExtra(EXTRA_ROOT_TOKEN)]
        val feedback = pending?.feedback.orEmpty()
        val previousSelection = pending?.selection
        // Publish a real loading strip before the first traversal, not after an empty draw.
        if (activity.intent.getBooleanExtra(EXTRA_ROOT_TRANSITION, false) && frameState() == null) loadingFrame()
        coordinateFirstDraw(activity, parent, loadingFrame, frameState)
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
                        touchTokens[activity] = java.util.UUID.randomUUID().toString()
                    }
                    trace(activity, "touch", mapOf("action" to event.actionMasked, "tab" to tab.name))
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        // CoordinatorLayout can defer child pressed feedback while
                        // deciding whether a gesture belongs to a scrolling parent.
                        // Set the Drawable state immediately; leave click,
                        // release, cancel, and long-press handling to the View.
                        (view.background as? NavigationRippleDrawable)?.durationScale = animationScale(this)
                        view.drawableHotspotChanged(event.x, event.y)
                        view.isPressed = true
                        trace(activity, "pulse_created", mapOf("tab" to tab.name,
                            "started" to (view.background as? NavigationRippleDrawable)?.pulse?.startedAt))
                    } else if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                        event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                        val ripple = view.background as? NavigationRippleDrawable
                        ripple?.release()
                        trace(activity, "pulse_released", mapOf("tab" to tab.name,
                            "released" to ripple?.pulse?.releasedAt))
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
                        trace(activity, "click", mapOf("tab" to tab.name))
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
        if (previousSelection != null && previousSelection != active.name) bar.doOnPreDraw {
            if (bars[activity]?.get() === bar && !activity.isFinishing && !activity.isDestroyed && animationScale(bar) > 0f) {
                val currentColors = OxygenThemeEngine.snapshot(activity, activity.config)
                val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = OxygenClassicMotion.TAB_MS
                    interpolator = OxygenClassicMotion.easing()
                    addUpdateListener { applySelectionBlend(bar, previousSelection, it.animatedValue as Float, currentColors) }
                }
                animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (tintAnimations[activity] === animation) tintAnimations.remove(activity)
                    }
                })
                tintAnimations.put(activity, animator)?.cancel()
                animator.start()
            }
        }
        ViewCompat.requestApplyInsets(bar)
    }
}

