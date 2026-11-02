package org.fossify.gallery.dialogs

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.helpers.backup.*

/** Read-only preview; dismissing or cancelling either dialog never invokes the writer. */
internal object GalleryRestorePreviewDialog {
    fun show(activity: BaseSimpleActivity, plan: GalleryRestorePlan, restore: (GalleryRestoreMode) -> Unit) {
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val summary = TextView(activity).apply { textSize = 16f; setTextColor(colors.primaryText) }
        val note = TextView(activity).apply {
            textSize = 14f; setTextColor(colors.secondaryText)
            text = activity.getString(R.string.full_backup_path_note)
            setPadding(0, dp(12), 0, dp(8))
        }
        val group = RadioGroup(activity)
        val merge = RadioButton(activity).apply { id = View.generateViewId(); setText(R.string.full_backup_merge); textSize = 16f }
        val replace = RadioButton(activity).apply { id = View.generateViewId(); setText(R.string.full_backup_replace); textSize = 16f }
        group.addView(merge); group.addView(replace)
        content.addView(summary); content.addView(note); content.addView(group)
        val scroll = ClassicDialogScrollView(activity).apply { addView(content) }
        var mode = GalleryRestoreMode.MERGE
        fun update() {
            val target = plan.targets.getValue(mode)
            val statuses = BackupPathStatus.entries.filter { it != BackupPathStatus.AVAILABLE }.joinToString("\n") { status ->
                val title = when (status) {
                    BackupPathStatus.MISSING -> R.string.full_backup_path_missing
                    BackupPathStatus.OFFLINE_STORAGE -> R.string.full_backup_path_offline
                    BackupPathStatus.SAF_PERMISSION -> R.string.full_backup_path_saf
                    BackupPathStatus.MEDIA_PERMISSION -> R.string.full_backup_path_permission
                    BackupPathStatus.PROTECTED -> R.string.full_backup_path_protected
                    else -> R.string.full_backup_path_unconfirmed
                }
                activity.getString(R.string.full_backup_status_line, activity.getString(title), plan.statuses.values.count { it == status })
            }
            summary.text = activity.getString(R.string.full_backup_preview_counts, plan.settingsCount,
                target.mediaCount.incoming, target.folderCount.incoming, target.profileCount.incoming, plan.incoming.oxygenSettings.values.size) +
                "\n\n" + activity.getString(R.string.full_backup_preview_changes,
                    target.mediaCount.existing, target.mediaCount.added, target.mediaCount.removed,
                    target.folderCount.existing, target.folderCount.added, target.folderCount.removed,
                    target.profileCount.existing, target.profileCount.added, target.profileCount.conflicts, target.profileCount.removed, target.folderCount.conflicts) +
                "\n\n" + activity.getString(R.string.full_backup_security_retained, target.retainedSecuritySettings.size) + "\n" + statuses
        }
        group.setOnCheckedChangeListener { _, id -> mode = if (id == replace.id) GalleryRestoreMode.REPLACE else GalleryRestoreMode.MERGE; update() }
        merge.isChecked = true
        activity.getAlertDialogBuilder().setPositiveButton(R.string.full_backup_restore_action, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null).apply {
                activity.setupDialogStuff(scroll, this, R.string.full_backup_restore) { dialog ->
                    OxygenClassicDialog.apply(activity, dialog, scroll)
                    listOf(merge, replace).forEach { TiebaDialogChoices.apply(it, colors.interactiveAccent, colors.primaryText, colors.dialogBackground) }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (mode == GalleryRestoreMode.MERGE) { dialog.dismiss(); restore(mode) }
                        else {
                            val target = plan.targets.getValue(mode)
                            activity.getAlertDialogBuilder()
                                .setTitle(R.string.full_backup_replace_confirm_title)
                                .setMessage(activity.getString(R.string.full_backup_replace_confirm,
                                    target.mediaCount.removed, target.folderCount.removed, target.profileCount.removed))
                                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                                .setPositiveButton(R.string.full_backup_restore_action) { _, _ -> dialog.dismiss(); restore(GalleryRestoreMode.REPLACE) }
                                .create().also { confirm -> confirm.show(); OxygenClassicDialog.apply(activity, confirm, confirm.findViewById(android.R.id.message) ?: confirm.window!!.decorView) }
                        }
                    }
                }
            }
    }
}
