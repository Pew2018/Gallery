package org.fossify.gallery.helpers

import androidx.annotation.StringRes
import org.fossify.gallery.R

/** Resource ids for the next action offered by the two favorite-folder menu items. */
object FavoriteMenuActionTitles {
    @StringRes
    fun excluded(currentlyShown: Boolean): Int =
        if (currentlyShown) R.string.favorite_hide_excluded_action else R.string.favorite_show_excluded_action

    @StringRes
    fun hidden(currentlyShown: Boolean): Int =
        if (currentlyShown) R.string.favorite_hide_hidden_action else R.string.favorite_show_hidden_action
}
