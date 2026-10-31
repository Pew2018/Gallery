package org.fossify.gallery.helpers

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.*
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.extensions.*
import org.fossify.gallery.R

/** Classic dialogs, with a private mutable draft until Save is explicitly pressed. */
class PhotoProfileDialogs(private val activity: Activity, private val changed: () -> Unit) {
    private val store = PhotoProfileRepository.of(activity)
    private fun text(id: Int) = activity.getString(id)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()

    fun switch() {
        val state = store.state()
        if (state.profiles.isEmpty()) { edit(); return }
        val builder = OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_switch))
            .setSingleChoiceItems(state.profiles.map { it.name }.toTypedArray(), state.profiles.indexOf(state.current)) { dialog, index ->
                store.select(state.profiles[index].id)
                dialog.dismiss()
                changed()
            }.setNegativeButton(org.fossify.commons.R.string.cancel, null)
        OxygenClassicDialog.show(activity, builder)
    }

    fun manage() {
        val state = store.state()
        val profiles = state.profiles
        val colors = photoDialogColors(activity)
        val density = activity.resources.displayMetrics.density
        lateinit var dialog: AlertDialog
        val list = ListView(activity).apply { dividerHeight = dp(1) }
        val rows = object : BaseAdapter() {
            override fun getCount() = profiles.size
            override fun getItem(position: Int) = profiles[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val profile = getItem(position)
                val row = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(72)
                }
                row.removeAllViews()
                val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, 0, 0) }
                labels.addView(TextView(activity).apply { text = profile.name; textSize = 16f; setTextColor(colors.text); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                labels.addView(TextView(activity).apply {
                    text = activity.getString(R.string.photo_profiles_source_count, profile.sources.size)
                    textSize = 12f
                    setTextColor(colors.secondary)
                })
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                if (profile.id == state.current?.id) row.addView(ImageView(activity).apply {
                    setImageResource(org.fossify.commons.R.drawable.ic_check_vector)
                    imageTintList = android.content.res.ColorStateList.valueOf(colors.accent)
                    contentDescription = text(R.string.photo_profiles_current_accessibility)
                }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
                val overflow = ImageButton(activity).apply {
                    setImageResource(R.drawable.photo_profile_more)
                    imageTintList = android.content.res.ColorStateList.valueOf(colors.text)
                    contentDescription = activity.getString(R.string.photo_profiles_more_actions_for, profile.name)
                    background = OxygenClassicDialog.ripple(colors.accent, density)
                    isFocusable = false
                    setOnClickListener { anchor ->
                        val current = store.state().profiles.firstOrNull { it.id == profile.id } ?: return@setOnClickListener
                        showActionsPopup(current, anchor) { dialog.dismiss() }
                    }
                }
                row.addView(overflow, LinearLayout.LayoutParams(dp(48), dp(48)))
                row.setPadding(0, 0, dp(8), 0)
                row.isClickable = true
                row.isFocusable = true
                row.contentDescription = activity.getString(
                    if (profile.id == state.current?.id) R.string.photo_profiles_row_current_accessibility
                    else R.string.photo_profiles_row_accessibility,
                    profile.name,
                    profile.sources.size
                )
                row.setOnClickListener { selectProfile(profile) { dialog.dismiss() } }
                return row
            }
        }
        list.adapter = rows
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val create = TextView(activity).apply {
            text = text(R.string.photo_profiles_create)
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(colors.accent)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            minHeight = dp(56)
            background = OxygenClassicDialog.ripple(colors.accent, density)
            setOnClickListener { dialog.dismiss(); edit() }
        }
        content.addView(create)
        if (profiles.isEmpty()) {
            content.addView(TextView(activity).apply {
                setText(R.string.photo_profiles_empty_description)
                textSize = 14f
                setTextColor(colors.secondary)
                setPadding(dp(24), dp(12), dp(24), dp(12))
            }, LinearLayout.LayoutParams(-1, dp(72)))
        } else {
            content.addView(list, LinearLayout.LayoutParams(-1, dp((profiles.size.coerceAtMost(5) * 72).coerceAtLeast(72))))
        }
        val builder = OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_manage))
            .setView(content)
            .setNegativeButton(R.string.photo_profiles_done, null)
        dialog = OxygenClassicDialog.show(activity, builder, content)
    }

    private fun selectProfile(profile: PhotoProfile, closeManagement: () -> Unit) {
        val current = store.state().profiles.firstOrNull { it.id == profile.id } ?: return
        store.select(current.id)
        closeManagement()
        changed()
    }

    private fun showActionsPopup(profile: PhotoProfile, anchor: android.view.View, closeManagement: () -> Unit) {
        val isCurrent = store.state().current?.id == profile.id
        val light = android.graphics.Color.luminance(activity.getProperBackgroundColor()) >= 0.5f
        val popupContext = ContextThemeWrapper(
            activity,
            if (light) R.style.OxygenClassicPopupTheme_Light else R.style.OxygenClassicPopupTheme_Dark
        )
        val popup = PopupMenu(popupContext, anchor)
        if (!isCurrent) popup.menu.add(0, 0, 0, R.string.photo_profiles_use)
        popup.menu.add(0, 1, 1, R.string.photo_profiles_edit)
        popup.menu.add(0, 2, 2, R.string.photo_profiles_rename)
        popup.menu.add(0, 3, 3, R.string.photo_profiles_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> {
                    val current = store.state().profiles.firstOrNull { it.id == profile.id }
                    if (current != null) { closeManagement(); store.select(current.id); changed() }
                }
                1 -> { closeManagement(); edit(profile) }
                2 -> { closeManagement(); rename(profile) }
                3 -> {
                    closeManagement()
                    OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_delete))
                    .setMessage(activity.getString(R.string.photo_profiles_delete_confirm, profile.name))
                    .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                    .setPositiveButton(R.string.photo_profiles_delete) { _, _ -> store.delete(profile.id); changed() })
                }
            }
            true
        }
        popup.show()
    }

    private fun rename(profile: PhotoProfile) {
        val context = OxygenClassicDialog.builder(activity, "").context
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val field = EditText(context).apply { setText(profile.name); setSingleLine(); hint = text(R.string.photo_profiles_name) }
        body.addView(field, LinearLayout.LayoutParams(-1, -2))
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_rename))
            .setView(body).setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(R.string.photo_profiles_save, null), field)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (field.text.toString().isBlank()) { field.error = text(R.string.photo_profiles_name_required); return@setOnClickListener }
            runCatching { store.updateExisting(profile.copy(name = field.text.toString())) }
                .onFailure { field.error = text(R.string.photo_profiles_edit_target_missing); return@setOnClickListener }
            dialog.dismiss(); changed()
        }
    }

    fun edit(profile: PhotoProfile? = null, creating: Boolean = profile == null) {
        (activity as? org.fossify.gallery.activities.MediaActivity)?.let {
            org.fossify.gallery.activities.PhotoProfileEditActivity.launch(it, profile, creating)
        }
    }

}
