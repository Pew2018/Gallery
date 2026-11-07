package org.fossify.gallery.helpers

import android.app.Activity
import android.view.MenuItem
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Two independent page families sharing one stable catalogue of quick actions. */
enum class ToolbarQuickScope { HOME, FOLDER }

object ToolbarQuickActions {
    const val SEARCH = "search"
    const val SORT = "sort"
    const val FILTER = "filter"
    const val VIEW = "view"
    const val COLUMNS = "columns"
    const val HIDDEN = "hidden"
    const val SLIDESHOW = "slideshow"
    const val GROUP = "group"
    const val FILENAME = "filename"
    const val EXCLUDED = "excluded"

    val defaults = listOf(SEARCH, SORT, FILTER)
    val homeOptions = listOf(SEARCH, SORT, FILTER, VIEW, COLUMNS, HIDDEN, SLIDESHOW, EXCLUDED)
    val folderOptions = listOf(SEARCH, SORT, FILTER, VIEW, COLUMNS, HIDDEN, SLIDESHOW, GROUP, FILENAME)

    private val slots = listOf(
        R.id.quick_toolbar_slot_0,
        R.id.quick_toolbar_slot_1,
        R.id.quick_toolbar_slot_2,
        R.id.quick_toolbar_slot_3,
        R.id.quick_toolbar_slot_4
    )
    private data class AppliedSlot(val action: String, val item: WeakReference<MenuItem>)
    private data class AppliedPlan(
        val actions: List<String>,
        val slots: Map<Int, AppliedSlot>,
        val measuredWidthDp: Int,
        val fixedActionCount: Int
    )
    private val displayed = WeakHashMap<MySearchMenu, AppliedPlan>()

    fun options(scope: ToolbarQuickScope): List<String> =
        if (scope == ToolbarQuickScope.HOME) homeOptions else folderOptions

    /** The fixed browse-mode switch belongs only to the All files root page. */
    fun hasFixedBrowseSwitch(path: String, section: GallerySection?): Boolean =
        path.isEmpty() && section == GallerySection.ALL

    /** Keep search anchored at the left; drag-order applies to the remaining actions. */
    fun normalize(scope: ToolbarQuickScope, values: List<String>): List<String> {
        val valid = values.filter { it in options(scope) }.distinct().take(5)
        if (valid.isEmpty()) return defaults
        return (if (SEARCH in valid) listOf(SEARCH) else emptyList()) + valid.filter { it != SEARCH }
    }

    /** Pure planning boundary for screen-size, unsupported actions and test coverage. */
    fun plan(
        scope: ToolbarQuickScope,
        selected: List<String>,
        slotsAvailable: Int,
        supported: (String) -> Boolean = { true }
    ): List<String> = normalize(scope, selected)
        .filter { it != SEARCH && supported(it) }
        .take(slotsAvailable.coerceIn(0, 5))

    fun availableSlots(
        widthDp: Int,
        hasBack: Boolean,
        showSearch: Boolean,
        fixedActionCount: Int = 0
    ): Int {
        val reservedDp = (if (hasBack) 48 else 16) + 96 + 48 +
            (if (showSearch) 48 else 0) + fixedActionCount.coerceAtLeast(0) * 48
        return ((widthDp - reservedDp) / 48f).toInt().coerceIn(0, slots.size)
    }

    /** A page-level action outside the customizable quick-action catalogue. */
    fun photoProfileManageVisible(isPhotoProfiles: Boolean, isPicker: Boolean, isRecycleBin: Boolean): Boolean =
        isPhotoProfiles && !isPicker && !isRecycleBin

    fun iconRes(action: String): Int = when (action) {
        SORT -> org.fossify.commons.R.drawable.ic_sort_vector
        FILTER -> R.drawable.ic_quick_media_filter_vector
        VIEW -> R.drawable.ic_quick_view_vector
        COLUMNS -> org.fossify.commons.R.drawable.ic_column_count_vector
        HIDDEN -> R.drawable.ic_quick_hidden_vector
        SLIDESHOW -> R.drawable.ic_slideshow_vector
        GROUP -> R.drawable.ic_quick_group_vector
        FILENAME -> org.fossify.commons.R.drawable.ic_label_vector
        EXCLUDED -> R.drawable.ic_quick_excluded_vector
        else -> org.fossify.commons.R.drawable.ic_search_vector
    }

    fun titleRes(action: String): Int = when (action) {
        SEARCH -> org.fossify.commons.R.string.search
        SORT -> R.string.quick_action_sort
        FILTER -> R.string.quick_action_filter
        VIEW -> R.string.quick_action_view
        COLUMNS -> R.string.quick_action_columns
        HIDDEN -> R.string.quick_action_hidden
        SLIDESHOW -> R.string.quick_action_slideshow
        GROUP -> R.string.quick_action_group
        FILENAME -> R.string.quick_action_filename
        EXCLUDED -> R.string.quick_action_excluded
        else -> R.string.quick_action_sort
    }

    /** Menu slot indices, not action names, own visible ordering. */
    fun actionForSlot(menu: MySearchMenu, itemId: Int): String? = displayed[menu]?.slots?.get(itemId)?.action

    fun isSlot(itemId: Int): Boolean = itemId in slots

    /** Rebind an icon only when its action or the actual inflated item changed. */
    fun shouldBindSlot(previousAction: String?, sameMenuItem: Boolean, action: String?): Boolean =
        action != null && (previousAction != action || !sameMenuItem)

    /**
     * Preserve 48dp click targets, overflow, navigation and a 96dp title area.
     * Five preferences can be saved, but only actions fitting the current width
     * become icons; original overflow commands remain accessible at any width.
     */
    fun apply(
        activity: Activity,
        searchMenu: MySearchMenu,
        scope: ToolbarQuickScope,
        selected: List<String>,
        hasBack: Boolean,
        fixedActionCount: Int = 0,
        supported: (String) -> Boolean = { true },
        enabled: (String) -> Boolean = { true }
    ) {
        val chosen = normalize(scope, selected)
        val showSearch = SEARCH in chosen
        val menu = searchMenu.requireToolbar().menu
        val density = activity.resources.displayMetrics.density
        // screenWidthDp is already expressed in the app's usable window width.
        // It avoids planning from the physical display width before the toolbar
        // is measured (which can include space lost to system/window insets).
        val measuredDp = (searchMenu.width.takeIf { it > 0 }?.div(density)
            ?: activity.resources.configuration.screenWidthDp.toFloat()).toInt()
        val count = availableSlots(measuredDp, hasBack, showSearch, fixedActionCount)
        val actions = plan(scope, chosen, count, supported)
        val previous = displayed[searchMenu]
        val activeSlots = mutableMapOf<Int, AppliedSlot>()
        slots.forEachIndexed { index, id ->
            val item = menu.findItem(id) ?: return@forEachIndexed
            val action = actions.getOrNull(index)
            val priorSlot = previous?.slots?.get(id)
            val visible = action != null
            if (item.isVisible != visible) item.isVisible = visible
            if (action != null) {
                if (shouldBindSlot(priorSlot?.action, priorSlot?.item?.get() === item, action)) {
                    item.setIcon(iconRes(action))
                    item.setTitle(titleRes(action))
                }
                val shouldEnable = enabled(action)
                if (item.isEnabled != shouldEnable) item.isEnabled = shouldEnable
                activeSlots[id] = AppliedSlot(action, WeakReference(item))
            }
        }
        displayed[searchMenu] = AppliedPlan(actions, activeSlots, measuredDp, fixedActionCount)
        menu.findItem(R.id.quick_toolbar_search_overflow)?.let {
            if (it.isVisible == showSearch) it.isVisible = !showSearch
        }
        OxygenToolbarStyle.setSearchActionVisible(searchMenu, showSearch)
        // Tinting is independently cached by item identity and foreground so a
        // content refresh does not replace otherwise unchanged drawables.
        OxygenToolbarStyle.tintMenu(menu, OxygenThemeEngine.toolbarForeground(activity, activity.config))
    }
}
