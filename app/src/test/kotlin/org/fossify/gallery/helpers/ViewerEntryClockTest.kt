package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.RelativeLayout
import org.fossify.gallery.R
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.extensions.config
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
 * The app-owned entry must be one clock. This drives the production Activity's
 * entry animation with a published thumbnail/source and asserts that the window
 * alpha equals the same progress that moves the preview. It does not render or
 * validate the final visual; device acceptance remains required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerEntryClockTest {
    private lateinit var controller: ActivityController<ViewPagerActivity>
    private lateinit var activity: ViewPagerActivity
    private lateinit var session: MediaTransitionSessionCoordinator
    private lateinit var entry: PhotoTransitionCompletionGate
    private val path = "/storage/emulated/0/Gallery/entry.jpg"

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
        // The Activity's own session finishes its entry on create because this
        // fixture has no staged path. Install a PREPARING session so the real
        // entry methods can be driven with a published source.
        session = MediaTransitionSessionCoordinator()
        set("transitionSession", session)
        entry = session.entryGate
        set("mediaEntry", entry)

        val root = activity.findViewById<RelativeLayout>(R.id.fragment_holder)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        activity.findViewById<View>(R.id.view_pager).layout(0, 0, 1080, 1920)

        val header = StaticPhotoHeader(400, 800, 1, jpeg = true)
        val thumbnail = StaticPhotoThumbnail(200, 200, 200, 200, cropped = true, radius = 0)
        val window = PhotoWindowGeometry(PhotoBox(0f, 0f, 1080f, 1920f), emptyList())
        val source = StaticPhotoSource(
            path, "version", 1, thumbnail,
            PhotoViewGeometry(PhotoBox(0f, 0f, 200f, 200f), PhotoBox(0f, 0f, 200f, 200f),
                PhotoBox(0f, 0f, 200f, 200f), window),
        )
        session.publishStaticPhotoSource(session.entry, source)
        session.publishEntryPreview(session.entry, Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888))
        set("appEntryBitmap", Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888))
        set("appEntryHeader", header)
        invoke("tryStartAppOwnedEntryAnimation", entry)
    }

    @After
    fun tearDown() {
        if (!activity.isFinishing) controller.pause().stop().destroy()
    }

    @Test
    fun windowAlphaAndPreviewShareOneEntryProgress() {
        assertTrue(get("appEntryAnimatorStarted") as Boolean)
        assertNotNull(get("appEntryPreviewSurface"))
        assertEquals(0f, (get("appEntryProgress") as Float), 0.001f)
        assertEquals(0f, activity.window.attributes.alpha, 0.001f)

        // The old entry faded the whole window on its own clock. At this point the
        // photo clock has started and the window must still be at its start value,
        // not already fully visible over a stationary thumbnail.
        assertEquals(0f, activity.window.attributes.alpha, 0.001f)
        assertEquals(0f, (get("appEntryProgress") as Float), 0.001f)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertTrue(get("appEntryAnimatorEnded") as Boolean)
        assertEquals(1f, activity.window.attributes.alpha, 0.001f)
        assertEquals(1f, (get("appEntryProgress") as Float), 0.001f)
        assertFalse(activity.isFinishing)
    }

    private fun get(name: String): Any? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun invoke(name: String, argument: Any?) {
        activity.javaClass.getDeclaredMethod(name, PhotoTransitionCompletionGate::class.java)
            .apply { isAccessible = true }
            .invoke(activity, argument)
    }
}
