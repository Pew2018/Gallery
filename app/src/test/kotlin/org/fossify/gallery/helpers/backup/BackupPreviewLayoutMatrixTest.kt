package org.fossify.gallery.helpers.backup

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.room.Room
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.dialogs.GalleryRestorePreviewDialog
import org.fossify.gallery.helpers.ClassicDialogScrollView
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** Native Robolectric renders, not emulator/physical-device screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupPreviewLayoutMatrixTest {
    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    @Test fun previewKeepsFixedActionsAndScrollableBodyAcrossScreenFontAndThemeMatrix() {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, GalleryDatabase::class.java).allowMainThreadQueries().build()
        try {
            for ((width, height) in listOf(360 to 800, 393 to 800, 412 to 800, 800 to 360)) {
                for (dark in listOf(false, true)) for (font in listOf(1f, 1.3f, 1.5f)) {
                    RuntimeEnvironment.setQualifiers("zh-rCN-w${width}dp-h${height}dp-" + (if (dark) "night" else "notnight") + "-mdpi")
                    val configuration = android.content.res.Configuration(app.resources.configuration).apply { fontScale = font }
                    @Suppress("DEPRECATION")
                    app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
                    app.getSharedPrefs().edit().clear().commit()
                    GalleryRestoreJournal(app).delete()
                    val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup().visible()
                    val host = controller.get()
                    try {
                        host.baseConfig.backgroundColor = if (dark) Color.BLACK else Color.WHITE
                        host.baseConfig.textColor = if (dark) Color.WHITE else Color.BLACK
                        host.baseConfig.isSystemThemeEnabled = false
                        val base = GalleryRestoreService(app, db, BackupPathAccess { _, _ -> BackupPathStatus.AVAILABLE })
                            .preview(fullBackupFixture(app, listOf("/new.jpg")))
                        for (many in listOf(false, true)) {
                            val plan = if (!many) base else base.copy(
                                statuses = (1..300).associate { BackupPathRequest("/protected/$it", false) to BackupPathStatus.PROTECTED },
                                targets = base.targets.mapValues { (_, target) -> target.copy(
                                    mediaCount = RestoreCategoryCount(300, 200, 100, removed = 30),
                                    folderCount = RestoreCategoryCount(30, 20, 10, conflicts = 4, removed = 2),
                                    profileCount = RestoreCategoryCount(20, 10, 10, conflicts = 3, removed = 2)) })
                            GalleryRestorePreviewDialog.show(host, plan) { fail("layout must not restore") }
                            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                            // Let WindowManager dispatch attachment/traversal before native Canvas draws scroll indicators.
                            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                            val root = dialog.window!!.decorView
                            val available = OxygenClassicDialog.availableHeight(host)
                            root.measure(View.MeasureSpec.makeMeasureSpec(dialog.window!!.attributes.width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
                            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                            val scroll = dialog.findViewById<ClassicDialogScrollView>(R.id.full_backup_preview)!!
                            val all = views(root)
                            val options = all.filterIsInstance<RadioButton>()
                            assertEquals(options[0].width, options[1].width)
                            assertTrue(root.height <= available)
                            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, dialog.window!!.attributes.height)
                            val restore = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            val bounds = android.graphics.Rect(0, 0, scroll.width, scroll.height)
                            (root as ViewGroup).offsetDescendantRectToMyCoords(scroll, bounds)
                            val actionBounds = android.graphics.Rect(0, 0, restore.width, restore.height)
                            root.offsetDescendantRectToMyCoords(restore, actionBounds)
                            assertTrue("scroll body cannot cover actions", bounds.bottom <= actionBounds.top)
                            assertTrue("restore stays visible", actionBounds.bottom <= root.height)
                            val title = all.filterIsInstance<TextView>().first { it.text == host.getString(R.string.full_backup_restore) }
                            assertTrue("title must be outside scroll", title !in views(scroll))
                            // Save representative Chinese layouts at both scroll positions for visual review.
                            if (font == 1.5f || (width == 360 && font == 1f)) {
                                val folder = File("build/reports/backup-ui-previews").apply { mkdirs() }
                                for (bottom in listOf(false, true)) {
                                    if (bottom) scroll.scrollTo(0, scroll.getChildAt(0).height)
                                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                                    root.draw(Canvas(bitmap))
                                    File(folder, "backup-${width}x$height-dark$dark-font$font-many$many-bottom$bottom.png").outputStream().use {
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                    bitmap.recycle()
                                }
                            }
                            dialog.dismiss()
                        }
                    } finally { ShadowDialog.getShownDialogs().toList().forEach { it.dismiss() }; controller.destroy() }
                }
            }
        } finally { db.close() }
    }
}
