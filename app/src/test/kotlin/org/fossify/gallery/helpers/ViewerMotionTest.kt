package org.fossify.gallery.helpers

import android.app.Application
import android.os.Looper
import android.provider.Settings
import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    @Before fun animationsOn() { Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
    private fun advance(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun indicator() = View(app).apply { visibility = View.GONE }

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
        val motion = ViewerLoadingMotion(view)
        motion.setLoading(true)
        advance(130)
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
        assertEquals(1f, view.alpha)
    }

    @Test fun rapidChromeTogglesCannotFinishWithAnOldHiddenState() {
        val view = View(app)
        ViewerChromeMotion.show(view, false)
        advance(70)
        ViewerChromeMotion.show(view, true)
        advance(70)
        ViewerChromeMotion.show(view, false)
        advance(70)
        ViewerChromeMotion.show(view, true)
        advance(500)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha)
    }
}
