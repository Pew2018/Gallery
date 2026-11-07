package org.fossify.gallery.helpers

import android.app.Application
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class OxygenClassicPopupMotionTest {
    @Test fun popupThemesResolveTheSharedWindowAnimationStyle() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        listOf(R.style.OxygenClassicPopupTheme_Light, R.style.OxygenClassicPopupTheme_Dark).forEach { themeId ->
            val popupContext = ContextThemeWrapper(activity, themeId)
            val popupWindow = TypedValue()
            assertTrue(popupContext.theme.resolveAttribute(
                androidx.appcompat.R.attr.popupWindowStyle, popupWindow, true
            ))
            assertEquals(R.style.OxygenClassicPopupWindow, popupWindow.resourceId)
            val popupAttrs = popupContext.obtainStyledAttributes(
                popupWindow.resourceId, intArrayOf(android.R.attr.popupAnimationStyle)
            )
            try {
                assertEquals(
                    R.style.OxygenClassicPopupWindowAnimation,
                    popupAttrs.getResourceId(0, 0)
                )
            } finally {
                popupAttrs.recycle()
            }
        }
        activity.finish()
    }
}
