package org.fossify.gallery.helpers

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MySearchMenu
import java.util.WeakHashMap

/**
 * Classic, fixed-height toolbar atop Commons 7.1.3 MySearchMenu.
 * Keep Commons' focus-driven search state and keyboard listeners unchanged.
 */
object OxygenToolbarStyle {
    private const val TITLE_TAG = "oxygen_classic_toolbar_title"
    private const val BACK_TAG = "oxygen_classic_toolbar_back"
    private class State(var title: String, var back: Boolean)
    private val hooked = WeakHashMap<MySearchMenu, State>()

    fun apply(activity: Activity, searchMenu: MySearchMenu, title: String, backButton: Boolean = false) {
        val current = hooked[searchMenu]
        if (current != null) {
            current.title = title
            current.back = backButton
            render(searchMenu, current)
            return
        }
        val state = State(title, backButton)
        val oldOpen = searchMenu.onSearchOpenListener
        val oldClose = searchMenu.onSearchClosedListener
        searchMenu.onSearchOpenListener = {
            oldOpen?.invoke()
            render(searchMenu, state)
        }
        searchMenu.onSearchClosedListener = {
            oldClose?.invoke()
            searchMenu.binding.topToolbarSearch.clearFocus()
            render(searchMenu, state)
        }
        hooked[searchMenu] = state
        render(searchMenu, state)
    }

    private fun render(menu: MySearchMenu, state: State) {
        val activity = menu.context as? Activity ?: return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val opened = menu.isSearchOpen
        val foreground = activity.getProperTextColor()
        val container = menu.binding.toolbarContainer
        val icon = menu.binding.topToolbarSearchIcon
        val input = menu.binding.topToolbarSearch
        val actions = menu.binding.topToolbar

        menu.binding.searchBarContainer.setPadding(0, 0, 0, 0)
        container.layoutParams = container.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val surface = activity.getProperBackgroundColor()
        container.background = ColorDrawable(surface)
        actions.setPopupTheme(if (ColorUtils.calculateLuminance(surface) >= 0.5) {
            org.fossify.gallery.R.style.OxygenClassicPopupTheme_Light
        } else {
            org.fossify.gallery.R.style.OxygenClassicPopupTheme_Dark
        })
        actions.visibility = if (opened) View.GONE else View.VISIBLE

        // Closed search icon is to the left of menu actions; open arrow is leading.
        icon.layoutParams = (icon.layoutParams as RelativeLayout.LayoutParams).apply {
            width = dp(48)
            height = ViewGroup.LayoutParams.MATCH_PARENT
            removeRule(RelativeLayout.ALIGN_PARENT_START)
            removeRule(RelativeLayout.START_OF)
            if (opened) addRule(RelativeLayout.ALIGN_PARENT_START)
            else addRule(RelativeLayout.START_OF, actions.id)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.setPadding(dp(12), dp(12), dp(12), dp(12))

        // Commons opens search on input focus. Keep it focusable even when
        // visually closed, then restore full input width on the focus callback.
        input.layoutParams = (input.layoutParams as RelativeLayout.LayoutParams).apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
            removeRule(RelativeLayout.START_OF)
            removeRule(RelativeLayout.END_OF)
            removeRule(RelativeLayout.ALIGN_PARENT_END)
            removeRule(RelativeLayout.ALIGN_PARENT_START)
            if (opened) {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                addRule(RelativeLayout.END_OF, icon.id)
                addRule(RelativeLayout.ALIGN_PARENT_END)
                marginEnd = dp(16)
            } else {
                width = dp(1)
                addRule(RelativeLayout.ALIGN_PARENT_START)
                marginEnd = 0
            }
        }
        input.alpha = if (opened) 1f else 0f
        input.isClickable = opened
        input.importantForAccessibility = if (opened)
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO

        // Secondary screens need a separate back button, never a search icon
        // masquerading as one. Retain the visible search action at the right.
        val back = container.findViewWithTag<ImageView>(BACK_TAG) ?: ImageView(activity).also {
            it.tag = BACK_TAG
            it.setImageResource(org.fossify.commons.R.drawable.ic_arrow_left_vector)
            it.scaleType = ImageView.ScaleType.CENTER_INSIDE
            it.setPadding(dp(12), dp(12), dp(12), dp(12))
            it.contentDescription = activity.getString(org.fossify.commons.R.string.back)
            it.isClickable = true
            it.isFocusable = true
            it.background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(28, Color.red(foreground), Color.green(foreground), Color.blue(foreground))),
                ColorDrawable(Color.TRANSPARENT), null
            )
            it.setOnClickListener {
                val dispatcher = (activity as? ComponentActivity)?.onBackPressedDispatcher
                if (dispatcher != null) dispatcher.onBackPressed() else activity.onBackPressed()
            }
            container.addView(it)
        }
        back.imageTintList = ColorStateList.valueOf(foreground)
        back.visibility = if (state.back && !opened) View.VISIBLE else View.GONE
        back.layoutParams = RelativeLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
        }

        val heading = container.findViewWithTag<TextView>(TITLE_TAG) ?: TextView(activity).also {
            it.tag = TITLE_TAG
            it.gravity = Gravity.CENTER_VERTICAL
            it.textSize = 20f
            it.includeFontPadding = false
            it.maxLines = 1
            it.ellipsize = TextUtils.TruncateAt.END
            container.addView(it)
        }
        heading.text = state.title
        heading.setTextColor(foreground)
        heading.visibility = if (opened) View.GONE else View.VISIBLE
        heading.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.START_OF, icon.id)
            marginStart = dp(if (state.back) 64 else 16)
            marginEnd = dp(8)
        }
    }
}
