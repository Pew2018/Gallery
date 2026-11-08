package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryBottomBarRippleTest {

    private fun fixture(active: GalleryBottomBar.Tab = GalleryBottomBar.Tab.ALL,
                        order: List<String> = listOf("ALL", "FAVORITES", "PHOTOS"),
                        onNavigate: (GalleryBottomBar.Tab) -> Unit = {}):
        Pair<android.app.Activity, LinearLayout> {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.config.oxygenThemeActivated = true
        activity.config.oxygenThemePresetId = "custom"
        activity.config.oxygenAppearanceMode = "light"
        activity.config.oxygenCustomAccent = Color.rgb(0, 113, 205)
        activity.config.bottomNavigationOrder = order
        val root = CoordinatorLayout(activity)
        activity.setContentView(root)
        GalleryBottomBar.attach(activity, root, active, navigate = onNavigate)
        return activity to root.findViewWithTag("oxygen_classic_bottom_bar")
    }

    private fun items(bar: LinearLayout) = (0 until bar.childCount)
        .map { bar.getChildAt(it) as LinearLayout }

    @Test fun recolorPreservesAllThreeRippleInstancesAcrossIdenticalAndChangedThemes() {
        val (activity, bar) = fixture()
        val tabs = items(bar)
        val ripples = tabs.map { it.background as NavigationRippleDrawable }
        val firstColor = GalleryBottomBar.currentRippleColor(tabs[0])
        tabs[1].isPressed = true
        repeat(4) { GalleryBottomBar.recolor(activity) }
        assertTrue(tabs.indices.all { ripples[it] === tabs[it].background })
        activity.config.oxygenCustomAccent = Color.rgb(215, 55, 95)
        GalleryBottomBar.recolor(activity)
        assertTrue(tabs.indices.all { ripples[it] === tabs[it].background })
        val secondColor = GalleryBottomBar.currentRippleColor(tabs[0])
        assertNotEquals(firstColor, secondColor)
        assertTrue(tabs.all { GalleryBottomBar.currentRippleColor(it) == secondColor })
        val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
        assertEquals(GalleryBottomBar.rippleColor(tokens.interactiveAccent, tokens.pageBackground), secondColor)
        assertTrue(tabs[1].isPressed)
        tabs[1].isPressed = false
        GalleryBottomBar.recolor(activity)
        assertSame(ripples[1], tabs[1].background)
        activity.finish()
    }

    @Test fun rippleUsesResolvedThemeAccentAndAdaptsAlphaToSurfaceContrast() {
        val (activity, bar) = fixture()
        val item = items(bar).first()
        activity.config.oxygenCustomAccent = Color.BLACK
        GalleryBottomBar.recolor(activity)
        val highContrast = OxygenThemeEngine.snapshot(activity, activity.config)
        assertEquals(GalleryBottomBar.rippleColor(highContrast.interactiveAccent, highContrast.pageBackground),
            GalleryBottomBar.currentRippleColor(item))
        assertEquals(32, Color.alpha(GalleryBottomBar.currentRippleColor(item)!!))

        val paleAccent = Color.rgb(255, 245, 220)
        activity.config.oxygenCustomAccent = paleAccent
        GalleryBottomBar.recolor(activity)
        val lightSurface = OxygenThemeEngine.snapshot(activity, activity.config)
        assertNotEquals(paleAccent, lightSurface.interactiveAccent)
        assertEquals(40, Color.alpha(GalleryBottomBar.currentRippleColor(item)!!))
        assertEquals(GalleryBottomBar.rippleColor(lightSurface.interactiveAccent, lightSurface.pageBackground),
            GalleryBottomBar.currentRippleColor(item))

        activity.config.oxygenAppearanceMode = "dark"
        GalleryBottomBar.recolor(activity)
        val darkSurface = OxygenThemeEngine.snapshot(activity, activity.config)
        assertEquals(GalleryBottomBar.rippleColor(darkSurface.interactiveAccent, darkSurface.pageBackground),
            GalleryBottomBar.currentRippleColor(item))
        activity.finish()
    }

    @Test fun rippleOpacityUsesBalancedContrastBands() {
        val surface = Color.WHITE
        assertEquals(32, Color.alpha(GalleryBottomBar.rippleColor(Color.BLACK, surface)))
        assertEquals(36, Color.alpha(GalleryBottomBar.rippleColor(Color.rgb(80, 80, 80), surface)))
        assertEquals(40, Color.alpha(GalleryBottomBar.rippleColor(Color.rgb(102, 102, 102), surface)))
    }

    @Test fun rapidTapsOnlyNavigateOnceAndOutgoingSelectionStillMatchesOutgoingContent() {
        val visited = mutableListOf<GalleryBottomBar.Tab>()
        val (activity, bar) = fixture(onNavigate = { visited.add(it) })
        val tabs = items(bar)
        assertEquals(3, tabs.size)
        assertTrue(tabs[0].isSelected)
        val density = activity.resources.displayMetrics.density
        assertEquals(56f,
            activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height) / density, 0.1f)
        assertEquals(24f,
            activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_icon_size) / density, 0.1f)
        assertEquals(12f, (tabs[0].getChildAt(1) as TextView).textSize /
            activity.resources.displayMetrics.scaledDensity, 0.01f)
        assertTrue(tabs.all { it.gravity == android.view.Gravity.CENTER })
        assertTrue(tabs.all { it.getChildAt(1).layoutParams.height == -2 })
        assertEquals(2f, activity.resources.getDimension(R.dimen.oxygen_bottom_bar_label_gap) /
            activity.resources.displayMetrics.density, 0.01f)
        assertTrue(tabs.all { it.background is NavigationRippleDrawable })
        tabs[1].performClick()
        tabs[2].performClick()
        tabs[1].performClick()
        assertEquals(listOf(GalleryBottomBar.Tab.FAVORITES), visited)
        assertTrue(tabs[0].isSelected)
        assertFalse(tabs[1].isSelected)
        assertFalse(tabs[2].isSelected)
        // Medium (500) is not Typeface.BOLD (700); test the actual family.
        assertEquals(Typeface.create("sans-serif-medium", Typeface.NORMAL),
            (tabs[0].getChildAt(1) as TextView).typeface)
        assertEquals(Typeface.create("sans-serif", Typeface.NORMAL),
            (tabs[1].getChildAt(1) as TextView).typeface)
        // Selection changes with destination content, never ahead of it.
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        assertEquals(colors.interactiveAccent, (tabs[0].getChildAt(0) as ImageView).imageTintList!!.defaultColor)
        assertEquals(colors.secondaryText, (tabs[1].getChildAt(1) as TextView).currentTextColor)
        activity.finish()
    }

    @Test fun failedNavigationRestoresSelectionAndAllowsRetry() {
        var shouldFail = true
        var attempts = 0
        val (activity, bar) = fixture(onNavigate = {
            attempts++
            if (shouldFail) throw IllegalStateException("simulated Activity launch failure")
        })
        val tabs = items(bar)
        try {
            try {
                tabs[1].performClick()
                fail("first navigation attempt should fail")
            } catch (_: IllegalStateException) {
                // The tab selection must roll back with the failed launch.
            }
            assertTrue(tabs[0].isSelected)
            assertFalse(tabs[1].isSelected)

            shouldFail = false
            tabs[1].performClick()
            assertEquals(2, attempts)
            assertTrue(tabs[0].isSelected)
            assertFalse(tabs[1].isSelected)
        } finally {
            GalleryBottomBar.dispose(activity)
            activity.finish()
        }
    }

    @Test fun navigationStartsImmediatelyAndUnresponsiveTargetCannotFinishUnconfirmedSource() {
        val (activity, _) = fixture()
        GalleryBottomBar.open(activity, Intent(activity, android.app.Activity::class.java), finishCurrent = true)
        assertNotNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertFalse("navigation must not finish the ripple host on the click frame", activity.isFinishing)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            GalleryBottomBar.ROOT_FINISH_FALLBACK_MS - 1, TimeUnit.MILLISECONDS)
        assertFalse("unresponsive target remains bounded by the fallback", activity.isFinishing)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.MILLISECONDS)
        assertFalse("timeout cannot prove a frame or finish the recoverable source", activity.isFinishing)
        assertEquals(0, GalleryBottomBar.pendingRootCount)
    }

    @Test fun destinationFirstFrameRemainsOpaqueAndDoesNotStartASeparateContentFade() {
        val (source, _) = fixture()
        val destinationIntent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, destinationIntent)
        val destination = Robolectric.buildActivity(android.app.Activity::class.java, destinationIntent)
            .setup().get()
        val resolver = destination.contentResolver
        val priorScale = android.provider.Settings.Global.getFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        android.provider.Settings.Global.putFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val root = CoordinatorLayout(destination)
        val content = View(destination).apply { id = R.id.directories_holder }
        root.addView(content, CoordinatorLayout.LayoutParams(100, 100))
        destination.setContentView(root)
        val bar = LinearLayout(destination)
        root.addView(bar, CoordinatorLayout.LayoutParams(100, 56))

        try {
            GalleryBottomBar.coordinateFirstDraw(destination, root)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                GalleryBottomBar.FIRST_DRAW_LIMIT_MS, TimeUnit.MILLISECONDS)
            root.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("content must not be zeroed for a second independent fade", 1f, content.alpha)
            assertEquals(1f, bar.alpha)

            root.viewTreeObserver.dispatchOnDraw()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1f, content.alpha)
            assertEquals(1f, bar.alpha)
        } finally {
            GalleryBottomBar.dispose(destination)
            GalleryBottomBar.dispose(source)
            destination.finish()
            source.finish()
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, priorScale)
        }
    }

    @Test fun unchangedOrderRefreshDoesNotDetachItemsOrResetRipple() {
        val (activity, bar) = fixture()
        val tabs = items(bar)
        val backgrounds = tabs.map { it.background }
        GalleryBottomBar.refreshAll()
        assertEquals(tabs, items(bar))
        assertTrue(items(bar).indices.all { items(bar)[it].background === backgrounds[it] })
        activity.finish()
    }

    @Test fun changedOrderReusesTabViewsAndRippleDrawables() {
        val (activity, bar) = fixture(active = GalleryBottomBar.Tab.FAVORITES)
        val before = items(bar).associate { it.tag as String to (it to it.background) }
        activity.config.bottomNavigationOrder = listOf("PHOTOS", "FAVORITES", "ALL")
        GalleryBottomBar.refreshAll()
        val after = items(bar)
        assertEquals(listOf("PHOTOS", "FAVORITES", "ALL"), after.map { it.tag })
        after.forEach { item ->
            val previous = before[item.tag as String]!!
            assertSame(previous.first, item)
            assertSame(previous.second, item.background)
        }
        assertTrue(after[1].isSelected)
        activity.finish()
    }

    @Test fun allSixUserOrdersRenderMatchingEqualWidthTabsAndRippleMasks() {
        val permutations = listOf(
            listOf("ALL", "FAVORITES", "PHOTOS"), listOf("ALL", "PHOTOS", "FAVORITES"),
            listOf("FAVORITES", "ALL", "PHOTOS"), listOf("FAVORITES", "PHOTOS", "ALL"),
            listOf("PHOTOS", "ALL", "FAVORITES"), listOf("PHOTOS", "FAVORITES", "ALL")
        )
        permutations.forEach { order ->
            val (activity, bar) = fixture(order = order)
            val tabs = items(bar)
            assertEquals(order, tabs.map { it.tag })
            assertTrue(tabs.all { (it.layoutParams as LinearLayout.LayoutParams).weight == 1f })
            GalleryBottomBar.dispose(activity)
            activity.finish()
        }
    }

    @Test fun rootHandoffCarriesFeedbackClockAndOnlyDestinationSelectsTarget() {
        val (source, sourceBar) = fixture()
        val sourceItem = items(sourceBar).first { it.tag == GalleryBottomBar.Tab.FAVORITES.name }
        val ripple = sourceItem.background as NavigationRippleDrawable
        val now = android.os.SystemClock.uptimeMillis()
        ripple.continuePulse(NavigationRippleDrawable.Pulse(now - 60, 0.3f, 0.7f, now - 10))
        val intent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, intent, finishCurrent = true)
        assertTrue(items(sourceBar)[0].isSelected)
        val token = intent.getStringExtra("org.fossify.gallery.ROOT_TRANSITION_TOKEN")
        assertNotNull("launch must carry a transaction token", token)
        assertEquals(0, Shadows.shadowOf(source).pendingTransitionEnterAnimationResourceId)
        assertEquals(0, Shadows.shadowOf(source).pendingTransitionExitAnimationResourceId)
        val destination = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
        val root = CoordinatorLayout(destination)
        destination.setContentView(root)
        try {
            assertEquals("destination must retain launch token", token,
                destination.intent.getStringExtra("org.fossify.gallery.ROOT_TRANSITION_TOKEN"))
            GalleryBottomBar.prepareRootSurface(destination, root)
            GalleryBottomBar.attach(destination, root, GalleryBottomBar.Tab.FAVORITES, navigate = {})
            val bar = root.findViewWithTag<LinearLayout>("oxygen_classic_bottom_bar")
            val targetItem = items(bar).first { it.tag == GalleryBottomBar.Tab.FAVORITES.name }
            val continued = targetItem.background as NavigationRippleDrawable
            assertEquals("feedback tags=" + items(bar).map { it.tag } +
                " pending=" + GalleryBottomBar.pendingRootCount + " at=" + android.os.SystemClock.uptimeMillis(),
                ripple.pulse, continued.pulse)
            assertEquals(ripple.progress(now), continued.progress(now))
            assertTrue(targetItem.isSelected)
            assertFalse(items(bar).first { it.tag == GalleryBottomBar.Tab.ALL.name }.isSelected)
            assertEquals(1f, bar.alpha)
            assertEquals(0, destination.window.attributes.windowAnimations)
        } finally {
            GalleryBottomBar.dispose(destination)
            GalleryBottomBar.dispose(source)
            destination.finish()
            source.finish()
        }
    }

    @Test fun cancelledPressDoesNotNavigateOrLeavePressedState() {
        var visits = 0
        val (activity, bar) = fixture(onNavigate = { visits++ })
        val item = items(bar)[1]
        val ripple = item.background
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 50f, 28f, 0)
        item.dispatchTouchEvent(down)
        down.recycle()
        val cancel = android.view.MotionEvent.obtain(now, now + 10, android.view.MotionEvent.ACTION_CANCEL, 120f, 28f, 0)
        item.dispatchTouchEvent(cancel)
        cancel.recycle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, visits)
        assertFalse(item.isPressed)
        assertSame(ripple, item.background)
        GalleryBottomBar.dispose(activity)
        activity.finish()
    }

    @Test fun labelsFitAtLargeFontScaleAndEqualWidthsRemainInRtl() {
        val (activity, bar) = fixture()
        val configuration = activity.resources.configuration
        val originalFontScale = configuration.fontScale
        try {
            configuration.fontScale = 1.5f
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
            val width = (360 * activity.resources.displayMetrics.density).toInt()
            val height = activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height)
            bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            bar.layout(0, 0, width, height)
            val tabs = items(bar)
            assertEquals(3, tabs.size)
            assertEquals(tabs[0].measuredWidth, tabs[1].measuredWidth)
            assertEquals(tabs[1].measuredWidth, tabs[2].measuredWidth)
            tabs.forEach { item ->
                val label = item.getChildAt(1) as TextView
                assertEquals(1.5f, label.resources.configuration.fontScale, 0.01f)
                assertTrue("scaled label should remain measurable", label.measuredHeight > 0)
                assertTrue("scaled label should fit the item", label.bottom <= item.height)
                assertEquals(1, label.maxLines)
            }
        } finally {
            configuration.fontScale = originalFontScale
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            GalleryBottomBar.dispose(activity)
            activity.finish()
        }
    }

    @Test fun actualDownUpKeepsFeedbackAndDoesNotSelectAheadOfContent() {
        var visits = 0
        val (activity, bar) = fixture(onNavigate = { visits++ })
        val tabs = items(bar)
        val item = tabs[1]
        val ripple = item.background
        val resolver = activity.contentResolver
        val prior = android.provider.Settings.Global.getFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        android.provider.Settings.Global.putFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            // setContentView/addView after Activity.setup needs a traversal before
            // delayed touch callbacks have an attached View handler.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("touch host must be attached", item.isAttachedToWindow)
            item.layout(0, 0, 100, 56)
            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 50f, 28f, 0)
            item.dispatchTouchEvent(down)
            down.recycle()
            assertTrue("pressed feedback must start on ACTION_DOWN", item.isPressed)
            assertSame(ripple, item.background)
            // ViewGroup may defer pressed feedback until tap timeout.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                android.view.ViewConfiguration.getTapTimeout().toLong() + 1, TimeUnit.MILLISECONDS)
            assertTrue(item.isPressed)
            assertSame(ripple, item.background)
            val up = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, 50f, 28f, 0)
            item.dispatchTouchEvent(up)
            up.recycle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, visits)
            assertSame(ripple, item.background)
            assertFalse(item.isSelected)
            val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
            assertEquals(tokens.secondaryText, (item.getChildAt(1) as TextView).currentTextColor)
        } finally {
            GalleryBottomBar.dispose(activity)
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            activity.finish()
        }
    }

    @Test fun shortTapFinishesExpansionBeforeReleaseFade() {
        for (held in listOf(20L, 50L, 120L, 800L)) {
            val ripple = NavigationRippleDrawable(Color.GREEN)
            val start = android.os.SystemClock.uptimeMillis()
            ripple.continuePulse(NavigationRippleDrawable.Pulse(start, .5f, .5f, start + held))
            val expanded = ripple.progress(start + maxOf(held, NavigationRippleDrawable.EXPAND_MS))
            assertNotNull("held=$held must reach full radius visibly", expanded)
            assertEquals(1f, expanded!!.first, .001f)
            assertEquals(1f, expanded.second, .001f)
        }
    }

    @Test fun expiredSnapshotIsDiscardedInsteadOfBeingInstalled() {
        val ripple = NavigationRippleDrawable(Color.GREEN)
        ripple.continuePulse(NavigationRippleDrawable.Pulse(
            android.os.SystemClock.uptimeMillis() - 2_000L, .5f, .5f,
            android.os.SystemClock.uptimeMillis() - 1_980L))
        assertNull(ripple.pulse)
    }

    @Test fun iconAndLabelShareOneSelectionBlendAndPreserveSurfaceOpacity() {
        val (activity, bar) = fixture(active = GalleryBottomBar.Tab.PHOTOS)
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        val tabs = items(bar)
        for (fraction in listOf(0f, .3f, .7f, 1f)) {
            GalleryBottomBar.applySelectionBlend(bar, "ALL", fraction, colors)
            tabs.forEach { item ->
                assertEquals((item.getChildAt(0) as ImageView).imageTintList!!.defaultColor,
                    (item.getChildAt(1) as TextView).currentTextColor)
            }
            assertTrue(tabs.single { it.tag == "PHOTOS" }.isSelected)
            assertEquals(1f, bar.alpha)
        }
        assertEquals(colors.interactiveAccent,
            (tabs.single { it.tag == "PHOTOS" }.getChildAt(1) as TextView).currentTextColor)
        GalleryBottomBar.dispose(activity)
        activity.finish()
    }

    @Test fun delayedDestinationDiscardsExpiredRippleButKeepsItsSelection() {
        val (source, bar) = fixture()
        val item = items(bar)[1]
        item.isPressed = true
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
        item.isPressed = false
        val intent = Intent(source, android.app.Activity::class.java)
        GalleryBottomBar.open(source, intent, finishCurrent = true)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
        val target = Robolectric.buildActivity(android.app.Activity::class.java, intent).setup().get()
        val root = CoordinatorLayout(target)
        target.setContentView(root)
        GalleryBottomBar.attach(target, root, GalleryBottomBar.Tab.FAVORITES, navigate = {})
        val targetBar = root.findViewWithTag<LinearLayout>("oxygen_classic_bottom_bar")
        val selected = items(targetBar).single { it.isSelected }
        assertEquals("FAVORITES", selected.tag)
        assertNull((selected.background as NavigationRippleDrawable).pulse)
        GalleryBottomBar.dispose(target)
        GalleryBottomBar.dispose(source)
        target.finish()
        source.finish()
    }

    @Test fun realTwentyAndFiftyMillisecondTouchesReleaseOnUpAndNavigateOnce() {
        val clock = Shadows.shadowOf(Looper.getMainLooper())
        for (held in listOf(20L, 50L)) {
            var visits = 0
            val (activity, bar) = fixture(onNavigate = { visits++ })
            clock.idle()
            val item = items(bar)[1]
            item.layout(0, 0, 100, 56)
            val start = android.os.SystemClock.uptimeMillis()
            fun dispatch(action: Int) {
                val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(),
                    action, 50f, 28f, 0)
                item.dispatchTouchEvent(event)
                event.recycle()
            }
            dispatch(android.view.MotionEvent.ACTION_DOWN)
            clock.idleFor(held, TimeUnit.MILLISECONDS)
            dispatch(android.view.MotionEvent.ACTION_UP)
            clock.idle()
            val ripple = item.background as NavigationRippleDrawable
            assertEquals(start + held, ripple.pulse!!.releasedAt)
            assertEquals(1, visits)
            assertNotNull(ripple.progress(start + NavigationRippleDrawable.EXPAND_MS))
            assertTrue(items(bar).first().isSelected)
            GalleryBottomBar.dispose(activity)
            activity.finish()
        }
    }

}
