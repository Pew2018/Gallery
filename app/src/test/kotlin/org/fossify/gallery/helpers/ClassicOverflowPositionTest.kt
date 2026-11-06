package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import kotlin.math.abs
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Inspect real PopupWindow coordinates, including the native Toolbar overflow presenter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicOverflowPositionTest {
    @Test
    @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun toolbarAndAnchoredMenusMatchClassicGeometryInLtr() = checkPositions(false, 360)

    @Test
    @Config(qualifiers = "ar-rSA-ldrtl-w360dp-h800dp-mdpi")
    fun toolbarAndAnchoredMenusMatchClassicGeometryInRtl() = checkPositions(true, 360)

    @Test @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun narrowWindowMenusStayInsideBoundsInLtr() = checkPositions(false, 320)

    @Test @Config(qualifiers = "ar-rSA-ldrtl-w320dp-h800dp-mdpi")
    fun narrowWindowMenusStayInsideBoundsInRtl() = checkPositions(true, 320)

    @Test @Config(qualifiers = "w412dp-h900dp-mdpi")
    fun widePhoneMenusKeepTheSameMinimumInLtr() = checkPositions(false, 412)

    @Test @Config(qualifiers = "ar-rSA-ldrtl-w412dp-h900dp-mdpi")
    fun widePhoneMenusKeepTheSameMinimumInRtl() = checkPositions(true, 412)

    @Test @Config(qualifiers = "w600dp-h900dp-mdpi")
    fun tabletMenusStayContentBoundedInLtr() = checkPositions(false, 600)

    @Test @Config(qualifiers = "ar-rSA-ldrtl-w600dp-h900dp-mdpi")
    fun tabletMenusStayContentBoundedInRtl() = checkPositions(true, 600)

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()

    private fun checkPositions(rtl: Boolean, widthDp: Int) {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.config.oxygenThemeActivated = true
        activity.config.oxygenAppearanceMode = "light"
        assertEquals("Fixture resources must match the view's layout direction",
            if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
            activity.resources.configuration.layoutDirection)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val output = File("build/reports/photo-source-previews").apply { mkdirs() }
        try {
            for (nativeToolbar in listOf(true, false)) for (count in listOf(3, 20)) {
                val host = FrameLayout(activity).apply {
                    layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    setBackgroundColor(Color.rgb(250, 250, 250))
                }
                val toolbar = MaterialToolbar(activity).apply {
                    title = "Gallery"
                    setTitleTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(85, 124, 36))
                }
                host.addView(toolbar, FrameLayout.LayoutParams(-1, dp(56)))
                val button = ImageView(activity).apply {
                    setImageResource(org.fossify.commons.R.drawable.ic_three_dots_vector)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }
                if (!nativeToolbar) host.addView(button,
                    FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.TOP).apply {
                        topMargin = dp(4)
                    })
                activity.setContentView(host)
                OxygenToolbarStyle.preparePopup(activity, toolbar)
                toolbar.overflowIcon?.mutate()?.setTint(Color.WHITE)
                val popup = if (nativeToolbar) null else OxygenClassicMenus.createPopup(activity, button)
                val menu = popup?.menu ?: toolbar.menu
                val labels = listOf("分享", "发送到桌面", "取消关注")
                repeat(count) { index ->
                    menu.add(0, index + 1, index, if (count == 3) labels[index] else "Menu item ${index + 1}")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                }
                host.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(800), View.MeasureSpec.EXACTLY))
                host.layout(0, 0, host.measuredWidth, host.measuredHeight)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                val anchor = if (nativeToolbar) descendants(toolbar).single {
                    it.javaClass.simpleName == "OverflowMenuButton"
                } else button
                if (nativeToolbar) assertTrue(toolbar.showOverflowMenu()) else popup!!.show()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                val window = Shadows.shadowOf(activity.application).latestPopupWindow!!
                try {
                    assertTrue(window.isShowing)
                    val decor = window.contentView.rootView
                    val params = decor.layoutParams as WindowManager.LayoutParams
                    val location = IntArray(2).also { anchor.getLocationOnScreen(it) }
                    val centerX = location[0] + anchor.width / 2
                    val toolbarLocation = IntArray(2).also { toolbar.getLocationOnScreen(it) }
                    val toolbarBottom = toolbarLocation[1] + toolbar.height
                    val panelEnd = if (rtl) params.x else params.x + window.width
                    val label = "${if (nativeToolbar) "toolbar" else "anchored"} rtl=$rtl count=$count"
                    val offset = activity.resources.getDimensionPixelOffset(
                        org.fossify.gallery.R.dimen.oxygen_classic_overflow_horizontal_offset)
                    assertTrue("$label: menu edge $panelEnd is detached from glyph $centerX (offset=$offset)",
                        abs(panelEnd - centerX) <= dp(8))
                    val visible = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
                    assertTrue("$label: popup escapes leading screen edge", params.x >= visible.left)
                    assertTrue("$label: popup escapes trailing screen edge",
                        params.x + window.width <= visible.right)
                    assertTrue("$label: popup is narrower than the OxygenOS Classic reference",
                        window.width >= dp(188))
                    assertTrue("$label: popup exceeds its intrinsic width cap",
                        window.width <= dp(280))
                    assertTrue("$label: popup overlaps Toolbar (popupY=${params.y}, toolbarBottom=$toolbarBottom)",
                        params.y >= toolbarBottom - dp(8))
                    assertTrue("$label: popup is detached too far below Toolbar (popupY=${params.y}, toolbarBottom=$toolbarBottom)",
                        params.y <= toolbarBottom + dp(16))
                    assertTrue("$label: popup escapes the top of the visible window", params.y >= visible.top)
                    assertTrue("$label: popup escapes the bottom of the visible window",
                        params.y + window.height <= visible.bottom)
                    if (count == 3) {
                        decor.measure(View.MeasureSpec.makeMeasureSpec(window.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(window.height, View.MeasureSpec.EXACTLY))
                        decor.layout(0, 0, window.width, window.height)
                        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        host.draw(canvas)
                        val hostLocation = IntArray(2).also { host.getLocationOnScreen(it) }
                        canvas.translate((params.x - hostLocation[0]).toFloat(),
                            (params.y - hostLocation[1]).toFloat())
                        decor.draw(canvas)
                        File(output, "overflow-position-${if (nativeToolbar) "toolbar" else "anchored"}-${if (rtl) "rtl" else "ltr"}.png")
                            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                } finally {
                    if (nativeToolbar) toolbar.dismissPopupMenus() else popup!!.dismiss()
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
