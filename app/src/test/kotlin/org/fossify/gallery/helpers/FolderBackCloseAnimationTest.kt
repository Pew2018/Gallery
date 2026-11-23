package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Matrix
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Contract of the folder page's post-commit back exit.
 *
 * The album grid is the one page where the entering parent is guaranteed to be a static
 * full-screen rect: `CustomCrossActivityBackAnimation` keeps the parent on a full-screen
 * base rect with alpha pinned to 1 and freezes the closing rect at the release position,
 * so the CLOSE-exit resources carry the whole visible motion.
 *
 * The previous pair cleared the foreground at 35% of a 450 ms clock (~157 ms) and then
 * held alpha 0 for ~293 ms while the Shell's clock kept running: the page vanished, the
 * exit stood still, and the window was removed afterwards. That is the "it just switches
 * to the full home page" reading. This pair puts the fade and the travel on one 300 ms
 * clock that ends together, so the last visible frame is the frame the window is removed
 * on.
 *
 * Assertions evaluate the real resources through `Animation#getTransformationAt`, the
 * exact call the Shell makes. Robolectric cannot composite the Shell surfaces, so the
 * visual result itself still needs device acceptance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FolderBackCloseAnimationTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()

    /** Minimal stand-in for the album grid: the marker is the only thing the style reads. */
    class FolderPageStub : AppCompatActivity(), FolderBackPage {
        override val usesFolderBackMotion = true
    }

    /** A page that reuses MediaActivity's class but must keep the shared page motion. */
    class SharedPageStub : AppCompatActivity(), FolderBackPage {
        override val usesFolderBackMotion = false
    }

    @Test
    fun folderPageSelectsTheFolderCloseStyle() {
        val activity = Robolectric.buildActivity(FolderPageStub::class.java).setup().get()
        OxygenClassicActivityMotion.install(activity)
        assertEquals(R.style.OxygenClassicFolderBackWindow, activity.window.attributes.windowAnimations)
        activity.finish()
    }

    @Test
    fun nonFolderPagesKeepTheSharedCloseStyle() {
        listOf(
            Robolectric.buildActivity(SharedPageStub::class.java).setup().get(),
            Robolectric.buildActivity(AppCompatActivity::class.java).setup().get(),
        ).forEach { activity ->
            OxygenClassicActivityMotion.install(activity)
            // Settings, the theme picker and the custom-photo-profile pages must not be
            // retimed by a folder-only change.
            assertEquals(R.style.OxygenClassicPageBackWindow, activity.window.attributes.windowAnimations)
            activity.finish()
        }
    }

    @Test
    fun folderStyleStillSelectsTheCustomPlatformBranch() {
        val style = closeStyleAttributes(R.style.OxygenClassicFolderBackWindow)
        assertEquals(R.anim.oxygen_classic_folder_back_enter, style.first)
        assertEquals(R.anim.oxygen_classic_folder_back_exit, style.second)
    }

    @Test
    fun folderStyleKeepsTheOpenAnimation() {
        // WindowManager reads activityOpenEnterAnimation from *this* window's style only
        // (ANIM_FROM_STYLE); a CLOSE-only folder style would open with no animation at all.
        val values = ContextThemeWrapper(application, R.style.OxygenClassicFolderBackWindow)
            .obtainStyledAttributes(intArrayOf(android.R.attr.activityOpenEnterAnimation))
        val open = values.getResourceId(0, 0)
        values.recycle()
        assertEquals(R.anim.oxygen_classic_page_open_enter, open)
    }

    @Test
    fun sharedClosePairIsLeftUntouched() {
        // Guard for the "do not retime the pages that already read as correct" rule.
        val style = closeStyleAttributes(R.style.OxygenClassicPageBackWindow)
        assertEquals(R.anim.oxygen_classic_page_back_enter, style.first)
        assertEquals(R.anim.oxygen_classic_page_back_exit, style.second)
        val shared = load(R.anim.oxygen_classic_page_back_exit)
        assertEquals(450L, shared.duration)
        assertEquals(
            application.resources.getInteger(R.integer.oxygen_classic_page_back_duration_ms).toLong(),
            shared.duration,
        )
        // The shared curve still clears the foreground early; only the folder page changed.
        assertTrue(transformation(shared, 0.40f).alpha <= 0.001f)
    }

    @Test
    fun folderExitUsesTheFolderOnlyClock() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        val folderClock = application.resources
            .getInteger(R.integer.oxygen_classic_folder_back_duration_ms).toLong()
        assertEquals(300L, folderClock)
        assertEquals(folderClock, animation.duration)
        // The Shell forwards the same progress to every child without renormalising, so
        // every child has to run on the set clock.
        (animation as AnimationSet).animations.forEach { child ->
            assertEquals(animation.duration, child.duration)
        }
        // Both sides of the Shell's post-commit clock must agree, otherwise
        // max(close.duration, enter.duration) re-introduces the invisible tail.
        assertEquals(folderClock, load(R.anim.oxygen_classic_folder_back_enter).duration)
    }

    @Test
    fun folderExitFadeEndsWithTheTravelInsteadOfLeavingADeadTail() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        var fadeEnd = -1f
        for (step in 0..1000) {
            val progress = step / 1000f
            if (transformation(animation, progress).alpha <= 0.001f) {
                fadeEnd = progress
                break
            }
        }
        assertTrue("the foreground never reaches zero", fadeEnd > 0f)
        // The measured defect: the fade used to end at 35% and left ~65% of the clock with
        // nothing visible. The window is removed at the end of that clock.
        assertTrue("fade ended at $fadeEnd", fadeEnd >= 0.95f)
        // ...and the geometry is already at its target on that same frame.
        val distance = application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)
        assertEquals(distance, translationX(transformation(animation, 1f).matrix), 0.01f)
    }

    @Test
    fun folderExitHoldsTheReleaseFrame() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        // The shared curve already loses 8% of its opacity in the first 9 ms, which reads
        // as a pop on the frame the finger released on.
        assertTrue("alpha at 2% was ${transformation(animation, 0.02f).alpha}",
            transformation(animation, 0.02f).alpha > 0.99f)
        assertEquals(1f, transformation(animation, 0f).alpha, 0.001f)
    }

    @Test
    fun folderExitIsMotionLedBeforeItDissolves() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        // A fade that runs ahead of the travel is a cross-dissolve over a static parent,
        // which is what produced the double-exposed grid. The foreground has to stay
        // substantially opaque while it is still starting to move.
        assertTrue("alpha at 20% was ${transformation(animation, 0.20f).alpha}",
            transformation(animation, 0.20f).alpha > 0.80f)
        val distance = application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)
        val travel = translationX(transformation(animation, 0.20f).matrix) / distance
        assertTrue("travel at 20% was $travel", travel > 0.15f)
        // The mid-opacity band is carried by the fastest part of the motion, and it is
        // short enough in absolute time to not read as two grids superimposed.
        assertTrue("alpha at 50% was ${transformation(animation, 0.50f).alpha}",
            transformation(animation, 0.50f).alpha in 0.20f..0.60f)
    }

    @Test
    fun folderExitIsMonotonicAndEndsAtItsTarget() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        val distance = application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)
        var previousX = -1f
        var previousAlpha = Float.MAX_VALUE
        for (step in 0..200) {
            val transform = transformation(animation, step / 200f)
            val x = translationX(transform.matrix)
            assertTrue("alpha must not increase at $step", transform.alpha <= previousAlpha + 0.0001f)
            assertTrue("the page must keep moving at $step", x >= previousX - 0.001f)
            previousX = x
            previousAlpha = transform.alpha
        }
        assertEquals(0f, translationX(transformation(animation, 0f).matrix), 0.01f)
        assertEquals(distance, previousX, 0.01f)
    }

    @Test
    fun folderExitIsAPureHorizontalTranslation() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        listOf(0f, 0.25f, 0.5f, 1f).forEach { progress ->
            val values = matrixValues(transformation(animation, progress).matrix)
            assertEquals("progress $progress", 1f, values[Matrix.MSCALE_X], 0.001f)
            assertEquals("progress $progress", 1f, values[Matrix.MSCALE_Y], 0.001f)
            assertEquals("progress $progress", 0f, values[Matrix.MSKEW_X], 0.001f)
            assertEquals("progress $progress", 0f, values[Matrix.MSKEW_Y], 0.001f)
            assertEquals("progress $progress", 0f, values[Matrix.MTRANS_Y], 0.001f)
        }
    }

    @Test
    fun folderExitKeepsThePlatformDirectionOnBothEdges() {
        // The gesture owns the direction: the pre-commit phase translates the preview to
        // the edge the user dragged from. The post-commit target is always the release
        // position moved further along +X, for both edges, so the resource must contain a
        // single positive target and must not invert itself.
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        val distance = application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)
        assertEquals(96f * application.resources.displayMetrics.density, distance, 0.01f)
        assertEquals(distance, translationX(transformation(animation, 1f).matrix), 0.01f)
        // A single translate child, so there is no second, opposing target to invert.
        assertEquals(1, (animation as AnimationSet).animations
            .count { it is android.view.animation.TranslateAnimation })
    }

    @Test
    fun folderInterpolatorsAreLegalMonotonicPaths() {
        listOf(
            R.interpolator.oxygen_classic_folder_back_alpha,
            R.interpolator.oxygen_classic_folder_back_slide,
        ).forEach { resource ->
            val interpolator = AnimationUtils.loadInterpolator(application, resource)
            assertNotNull(interpolator)
            assertEquals(0f, interpolator.getInterpolation(0f), 0.001f)
            assertEquals(1f, interpolator.getInterpolation(1f), 0.001f)
            var previous = -1f
            for (step in 0..100) {
                val value = interpolator.getInterpolation(step / 100f)
                assertTrue("must not decrease at $step", value >= previous - 0.0001f)
                assertTrue("must stay in 0..1 at $step", value in -0.0001f..1.0001f)
                previous = value
            }
        }
    }

    @Test
    fun legacyPlatformsKeepTheExistingPageAnimation() {
        // Below Android 14 the app owns the transition. That pair is not part of this
        // change and must stay exactly as it was.
        val values = ContextThemeWrapper(application, R.style.OxygenClassicPageWindow)
            .obtainStyledAttributes(intArrayOf(
                android.R.attr.windowEnterAnimation, android.R.attr.windowExitAnimation))
        val enter = values.getResourceId(0, 0)
        val exit = values.getResourceId(1, 0)
        values.recycle()
        assertEquals(R.anim.oxygen_classic_page_open_enter, enter)
        assertEquals(R.anim.oxygen_classic_page_open_exit, exit)
    }

    private fun closeStyleAttributes(style: Int): Pair<Int, Int> {
        val context = ContextThemeWrapper(application, style)
        val attrs = context.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.activityCloseEnterAnimation,
                android.R.attr.activityCloseExitAnimation,
            )
        )
        return try {
            attrs.getResourceId(0, 0) to attrs.getResourceId(1, 0)
        } finally {
            attrs.recycle()
        }
    }

    private fun load(resource: Int): Animation {
        val animation = AnimationUtils.loadAnimation(application, resource)
        assertNotNull(animation)
        animation.initialize(1080, 1920, 1080, 1920)
        return animation
    }

    /** Mirrors `CustomCrossActivityBackAnimation`'s progress-driven evaluation. */
    private fun transformation(animation: Animation, progress: Float): Transformation {
        val transformation = Transformation()
        transformation.clear()
        Animation::class.java
            .getMethod("getTransformationAt", java.lang.Float.TYPE, Transformation::class.java)
            .invoke(animation, progress, transformation)
        return transformation
    }

    private fun matrixValues(matrix: Matrix): FloatArray = FloatArray(9).also(matrix::getValues)

    private fun translationX(matrix: Matrix): Float = matrixValues(matrix)[Matrix.MTRANS_X]
}
