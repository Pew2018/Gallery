package org.fossify.gallery.helpers.backup

import android.app.Application
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.room.Room
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.dialogs.GalleryRestorePreviewDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GalleryRestorePreviewDialogTest {
    private fun <T> dialogTest(block: (SettingsActivity, GalleryRestorePlan, AlertDialog, () -> List<GalleryRestoreMode>) -> T): T {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPrefs().edit().clear().commit(); GalleryRestoreJournal(context).delete()
        val db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java).allowMainThreadQueries().build()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val activity = controller.get()
        try {
            val plan = GalleryRestoreService(context, db, BackupPathAccess { _, _ -> BackupPathStatus.AVAILABLE })
                .preview(fullBackupFixture(context, listOf("/new.jpg")))
            val calls = mutableListOf<GalleryRestoreMode>()
            GalleryRestorePreviewDialog.show(activity, plan) { calls.add(it) }
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            return block(activity, plan, dialog) { calls.toList() }
        } finally { ShadowDialog.getShownDialogs().toList().forEach { it.dismiss() }; controller.destroy(); db.close() }
    }
    private fun radios(view: android.view.View): List<RadioButton> = buildList {
        if (view is RadioButton) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(radios(view.getChildAt(i)))
    }
    @Test fun previewDefaultsToMergeAndCancelDoesNotInvokeRestore() = dialogTest { activity, _, dialog, calls ->
        val buttons = radios(dialog.window!!.decorView)
        assertEquals(2, buttons.size); assertTrue(buttons.first().isChecked); assertFalse(buttons.last().isChecked)
        val before = activity.getSharedPrefs().all
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertTrue(calls().isEmpty()); assertEquals(before, activity.getSharedPrefs().all)
    }
    @Test fun mergeRequiresOnlyTheExplicitPreviewConfirmation() = dialogTest { _, _, dialog, calls ->
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(listOf(GalleryRestoreMode.MERGE), calls())
    }
    @Test fun replaceRequiresSecondConfirmationAndCancellingItDoesNotWrite() = dialogTest { _, _, dialog, calls ->
        radios(dialog.window!!.decorView).last().isChecked = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotSame(dialog, confirmation); assertTrue(calls().isEmpty())
        confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertTrue(calls().isEmpty()); assertTrue(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(listOf(GalleryRestoreMode.REPLACE), calls())
    }
}
