package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.widget.CheckBox
import androidx.core.widget.TintableCompoundButton
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.R
import org.fossify.gallery.activities.PhotoProfileEditActivity
import org.fossify.gallery.dialogs.ChangeFileThumbnailStyleDialog
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ThumbnailSpacingDialogTest {
    @Test fun fileStyleCheckboxesKeepTheirClassicCheckmarkWithoutFrameworkOrSupportTint() {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(PhotoProfileEditActivity::class.java,
            Intent(app, PhotoProfileEditActivity::class.java).putExtra(PhotoProfileEditActivity.EXTRA_CREATING, true)).setup()
        val host = controller.get()
        ChangeFileThumbnailStyleDialog(host)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val checkboxIds = intArrayOf(
            R.id.dialog_file_style_rounded_corners,
            R.id.dialog_file_style_show_thumbnail_video_duration,
            R.id.dialog_file_style_show_thumbnail_file_types,
            R.id.dialog_file_style_mark_favorite_items
        )
        checkboxIds.forEach { id ->
            val checkbox = dialog.findViewById<CheckBox>(id)!!
            assertNull("framework tint should not recolor the classic checkmark", checkbox.buttonTintList)
            assertNull("AppCompat tint should not recolor the classic checkmark",
                (checkbox as TintableCompoundButton).supportButtonTintList)
            assertNotNull(checkbox.buttonDrawable)
        }
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun spacingAndCheckboxStayTemporaryUntilPositiveAction() {
        val app = RuntimeEnvironment.getApplication()
        val intent = Intent(app, PhotoProfileEditActivity::class.java)
            .putExtra(PhotoProfileEditActivity.EXTRA_CREATING, true)
        val controller = Robolectric.buildActivity(PhotoProfileEditActivity::class.java, intent).setup()
        val host = controller.get()
        host.config.thumbnailSpacing = 1
        host.config.fileRoundedCorners = false
        fun open(): AlertDialog {
            ChangeFileThumbnailStyleDialog(host)
            return ShadowDialog.getLatestDialog() as AlertDialog
        }
        val first = open()
        val slider = first.findViewById<SeekBar>(R.id.dialog_file_style_spacing_slider)!!
        assertEquals(7, slider.max)
        assertEquals(1, slider.progress)
        slider.progress = 6
        first.findViewById<android.view.View>(R.id.dialog_file_style_rounded_corners_holder)!!.performClick()
        assertTrue(first.findViewById<CheckBox>(R.id.dialog_file_style_rounded_corners)!!.isChecked)
        assertEquals(1, host.config.thumbnailSpacing)
        assertFalse(host.config.fileRoundedCorners)
        first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, host.config.thumbnailSpacing)
        assertFalse(host.config.fileRoundedCorners)
        val second = open()
        second.findViewById<SeekBar>(R.id.dialog_file_style_spacing_slider)!!.progress = 7
        second.findViewById<android.view.View>(R.id.dialog_file_style_rounded_corners_holder)!!.performClick()
        second.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(64, host.config.thumbnailSpacing)
        assertTrue(host.config.fileRoundedCorners)
        controller.pause().stop().destroy()
    }
}
