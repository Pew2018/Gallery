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
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
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

    @Test fun animatedRapidDeletionDoesNotDetachRetainedRowsOrResurrectRemovedSources() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(PhotoProfileEditActivity::class.java,
            Intent(app, PhotoProfileEditActivity::class.java)
                .putExtra(PhotoProfileEditActivity.EXTRA_CREATING, true)).setup()
        val activity = controller.get()
        val resolver = activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val draftField = activity.javaClass.getDeclaredField("draft").apply { isAccessible = true }
        val rowsField = activity.javaClass.getDeclaredField("sourceRows").apply { isAccessible = true }
        val render = activity.javaClass.getDeclaredMethod("renderSources").apply { isAccessible = true }
        val original = draftField.get(activity) as PhotoProfile
        val a = PhotoSource(PhotoSourceType.FOLDER, "/storage/emulated/0/DCIM/A")
        val b = PhotoSource(PhotoSourceType.FOLDER, "/storage/emulated/0/DCIM/B")
        val c = PhotoSource(PhotoSourceType.FAVORITES)
        val container = activity.findViewById<LinearLayout>(R.id.photo_profile_edit_sources)
        fun display(sources: List<PhotoSource>) {
            draftField.set(activity, original.copy(sources = sources))
            render.invoke(activity)
            container.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1800, android.view.View.MeasureSpec.AT_MOST))
            container.layout(0, 0, 1080, container.measuredHeight)
        }
        try {
            display(listOf(a, b, c))
            val cRow = container.getChildAt(4)
            val aRow = container.getChildAt(0)
            aRow.animate().cancel()
            aRow.alpha = 0.35f // Delete while the entrance fade is incomplete.
            var detaches = 0
            cRow.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) = Unit
                override fun onViewDetachedFromWindow(view: android.view.View) { detaches++ }
            })
            assertTrue(container.layoutTransition.isTransitionTypeEnabled(android.animation.LayoutTransition.DISAPPEARING))
            display(listOf(b, c))
            assertTrue("deletion must not flash a partly visible row opaque", aRow.alpha <= 0.35f)
            display(listOf(c))
            render.invoke(activity) // A late background status update must use current draft.
            assertSame(cRow, container.getChildAt(0))
            assertEquals(0, detaches)
            assertEquals(setOf(c), (rowsField.get(activity) as Map<*, *>).keys)
            assertEquals(1, container.childCount)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                500, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals(1f, cRow.alpha, 0f)
            display(emptyList())
            controller.pause().stop().destroy()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                500, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue((rowsField.get(activity) as Map<*, *>).isEmpty())
            assertEquals(0, container.childCount)
            assertNull(container.layoutTransition)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }

}
