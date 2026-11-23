package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Matrix
import android.provider.Settings
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.view.animation.Transformation
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Contract of the ordinary (non-Viewer) page entry.
 *
 * Which branch actually runs, per AOSP `android16-release`:
 *  - Android 13 and below: `AppTransition.loadAnimation` maps `TRANSIT_OLD_ACTIVITY_OPEN`
 *    to `activityOpenEnterAnimation` of the app's window animation style.
 *  - Android 14+: `Transition.calculateAnimationOptionsForActivityTransition` attaches
 *    `ANIM_FROM_STYLE` with the main window's animation style as soon as
 *    `windowAnimations != 0`, and `TransitionAnimationHelper.loadAttributeAnimation`
 *    reads `activityOpenEnterAnimation` from *that* style. It does not fall back to the
 *    framework default, so an undeclared attribute means no open animation at all.
 *
 * Both page styles therefore declare the same resource. Robolectric cannot composite
 * the Shell's surfaces: these assertions pin the wiring and the rendered frames of the
 * resource, not the on-device look.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PageEntryMotionTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()

    private fun animationResource(style: Int, attr: Int): Int {
        val theme = application.resources.newTheme().apply { applyStyle(style, true) }
        val values = theme.obtainStyledAttributes(intArrayOf(attr))
        val resource = values.getResourceId(0, 0)
        values.recycle()
        return resource
    }

    private fun loadedEntry(): AnimationSet =
        AnimationUtils.loadAnimation(application, R.anim.oxygen_classic_page_open_enter) as AnimationSet

    @Test fun android14PageStyleDrivesOpenFromTheSameClassicResource() {
        // The CLOSE-only style used to resolve OPEN to 0, which made the platform finish
        // the open transition without a single animator, i.e. a hard cut.
        assertEquals(
            R.anim.oxygen_classic_page_open_enter,
            animationResource(R.style.OxygenClassicPageBackWindow, android.R.attr.activityOpenEnterAnimation),
        )
        // Declaring OPEN must not disturb the frozen return contract.
        assertEquals(
            R.anim.oxygen_classic_page_back_enter,
            animationResource(R.style.OxygenClassicPageBackWindow, android.R.attr.activityCloseEnterAnimation),
        )
        assertEquals(
            R.anim.oxygen_classic_page_back_exit,
            animationResource(R.style.OxygenClassicPageBackWindow, android.R.attr.activityCloseExitAnimation),
        )
        assertNotEquals(
            animationResource(R.style.OxygenClassicPageBackWindow, android.R.attr.activityOpenEnterAnimation),
            animationResource(R.style.OxygenClassicPageBackWindow, android.R.attr.activityCloseExitAnimation),
        )
    }

    @Test fun legacyPageWindowDeclaresTheOpenBranchInsteadOfRelyingOnWindowEnter() {
        // windowEnterAnimation is the window-level TRANSIT_ENTER animation; Activity
        // opens read activityOpenEnterAnimation, so both point at the page entry.
        assertEquals(
            R.anim.oxygen_classic_page_open_enter,
            animationResource(R.style.OxygenClassicPageWindow, android.R.attr.activityOpenEnterAnimation),
        )
        assertEquals(
            R.anim.oxygen_classic_page_open_enter,
            animationResource(R.style.OxygenClassicPageWindow, android.R.attr.windowEnterAnimation),
        )
    }

    @Test fun entryResourceMatchesTheMotionTokens() {
        assertEquals(180L, OxygenClassicMotion.PAGE_ENTRY_MS)
        assertTrue(OxygenClassicMotion.PAGE_ENTRY_MS in 160L..200L)
        // The settings handoff is the same page clock and weight; it must not drift.
        assertEquals(ClassicSettingsPageTransition.DURATION_MS, OxygenClassicMotion.PAGE_ENTRY_MS)
        assertEquals(ClassicSettingsPageTransition.DISTANCE_DP, OxygenClassicMotion.PAGE_ENTRY_DISTANCE_DP)
        assertEquals(
            OxygenClassicMotion.PAGE_ENTRY_MS,
            application.resources.getInteger(R.integer.oxygen_classic_page_enter_duration_ms).toLong(),
        )
        val entry = loadedEntry()
        assertTrue(entry.animations.all { it.duration == OxygenClassicMotion.PAGE_ENTRY_MS })
        assertTrue(entry.animations.none { it is ScaleAnimation })
        val start = animationFrame(entry, 0f)
        assertEquals(OxygenClassicMotion.PAGE_ENTRY_START_ALPHA, start.alpha, 0.001f)
        assertEquals(1f, scaleX(start.matrix), 0.001f)
        assertEquals(1f, scaleY(start.matrix), 0.001f)
    }

    @Test fun pageEntryOffsetMatchesTheClassicWeight() {
        val density = application.resources.displayMetrics.density
        val offset = application.resources.getDimension(R.dimen.oxygen_classic_page_enter_offset) / density
        assertEquals(OxygenClassicMotion.PAGE_ENTRY_DISTANCE_DP.toFloat(), offset, 0.01f)
        assertEquals("LTR forward entry arrives from the right",
            8f * density, translationX(animationFrame(loadedEntry(), 0f).matrix), 0.01f)
        assertEquals(0f, translationX(animationFrame(loadedEntry(), 1f).matrix), 0.01f)
    }

    @Test
    @Config(qualifiers = "ar-rSA-ldrtl")
    fun rtlMirrorsTheEntryOffsetToTheInlineStartEdge() {
        val density = application.resources.displayMetrics.density
        val offset = application.resources.getDimension(R.dimen.oxygen_classic_page_enter_offset) / density
        assertEquals("RTL forward entry arrives from the left", -8f, offset, 0.01f)
        assertEquals(-8f * density, translationX(animationFrame(loadedEntry(), 0f).matrix), 0.01f)
        // A window animation is evaluated on the surface, so the mirror has to come from
        // the resource configuration; no animation XML resolves layout direction.
        assertEquals(-1, OxygenClassicMotion.horizontalDirection(true, FrameLayout(application)))
        assertEquals(1, OxygenClassicMotion.horizontalDirection(false, FrameLayout(application)))
    }

    @Test fun nestedFolderEntryMovesOnlyTheGridAndClearsItself() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        val host = FrameLayout(activity)
        val grid = FrameLayout(activity)
        activity.setContentView(host)
        host.addView(grid)
        val clock = Shadows.shadowOf(android.os.Looper.getMainLooper())
        val density = activity.resources.displayMetrics.density
        try {
            assertEquals(OxygenClassicMotion.NESTED_FOLDER_MS, GalleryEntryMotion.DURATION_MS)
            assertTrue(GalleryEntryMotion.DURATION_MS in 160L..180L)
            assertTrue(OxygenClassicMotion.NESTED_FOLDER_DISTANCE_DP in 8..10)
            GalleryEntryMotion.enter(grid, forward = true)
            assertEquals(0.96f, grid.alpha, 0.001f)
            assertEquals(8f * density, grid.translationX, 0.5f)
            clock.idleFor(GalleryEntryMotion.DURATION_MS + 80, TimeUnit.MILLISECONDS)
            assertEquals("a finished run must not leave alpha < 1", 1f, grid.alpha, 0.001f)
            assertEquals("a finished run must not leave translationX", 0f, grid.translationX, 0.001f)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "ar-rSA-ldrtl")
    fun nestedFolderEntryKeepsTheBackDirectionInlineInRtl() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        val host = FrameLayout(activity)
        val grid = FrameLayout(activity)
        activity.setContentView(host)
        host.addView(grid)
        val density = activity.resources.displayMetrics.density
        try {
            // Backward is the mirror of forward in every layout direction.
            GalleryEntryMotion.enter(grid, forward = false)
            assertEquals(8f * density, grid.translationX, 0.5f)
            grid.animate().cancel()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun disabledAnimationsLeaveTheGridInItsCommittedFrame() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        val resolver = activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val host = FrameLayout(activity)
        val grid = FrameLayout(activity)
        activity.setContentView(host)
        host.addView(grid)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            assertFalse(OxygenClassicMotion.enabled(activity))
            GalleryEntryMotion.enter(grid, forward = true)
            assertEquals(1f, grid.alpha, 0.001f)
            assertEquals(0f, grid.translationX, 0.001f)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            controller.pause().stop().destroy()
        }
    }

    @Test fun theGestureStepSettlesOnTheSameNestedFolderTokens() {
        // Only the tap path can be driven from a unit test; the gesture step is pinned by
        // its source, so the two entries cannot drift apart again.
        val main = File("src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt").readText()
        val step = main.substringAfter("private fun beginNestedFolderBackStep(")
            .substringBefore("\n    /**")
        assertTrue(step.contains("distanceDp = OxygenClassicMotion.NESTED_FOLDER_DISTANCE_DP"))
        assertTrue(step.contains("incomingStartAlpha = OxygenClassicMotion.NESTED_FOLDER_START_ALPHA"))
        assertTrue(step.contains("settleDurationMs = OxygenClassicMotion.NESTED_FOLDER_MS"))
        assertFalse("the old 18dp / 0.84 values must not come back",
            step.contains("distanceDp = 18") || step.contains("incomingStartAlpha = 0.84"))
        val transition = File("src/main/kotlin/org/fossify/gallery/helpers/ClassicSettingsPageTransition.kt")
            .readText()
        assertTrue(transition.contains("duration = (run.settleDurationMs * remaining)"))
    }
}

/**
 * Evaluates an animation resource at a normalised progress through the same framework
 * call the Shell uses (`AnimationSet.getTransformationAt`), so the assertions read the
 * rendered frame instead of private fields such as `AlphaAnimation.mFromAlpha`.
 */
internal fun animationFrame(animation: Animation, progress: Float): Transformation {
    animation.initialize(1080, 1920, 1080, 1920)
    val transformation = Transformation()
    transformation.clear()
    Animation::class.java
        .getMethod("getTransformationAt", java.lang.Float.TYPE, Transformation::class.java)
        .invoke(animation, progress, transformation)
    return transformation
}

internal fun translationX(matrix: Matrix): Float = FloatArray(9).also(matrix::getValues)[Matrix.MTRANS_X]

internal fun translationY(matrix: Matrix): Float = FloatArray(9).also(matrix::getValues)[Matrix.MTRANS_Y]

internal fun scaleX(matrix: Matrix): Float = FloatArray(9).also(matrix::getValues)[Matrix.MSCALE_X]

internal fun scaleY(matrix: Matrix): Float = FloatArray(9).also(matrix::getValues)[Matrix.MSCALE_Y]
