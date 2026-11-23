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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * The pre-decode Grid thumbnail, the frozen Grid backdrop and the full-size
 * renderer must share one geometry/ownership handoff. These assertions cover the
 * frame-zero contract and the cleanup of the temporary backdrop; pixel-perfect
 * visual acceptance remains a device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerEntryHandoffTest {
    private lateinit var controller: ActivityController<ViewPagerActivity>
    private lateinit var activity: ViewPagerActivity
    private lateinit var session: MediaTransitionSessionCoordinator
    private lateinit var entry: PhotoTransitionCompletionGate
    private val path = "/storage/emulated/0/Gallery/handoff.jpg"

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
        entry = session.entryGate
        set("transitionSession", session)
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
        session.publishEntryBackdrop(session.entry, Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888))
        set("appEntryBitmap", Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888))
        set("appEntryHeader", header)
    }

    @After
    fun tearDown() {
        if (!activity.isFinishing) controller.pause().stop().destroy()
    }

    @Test
    fun backdropIsFrameZeroAndKeepsTheWindowOpaque() {
        invoke("showAppOwnedEntryPreview", entry)
        assertNotNull(get("appEntryBackdropView"))
        assertEquals("frame zero must be the captured Grid, not a transparent window", 1f,
            activity.window.attributes.alpha, 0.001f)

        invoke("tryStartAppOwnedEntryAnimation", entry)
        assertEquals(0f, get("appEntryProgress") as Float, 0.001f)

        // Even at progress 0 the window stays opaque; the backdrop is the grid.
        activity.javaClass.getDeclaredMethod(
            "applyAppEntryProgress", Float::class.javaPrimitiveType,
            GridPhotoEntryPreview::class.java, StaticPhotoEntryView::class.java,
        ).apply { isAccessible = true }.invoke(activity, 0f, null, null)
        assertEquals(1f, activity.window.attributes.alpha, 0.001f)
    }

    @Test
    fun backdropIsRemovedAndRecycledAfterTheEntry() {
        invoke("showAppOwnedEntryPreview", entry)
        val bitmap = get("appEntryBackdropBitmap") as Bitmap
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        activity.javaClass.getDeclaredMethod("removeAppEntryBackdrop")
            .apply { isAccessible = true }.invoke(activity)
        assertNull(get("appEntryBackdropView"))
        assertTrue(bitmap.isRecycled)
    }

    private fun invoke(name: String, argument: Any?) {
        activity.javaClass.getDeclaredMethod(name, PhotoTransitionCompletionGate::class.java)
            .apply { isAccessible = true }
            .invoke(activity, argument)
    }

    private fun get(name: String): Any? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
