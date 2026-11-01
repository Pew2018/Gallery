package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.Looper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Directory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlbumSearchLifecycleTest {
    private class ManualExecutor : Executor {
        val tasks = ArrayList<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeAt(0).run() }
    }
    private fun directory(name: String) = Directory().apply { path = "/pictures/$name"; this.name = name }
    private fun field(activity: MainActivity, name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun withActivity(block: (MainActivity) -> Unit) {
        val app = RuntimeEnvironment.getApplication<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        app.config.lastMainViewAll = false
        // Create/start without resuming: exercise real presentation and lifecycle
        // methods without launching an unrelated storage scan in onResume.
        val controller = Robolectric.buildActivity(MainActivity::class.java).create(Bundle()).start()
        try { block(controller.get()) } finally { controller.stop().destroy() }
    }

    private fun publish(activity: MainActivity, directories: ArrayList<Directory>, query: String? = null,
                        isCurrent: () -> Boolean = { true }) {
        val method = MainActivity::class.java.declaredMethods.single { it.name == "setupAdapter" }
        method.isAccessible = true
        method.invoke(activity, directories, query, false, true, null, false, isCurrent)
    }

    @Test fun newlyDiscoveredAlbumIsSearchableAndClearingSearchKeepsTheLatestCatalog() = withActivity { activity ->
        activity.config.groupDirectSubfolders = false
        val a = directory("old")
        val b = directory("new")
        publish(activity, arrayListOf(a))
        val recycler = activity.findViewById<MyRecyclerView>(R.id.directories_grid)
        val executor = ManualExecutor()
        val adapter = DirectoryAdapter(activity, arrayListOf(a), null, recycler, false, diffExecutor = executor) {}
        recycler.adapter = adapter
        publish(activity, arrayListOf(a, b))
        @Suppress("UNCHECKED_CAST")
        val catalog = field(activity, "mDirsIgnoringSearch").get(activity) as ArrayList<Directory>
        assertEquals(setOf(a.path, b.path), catalog.map { it.path }.toSet())
        publish(activity, catalog, "new")
        executor.drain()
        assertEquals(listOf(b.path), adapter.dirs.map { it.path })
        publish(activity, catalog, "")
        executor.drain()
        assertEquals(setOf(a.path, b.path), adapter.dirs.map { it.path }.toSet())
    }

    @Test fun invalidatedCategoryResultCannotCommitAnAlreadyCalculatedDiff() = withActivity { activity ->
        val a = directory("old")
        publish(activity, arrayListOf(a))
        val recycler = activity.findViewById<MyRecyclerView>(R.id.directories_grid)
        val executor = ManualExecutor()
        val adapter = DirectoryAdapter(activity, arrayListOf(a), null, recycler, false, diffExecutor = executor) {}
        recycler.adapter = adapter
        var current = true
        publish(activity, arrayListOf(directory("retired")), isCurrent = { current })
        current = false
        executor.drain()
        assertEquals(listOf(a.path), adapter.dirs.map { it.path })
    }

    @Test fun pauseWithoutStopReleasesOnlyTheInvalidatedCategoryLoad() = withActivity { activity ->
        field(activity, "categorizedLoadActive").setBoolean(activity, true)
        field(activity, "mIsGettingDirs").setBoolean(activity, true)
        val before = field(activity, "categorizedLoadVersion").getLong(activity)
        MainActivity::class.java.getDeclaredMethod("onPause").apply { isAccessible = true }.invoke(activity)
        assertFalse(field(activity, "categorizedLoadActive").getBoolean(activity))
        assertFalse(field(activity, "mIsGettingDirs").getBoolean(activity))
        assertTrue(field(activity, "categorizedLoadVersion").getLong(activity) > before)
        // An ordinary scan is not canceled merely by a temporary pause.
        field(activity, "mIsGettingDirs").setBoolean(activity, true)
        MainActivity::class.java.getDeclaredMethod("onPause").apply { isAccessible = true }.invoke(activity)
        assertTrue(field(activity, "mIsGettingDirs").getBoolean(activity))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
