package org.fossify.gallery.helpers

import android.app.Activity
import android.view.View
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R

/** Page-family scoped toolbar actions. IDs are stable preference values, never view positions. */
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

    fun options(scope: ToolbarQuickScope) =
        if (scope == ToolbarQuickScope.HOME) homeOptions else folderOptions

    fun normalize(scope: ToolbarQuickScope, values: List<String>): List<String> =
        values.filter { it in options(scope) }.distinct().take(5).ifEmpty { defaults }

    fun menuItemId(action: String): Int? = when (action) {
        SORT -> R.id.quick_toolbar_sort
        FILTER -> R.id.quick_toolbar_filter
        VIEW -> R.id.quick_toolbar_view
        COLUMNS -> R.id.quick_toolbar_columns
        HIDDEN -> R.id.quick_toolbar_hidden
        SLIDESHOW -> R.id.quick_toolbar_slideshow
        GROUP -> R.id.quick_toolbar_group
        FILENAME -> R.id.quick_toolbar_filename
        EXCLUDED -> R.id.quick_toolbar_excluded
        else -> null
    }

    fun titleRes(action: String): Int = when (action) {
        SEARCH -> org.fossify.commons.R.string.search
        SORT -> org.fossify.gallery.R.string.quick_action_sort
        FILTER -> org.fossify.gallery.R.string.quick_action_filter
        VIEW -> org.fossify.gallery.R.string.quick_action_view
        COLUMNS -> org.fossify.gallery.R.string.quick_action_columns
        HIDDEN -> org.fossify.gallery.R.string.quick_action_hidden
        SLIDESHOW -> org.fossify.gallery.R.string.quick_action_slideshow
        GROUP -> org.fossify.gallery.R.string.quick_action_group
        FILENAME -> org.fossify.gallery.R.string.quick_action_filename
        EXCLUDED -> org.fossify.gallery.R.string.quick_action_excluded
        else -> org.fossify.gallery.R.string.quick_action_sort
    }

    /**
     * 48dp targets remain full-sized. Reserve navigation, title, search, overflow
     * and any existing fixed navigation action before allocating quick slots.
     * Items that do not fit remain accessible in the existing overflow menu.
     */
    fun apply(
        activity: Activity,
        searchMenu: MySearchMenu,
        scope: ToolbarQuickScope,
        selected: List<String>,
        hasBack: Boolean,
        fixedActionCount: Int = 0,
        supported: (String) -> Boolean = { true }
    ) {
        val chosen = normalize(scope, selected)
        val showSearch = SEARCH in chosen
        val toolbar = searchMenu.requireToolbar()
        val menu = toolbar.menu
        val density = activity.resources.displayMetrics.density
        val measuredDp = (searchMenu.width.takeIf { it > 0 }
            ?: activity.resources.displayMetrics.widthPixels) / density
        val reservedDp = (if (hasBack) 48 else 16) + 104 + 48 +
            (if (showSearch) 48 else 0) + fixedActionCount * 48
        val slots = ((measuredDp - reservedDp) / 48f).toInt().coerceIn(0, 5)
        val available = chosen.filter { it != SEARCH && supported(it) }.take(slots)
        for (action in (homeOptions + folderOptions).distinct()) {
            menuItemId(action)?.let { menu.findItem(it)?.isVisible = action in available }
        }
        // This entry is always available when the inline search icon is removed.
        menu.findItem(R.id.quick_toolbar_search_overflow)?.isVisible = !showSearch
        OxygenToolbarStyle.setSearchActionVisible(searchMenu, showSearch)
    }

    /** Common action menu ids used by both normal and favorite pages. */
    fun actionForMenuId(itemId: Int): String? = (homeOptions + folderOptions).distinct()
        .firstOrNull { menuItemId(it) == itemId }
}
