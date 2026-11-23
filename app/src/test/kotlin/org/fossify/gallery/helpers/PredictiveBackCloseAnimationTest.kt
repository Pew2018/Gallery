package org.fossify.gallery.helpers

import android.app.Application
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.appcompat.view.ContextThemeWrapper
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Android 14+ selects the Shell's custom CLOSE branch only when
 * `activityCloseExitAnimation` is non-zero. That branch owns the directional
 * whole-window geometry; our resources must stay loadable but must not add a
 * second alpha/transform stage on top of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PredictiveBackCloseAnimationTest {
    @Test
    fun closeStyleStillSelectsTheCustomPlatformBranch() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.OxygenClassicPageBackWindow)
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
    fun closePairIsLoadableAndAddsNoSecondVisualStage() {
        val context = RuntimeEnvironment.getApplication()
        listOf(
            R.anim.oxygen_classic_page_back_enter,
            R.anim.oxygen_classic_page_back_exit,
        ).forEach { resource ->
            val animation = AnimationUtils.loadAnimation(context, resource)
            assertNotNull(animation)
            // The platform loads these at gesture time and calls getTransformation.
            animation.initialize(1080, 1920, 1080, 1920)
            val start = transformationAt(animation, 0L)
            val end = transformationAt(animation, animation.duration)
            assertEquals("start alpha must not fade the real parent", 1f, start.alpha, 0.001f)
            assertEquals("end alpha must not add a second exit fade", 1f, end.alpha, 0.001f)

            // No app-authored translation/scale may double the Shell's edge movement.
            listOf(start, end).forEach { transform ->
                val values = FloatArray(9)
                transform.matrix.getValues(values)
                assertEquals(1f, values[android.graphics.Matrix.MSCALE_X], 0.001f)
                assertEquals(1f, values[android.graphics.Matrix.MSCALE_Y], 0.001f)
                assertEquals(0f, values[android.graphics.Matrix.MTRANS_X], 0.001f)
                assertEquals(0f, values[android.graphics.Matrix.MTRANS_Y], 0.001f)
            }
        }
    }

    private fun transformationAt(animation: Animation, time: Long): Transformation {
        val transformation = Transformation()
        animation.getTransformation(time, transformation)
        return transformation
    }
}
