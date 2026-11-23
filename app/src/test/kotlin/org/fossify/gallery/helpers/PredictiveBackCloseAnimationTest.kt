package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Matrix
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.appcompat.view.ContextThemeWrapper
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Contract of the platform's custom (CLOSE) cross-Activity back branch.
 *
 * Android 14+ only offers that branch when `activityCloseExitAnimation` is non-zero
 * (`BackNavigationController.isCustomizeExitAnimation`). Once selected,
 * `CustomCrossActivityBackAnimation` takes over:
 *
 *  - the entering parent keeps a full-screen base rect and receives the CLOSE-enter
 *    resource during the gesture via
 *    `getPreCommitEnteringBaseTransformation(progress * PRE_COMMIT_MAX_PROGRESS)`;
 *  - the closing page's rect is frozen at the release position, so the CLOSE-exit
 *    resource is the only thing that can finish the exit;
 *  - `getPostCommitAnimationDuration()` is `min(2000, max(close, enter))`, derived from
 *    these same resources;
 *  - both resources are driven through `AnimationSet#getTransformationAt(progress)`,
 *    which forwards an unclamped 0..1 progress to every child without renormalising by
 *    child duration.
 *
 * The assertions below evaluate the resources through that exact call rather than
 * matching XML text. Robolectric cannot composite the Shell's surfaces, so this pins
 * the animation contract, not the on-device visual result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PredictiveBackCloseAnimationTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun closeStyleStillSelectsTheCustomPlatformBranch() {
        val context = ContextThemeWrapper(application, R.style.OxygenClassicPageBackWindow)
        val attrs = context.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.activityCloseEnterAnimation,
                android.R.attr.activityCloseExitAnimation,
            )
        )
        try {
            assertEquals(R.anim.oxygen_classic_page_back_enter, attrs.getResourceId(0, 0))
            assertEquals(R.anim.oxygen_classic_page_back_exit, attrs.getResourceId(1, 0))
        } finally {
            attrs.recycle()
        }
    }

    @Test
    fun closePairIsLoadableOnTheShellTimeline() {
        // The Shell only uses the pair if the CLOSE animation loads, and its post-commit
        // clock is max(close, enter). Both resources therefore have to exist and share
        // the 450ms reference length.
        listOf(
            R.anim.oxygen_classic_page_back_enter,
            R.anim.oxygen_classic_page_back_exit,
        ).forEach { resource ->
            val animation = load(resource)
            assertNotNull(animation)
            assertEquals(450L, animation.duration)
            (animation as AnimationSet).animations.forEach { child ->
                // getTransformationAt feeds the same progress to every child, so a child
                // shorter than the set would be evaluated past its end (no clamp).
                assertEquals(animation.duration, child.duration)
            }
        }
    }

    @Test
    fun shellPostCommitClockMatchesThePlatformReferenceLength() {
        val close = load(R.anim.oxygen_classic_page_back_exit).duration
        val enter = load(R.anim.oxygen_classic_page_back_enter).duration
        val shellClock = minOf(2000L, maxOf(close, enter))
        // DefaultCrossActivityBackAnimation.POST_COMMIT_DURATION.
        assertEquals(450L, shellClock)
        assertEquals(
            application.resources.getInteger(R.integer.oxygen_classic_page_back_duration_ms).toLong(),
            shellClock,
        )
    }

    @Test
    fun closeEnterKeepsTheRealParentUndisplaced() {
        val animation = load(R.anim.oxygen_classic_page_back_enter)
        // The Shell applies this during the gesture at progress * 0.2. Any alpha or
        // geometry here would dim or shift the real previous page and destroy the
        // full-screen preview the custom branch exists for.
        for (step in 0..20) {
            val transform = shellTransformation(animation, step / 20f)
            assertEquals("progress $step", 1f, transform.alpha, 0.001f)
            assertIdentity(transform.matrix, "progress $step")
        }
    }

    @Test
    fun closeExitFinishesTheFrozenClosingPageInsteadOfLeavingItStill() {
        val animation = load(R.anim.oxygen_classic_page_back_exit)
        val start = shellTransformation(animation, 0f)
        val end = shellTransformation(animation, 1f)

        assertEquals("the release frame must be fully opaque", 1f, start.alpha, 0.001f)
        assertEquals("the end frame must be fully transparent", 0f, end.alpha, 0.001f)

        // A pure +X translation: the Shell composes this matrix under its own scale and
        // crop, so any scale/skew here would double the platform's geometry.
        assertTranslation(start.matrix, 0f)
        assertTranslation(end.matrix, slideDistance())

        var previousAlpha = Float.MAX_VALUE
        var previousX = -1f
        for (step in 0..20) {
            val transform = shellTransformation(animation, step / 20f)
            assertTrue("alpha must not increase at progress $step", transform.alpha <= previousAlpha + 0.0001f)
            val x = translationX(transform.matrix)
            assertTrue("the closing page must keep moving at progress $step", x >= previousX - 0.001f)
            previousAlpha = transform.alpha
            previousX = x
        }
    }

    @Test
    fun closeExitIsNotAnotherIdentityNoOp() {
        // Regression guard: an identity CLOSE-exit froze the closing page until the Shell
        // removed its window, which is the hard pop this resource now prevents.
        val animation = load(R.anim.oxygen_classic_page_back_exit)
        val mid = shellTransformation(animation, 0.5f)
        assertTrue("the exit must actually fade", mid.alpha < 0.99f)
        assertTrue("the exit must actually move", translationX(mid.matrix) > 0.01f)
    }

    private fun slideDistance(): Float =
        application.resources.getDimension(R.dimen.oxygen_classic_page_back_slide_distance)

    private fun load(resource: Int): Animation {
        val animation = AnimationUtils.loadAnimation(application, resource)
        assertNotNull(animation)
        // WindowManager/CustomCrossActivityBackAnimation initialise the animation with
        // the closing target's bounds before evaluating it.
        animation.initialize(1080, 1920, 1080, 1920)
        return animation
    }

    /**
     * Mirrors `CustomCrossActivityBackAnimation`'s call exactly:
     * `animation.getTransformationAt(progress, transformation)` on a `<set>`, which
     * forwards an unclamped progress to each child. The overload is framework-internal,
     * hence reflection; if it disappears this test must fail loudly rather than silently
     * assert a different code path.
     */
    private fun shellTransformation(animation: Animation, progress: Float): Transformation {
        val transformation = Transformation()
        transformation.clear()
        Animation::class.java
            .getMethod(
                "getTransformationAt",
                java.lang.Float.TYPE,
                Transformation::class.java,
            )
            .invoke(animation, progress, transformation)
        return transformation
    }

    private fun matrixValues(matrix: Matrix): FloatArray = FloatArray(9).also(matrix::getValues)

    private fun translationX(matrix: Matrix): Float = matrixValues(matrix)[Matrix.MTRANS_X]

    private fun assertTranslation(matrix: Matrix, expectedX: Float) {
        val values = matrixValues(matrix)
        assertEquals("no horizontal scale", 1f, values[Matrix.MSCALE_X], 0.001f)
        assertEquals("no vertical scale", 1f, values[Matrix.MSCALE_Y], 0.001f)
        assertEquals("no skew", 0f, values[Matrix.MSKEW_X], 0.001f)
        assertEquals("no skew", 0f, values[Matrix.MSKEW_Y], 0.001f)
        assertEquals("horizontal offset", expectedX, values[Matrix.MTRANS_X], 0.5f)
        assertEquals("no vertical offset", 0f, values[Matrix.MTRANS_Y], 0.5f)
    }

    private fun assertIdentity(matrix: Matrix, label: String) {
        val values = matrixValues(matrix)
        assertEquals(label, 1f, values[Matrix.MSCALE_X], 0.001f)
        assertEquals(label, 1f, values[Matrix.MSCALE_Y], 0.001f)
        assertEquals(label, 0f, values[Matrix.MSKEW_X], 0.001f)
        assertEquals(label, 0f, values[Matrix.MSKEW_Y], 0.001f)
        assertEquals(label, 0f, values[Matrix.MTRANS_X], 0.001f)
        assertEquals(label, 0f, values[Matrix.MTRANS_Y], 0.001f)
    }
}
