package org.fossify.gallery.helpers

import android.app.Activity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.fossify.commons.views.MySearchMenu

/** Explicit toolbar actions, never a replacement for system IME back. */
object OxygenSearchNavigation {
    fun hideImeAndClearFocus(activity: Activity, menu: MySearchMenu) {
        WindowInsetsControllerCompat(activity.window, menu)
            .hide(WindowInsetsCompat.Type.ime())
        menu.binding.topToolbarSearch.clearFocus()
    }

    fun closeInlineSearch(activity: Activity, menu: MySearchMenu) {
        hideImeAndClearFocus(activity, menu)
        if (menu.isSearchOpen) menu.closeSearch()
    }
}
