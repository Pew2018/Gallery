package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.R
import org.fossify.gallery.activities.PhotoProfileEditActivity
import org.fossify.gallery.activities.PhotoProfileManageActivity
import org.fossify.gallery.adapters.PhotoProfileManageAdapter
import org.fossify.gallery.adapters.PhotoProfileManageRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PhotoProfileManageActivityTest {
    @Test fun managementPageShowsEmptyStateAndCreateLaunchesExistingEditor() {
        val app = RuntimeEnvironment.getApplication()
        val store = PhotoProfileRepository.of(app)
        store.state().profiles.forEach { store.delete(it.id) }

        val controller = Robolectric.buildActivity(PhotoProfileManageActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.photo_profile_manage_empty).visibility)
        assertEquals(View.GONE, activity.findViewById<RecyclerView>(R.id.photo_profile_manage_list).visibility)

        activity.findViewById<View>(R.id.photo_profile_manage_create).performClick()
        val editorIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(PhotoProfileEditActivity::class.java.name, editorIntent.component?.className)
        assertTrue(editorIntent.getBooleanExtra(PhotoProfileEditActivity.EXTRA_CREATING, false))
        controller.pause().stop().destroy()
        store.state().profiles.forEach { store.delete(it.id) }
    }

    @Test fun rowAndOverflowClicksUseTheStableProfileIdIndependently() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
            baseConfig.isSystemThemeEnabled = false
            baseConfig.backgroundColor = Color.WHITE
            baseConfig.textColor = Color.BLACK
            baseConfig.primaryColor = Color.rgb(33, 150, 243)
        }
        val id = "287b4c28-6c8b-4331-b9dd-6fedee7ff9bd"
        var selected: String? = null
        var menuTarget: String? = null
        val adapter = PhotoProfileManageAdapter(
            onProfileClick = { selected = it },
            onMoreClick = { profileId, _ -> menuTarget = profileId }
        )
        adapter.submitList(listOf(PhotoProfileManageRow(id, "A long profile name that should truncate safely", 3, true)))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.VISIBLE, holder.binding.profileManageCurrent.visibility)
        holder.binding.profileManageMore.performClick()
        assertEquals(id, menuTarget)
        assertNull(selected)
        holder.binding.profileManageRow.performClick()
        assertEquals(id, selected)
        assertEquals((48 * activity.resources.displayMetrics.density + 0.5f).toInt(),
            holder.binding.profileManageMore.layoutParams.width)
        adapter.submitList(emptyList())
        activity.finish()
    }
}
