package org.fossify.gallery.adapters

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager.widget.PagerAdapter
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.helpers.ViewerContentSignature
import org.fossify.gallery.helpers.ViewerMediaUpdatePolicy
import org.fossify.gallery.helpers.ViewerMediumIdentity
import org.fossify.gallery.helpers.GalleryPerfTrace
import org.fossify.gallery.models.Medium
import java.security.MessageDigest

interface ViewerPagerHost : ViewPagerFragment.FragmentListener {
    fun mayLoadFavoritePath(path: String): Boolean
}

internal class MyPagerAdapter(
    val activity: ViewerPagerHost,
    private val fm: FragmentManager,
    media: List<Medium>,
    preparedPositions: Map<ViewerMediumIdentity, Int>? = null,
    private val fragmentFactory: ((Medium) -> ViewPagerFragment)? = null
) : PagerAdapter() {
    private var items: List<Medium> = media
    private var positionByIdentity = preparedPositions ?: ViewerMediaUpdatePolicy.positionIndex(media)
    private val fragments = LinkedHashMap<ViewerMediumIdentity, ViewPagerFragment>()
    private val pendingRemovals = LinkedHashSet<ViewPagerFragment>()
    private var hasDeferredPages = false
    val media: List<Medium> get() = items
    private var currentPrimary: ViewPagerFragment? = null

    override fun getCount() = items.size

    override fun isViewFromObject(view: View, item: Any) = when (item) {
        is Fragment -> item.view === view
        is View -> item === view
        else -> false
    }

    private fun addDeferredPage(container: ViewGroup): View {
        hasDeferredPages = true
        return FrameLayout(container.context).also {
            it.isClickable = false
            container.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun createItem(medium: Medium): ViewPagerFragment {
        val bundle = Bundle().apply {
            putSerializable(MEDIUM, medium)
            putBoolean(SHOULD_INIT_FRAGMENT, true)
            putSignature(ViewerMediaUpdatePolicy.contentSignature(medium))
        }
        return (fragmentFactory?.invoke(medium) ?: if (medium.isVideo()) VideoFragment() else PhotoFragment()).also {
            it.arguments = bundle
        }
    }

    override fun getItemPosition(item: Any): Int {
        val fragment = item as? ViewPagerFragment ?: return POSITION_NONE
        val oldMedium = fragment.arguments?.getSerializable(MEDIUM) as? Medium ?: return POSITION_NONE
        val identity = ViewerMediaUpdatePolicy.identity(oldMedium)
        val position = positionByIdentity[identity] ?: return POSITION_NONE
        val fresh = items.getOrNull(position) ?: return POSITION_NONE
        if (!activity.mayLoadFavoritePath(fresh.path)) return POSITION_NONE

        val oldSignature = fragment.arguments?.getSignature() ?: ViewerMediaUpdatePolicy.contentSignature(oldMedium)
        val freshSignature = ViewerMediaUpdatePolicy.contentSignature(fresh)
        if (ViewerMediaUpdatePolicy.contentChanged(oldSignature, freshSignature)) {
            GalleryPerfTrace.event("viewer_fragment_content_reloaded")
            return POSITION_NONE
        }

        val merged = ViewerMediaUpdatePolicy.mergeMetadata(oldMedium, fresh)
        if (merged != oldMedium) fragment.updateMedium(merged)
        fragment.arguments?.putSignature(ViewerMediaUpdatePolicy.mergeSignature(oldSignature, freshSignature))
        return position
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val medium = items[position]
        if (!activity.mayLoadFavoritePath(medium.path)) return addDeferredPage(container)
        val identity = ViewerMediaUpdatePolicy.identity(medium)
        val existing = fragments[identity]
        if (fm.isStateSaved) {
            if (existing?.isAdded == true && existing !in pendingRemovals) {
                existing.listener = activity
                return existing
            }
            return addDeferredPage(container)
        }
        val fragment = existing?.takeUnless { it in pendingRemovals } ?: createItem(medium)
        GalleryPerfTrace.event(if (fragment === existing) "viewer_fragment_reused" else "viewer_fragment_created")
        // The listener and authorization are in place before commitNow can invoke onCreateView.
        fragment.listener = activity
        fragment.setMenuVisibility(fragment === currentPrimary)
        if (!fragment.isAdded) {
            val transaction = fm.beginTransaction().add(container.id, fragment, fragmentTag(identity))
            transaction.setMaxLifecycle(fragment, if (fragment === currentPrimary) Lifecycle.State.RESUMED else Lifecycle.State.STARTED)
            transaction.commitNow()
        }
        fragments[identity] = fragment
        pendingRemovals.remove(fragment)
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        if (any is View) {
            container.removeView(any)
            return
        }
        val fragment = any as? ViewPagerFragment ?: return
        if (fragment === currentPrimary) currentPrimary = null
        if (fm.isStateSaved) {
            // Keep the registration until FragmentManager can perform the real removal.
            fragment.view?.visibility = View.GONE
            pendingRemovals += fragment
            return
        }
        removeFragment(fragment)
    }

    private fun removeFragment(fragment: ViewPagerFragment) {
        if (fragment.isAdded && !fm.isStateSaved) fm.beginTransaction().remove(fragment).commitNow()
        fragments.entries.removeAll { it.value === fragment }
        pendingRemovals.remove(fragment)
        fragment.listener = null
        GalleryPerfTrace.event("viewer_fragment_destroyed")
    }

    /** Call from Activity.onPostResume; deferred removals are committed only after state is writable. */
    fun onHostResumed() {
        if (fm.isStateSaved) return
        val removals = pendingRemovals.toList()
        val transaction = fm.beginTransaction()
        removals.filter { it.isAdded }.forEach(transaction::remove)
        fragments.values.filter { it.isAdded && it !in pendingRemovals && it !== currentPrimary }
            .forEach { transaction.setMaxLifecycle(it, Lifecycle.State.STARTED) }
        currentPrimary?.takeIf { it.isAdded && it !in pendingRemovals }
            ?.let { transaction.setMaxLifecycle(it, Lifecycle.State.RESUMED) }
        if (removals.isNotEmpty() || currentPrimary?.isAdded == true) transaction.commitNow()
        removals.forEach {
            fragments.entries.removeAll { entry -> entry.value === it }
            pendingRemovals.remove(it)
            it.listener = null
        }
        if (hasDeferredPages) {
            hasDeferredPages = false
            notifyDataSetChanged()
        }
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, item: Any) {
        val next = item as? ViewPagerFragment
        if (next !== currentPrimary) {
            if (!fm.isStateSaved) {
                val transaction = fm.beginTransaction()
                currentPrimary?.takeIf { it.isAdded }?.let { transaction.setMaxLifecycle(it, Lifecycle.State.STARTED) }
                next?.takeIf { it.isAdded }?.let { transaction.setMaxLifecycle(it, Lifecycle.State.RESUMED) }
                transaction.commitNow()
            }
            currentPrimary = next
        }
        fragments.values.forEach { it.setMenuVisibility(it === next) }
        super.setPrimaryItem(container, position, item)
    }

    /** Catalog and indexes are prepared off the UI thread by ViewPagerActivity. */
    fun updateMedia(fresh: List<Medium>, preparedPositions: Map<ViewerMediumIdentity, Int>? = null): Boolean {
        if (items === fresh) return false
        items = fresh
        positionByIdentity = preparedPositions ?: ViewerMediaUpdatePolicy.positionIndex(fresh)
        GalleryPerfTrace.event("viewer_adapter_publish", fresh.size)
        notifyDataSetChanged()
        return true
    }

    /** Recreate deferred placeholders after the viewer authorizes a nearby page. */
    fun onAccessWindowUpdated() {
        if (hasDeferredPages) {
            hasDeferredPages = false
            notifyDataSetChanged()
        }
    }

    fun getCurrentFragment(position: Int) = items.getOrNull(position)?.let { fragments[ViewerMediaUpdatePolicy.identity(it)] }

    fun toggleFullscreen(isFullscreen: Boolean) {
        fragments.values.toSet().forEach { it.fullscreenToggled(isFullscreen) }
    }

    private fun fragmentTag(identity: ViewerMediumIdentity): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toString().toByteArray(Charsets.UTF_8))
        return "viewer:${digest.joinToString("") { "%02x".format(it) }}"
    }

    private fun Bundle.putSignature(signature: ViewerContentSignature) {
        putLong(KEY_SIZE, signature.size)
        putLong(KEY_MODIFIED, signature.modified)
        putLong(KEY_MEDIA_STORE_ID, signature.mediaStoreId)
        putLong(KEY_INVALIDATION_VERSION, signature.invalidationVersion)
    }

    private fun Bundle.getSignature() = ViewerContentSignature(
        getLong(KEY_SIZE), getLong(KEY_MODIFIED), getLong(KEY_MEDIA_STORE_ID),
        getLong(KEY_INVALIDATION_VERSION)
    )

    private companion object {
        const val KEY_SIZE = "viewer_size"
        const val KEY_MODIFIED = "viewer_modified"
        const val KEY_MEDIA_STORE_ID = "viewer_media_store_id"
        const val KEY_INVALIDATION_VERSION = "viewer_invalidation_version"
    }
}
