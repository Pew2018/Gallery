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
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(calls().isEmpty()); assertTrue(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf(GalleryRestoreMode.REPLACE), calls())
    }
    @Test fun previewHasDedicatedWidthAndEqualTrailingChoicesWithoutFixedHeight() = dialogTest { activity, _, dialog, _ ->
        val root = dialog.window!!.decorView
        val density = activity.resources.displayMetrics.density
        assertEquals(org.fossify.gallery.helpers.OxygenClassicDialog.widthForContent(
            org.fossify.gallery.helpers.OxygenClassicDialog.availableWidth(activity), density, 400), dialog.window!!.attributes.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, dialog.window!!.attributes.height)
        assertNotNull(dialog.findViewById<android.view.View>(org.fossify.gallery.R.id.full_backup_preview))
        val buttons = radios(root)
        buttons.forEach {
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, it.layoutParams.width)
            assertTrue(it.minHeight >= (48 * density).toInt())
            assertNull(it.buttonDrawable)
            assertNotNull(it.compoundDrawablesRelative[2])
        }
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(dialog.window!!.attributes.width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec((700 * density).toInt(), android.view.View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        assertEquals(buttons[0].width, buttons[1].width)
        assertEquals(buttons[0].right, buttons[1].right)
    }

    @Test fun changesHideZerosButKeepActualDeletionsConflictsAndProtectedPaths() = dialogTest { activity, plan, dialog, _ ->
        val merge = plan.targets.getValue(GalleryRestoreMode.MERGE)
        val replace = plan.targets.getValue(GalleryRestoreMode.REPLACE).copy(
            mediaCount = RestoreCategoryCount(1, 3, 1, removed = 2),
            folderCount = RestoreCategoryCount(1, 2, 0, conflicts = 1, removed = 1),
            profileCount = RestoreCategoryCount(1, 1, 0, conflicts = 2, removed = 1))
        val review = plan.copy(statuses = mapOf(BackupPathRequest("/protected", true) to BackupPathStatus.PROTECTED),
            targets = mapOf(GalleryRestoreMode.MERGE to merge, GalleryRestoreMode.REPLACE to replace))
        val mergeRows = GalleryRestorePreviewDialog.changeRows(activity, review, GalleryRestoreMode.MERGE)
        val replaceRows = GalleryRestorePreviewDialog.changeRows(activity, review, GalleryRestoreMode.REPLACE)
        assertTrue(mergeRows.all { it.second > 0 }); assertTrue(replaceRows.all { it.second > 0 })
        assertTrue(replaceRows.contains(activity.getString(org.fossify.gallery.R.string.full_backup_path_protected) to 1))
        assertFalse(replaceRows.any { it.first == activity.getString(org.fossify.gallery.R.string.full_backup_path_missing) })
        assertTrue(replaceRows.contains(activity.getString(org.fossify.gallery.R.string.full_backup_change_label,
            activity.getString(org.fossify.gallery.R.string.full_backup_media_label),
            activity.getString(org.fossify.gallery.R.string.full_backup_removed_label)) to 2))
        GalleryRestorePreviewDialog.show(activity, review) { fail("preview must not restore") }
        val preview = ShadowDialog.getLatestDialog() as AlertDialog
        fun changeTexts(): List<String> {
            val changes = preview.findViewById<android.widget.LinearLayout>(org.fossify.gallery.R.id.full_backup_changes)!!
            fun collect(view: android.view.View): List<String> = when (view) {
                is android.widget.TextView -> listOf(view.text.toString())
                is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
                else -> emptyList()
            }
            return collect(changes)
        }
        assertTrue(changeTexts().containsAll(mergeRows.map { it.first }))
        radios(preview.window!!.decorView).last().isChecked = true
        assertTrue(changeTexts().containsAll(replaceRows.map { it.first }))
        assertTrue(changeTexts().contains(activity.getString(org.fossify.gallery.R.string.full_backup_protected_note)))
        preview.dismiss(); dialog.dismiss()
    }

    @Test fun duplicateRestoreClicksDoNotOpenTwoConfirmationsAndCancelledConfirmationHasItsOwnCallback() = dialogTest { activity, plan, first, _ ->
        first.dismiss()
        var cancelled = 0
        var restored = 0
        GalleryRestorePreviewDialog.show(activity, plan, initialMode = GalleryRestoreMode.REPLACE,
            onConfirmationCancelled = { cancelled++ }) { restored++ }
        val preview = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(radios(preview.window!!.decorView).last().isChecked)
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val confirm = ShadowDialog.getLatestDialog() as AlertDialog
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertSame(confirm, ShadowDialog.getLatestDialog())
        confirm.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, cancelled); assertEquals(0, restored); assertTrue(preview.isShowing)
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val next = ShadowDialog.getLatestDialog() as AlertDialog
        next.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        next.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, restored)
    }

    @Test fun previewCancellationCallbackIsDeliveredOnlyOnceAndNeverRestores() = dialogTest { activity, plan, first, _ ->
        first.dismiss()
        var cancelled = 0
        GalleryRestorePreviewDialog.show(activity, plan, onCancelled = { cancelled++ }) { fail("cancel must not restore") }
        val preview = ShadowDialog.getLatestDialog() as AlertDialog
        preview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        preview.cancel(); preview.dismiss()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, cancelled)
    }

}
