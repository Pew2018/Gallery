package org.fossify.gallery.dialogs

import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.helpers.*
import org.fossify.gallery.helpers.backup.*

/** Read-only preview; dismissing or cancelling either dialog never invokes the writer. */
internal object GalleryRestorePreviewDialog {
    fun show(activity: BaseSimpleActivity, plan: GalleryRestorePlan, restore: (GalleryRestoreMode) -> Unit) {
        val colors = photoDialogColors(activity)
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        fun heading(label: Int) = TextView(activity).apply {
            setText(label)
            textSize = 14f
            setTextColor(colors.accent)
            setPadding(0, dp(12), 0, dp(8))
        }
        fun row(parent: LinearLayout, label: String, count: Int) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            line.addView(TextView(activity).apply {
                text = label; textSize = 14f; setTextColor(colors.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(TextView(activity).apply {
                text = activity.getString(R.string.full_backup_quantity, count)
                textSize = 14f; setTextColor(colors.text)
                gravity = Gravity.END
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(heading(R.string.full_backup_contents_heading))
        val initial = plan.targets.getValue(GalleryRestoreMode.MERGE)
        listOf(
            R.string.full_backup_settings_label to plan.settingsCount,
            R.string.full_backup_media_label to initial.mediaCount.incoming,
            R.string.full_backup_folders_label to initial.folderCount.incoming,
            R.string.full_backup_profiles_label to initial.profileCount.incoming,
            R.string.full_backup_personalization_label to plan.incoming.oxygenSettings.values.size
        ).forEach { (label, count) -> row(content, activity.getString(label), count) }
        content.addView(heading(R.string.full_backup_changes_heading))
        val changes = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; id = R.id.full_backup_changes }
        content.addView(changes)
        content.addView(heading(R.string.full_backup_notes_heading))
        content.addView(TextView(activity).apply {
            textSize = 14f; setTextColor(colors.secondary)
            text = activity.getString(R.string.full_backup_mode_note) + "\n" + activity.getString(R.string.full_backup_path_note)
        })
        content.addView(heading(R.string.full_backup_mode_heading))
        val group = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val merge = RadioButton(activity).apply { id = View.generateViewId(); setText(R.string.full_backup_merge); textSize = 16f }
        val replace = RadioButton(activity).apply { id = View.generateViewId(); setText(R.string.full_backup_replace); textSize = 16f }
        listOf(merge, replace).forEach {
            group.addView(it, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val scroll = ClassicDialogScrollView(activity).apply {
            id = R.id.full_backup_preview
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        var mode = GalleryRestoreMode.MERGE
        fun update() {
            changes.removeAllViews()
            changeRows(activity, plan, mode).forEach { (label, count) -> row(changes, label, count) }
            if (plan.statuses.values.any { it == BackupPathStatus.PROTECTED }) {
                changes.addView(TextView(activity).apply {
                    textSize = 14f; setTextColor(colors.secondary)
                    setText(R.string.full_backup_protected_note)
                })
            }
        }
        group.setOnCheckedChangeListener { _, id -> mode = if (id == replace.id) GalleryRestoreMode.REPLACE else GalleryRestoreMode.MERGE; update() }
        merge.isChecked = true
        val dialog = OxygenClassicDialog.show(activity,
            OxygenClassicDialog.builder(activity, activity.getString(R.string.full_backup_restore))
                .setView(scroll)
                .setPositiveButton(R.string.full_backup_restore_action, null)
                .setNegativeButton(org.fossify.commons.R.string.cancel, null), scroll)
        listOf(merge, replace).forEach {
            TiebaDialogChoices.apply(it, colors.accent, colors.text, colors.surface)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (mode == GalleryRestoreMode.MERGE) {
                dialog.dismiss()
                restore(mode)
            } else {
                val target = plan.targets.getValue(mode)
                OxygenClassicDialog.show(activity,
                    OxygenClassicDialog.builder(activity, activity.getString(R.string.full_backup_replace_confirm_title))
                        .setMessage(activity.getString(R.string.full_backup_replace_confirm,
                            target.mediaCount.removed, target.folderCount.removed, target.profileCount.removed))
                        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                        .setPositiveButton(R.string.full_backup_restore_action) { _, _ ->
                            dialog.dismiss()
                            restore(GalleryRestoreMode.REPLACE)
                        })
            }
        }
    }

    internal fun changeRows(activity: BaseSimpleActivity, plan: GalleryRestorePlan, mode: GalleryRestoreMode): List<Pair<String, Int>> = buildList {
        val target = plan.targets.getValue(mode)
        fun addCount(label: Int, count: Int) {
            if (count > 0) add(activity.getString(label) to count)
        }
        listOf(
            R.string.full_backup_media_label to target.mediaCount,
            R.string.full_backup_folders_label to target.folderCount,
            R.string.full_backup_profiles_label to target.profileCount
        ).forEach { (category, counts) ->
            listOf(R.string.full_backup_added_label to counts.added,
                (if (category == R.string.full_backup_profiles_label) R.string.full_backup_profile_existing_label else R.string.full_backup_existing_label) to counts.existing,
                R.string.full_backup_removed_label to counts.removed).forEach { (change, count) ->
                if (count > 0) add(activity.getString(R.string.full_backup_change_label,
                    activity.getString(category), activity.getString(change)) to count)
            }
        }
        addCount(R.string.full_backup_folder_conflicts_label, target.folderCount.conflicts)
        addCount(R.string.full_backup_profile_conflicts_label, target.profileCount.conflicts)
        addCount(R.string.full_backup_security_label, target.retainedSecuritySettings.size)
        BackupPathStatus.entries.filter { it != BackupPathStatus.AVAILABLE }.forEach { status ->
            val label = when (status) {
                BackupPathStatus.MISSING -> R.string.full_backup_path_missing
                BackupPathStatus.OFFLINE_STORAGE -> R.string.full_backup_path_offline
                BackupPathStatus.SAF_PERMISSION -> R.string.full_backup_path_saf
                BackupPathStatus.MEDIA_PERMISSION -> R.string.full_backup_path_permission
                BackupPathStatus.PROTECTED -> R.string.full_backup_path_protected
                else -> R.string.full_backup_path_unconfirmed
            }
            addCount(label, plan.statuses.values.count { it == status })
        }
    }

}
