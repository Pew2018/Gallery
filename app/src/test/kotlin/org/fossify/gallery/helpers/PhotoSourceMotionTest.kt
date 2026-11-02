package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import org.fossify.gallery.R
import org.fossify.gallery.activities.PhotoProfileEditActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoSourceMotionTest {
    @Test fun addingRemovingAndRefreshingSourcesKeepsUnchangedRowInstances() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val intent = Intent(app, PhotoProfileEditActivity::class.java)
            .putExtra(PhotoProfileEditActivity.EXTRA_CREATING, true)
        val controller = Robolectric.buildActivity(PhotoProfileEditActivity::class.java, intent).setup()
        val activity = controller.get()
        val resolver = activity.contentResolver
        val oldScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val draftField = activity.javaClass.getDeclaredField("draft").apply { isAccessible = true }
            val render = activity.javaClass.getDeclaredMethod("renderSources").apply { isAccessible = true }
            val original = draftField.get(activity) as PhotoProfile
            val a = PhotoSource(PhotoSourceType.FOLDER, "/storage/emulated/0/DCIM/A")
            val b = PhotoSource(PhotoSourceType.FOLDER, "/storage/emulated/0/DCIM/B")
            val c = PhotoSource(PhotoSourceType.FAVORITES)
            val container = activity.findViewById<LinearLayout>(R.id.photo_profile_edit_sources)
            draftField.set(activity, original.copy(sources = listOf(a, b)))
            render.invoke(activity)
            val aRow = container.getChildAt(0)
            val bRow = container.getChildAt(2)
            render.invoke(activity)
            assertSame(aRow, container.getChildAt(0))
            assertSame(bRow, container.getChildAt(2))
            draftField.set(activity, original.copy(sources = listOf(a, b, c)))
            render.invoke(activity)
            assertSame(aRow, container.getChildAt(0))
            assertSame(bRow, container.getChildAt(2))
            assertEquals(1f, container.getChildAt(4).alpha, 0f)
            draftField.set(activity, original.copy(sources = listOf(b, c)))
            render.invoke(activity)
            assertSame(bRow, container.getChildAt(0))
            assertEquals(3, container.childCount)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, oldScale)
            controller.pause().stop().destroy()
        }
    }
}
