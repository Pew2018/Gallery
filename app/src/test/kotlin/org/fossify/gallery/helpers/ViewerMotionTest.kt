package org.fossify.gallery.helpers

import android.app.Application
import android.app.Activity
import android.widget.FrameLayout
import android.os.Looper
import android.provider.Settings
import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ViewerMotionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var controller: ActivityController<Activity>
    private lateinit var content: FrameLayout
    @Before fun animationsOn() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        content = FrameLayout(controller.get())
        controller.get().setContentView(content)
    }
    @After fun destroyHost() { controller.pause().stop().destroy() }
    private fun advance(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun indicator() = View(controller.get()).apply {
        visibility = View.GONE
        content.addView(this, FrameLayout.LayoutParams(300, 3))
    }

    @Test fun fastCompletionNeverShowsLoading() {
        val view = indicator()
        val motion = ViewerLoadingMotion(view)
        motion.setLoading(true)
        advance(80)
        motion.setLoading(false)
        advance(500)
        assertEquals(View.GONE, view.visibility)
    }

    @Test fun repeatedRequestsKeepOriginalShowDeadlineAndNewRequestCancelsHide() {
        val view = indicator()
        val motion = ViewerLoadingMotion(view)
        motion.setLoading(true)
        advance(80)
        motion.setLoading(true)
        advance(50)
        assertEquals(View.VISIBLE, view.visibility)
        motion.setLoading(false)
        advance(30)
        motion.setLoading(true)
        advance(500)
        assertEquals(View.VISIBLE, view.visibility)
        motion.setLoading(false)
        advance(500)
        assertEquals(View.GONE, view.visibility)
    }

    @Test fun shownLoadingHasMinimumVisibleTimeAndDisposeCancelsPendingShow() {
        val view = indicator()
        // Keep the visibility policy clock independent of Choreographer's
        // frame clock. The actual View callbacks and cancellation still run.
        var clock = 1_000L
        val motion = ViewerLoadingMotion(view) { clock }
        motion.setLoading(true)
        advance(200)
        assertEquals(View.VISIBLE, view.visibility)
        clock += 10L
        motion.setLoading(false)
        advance(100)
        assertEquals(View.VISIBLE, view.visibility)
        motion.dispose()
        advance(500)
        assertEquals(View.GONE, view.visibility)
        val second = ViewerLoadingMotion(view)
        second.setLoading(true)
        second.dispose()
        advance(500)
        assertEquals(View.GONE, view.visibility)
    }

    @Test fun disabledAnimationsApplyVisibilityImmediately() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val view = indicator()
        val motion = ViewerLoadingMotion(view)
        motion.setLoading(true)
        assertEquals(View.VISIBLE, view.visibility)
        motion.setLoading(false)
        assertEquals(View.GONE, view.visibility)
        ViewerChromeMotion.show(view, false)
        assertEquals(View.INVISIBLE, view.visibility)
        ViewerChromeMotion.show(view, true)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha, 0.001f)
    }

    @Test fun rapidChromeTogglesCannotFinishWithAnOldHiddenState() {
        val view = indicator().apply { visibility = View.VISIBLE }
        ViewerChromeMotion.show(view, false)
        advance(70)
        ViewerChromeMotion.show(view, true)
        advance(70)
        ViewerChromeMotion.show(view, false)
        advance(70)
        ViewerChromeMotion.show(view, true)
        advance(500)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha, 0.001f)
    }
}
