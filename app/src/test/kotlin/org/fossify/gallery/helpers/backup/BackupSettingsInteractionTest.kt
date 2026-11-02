package org.fossify.gallery.helpers.backup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupSettingsInteractionTest {
    @Test fun cancelledOrMissingDocumentSelectionDoesNotMutatePreferences() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            val prefs = activity.getSharedPrefs()
            val before = prefs.all
            val method = SettingsActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Intent::class.java).apply { isAccessible = true }
            for (code in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 1006)) {
                method.invoke(activity, code, Activity.RESULT_CANCELED, null)
                method.invoke(activity, code, Activity.RESULT_OK, Intent())
            }
            assertEquals(before, prefs.all)
        } finally { controller.destroy() }
    }
    @Test fun destroyedActivityDropsQueuedUiCallback() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        controller.destroy()
        var called = false
        val method = SettingsActivity::class.java.getDeclaredMethod("backupUi", kotlin.jvm.functions.Function0::class.java)
            .apply { isAccessible = true }
        method.invoke(activity, { called = true })
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(called)
    }
    @Test fun backupRowsAreAppendedWithIdenticalClassicDimensionsAndRegisteredInAdvancedPage() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        try {
            val activity = controller.get()
            val ids = listOf(R.id.settings_export_favorites_holder, R.id.settings_import_favorites_holder,
                R.id.settings_export_favorite_folders_holder, R.id.settings_import_favorite_folders_holder,
                R.id.settings_export_holder, R.id.settings_import_holder,
                R.id.settings_export_photo_profiles_holder, R.id.settings_import_photo_profiles_holder,
                R.id.settings_export_full_backup_holder, R.id.settings_restore_full_backup_holder)
            val parent = activity.findViewById<android.view.ViewGroup>(R.id.settings_holder)
            val rows = ids.map { activity.findViewById<android.view.View>(it) }
            assertEquals(rows.map { parent.indexOfChild(it) }.sorted(), rows.map { parent.indexOfChild(it) })
            rows.forEach {
                assertEquals(rows.first().minimumHeight, it.minimumHeight)
                assertEquals(rows.first().paddingLeft, it.paddingLeft)
                assertEquals(rows.first().paddingRight, it.paddingRight)
            }
            val field = SettingsActivity::class.java.getDeclaredField("settingsSectionRows").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") val sections = field.get(activity) as Map<Int, Set<Int>>
            assertEquals(ids.toSet(), sections[R.id.settings_section_advanced_backup])
        } finally { controller.destroy() }
    }
}
