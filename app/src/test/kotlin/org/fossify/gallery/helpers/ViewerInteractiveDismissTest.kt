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
 * The released-drag envelope must start from the live finger frame and a
 * sub-threshold release must return to full screen from that same frame, not
 * from a default full-screen pose. It also asserts the settle animation cannot
 * leave the surface half-scaled after the gesture is cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerInteractiveDismissTest {
    private lateinit var controller: ActivityController<ViewPagerActivity>
    private lateinit var activity: ViewPagerActivity
    private lateinit var session: MediaTransitionSessionCoordinator
    private lateinit var surface: ImageView

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

        session = MediaTransitionSessionCoordinator()
        set("transitionSession", session)
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        assertNotNull(session.beginGesture())

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
        set("interactiveDismissPath", "/storage/emulated/0/Gallery/drag.jpg")
        set("interactiveDismissDragActive", true)
        set("interactiveDismissDeltaXSlope", 120f)
        set("interactiveDismissDeltaYSlope", 480f)
    }

    @After
    fun tearDown() {
        if (!activity.isFinishing) controller.pause().stop().destroy()
    }

    @Test
    fun releasedSubThresholdDragReboundsFromTheLiveFrame() {
        invokeRender(0.5f)
        assertEquals(State.INTERACTIVE_RETURN, session.state)
        assertEquals(0.83f, surface.scaleX, 0.001f)
        assertEquals(60f, surface.translationX, 0.001f)
        assertEquals(240f, surface.translationY, 0.001f)

        activity.mediaDismissSettled(false)
        // The rebound animator is driven by the production progress renderer.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(State.VIEWING, session.state)
        assertEquals(1f, surface.scaleX, 0.001f)
        assertEquals(1f, surface.scaleY, 0.001f)
        assertEquals(0f, surface.translationX, 0.001f)
        assertEquals(0f, surface.translationY, 0.001f)
        assertEquals(0f, (get("interactiveDismissProgress") as Float), 0.001f)
    }

    private fun invokeRender(progress: Float) {
        activity.javaClass.getDeclaredMethod("renderInteractiveDismissProgress", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(activity, progress)
    }

    private fun get(name: String): Any? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
