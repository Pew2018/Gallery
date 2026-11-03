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
        val app = RuntimeEnvironment.getApplication()
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
        method.invoke(activity, directories, query, false, true, null, false, isCurrent, {}, {})
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
    @Test fun sortingSearchResultsKeepsTheFullCatalogAndTheActiveScan() = withActivity { activity ->
        activity.config.groupDirectSubfolders = false
        activity.config.searchAllFilesByDefault = false
        val a = directory("alpha")
        val b = directory("beta")
        publish(activity, arrayListOf(a, b))
        val recycler = activity.findViewById<MyRecyclerView>(R.id.directories_grid)
        val executor = ManualExecutor()
        val adapter = DirectoryAdapter(activity, arrayListOf(a), null, recycler, false, diffExecutor = executor) {}
        recycler.adapter = adapter
        val epoch = field(activity, "directoryScanEpoch").get(activity) as DirectoryScanEpoch
        val request = epoch.begin()
        field(activity, "mIsGettingDirs").setBoolean(activity, true)
        activity.config.directorySorting = org.fossify.commons.helpers.SORT_BY_NAME
        MainActivity::class.java.getDeclaredMethod("refreshDirectoryPresentation").apply { isAccessible = true }.invoke(activity)
        executor.drain()
        @Suppress("UNCHECKED_CAST")
        val catalog = field(activity, "mDirsIgnoringSearch").get(activity) as ArrayList<Directory>
        assertEquals(setOf(a.path, b.path), catalog.map { it.path }.toSet())
        assertTrue(epoch.isCurrent(request))
        assertTrue(field(activity, "mIsGettingDirs").getBoolean(activity))
        publish(activity, catalog, "")
        executor.drain()
        assertEquals(setOf(a.path, b.path), adapter.dirs.map { it.path }.toSet())
    }

    @Test fun sortingGroupedRowsDoesNotDiscardThePhysicalChildren() = withActivity { activity ->
        activity.config.groupDirectSubfolders = true
        val a = directory("a").apply { path = "/pictures/shared/a" }
        val b = directory("b").apply { path = "/pictures/shared/b" }
        publish(activity, arrayListOf(a, b))
        val recycler = activity.findViewById<MyRecyclerView>(R.id.directories_grid)
        val executor = ManualExecutor()
        val adapter = DirectoryAdapter(activity,
            arrayListOf(directory("shared").apply { path = "/pictures/shared"; containsMediaFilesDirectly = false }),
            null, recycler, false, diffExecutor = executor) {}
        recycler.adapter = adapter
        MainActivity::class.java.getDeclaredMethod("refreshDirectoryPresentation").apply { isAccessible = true }.invoke(activity)
        @Suppress("UNCHECKED_CAST")
        val catalog = field(activity, "mDirs").get(activity) as ArrayList<Directory>
        assertEquals(setOf(a.path, b.path), catalog.map { it.path }.toSet())
        field(activity, "mCurrentPathPrefix").set(activity, "/pictures/shared")
        publish(activity, catalog, "")
        executor.drain()
        assertEquals(setOf(a.path, b.path), adapter.dirs.map { it.path }.toSet())
    }

    @Test fun pausedCategoryRestartsWhileInlineSearchStaysOpen() = withActivity { activity ->
        activity.config.searchAllFilesByDefault = false
        field(activity, "mFolderVisibility").set(activity, booleanArrayOf(true, false, false, true))
        val menu = activity.findViewById<org.fossify.commons.views.MySearchMenu>(R.id.main_menu)
        // This fixture deliberately never resumes the Activity (to avoid an
        // unrelated permission/storage scan), so there is no real window focus.
        // Supply the search state; the behavior under test is load recovery.
        menu.isSearchOpen = true
        field(activity, "categorizedLoadActive").setBoolean(activity, true)
        field(activity, "mIsGettingDirs").setBoolean(activity, true)
        MainActivity::class.java.getDeclaredMethod("onPause").apply { isAccessible = true }.invoke(activity)
        assertTrue(field(activity, "categorizedReloadPending").getBoolean(activity))
        val before = field(activity, "categorizedLoadVersion").getLong(activity)
        MainActivity::class.java.getDeclaredMethod("resumeCategorizedLoadIfNeeded").apply { isAccessible = true }.invoke(activity)
        assertTrue(menu.isSearchOpen)
        assertTrue(field(activity, "categorizedLoadActive").getBoolean(activity))
        assertFalse(field(activity, "categorizedReloadPending").getBoolean(activity))
        assertTrue(field(activity, "categorizedLoadVersion").getLong(activity) > before)
    }

}
