package org.fossify.gallery.helpers

import android.content.Context
import android.view.Gravity
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/** One presentation boundary for toolbar overflow, submenus and anchored action menus. */
object OxygenClassicMenus {
    fun theme(context: Context): Int =
        if (OxygenThemeEngine.snapshot(context, context.config).dark) {
            R.style.OxygenClassicPopupTheme_Dark
        } else {
            R.style.OxygenClassicPopupTheme_Light
        }

    fun createPopup(context: Context, anchor: View): PopupMenu =
        PopupMenu(ContextThemeWrapper(context, theme(context)), anchor, Gravity.END)
}
