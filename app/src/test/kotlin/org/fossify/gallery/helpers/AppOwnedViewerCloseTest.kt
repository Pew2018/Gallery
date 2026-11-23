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
import org.fossify.gallery.models.Medium
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
 * Drives the production Activity's released-drag path with a real shared surface
 * and a real transition session. The test does not render pixels; it proves the
 * close no longer collapses to `finish()`/`system_return_no_app_visual` and that
 * the app-owned return owns the session until its own terminal state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppOwnedViewerCloseTest {
    private lateinit var controller: ActivityController<ViewPagerActivity>
    private lateinit var activity: ViewPagerActivity
    private lateinit var session: MediaTransitionSessionCoordinator
    private val events = mutableListOf<String>()
    private val path = "/storage/emulated/0/Gallery/close.jpg"

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

        val mediaPath = path
        set("mMediaFiles", arrayListOf(Medium().apply {
            this.path = mediaPath
            name = "close.jpg"
            type = TYPE_IMAGES
        }))
        set("mPos", 0)
        set("mPath", path)
        session = MediaTransitionSessionCoordinator { event, _, detail -> events += "$event:$detail" }
        set("transitionSession", session)
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val operation = session.beginGesture()
        assertNotNull(operation)

        val root = activity.findViewById<RelativeLayout>(R.id.fragment_holder)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        val surface = object : ImageView(activity) {
            override fun isAttachedToWindow(): Boolean = true
        }.apply {
            setImageDrawable(ColorDrawable(Color.BLACK))
            layout(0, 0, 1080, 1920)
        }
        root.addView(surface, RelativeLayout.LayoutParams(1080, 1920))
        surface.layout(0, 0, 1080, 1920)
        set("interactiveDismissSurface", surface)
        set("interactiveDismissPath", path)
        val page = currentFragment()
        set("interactiveDismissPage", page)
        set("interactiveDismissDragActive", true)
        assertEquals(path, currentPath())
        assertEquals(page, currentFragment())
    }

    @After
    fun tearDown() {
        if (!activity.isFinishing) controller.pause().stop().destroy()
    }

    @Test
    fun releasedDragContinuesThroughTheAppReturnInsteadOfAWindowFinish() {
        activity.mediaDismissSettled(true)

        assertEquals(events.toString(), State.RETURN_PREPARING, session.state)
        assertFalse("the window must not close before the image return runs", activity.isFinishing)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertTrue(activity.isFinishing)
        assertTrue("expected an app return terminal, got $events",
            events.any { it.contains("FALLBACK:app_return") || it.contains("COMPLETE:app_return") })
        assertFalse("the released drag must not take the system no-visual branch",
            events.any { it.contains("system_return_no_app_visual") })
    }

    private fun call(name: String): Any? = activity.javaClass
        .getDeclaredMethod(name)
        .apply { isAccessible = true }
        .invoke(activity)

    private fun currentPath(): Any? = activity.javaClass
        .getDeclaredMethod("getCurrentPath")
        .apply { isAccessible = true }
        .invoke(activity)

    private fun currentFragment(): Any? = activity.javaClass
        .getDeclaredMethod("getCurrentFragment")
        .apply { isAccessible = true }
        .invoke(activity)

    private fun get(name: String): Any? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
