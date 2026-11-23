package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import org.fossify.gallery.R
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/**
 * The target-missing fallback must be one continuous visible exit:
 * current frame -> single window fade -> Grid. The old code faded the photo,
 * then installed a second pending window fade and left a black canvas between
 * the two. This drives the production animator and asserts the terminal state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerSingleExitAnimatorTest {
    private lateinit var controller: ActivityController<ViewPagerActivity>
    private lateinit var activity: ViewPagerActivity
    private lateinit var session: MediaTransitionSessionCoordinator
    private lateinit var surface: ImageView
    private val events = mutableListOf<String>()
    private val path = "/storage/emulated/0/Gallery/fallback.jpg"

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.config.oxygenThemeActivated = true
        app.config.oxygenAppearanceMode = "light"
        val shadows = Shadows.shadowOf(app)
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ).forEach(shadows::grantPermissions)
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val intent = Intent(app, ViewPagerActivity::class.java)
            .putExtra(PhotoViewerTransition.EXTRA_APP_OWNED_ENTRY, true)
        controller = Robolectric.buildActivity(ViewPagerActivity::class.java, intent).setup()
        activity = controller.get()

        session = MediaTransitionSessionCoordinator { event, _, detail -> events += "$event:$detail" }
        set("transitionSession", session)
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        assertNotNull(session.beginGesture())
        assertNotNull(session.beginReturn())

        val root = activity.findViewById<RelativeLayout>(R.id.fragment_holder)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        surface = ImageView(activity).apply {
            setImageDrawable(ColorDrawable(Color.BLACK))
            layout(0, 0, 1080, 1920)
        }
        root.addView(surface, RelativeLayout.LayoutParams(1080, 1920))
        set("interactiveDismissSurface", surface)
        set("interactiveDismissPath", path)
        set("appReturnGeneration", 1L)
    }

    @After
    fun tearDown() {
        if (!activity.isFinishing) controller.pause().stop().destroy()
    }

    @Test
    fun fallbackFadesTheWindowOnceAndFinishesWithoutASecondTransition() {
        val operation = requireNotNull(session.returnOperation)
        val startAlpha = activity.window.attributes.alpha
        invokeFallback(operation, 1L)
        assertFalse(activity.isFinishing)
        assertNotNull(get("appReturnAnimator"))
        assertEquals(startAlpha, activity.window.attributes.alpha, 0.001f)

        // Drive the single window fade. The photo surface itself must not run a
        // separate alpha fade under it, otherwise the exit has two stages.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
        assertEquals("the image layer must stay owned by the window fade", 1f, surface.alpha, 0.001f)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertTrue(activity.isFinishing)
        assertEquals(0f, activity.window.attributes.alpha, 0.001f)
        assertEquals(State.FALLBACK, session.outcome)
        assertEquals(1, events.count { it.startsWith("transition_terminal") })
        assertEquals("the fallback must not add a pending second exit fade", 0,
            Shadows.shadowOf(activity).pendingTransitionExitAnimationResourceId)
    }

    private fun invokeFallback(operation: MediaTransitionSessionCoordinator.Token, generation: Long) {
        activity.javaClass.getDeclaredMethod(
            "startAppReturnFadeFallback",
            MediaTransitionSessionCoordinator.Token::class.java,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
        ).apply { isAccessible = true }.invoke(activity, operation, path, "test", generation)
    }

    private fun get(name: String): Any? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
