package org.fossify.gallery.helpers

import android.app.Application
import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.views.TiebaSettingsCategoryRow
import org.fossify.gallery.views.TiebaSettingsContainer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.math.roundToInt

/**
 * Measured View contracts and actual row callbacks; not hardware/GPU or permission-prompt
 * tests. Each category is now its own page, so a category assertion builds that page from
 * the intent the hub row dispatched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsPhaseTwoTest {
    @Before fun noMotion() {
        // API 30+ Environment indexes the first external directory; Robolectric
        // intentionally has none until the fixture supplies one. Keep real AppOps checks.
        org.robolectric.shadows.ShadowEnvironment.addExternalDir("primary")
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val app = RuntimeEnvironment.getApplication()
            org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AppOpsManager::class.java)).setMode(
                "android:manage_external_storage",
                app.applicationInfo.uid, app.packageName, android.app.AppOpsManager.MODE_IGNORED)
        }
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun context(): Context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
    private fun dp(context: Context, n: Int) = (n * context.resources.displayMetrics.density).roundToInt()
    private fun layout(view: View, width: Int, height: Int? = null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height ?: 0,
                if (height == null) View.MeasureSpec.UNSPECIFIED else View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun hub(activity: SettingsActivity) = activity.findViewById<LinearLayout>(R.id.settings_holder).getChildAt(0) as LinearLayout
    private fun rows(activity: SettingsActivity) = (0 until hub(activity).childCount)
        .map { hub(activity).getChildAt(it) }.filterIsInstance<TiebaSettingsCategoryRow>()
    private fun visibleRows(activity: SettingsActivity): Set<Int> {
        val holder = activity.findViewById<LinearLayout>(R.id.settings_holder)
        // Child 0 is either the hub container or the first section heading.
        return (1 until holder.childCount).map { holder.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }.map { it.id }.toSet()
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    /** Clicks a hub row and builds the category page it dispatched. */

    @Test fun eightCategoriesAreIndependentAndResumePreservesRowsFocusAndRipple() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val categories = rows(activity)
            assertEquals(8, categories.size)
            assertEquals(15, hub(activity).childCount) // exactly seven separators, no trailing line
            categories.forEach { assertTrue(it.isClickable); assertTrue(it.isFocusable) }
            val expected = listOf(R.id.settings_color_customization_holder, R.id.settings_scroll_horizontally_holder,
                R.id.settings_max_brightness_holder, R.id.settings_show_extended_details_holder,
                R.id.settings_autoplay_videos_holder, R.id.settings_manage_included_folders_holder,
                R.id.settings_app_password_protection_holder, R.id.settings_change_date_time_format_holder)
            val seen = mutableSetOf<Int>()
            categories.forEachIndexed { index, _ ->
                val pageController = openCategoryController(activity, index)
                val page = pageController.get()
                try {
                    val visible = visibleRows(page)
                    assertTrue(visible.contains(expected[index]))
                    assertTrue("Previous category leaked", seen.intersect(visible).isEmpty())
                    seen.addAll(visible)
                    // Every visible header must precede at least one visible row.
                    val holder = page.findViewById<LinearLayout>(R.id.settings_holder)
                    val content = (0 until holder.childCount).map { holder.getChildAt(it) }
                        .filter { it.visibility == View.VISIBLE }
                    content.forEachIndexed { i, view ->
                        if (view.tag == "classic_section") {
                            assertTrue(i < content.lastIndex)
                            assertNotEquals("classic_section", content[i + 1].tag)
                        }
                    }
                } finally {
                    pageController.pause().stop().destroy()
                }
            }
            val first = categories.first()
            first.isFocusableInTouchMode = true
            first.requestFocus()
            val focused = first.hasFocus()
            val ripple = first.background
            controller.pause().resume()
            assertSame(first, rows(activity).first())
            assertSame(ripple, first.background)
            assertEquals(focused, first.hasFocus())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun dynamicZoomRowsPersistAcrossPagesAndRecreation() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val config = org.fossify.gallery.helpers.Config.newInstance(activity)
        try {
            val photosController = openCategoryController(activity, 2)
            val photos = photosController.get()
            val switch = photos.findViewById<org.fossify.gallery.views.ClassicSettingsSwitch>(R.id.settings_allow_zooming_images)
            val before = config.allowZoomingImages
            switch.performClick()
            assertEquals(!before, config.allowZoomingImages)
            val children = listOf(R.id.settings_allow_rotating_with_gestures_holder,
                R.id.settings_show_highest_quality_holder, R.id.settings_allow_one_to_one_zoom_holder)
            children.forEach { assertEquals(if (!before) View.VISIBLE else View.GONE, photos.findViewById<View>(it).visibility) }
            val appearanceController = openCategoryController(activity, 0)
            try {
                children.forEach { assertEquals(View.GONE, appearanceController.get().findViewById<View>(it).visibility) }
            } finally { appearanceController.pause().stop().destroy() }
            val saved = Bundle()
            photosController.saveInstanceState(saved).pause().stop().destroy()
            // The system recreates the page with the intent that opened it.
            val restored = Robolectric.buildActivity(SettingsActivity::class.java, photos.intent)
                .create(saved).start().restoreInstanceState(saved).resume().visible()
            try {
                val restoredActivity = restored.get()
                assertFalse(visibleRows(restoredActivity).isEmpty())
                assertTrue(visibleRows(restoredActivity).contains(R.id.settings_allow_zooming_images_holder))
                assertEquals(!before, org.fossify.gallery.helpers.Config.newInstance(restoredActivity).allowZoomingImages)
                assertEquals(!before, restoredActivity.findViewById<org.fossify.gallery.views.ClassicSettingsSwitch>(R.id.settings_allow_zooming_images).isChecked)
            } finally { restored.pause().stop().destroy() }
        } finally { if (!activity.isDestroyed) controller.pause().stop().destroy() }
    }

    @Test fun platformRestrictedRowsKeepTheirExistingConditions() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val gallery = openCategoryController(activity, 1)
            try {
                assertEquals(View.GONE, gallery.get().findViewById<View>(R.id.settings_enable_pull_to_refresh_holder).visibility)
            } finally { gallery.pause().stop().destroy() }
            val videos = openCategoryController(activity, 4)
            try {
                assertEquals(View.GONE, videos.get().findViewById<View>(R.id.settings_on_video_tap_holder).visibility)
            } finally { videos.pause().stop().destroy() }
            val files = openCategoryController(activity, 5)
            try {
                assertEquals(if (android.os.Build.VERSION.SDK_INT < 29) View.VISIBLE else View.GONE,
                    files.get().findViewById<View>(R.id.settings_manage_hidden_folders_holder).visibility)
            } finally { files.pause().stop().destroy() }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val securityController = openCategoryController(activity, 6)
                val security = securityController.get()
                try {
                    assertEquals(View.GONE, security.findViewById<View>(R.id.settings_hidden_item_password_protection_holder).visibility)
                    assertEquals(View.VISIBLE, security.findViewById<View>(R.id.settings_excluded_item_password_protection_holder).visibility)
                    org.robolectric.Shadows.shadowOf(security.getSystemService(android.app.AppOpsManager::class.java)).setMode(
                        "android:manage_external_storage",
                        security.applicationInfo.uid, security.packageName, android.app.AppOpsManager.MODE_ALLOWED)
                    securityController.pause().resume()
                    assertEquals(View.VISIBLE, security.findViewById<View>(R.id.settings_hidden_item_password_protection_holder).visibility)
                    assertEquals(View.GONE, security.findViewById<View>(R.id.settings_excluded_item_password_protection_holder).visibility)
                } finally { securityController.pause().stop().destroy() }
            }
            val advanced = openCategoryController(activity, 7)
            try {
                assertEquals(if (android.os.Build.VERSION.SDK_INT >= 33) View.VISIBLE else View.GONE,
                    advanced.get().findViewById<View>(R.id.settings_language_holder).visibility)
            } finally { advanced.pause().stop().destroy() }
        } finally { controller.pause().stop().destroy() }
    }

    private fun openCategoryController(activity: SettingsActivity, index: Int):
        org.robolectric.android.controller.ActivityController<SettingsActivity> {
        assertTrue(rows(activity)[index].performClick())
        val intent = requireNotNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        return Robolectric.buildActivity(SettingsActivity::class.java, intent).setup()
    }

    @Test fun eachPageRestoresItsOwnScrollPositionOnResume() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val hubScroll = activity.findViewById<View>(R.id.settings_nested_scrollview)
            layout(hubScroll, dp(activity, 320), dp(activity, 240))
            hubScroll.scrollTo(0, dp(activity, 160))
            val hubY = hubScroll.scrollY
            assertTrue(hubY > 0)
            controller.pause().resume()
            layout(hubScroll, dp(activity, 320), dp(activity, 240))
            assertEquals(hubY, hubScroll.scrollY)

            val pageController = openCategoryController(activity, 2)
            val page = pageController.get()
            val pageScroll = page.findViewById<View>(R.id.settings_nested_scrollview)
            layout(pageScroll, dp(page, 320), dp(page, 240))
            pageScroll.scrollTo(0, dp(page, 120))
            val pageY = pageScroll.scrollY
            assertTrue(pageY > 0)
            pageController.pause().resume()
            layout(pageScroll, dp(page, 320), dp(page, 240))
            assertEquals(pageY, pageScroll.scrollY)
            pageController.pause().stop().destroy()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun switchClickWritesPreferenceExactlyOnce() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val pageController = openCategoryController(activity, 2)
            val page = pageController.get()
            val prefs = page.getSharedPrefs()
            var changes = 0
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == ALLOW_ZOOMING_IMAGES) changes++
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            try {
                page.findViewById<View>(R.id.settings_allow_zooming_images).performClick()
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(1, changes)
            } finally { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            pageController.pause().stop().destroy()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun rowAndSectionMinimumsAndLongTextGrowAtAllCandidateWidths() {
        for (scale in listOf(1f, 1.3f, 1.5f)) for (rtl in listOf(false, true)) {
            val base = context()
            val config = android.content.res.Configuration(base.resources.configuration).apply { fontScale = scale }
            val ctx = ContextThemeWrapper(base.createConfigurationContext(config), R.style.AppTheme)
            val page = LayoutInflater.from(ctx).inflate(R.layout.activity_settings, null)
            val holder = page.findViewById<LinearLayout>(R.id.settings_holder)
            descendants(holder).forEach { it.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
            // Ensure the double-row layout is exercised with a genuinely multiline summary.
            holder.findViewById<TextView>(R.id.settings_folder_thumbnail_style).text =
                "按当前配置显示文件夹缩略图 / Display folder thumbnails using the current configuration"
            for (width in listOf(320, 360, 412, 600)) {
                layout(holder, dp(ctx, width))
                val single = holder.findViewById<View>(R.id.settings_app_icon_holder)
                val double = holder.findViewById<View>(R.id.settings_folder_thumbnail_style_holder)
                val toggle = holder.findViewById<View>(R.id.settings_crop_thumbnails_holder)
                assertTrue(single.height >= dp(ctx, 56))
                assertTrue(toggle.height >= dp(ctx, 56))
                assertTrue(double.height >= dp(ctx, 72))
                descendants(holder).filterIsInstance<TextView>().filter { it.tag == "classic_section" }.forEach {
                    assertTrue(it.height >= dp(ctx, 48))
                    assertEquals(dp(ctx, 16), it.paddingStart)
                }
                for (id in listOf(R.id.settings_app_icon_label, R.id.settings_folder_thumbnail_style_label, R.id.settings_folder_thumbnail_style)) {
                    val text = holder.findViewById<TextView>(id)
                    assertTrue("Clipped label $id", text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                    assertTrue(text.bottom <= (text.parent as View).height - (text.parent as View).paddingBottom)
                    for (line in 0 until text.lineCount) assertEquals(0, text.layout.getEllipsisCount(line))
                }
            }
        }
    }

    @Test fun wideContainerHasNoWidthDiscontinuityAndRetainsReferenceWidths() {
        val ctx = context()
        val container = TiebaSettingsContainer(ctx)
        var previous = 0
        for (width in 320..900) {
            layout(container, dp(ctx, width))
            assertTrue(container.width >= previous)
            if (previous != 0) assertTrue("Width jump at $width", container.width - previous <= dp(ctx, 2))
            previous = container.width
            if (width <= 412) assertEquals(dp(ctx, width), container.width)
            if (width == 600) assertEquals(dp(ctx, 522), container.width)
            if (width == 840) assertEquals(dp(ctx, 630), container.width)
        }
    }

    @Test fun rowRippleKeepsInstanceAndPressedStateAcrossThemeRebinds() {
        val row = View(context())
        var previous: RippleDrawable? = null
        for (dark in listOf(false, true)) for (seed in listOf(-1, 0xFF000000.toInt(), 0xFF7DC22F.toInt(), 0xFF9C27B0.toInt())) {
            val tokens = OxygenThemeColors.resolve(true, if (dark) "dark" else "light", false, seed, -1, 0xFF212121.toInt())
            row.isPressed = true
            ClassicSettingsPresentation.rowRipple(row, tokens)
            val ripple = row.background as RippleDrawable
            if (previous != null) assertSame(previous, ripple)
            assertTrue(row.isPressed)
            val expected = if (dark) 0x1FFFFFFF else 0x14000000
            assertEquals(expected, ClassicSettingsPresentation.rowRippleColor(tokens))
            assertTrue(OxygenThemeColors.contrastRatio(tokens.interactiveAccent, tokens.pageBackground) >= 4.5)
            previous = ripple
            row.isPressed = false
        }
    }
}
