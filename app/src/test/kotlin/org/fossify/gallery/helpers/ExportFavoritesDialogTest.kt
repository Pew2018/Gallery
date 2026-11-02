package org.fossify.gallery.helpers

import android.app.Application
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.dialogs.ExportFavoritesDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class ExportFavoritesDialogTest {
    @Test fun hintsAndCallbackNamesMatchTheSelectedFormatForSafAndLegacyPaths() {
        RuntimeEnvironment.getApplication().getSharedPrefs().edit().clear().commit()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create()
        val host = controller.get()
        try {
            for ((extension, hint) in listOf(".txt" to org.fossify.commons.R.string.filename_without_txt,
                ".json" to org.fossify.commons.R.string.filename_without_json, ".zip" to org.fossify.commons.R.string.filename_without_zip)) {
                for (hidePath in listOf(true, false)) {
                    var result: Pair<String, String>? = null
                    ExportFavoritesDialog(host, "backup" + extension.uppercase(), hidePath, extension) { path, name -> result = path to name }
                    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                    assertEquals(host.getString(hint), dialog.findViewById<TextInputLayout>(R.id.export_favorites_hint)!!.hint.toString())
                    val field = dialog.findViewById<EditText>(R.id.export_favorites_filename)!!
                    assertEquals("backup", field.text.toString())
                    for (invalid in listOf("", "bad/name", "backup.pdf")) {
                        field.setText(invalid)
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                        assertNull(result); assertTrue(dialog.isShowing)
                    }
                    field.setText("中文备份" + extension.uppercase())
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    assertEquals("中文备份$extension", result?.second)
                    assertTrue(result!!.first.endsWith("/中文备份$extension"))
                    assertFalse(dialog.isShowing)
                }
            }
        } finally { ShadowDialog.getShownDialogs().toList().forEach { it.dismiss() }; controller.destroy() }
    }
}
