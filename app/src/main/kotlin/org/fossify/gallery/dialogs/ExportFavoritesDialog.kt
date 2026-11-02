package org.fossify.gallery.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogExportFavoritesBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.ExportFilename

class ExportFavoritesDialog(
    val activity: BaseSimpleActivity, val defaultFilename: String, val hidePath: Boolean,
    val extension: String = ".txt", val dialogTitle: Int = R.string.export_favorite_paths,
    callback: (path: String, filename: String) -> Unit
) {
    init {
        val settingsExport = dialogTitle == org.fossify.commons.R.string.export_settings
        val lastUsedFolder = if (settingsExport) activity.baseConfig.lastExportedSettingsFolder
            else activity.config.lastExportedFavoritesFolder
        var folder = if (lastUsedFolder.isNotEmpty() && activity.getDoesFilePathExist(lastUsedFolder)) {
            lastUsedFolder
        } else {
            activity.internalStoragePath
        }

        val binding = DialogExportFavoritesBinding.inflate(activity.layoutInflater).apply {
            exportFavoritesHint.setHint(when (extension) {
                ".json" -> org.fossify.commons.R.string.filename_without_json
                ".zip" -> org.fossify.commons.R.string.filename_without_zip
                else -> org.fossify.commons.R.string.filename_without_txt
            })
            exportFavoritesFilename.setText(if (defaultFilename.endsWith(extension, ignoreCase = true))
                defaultFilename.dropLast(extension.length) else defaultFilename)

            if (hidePath) {
                exportFavoritesPathLabel.beGone()
                exportFavoritesPath.beGone()
            } else {
                exportFavoritesPath.text = activity.humanizePath(folder)
                exportFavoritesPath.setOnClickListener {
                    FilePickerDialog(activity, folder, false, showFAB = true) {
                        exportFavoritesPath.text = activity.humanizePath(it)
                        folder = it
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, dialogTitle) { alertDialog ->
                    OxygenClassicDialog.apply(activity, alertDialog, binding.root)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val input = binding.exportFavoritesFilename.value.trim()
                        if (input.isEmpty()) {
                            activity.toast(org.fossify.commons.R.string.filename_cannot_be_empty)
                            return@setOnClickListener
                        }

                        if (!input.isAValidFilename() || input.contains('/') || input.contains('\\')) {
                            activity.toast(org.fossify.commons.R.string.filename_invalid_characters)
                            return@setOnClickListener
                        }
                        val filename = ExportFilename.normalize(input, extension) ?: run {
                            activity.toast(activity.getString(R.string.export_filename_extension_error, extension))
                            return@setOnClickListener
                        }
                        val newPath = "${folder.trimEnd('/')}/$filename"
                        if (!newPath.getFilenameFromPath().isAValidFilename()) {
                            activity.toast(org.fossify.commons.R.string.filename_invalid_characters)
                            return@setOnClickListener
                        }

                        if (settingsExport) activity.baseConfig.lastExportedSettingsFolder = folder
                        else activity.config.lastExportedFavoritesFolder = folder
                        if (!hidePath && activity.getDoesFilePathExist(newPath)) {
                            val title = String.format(
                                activity.getString(org.fossify.commons.R.string.file_already_exists_overwrite),
                                newPath.getFilenameFromPath()
                            )
                            ConfirmationDialog(activity, title) {
                                callback(newPath, filename)
                                alertDialog.dismiss()
                            }
                        } else {
                            callback(newPath, filename)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }
}
