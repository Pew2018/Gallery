package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Build
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Menu
import com.google.android.material.appbar.MaterialToolbar
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.graphics.ColorUtils
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.extensions.config
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

    /** Set the ActionMenuPresenter popup theme before the menu is inflated. */
    fun preparePopup(activity: Activity, searchMenu: MySearchMenu) {
        val light = !OxygenThemeEngine.snapshot(activity, activity.config).dark
        searchMenu.binding.topToolbar.setPopupTheme(
            if (light) org.fossify.gallery.R.style.OxygenClassicPopupTheme_Light
            else org.fossify.gallery.R.style.OxygenClassicPopupTheme_Dark
        )
    }

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
            OxygenSearchNavigation.hideImeAndClearFocus(activity, searchMenu)
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
        val foreground = OxygenThemeEngine.toolbarForeground(activity, activity.config)
        val container = menu.binding.toolbarContainer
        val icon = menu.binding.topToolbarSearchIcon
        val input = menu.binding.topToolbarSearch
        val actions = menu.binding.topToolbar

        menu.binding.searchBarContainer.setPadding(0, 0, 0, 0)
        container.layoutParams = container.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val surface = OxygenThemeEngine.toolbarSurface(activity, activity.config)
        val rowHeight = maxOf(dp(56), (20 * activity.resources.displayMetrics.scaledDensity + dp(24)).toInt())
        menu.setBackgroundColor(surface)
        menu.binding.searchBarContainer.layoutParams = menu.binding.searchBarContainer.layoutParams.apply {
            height = rowHeight
        }
        container.background = ColorDrawable(surface)
        preparePopup(activity, menu)
        // The ActionMenu presenter owns the trailing 48dp action slots.
        // Remove Commons' extra end margin/insets so search aligns immediately
        // before the action menu instead of competing with its internal padding.
        actions.layoutParams = (actions.layoutParams as RelativeLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            addRule(RelativeLayout.ALIGN_PARENT_END)
            marginStart = 0
            marginEnd = 0
        }
        actions.visibility = if (opened) View.GONE else View.VISIBLE
        menu.minimumHeight = rowHeight
        applyToolbar(activity, actions)
        // Commons uses ImageView.setColorFilter(), which takes precedence over imageTintList.
        icon.clearColorFilter()
        icon.drawable?.mutate()?.clearColorFilter()
        icon.imageTintList = ColorStateList.valueOf(foreground)
        input.setTextColor(foreground)
        input.setHintTextColor(ColorUtils.setAlphaComponent(foreground, 160))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            input.textCursorDrawable = input.textCursorDrawable?.mutate()?.also {
                it.setTint(foreground)
            }
        }

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
                val navigate = menu.onNavigateBackClickListener
                if (navigate != null) navigate.invoke() else {
                    val dispatcher = (activity as? ComponentActivity)?.onBackPressedDispatcher
                    if (dispatcher != null) dispatcher.onBackPressed() else activity.onBackPressed()
                }
            }
            container.addView(it)
        }
        back.imageTintList = ColorStateList.valueOf(foreground)
        back.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 28)),
            ColorDrawable(Color.TRANSPARENT), null
        )
        // GONE removes both the hit target and accessibility node during search.
        back.visibility = if (state.back && !opened) View.VISIBLE else View.GONE
        back.layoutParams = RelativeLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
        }

        val heading = container.findViewWithTag<TextView>(TITLE_TAG) ?: TextView(activity).also {
            it.tag = TITLE_TAG
            it.gravity = Gravity.CENTER_VERTICAL
            it.textSize = 20f
            it.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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

    /** Final styling boundary after Commons setupTopAppBar/updateColors/menu mutations. */
    fun applyToolbar(activity: Activity, toolbar: MaterialToolbar) {
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        val foreground = colors.toolbarForeground
        val density = activity.resources.displayMetrics.density
        val height = maxOf((56 * density + 0.5f).toInt(),
            (20 * activity.resources.displayMetrics.scaledDensity + 24 * density).toInt())
        toolbar.minimumHeight = height
        toolbar.layoutParams?.let { params ->
            if (params.height != height) {
                params.height = height
                toolbar.layoutParams = params
            }
        }
        toolbar.isTitleCentered = false
        toolbar.isSubtitleCentered = false
        // Commons' top-toolbar has no visible title; its content insets would
        // otherwise leave a phantom gap before the overflow action.
        toolbar.setContentInsetsRelative(0, 0)
        toolbar.contentInsetEndWithActions = 0
        toolbar.contentInsetStartWithNavigation = 0
        // Use the exact opaque color tested by WCAG, without a Material elevation overlay.
        toolbar.backgroundTintList = null
        toolbar.background = ColorDrawable(colors.toolbarBackground)
        toolbar.elevation = 0f
        toolbar.stateListAnimator = null
        toolbar.setTitleTextAppearance(activity, org.fossify.gallery.R.style.OxygenClassicToolbarTitle)
        // TextAppearance may contain a color; apply the white foreground after typography.
        toolbar.setTitleTextColor(foreground)
        toolbar.setSubtitleTextColor(foreground)
        toolbar.setNavigationIconTint(foreground)
        toolbar.collapseIcon = toolbar.collapseIcon?.mutate()?.also {
            it.clearColorFilter()
            it.setTint(foreground)
        }
        toolbar.navigationIcon = toolbar.navigationIcon?.mutate()?.also {
            it.clearColorFilter()
            it.setTint(foreground)
        }
        toolbar.overflowIcon = toolbar.overflowIcon?.mutate()?.also {
            it.clearColorFilter()
            it.setTint(foreground)
        }
        toolbar.setPopupTheme(if (colors.dark) org.fossify.gallery.R.style.OxygenClassicPopupTheme_Dark
            else org.fossify.gallery.R.style.OxygenClassicPopupTheme_Light)
        tintMenu(toolbar.menu, foreground)
    }

    /** Apply the same tokens, title typography and 48dp navigation target to
     * the settings theme sub-page, which intentionally has no search menu.
     */
    fun applyStandaloneToolbar(
        activity: Activity, bar: LinearLayout, back: ImageView, title: TextView
    ) {
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val height = maxOf(dp(56),
            (20 * activity.resources.displayMetrics.scaledDensity + dp(24)).toInt())
        bar.layoutParams?.let { params ->
            if (params.height != height) {
                params.height = height
                bar.layoutParams = params
            }
        }
        bar.minimumHeight = height
        bar.setBackgroundColor(colors.toolbarBackground)
        back.layoutParams?.let { params ->
            if (params.width != dp(48) || params.height != height) {
                params.width = dp(48)
                params.height = height
                back.layoutParams = params
            }
        }
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.imageTintList = ColorStateList.valueOf(colors.toolbarForeground)
        back.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.toolbarForeground, 28)),
            ColorDrawable(Color.TRANSPARENT), null
        )
        title.textSize = 20f
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        title.gravity = Gravity.CENTER_VERTICAL
        title.includeFontPadding = false
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.setTextColor(colors.toolbarForeground)
    }

    fun tintMenu(menu: Menu?, foreground: Int = Color.WHITE) {
        if (menu == null) return
        val colors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(foreground, 110), foreground)
        )
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            // MenuItem tint survives ActionMenuPresenter rebinding and icon replacement.
            item.iconTintList = colors
            item.icon = item.icon?.mutate()?.also {
                it.clearColorFilter()
                it.setTintList(colors)
                it.alpha = 255
            }
            tintMenu(item.subMenu, foreground)
        }
    }

    /** One traversal at lifecycle/configuration boundaries; no polling or pre-draw restyling. */
    fun refresh(activity: Activity, root: View = activity.findViewById(android.R.id.content)) {
        when (root) {
            is MySearchMenu -> {
                hooked[root]?.let { render(root, it) }
                return
            }
            is org.fossify.commons.views.MyAppBarLayout ->
                root.setBackgroundColor(OxygenThemeEngine.toolbarSurface(activity, activity.config))
            is MaterialToolbar -> applyToolbar(activity, root)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) refresh(activity, root.getChildAt(i))
        }
    }

}
