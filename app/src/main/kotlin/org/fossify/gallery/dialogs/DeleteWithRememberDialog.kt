package org.fossify.gallery.dialogs

import org.fossify.gallery.helpers.OxygenClassicDialog

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.databinding.DialogDeleteWithRememberBinding

class DeleteWithRememberDialog(
    private val activity: Activity,
    private val message: String,
    private val showSkipRecycleBinOption: Boolean,
    private val onDismiss: () -> Unit = {},
    private val callback: (remember: Boolean, skipRecycleBin: Boolean) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var confirmed = false
    private val binding = DialogDeleteWithRememberBinding.inflate(activity.layoutInflater)

    init {
        binding.deleteRememberTitle.text = message
        binding.deleteRememberCheckbox.isChecked = false
        binding.skipTheRecycleBinCheckbox.isChecked = false
        binding.skipTheRecycleBinCheckbox.beGoneIf(!showSkipRecycleBinOption)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.yes) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.no, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    OxygenClassicDialog.apply(activity, alertDialog, binding.root)
                    dialog = alertDialog
                    alertDialog.setOnDismissListener { onDismiss() }
                }
            }
    }

    private fun dialogConfirmed() {
        if (confirmed) return
        confirmed = true
        val remember = binding.deleteRememberCheckbox.isChecked
        val skipRecycleBin = showSkipRecycleBinOption && binding.skipTheRecycleBinCheckbox.isChecked
        dialog?.dismiss()
        callback(remember, skipRecycleBin)
    }
}
