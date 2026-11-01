package org.fossify.gallery.adapters

import android.os.Bundle
import android.view.ViewGroup
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.helpers.ViewerMediaUpdatePolicy
import org.fossify.gallery.models.Medium

class MyPagerAdapter(
    val activity: ViewPagerActivity,
    private val fm: FragmentManager,
    media: List<Medium>,
    preparedPositions: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>? = null
) : PagerAdapter() {
    private var items: List<Medium> = media
    private var positionByIdentity = preparedPositions ?: ViewerMediaUpdatePolicy.positionIndex(media)
    private val fragments = LinkedHashMap<org.fossify.gallery.helpers.ViewerMediumIdentity, ViewPagerFragment>()
    val media: List<Medium> get() = items
    private var currentPrimary: ViewPagerFragment? = null

    override fun getCount() = items.size

    override fun isViewFromObject(view: View, item: Any) = (item as? Fragment)?.view === view

    private fun createItem(position: Int): Fragment {
        val medium = items[position]
        if (!activity.mayLoadFavoritePath(medium.path)) return Fragment()
        val bundle = Bundle()
        bundle.putSerializable(MEDIUM, medium)
        bundle.putBoolean(SHOULD_INIT_FRAGMENT, true)
        val fragment = if (medium.isVideo()) {
            VideoFragment()
        } else {
            PhotoFragment()
        }

        fragment.arguments = bundle
        return fragment
    }

    override fun getItemPosition(item: Any): Int {
        val fragment = item as? Fragment ?: return POSITION_NONE
        val oldMedium = fragment.arguments?.getSerializable(MEDIUM) as? Medium
            ?: return POSITION_NONE
        val identity = ViewerMediaUpdatePolicy.identity(oldMedium)
        val position = positionByIdentity[identity] ?: POSITION_NONE
        if (position == POSITION_NONE || !activity.mayLoadFavoritePath(items[position].path)) {
            fragments.remove(identity)
            return POSITION_NONE
        }
        return position
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val identity = ViewerMediaUpdatePolicy.identity(items[position])
        val fragment = fragments[identity] ?: createItem(position)
        if (!fragment.isAdded) {
            fm.beginTransaction()
                .add(container.id, fragment, fragmentTag(identity))
                .commitNow()
            fm.beginTransaction()
                .setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.STARTED)
                .commitNow()
        }
        if (fragment is ViewPagerFragment) {
            fragment.listener = activity
            fragment.setMenuVisibility(fragment === currentPrimary)
            fragments[identity] = fragment
        }
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        val fragment = any as? Fragment
        if (fragment != null) {
            fragments.entries.removeAll { it.value === fragment }
            if (fragment === currentPrimary) currentPrimary = null
            if (fragment.isAdded && !fm.isStateSaved) fm.beginTransaction().remove(fragment).commitNow()
        }
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, item: Any) {
        val next = item as? ViewPagerFragment
        if (next !== currentPrimary) {
            val transaction = fm.beginTransaction()
            currentPrimary?.takeIf { it.isAdded }?.let { transaction.setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED) }
            next?.takeIf { it.isAdded }?.let { transaction.setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.RESUMED) }
            if (!fm.isStateSaved) transaction.commitNow()
            currentPrimary = next
        }
        fragments.values.forEach { it.setMenuVisibility(it === next) }
        super.setPrimaryItem(container, position, item)
    }

    /** Reconcile the live catalog without replacing this adapter or stable fragments. */
    fun updateMedia(
        fresh: List<Medium>,
        preparedPositions: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>? = null
    ): Boolean {
        if (items === fresh) return false
        items = fresh
        positionByIdentity = preparedPositions ?: ViewerMediaUpdatePolicy.positionIndex(fresh)
        notifyDataSetChanged()
        return true
    }

    fun getCurrentFragment(position: Int) = items.getOrNull(position)?.let {
        fragments[ViewerMediaUpdatePolicy.identity(it)]
    }

    fun toggleFullscreen(isFullscreen: Boolean) {
        for (fragment in fragments.values.toSet()) {
            fragment.fullscreenToggled(isFullscreen)
        }
    }

    private fun fragmentTag(identity: org.fossify.gallery.helpers.ViewerMediumIdentity): String {
        val bytes = identity.toString().toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return "viewer:${digest.joinToString("") { "%02x".format(it) }}"
    }

}

