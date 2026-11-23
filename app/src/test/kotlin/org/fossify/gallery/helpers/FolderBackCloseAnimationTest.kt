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
 * base rect and freezes the closing rect at the release position, so the CLOSE-exit
 * resources carry the whole visible motion. The shared page pair fades the foreground out
 * after 26% of the 450 ms clock, i.e. after it has covered 26% of the 96dp slide - it
 * dissolves in place instead of leaving. The folder pair keeps the same clock, direction
 * and distance and only retimes the fade against the travel.
 *
 * Assertions evaluate the real resources through `Animation#getTransformationAt`, the exact
 * call the Shell makes; Robolectric cannot composite the Shell surfaces, so the visual
 * result itself still needs device acceptance.
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
        assertEquals(R.anim.oxygen_classic_page_back_enter, style.first)
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
        assertEquals(1f, transformation(load(R.anim.oxygen_classic_page_back_exit), 0f).alpha, 0.001f)
    }

    @Test
    fun folderExitKeepsThePlatformPostCommitClock() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        assertEquals(450L, animation.duration)
        assertEquals(
            application.resources.getInteger(R.integer.oxygen_classic_page_back_duration_ms).toLong(),
            animation.duration,
        )
        // getTransformationAt forwards the same progress to every child, so a shorter child
        // would be evaluated past its end without any clamp.
        (animation as AnimationSet).animations.forEach { child ->
            assertEquals(animation.duration, child.duration)
        }
    }

    @Test
    fun folderExitHoldsTheReleaseFrame() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        // The shared curve already loses 8% of its opacity in the first 9 ms, which reads as
        // a pop on the frame the finger released on.
        assertTrue("alpha at 2% was ${transformation(animation, 0.02f).alpha}", transformation(animation, 0.02f).alpha > 0.98f)
        // ...but it must not linger as a double-exposed copy of the parent either.
        assertTrue("alpha at 20% was ${transformation(animation, 0.2f).alpha}", transformation(animation, 0.2f).alpha < 0.8f)
    }

    @Test
    fun folderExitClearsTheForegroundOnlyAfterItHasVisiblyDeparted() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        val distance = application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)
        var fadeEndProgress = -1f
        var travelAtFadeEnd = -1f
        for (step in 0..100) {
            val progress = step / 100f
            val transform = transformation(animation, progress)
            if (fadeEndProgress < 0 && transform.alpha <= 0.001f) {
                fadeEndProgress = progress
                travelAtFadeEnd = translationX(transform.matrix)
            }
        }
        assertTrue("the foreground never reaches zero", fadeEndProgress > 0f)
        // The measured defect: the shared pair is invisible after 26% of the travel.
        assertTrue("fade ended at $fadeEndProgress with travel ${travelAtFadeEnd / distance}", travelAtFadeEnd / distance >= 0.7f)
        // Fade and geometry stay separate owners: the fade must not end late enough to keep
        // two different grids superimposed (the double-exposed text ghost).
        assertTrue("fade ended at $fadeEndProgress", fadeEndProgress <= 0.4f)
    }

    @Test
    fun folderExitKeepsTravelingAfterTheForegroundIsGone() {
        val animation = load(R.anim.oxygen_classic_folder_back_exit)
        val distance = application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)
        var previousX = -1f
        var previousAlpha = Float.MAX_VALUE
        for (step in 0..100) {
            val transform = transformation(animation, step / 100f)
            val x = translationX(transform.matrix)
            assertTrue("alpha must not increase at $step", transform.alpha <= previousAlpha + 0.0001f)
            assertTrue("the page must keep moving at $step", x >= previousX - 0.001f)
            previousX = x
            previousAlpha = transform.alpha
        }
        // The window is removed when the 450 ms clock expires, at the end of the travel.
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

    /** Mirrors `CustomCrossActivityBackAnimation`'s unclamped progress-driven evaluation. */
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
