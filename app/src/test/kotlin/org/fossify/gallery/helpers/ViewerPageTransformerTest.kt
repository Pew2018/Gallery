package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ViewerPageTransformerTest {
    @Test fun leavingFadeSlideshowRestoresNormalSlidePageProperties() {
        val page = View(RuntimeEnvironment.getApplication())
        page.layout(0, 0, 400, 600)
        FadePageTransformer().transformPage(page, 0.5f)
        assertEquals(0.5f, page.alpha, 0.001f)
        DefaultPageTransformer().transformPage(page, 0.5f)
        assertEquals(1f, page.alpha, 0.001f)
        assertEquals(0f, page.translationX, 0.001f)
    }

    @Test fun resetWorksForOffscreenPagesToo() {
        val page = View(RuntimeEnvironment.getApplication())
        page.alpha = 0f
        page.translationX = -300f
        DefaultPageTransformer().transformPage(page, -1f)
        assertEquals(1f, page.alpha, 0.001f)
        assertEquals(0f, page.translationX, 0.001f)
    }
}
