package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.util.TypedValue
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class OxygenClassicActivityMotionTest {
    @Test fun photoAndAlbumSharedElementsUseOneMaterialTimingSystem() {
        assertEquals(OxygenClassicMotion.MEDIA_SHARED_ELEMENT_MS, PhotoViewerTransition.DURATION_MS)
        assertEquals(PhotoViewerTransition.DURATION_MS, AlbumPageTransition.DURATION_MS)
        assertEquals(OxygenClassicMotion.MEDIA_SCRIM_FADE_MS, PhotoViewerTransition.FADE_DURATION_MS)
        assertEquals(120L, PhotoViewerTransition.DURATION_MS)
    }

    @Test fun sharedWindowDoesNotInstallASecondPageAnimation() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.intent.putExtra(PhotoViewerTransition.EXTRA_PATH, "/test/video.mp4")
        OxygenClassicActivityMotion.install(activity)
        assertEquals(0, activity.window.attributes.windowAnimations)
        assertEquals(0L, activity.window.transitionBackgroundFadeDuration)
        assertEquals(false, activity.window.sharedElementsUseOverlay)
        assertEquals(null, activity.window.enterTransition)
        assertEquals(null, activity.window.returnTransition)
        activity.finish()
    }

    @Test fun activityWindowReceivesTheSharedDirectionalTransitionStyle() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        OxygenClassicActivityMotion.install(activity)
        assertEquals(R.style.OxygenClassicPageWindow, activity.window.attributes.windowAnimations)
        assertEquals(170, activity.resources.getInteger(R.integer.oxygen_classic_page_enter_duration_ms))
        assertEquals(140, activity.resources.getInteger(R.integer.oxygen_classic_page_exit_duration_ms))
        activity.finish()
    }

    @Test
    @Config(sdk = [36])
    fun android16OrdinaryPageSelectsThePlatformCustomCloseBranch() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        OxygenClassicActivityMotion.install(activity)
        // WindowManager reads a non-zero windowAnimations style from the closing
        // window and only then offers a CustomAnimationInfo to the Shell. A zero
        // animation (the previous contract) made the Shell fall back to the default
        // branch, which also scales and offsets the parent page.
        assertEquals(R.style.OxygenClassicPageBackWindow, activity.window.attributes.windowAnimations)
        activity.finish()
    }

    @Test
    fun backWindowStyleDeclaresBothLoadableCloseAnimations() {
        val context = RuntimeEnvironment.getApplication()
        val theme = context.resources.newTheme().apply { applyStyle(R.style.OxygenClassicPageBackWindow, true) }
        val values = theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.activityCloseEnterAnimation, android.R.attr.activityCloseExitAnimation)
        )
        val enter = values.getResourceId(0, 0)
        val exit = values.getResourceId(1, 0)
        values.recycle()
        // CustomAnimationLoader.loadAll returns null unless the CLOSE animation
        // loads, and the server only forwards windowAnimations when the CLOSE exit
        // differs from the framework default. Both ids therefore have to exist.
        assertEquals(R.anim.oxygen_classic_page_back_enter, enter)
        assertEquals(R.anim.oxygen_classic_page_back_exit, exit)
    }

    @Test
    fun viewerWindowIsOpaqueSoThePlatformCanAnimateTheReturn() {
        val context = RuntimeEnvironment.getApplication()
        val theme = context.resources.newTheme().apply { applyStyle(R.style.GalleryViewerTransitionTheme, true) }
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowIsTranslucent, value, true))
        assertEquals("a translucent Viewer is forced to TYPE_CALLBACK", 0, value.data)
    }

    @Test fun folderPageEntryUsesShortLayeredMotionWithoutAFullBlackStart() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val motion = AnimationUtils.loadAnimation(activity, R.anim.oxygen_classic_page_open_enter)
            as AnimationSet
        val layers = motion.animations
        assertEquals(3, layers.size)
        assertTrue(layers.any { it is AlphaAnimation })
        assertTrue(layers.any { it is ScaleAnimation })
        assertTrue(layers.any { it is TranslateAnimation })
        assertTrue(layers.all { it.duration == 170L })
        activity.finish()
    }
}
