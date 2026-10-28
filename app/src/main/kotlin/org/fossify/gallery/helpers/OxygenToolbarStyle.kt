package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MySearchMenu
import java.util.WeakHashMap

/**
 * Fixed-height, flat OxygenOS classic toolbar on top of Commons' MySearchMenu.
 * All search listeners, keyboard behavior, and existing menu item callbacks
 * stay with the original Commons widget; only presentation is changed.
 */
object OxygenToolbarStyle {
    private const val TITLE_TAG = "oxygen_classic_toolbar_title"
    private val hooked = WeakHashMap<MySearchMenu, Boolean>()

    fun apply(activity: Activity, searchMenu: MySearchMenu, title: String, backButton: Boolean = false) {
        if (!hooked.containsKey(searchMenu)) {
            // Hook once: updateColors is called on every resume. Do not nest listeners.
            val oldOpen = searchMenu.onSearchOpenListener
            val oldClose = searchMenu.onSearchClosedListener
            searchMenu.onSearchOpenListener = {
                oldOpen?.invoke()
                render(searchMenu, title, backButton)
            }
            searchMenu.onSearchClosedListener = {
                oldClose?.invoke()
                searchMenu.binding.topToolbarSearch.clearFocus()
                render(searchMenu, title, backButton)
            }
            hooked[searchMenu] = true
        }
        render(searchMenu, title, backButton)
    }

    private fun render(menu: MySearchMenu, title: String, backButton: Boolean) {
        val activity = menu.context as? Activity ?: return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val opened = menu.isSearchOpen
        val isBack = backButton && !opened
        val background = activity.getProperBackgroundColor()
        val foreground = activity.getProperTextColor()

        val outer = menu.binding.searchBarContainer
        val container = menu.binding.toolbarContainer
        val icon = menu.binding.topToolbarSearchIcon
        val input = menu.binding.topToolbarSearch
        val actions = menu.binding.topToolbar

        // Commons' default 16dp padded rounded search field becomes a flat 56dp bar.
        outer.setPadding(0, 0, 0, 0)
        container.layoutParams = container.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        container.background = ColorDrawable(background)

        icon.layoutParams = (icon.layoutParams as RelativeLayout.LayoutParams).apply {
            width = dp(48)
            height = ViewGroup.LayoutParams.MATCH_PARENT
            removeRule(RelativeLayout.ALIGN_PARENT_START)
            removeRule(RelativeLayout.START_OF)
            if (opened || isBack) {
                addRule(RelativeLayout.ALIGN_PARENT_START)
            } else {
                addRule(RelativeLayout.START_OF, actions.id)
            }
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.setPadding(dp(12), dp(12), dp(12), dp(12))

        val heading = container.findViewWithTag<TextView>(TITLE_TAG) ?: TextView(activity).also {
            it.tag = TITLE_TAG
            it.gravity = Gravity.CENTER_VERTICAL
            it.textSize = 20f
            it.includeFontPadding = false
            it.maxLines = 1
            it.ellipsize = TextUtils.TruncateAt.END
            container.addView(it)
        }
        heading.text = title
        heading.setTextColor(foreground)
        heading.visibility = if (opened) View.GONE else View.VISIBLE
        heading.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.START_OF, if (isBack) actions.id else icon.id)
            marginStart = dp(if (isBack) 64 else 16)
            marginEnd = dp(8)
        }

        // Keep the EditText visible to the focus system (Commons' search
        // listener runs on focus). Fade only its appearance, not its focusability.
        input.alpha = if (opened) 1f else 0f
        input.isClickable = opened
        input.importantForAccessibility = if (opened) {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
}
