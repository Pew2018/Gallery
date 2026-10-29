package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI contracts; actual pixel comparison still requires a device/emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ClassicGalleryUiContractTest {
    @Test fun albumLabelsShareTwoLineAndScrimRulesInBothGridStyles() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val inflater = LayoutInflater.from(activity)
        for (layout in listOf(R.layout.directory_item_grid_square,
            R.layout.directory_item_grid_rounded_corners)) {
            val root = inflater.inflate(layout, null)
            assertNotNull(root.findViewById<ImageView>(R.id.dir_shadow_holder).background)
            assertEquals(2, root.findViewById<TextView>(R.id.dir_name).maxLines)
            assertEquals(1, root.findViewById<TextView>(R.id.photo_cnt).maxLines)
            assertNotNull(root.findViewById<ImageView>(R.id.dir_check))
            assertNotNull(root.findViewById<ImageView>(R.id.dir_pin))
            assertNotNull(root.findViewById<ImageView>(R.id.dir_lock))
            val lock = root.findViewById<ImageView>(R.id.dir_lock)
            assertEquals(ImageView.ScaleType.CENTER, lock.scaleType)
            assertEquals(0, lock.paddingLeft)
        }
        val dp = activity.resources.getDimension(R.dimen.tmb_shadow_height) /
            activity.resources.displayMetrics.density
        assertTrue(dp in 64f..72f)
        val scrim = activity.getDrawable(R.drawable.oxygen_classic_folder_label)
        assertTrue(scrim is GradientDrawable)
        val colors = (scrim as GradientDrawable).colors
        assertNotNull(colors)
        assertTrue(colors!!.size >= 2)
        assertEquals(0, colors.last() ushr 24)
    }

    @Test fun alreadyShownClassicDialogInheritsHostScreenshotRestriction() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val dialog = OxygenClassicDialog.builder(activity, "Delete")
            .setPositiveButton("Delete") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
        dialog.show()
        try {
            OxygenClassicDialog.apply(activity, dialog, dialog.window!!.decorView)
            val flags = dialog.window!!.attributes.flags
            assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertTrue(flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
            assertEquals(0.60f, dialog.window!!.attributes.dimAmount, 0.001f)
            assertNotNull(dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE))
        } finally {
            dialog.dismiss()
        }
    }
}
