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

/**
 * Destroying the Viewer mid-exit must release every owned lease and leave the
 * one session terminal/cleaned. A later visit must not inherit the stale input
 * lock, overlay or session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerTransitionCleanupTest {
    private lateinit var controller: ActivityController<ViewPagerActivity>
    private lateinit var activity: ViewPagerActivity
    private lateinit var session: MediaTransitionSessionCoordinator

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
        session.entryFinished(session.entry, withoutSharedEntry = true)
        assertNotNull(session.beginGesture())

        val root = activity.findViewById<RelativeLayout>(R.id.fragment_holder)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 1920)
        val surface = ImageView(activity).apply {
            setImageDrawable(ColorDrawable(Color.BLACK))
            layout(0, 0, 1080, 1920)
        }
        root.addView(surface, RelativeLayout.LayoutParams(1080, 1920))
        set("interactiveDismissSurface", surface)
        set("interactiveDismissDragActive", true)
    }

    @After
    fun tearDown() {
        if (!activity.isDestroyed) controller.pause().stop().destroy()
    }

    @Test
    fun destroyDuringAnOwnedGestureEndsOneSessionAndReleasesTheInputLock() {
        assertNotNull(session.returnOperation)
        controller.pause().stop().destroy()
        assertEquals(State.CLEANED, session.state)
        assertNotNull("destroy must produce one terminal outcome", session.outcome)
        assertFalse(session.hasLeases())
        // The Activity's input lock is derived from the session; after cleanup a
        // new visit must be able to install input again.
        assertFalse(session.state == State.INTERACTIVE_RETURN)
    }

    private fun set(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
