package org.fossify.gallery.helpers

import android.app.Activity
import android.view.Gravity
import android.widget.*
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import java.io.File
import java.util.UUID

/** Classic dialogs, with a private mutable draft until Save is explicitly pressed. */
class PhotoProfileDialogs(private val activity: Activity, private val changed: () -> Unit) {
    private data class Draft(val profile: PhotoProfile, val scrollY: Int, val creating: Boolean)
    private var draftProvider: (() -> Draft)? = null
    private var restoredScroll = 0
    fun saveDraft(): String? = draftProvider?.invoke()?.let { com.google.gson.Gson().toJson(it) }
    fun restoreDraft(json: String) {
        runCatching { com.google.gson.Gson().fromJson(json, Draft::class.java) }.getOrNull()?.let {
            restoredScroll = it.scrollY
            edit(it.profile, it.creating)
        }
    }
    private val store = PhotoProfileRepository.of(activity)
    private fun text(id: Int) = activity.getString(id)
    private fun alive() = !activity.isFinishing && !activity.isDestroyed
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
                    contentDescription = text(R.string.photo_profiles_more_actions)
                    background = OxygenClassicDialog.ripple(colors.accent, density)
                    setOnClickListener { anchor -> showActionsPopup(profile, anchor) { dialog.dismiss() } }
                }
                row.addView(overflow, LinearLayout.LayoutParams(dp(48), dp(48)))
                row.setPadding(0, 0, dp(8), 0)
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
        list.setOnItemClickListener { _, _, position, _ ->
            store.select(profiles[position].id)
            dialog.dismiss()
            changed()
        }
    }

    private fun showActionsPopup(profile: PhotoProfile, anchor: android.view.View, closeManagement: () -> Unit) {
        val isCurrent = store.state().current?.id == profile.id
        val popup = PopupMenu(activity, anchor)
        if (!isCurrent) popup.menu.add(0, 0, 0, R.string.photo_profiles_use)
        popup.menu.add(0, 1, 1, R.string.photo_profiles_edit)
        popup.menu.add(0, 2, 2, R.string.photo_profiles_rename)
        popup.menu.add(0, 3, 3, R.string.photo_profiles_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> { closeManagement(); store.select(profile.id); changed() }
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

    private fun actions(profile: PhotoProfile) {
        val isCurrent = store.state().current?.id == profile.id
        val labels = (if (isCurrent) intArrayOf(R.string.photo_profiles_edit, R.string.photo_profiles_rename, R.string.photo_profiles_delete)
            else intArrayOf(R.string.photo_profiles_use, R.string.photo_profiles_edit, R.string.photo_profiles_rename, R.string.photo_profiles_delete))
        OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, profile.name)
            .setItems(labels.map(::text).toTypedArray()) { _, which ->
                val action = which + if (isCurrent) 1 else 0
                when (action) {
                    0 -> { store.select(profile.id); changed() }
                    1 -> edit(profile)
                    2 -> rename(profile)
                    3 -> OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_delete))
                        .setMessage(activity.getString(R.string.photo_profiles_delete_confirm, profile.name))
                        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                        .setPositiveButton(R.string.photo_profiles_delete) { _, _ -> store.delete(profile.id); changed() })
                }
            })
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
            store.save(profile.copy(name = field.text.toString()))
            dialog.dismiss(); changed()
        }
    }

    fun edit(profile: PhotoProfile? = null, creating: Boolean = profile == null) {
        (activity as? org.fossify.gallery.activities.MediaActivity)?.let {
            org.fossify.gallery.activities.PhotoProfileEditActivity.launch(it, profile, creating)
        }
    }

    @Suppress("unused")
    private fun legacyEdit(profile: PhotoProfile? = null, creating: Boolean = profile == null) {
        val builder = OxygenClassicDialog.builder(activity, text(if (creating) R.string.photo_profiles_create else R.string.photo_profiles_edit))
        val context = builder.context
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val name = EditText(context).apply {
            hint = text(R.string.photo_profiles_name); setSingleLine(); setText(profile?.name.orEmpty())
        }
        body.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        name.setHint(R.string.photo_profiles_name)
        val selected = profile?.sources?.toMutableList() ?: mutableListOf()
        val sourceList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(sourceList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val sourceError = TextView(context).apply {
            setText(R.string.photo_profiles_source_required)
            textSize = 13f
            setTextColor(photoDialogColors(activity).error)
            visibility = android.view.View.GONE
        }
        body.addView(sourceError)
        var showSourceError = false
        val sourceStatuses = HashMap<PhotoSource, Int>()
        val statusVersion = PhotoSourceStatusRequests()
        var editDialog: AlertDialog? = null
        fun render() {
            sourceList.removeAllViews()
            sourceError.visibility = if (showSourceError && selected.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            selected.toList().forEach { source ->
                val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56) }
                row.setPadding(0, dp(8), 0, dp(8))
                val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(TextView(context).apply {
                    textSize = 16f
                    text = if (source.type == PhotoSourceType.FAVORITES) text(R.string.photo_profiles_favorites)
                        else File(source.path.orEmpty()).name
                    setTextColor(photoDialogColors(activity).text)
                })
                if (source.type == PhotoSourceType.FOLDER) labels.addView(TextView(context).apply {
                    textSize = 12f; text = source.path; maxLines = 2; ellipsize = TextUtils.TruncateAt.MIDDLE
                    setTextColor(photoDialogColors(activity).secondary)
                    setOnClickListener { OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_path_title)).setMessage(source.path).setNegativeButton(org.fossify.commons.R.string.ok, null)) }
                })
                sourceStatuses[source]?.takeIf { it != R.string.photo_source_ready }?.let { status -> labels.addView(TextView(context).apply {
                    textSize = 12f; setText(status)
                    setTextColor(photoDialogColors(activity).secondary)
                }) }
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(ImageButton(context).apply {
                    setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
                    imageTintList = android.content.res.ColorStateList.valueOf(photoDialogColors(activity).text)
                    contentDescription = text(R.string.photo_profiles_remove_source)
                    background = OxygenClassicDialog.ripple(photoDialogColors(activity).accent, activity.resources.displayMetrics.density)
                    setOnClickListener { selected.remove(source); sourceStatuses.remove(source); statusVersion.invalidate(); render() }
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                sourceList.addView(row)
            }
        }
        fun updateStatus() {
            val snapshot = selected.toList()
            val request = statusVersion.begin()
            ensureBackgroundThread {
                val statuses = inspectSources(snapshot)
                activity.runOnUiThread { if (alive() && editDialog?.isShowing == true && statusVersion.isCurrent(request)) { sourceStatuses.clear(); sourceStatuses.putAll(statuses); render() } }
            }
        }
        body.addView(TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL; textSize = 14f
            setTextColor(photoDialogColors(activity).accent)
            background = OxygenClassicDialog.ripple(photoDialogColors(activity).accent, activity.resources.displayMetrics.density)
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true; isFocusable = true
            setText(R.string.photo_profiles_add_source)
            setOnClickListener { chooseSources(selected.toList()) { sources -> selected.clear(); selected.addAll(sources); sourceStatuses.clear(); render(); updateStatus() } }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(TextView(context).apply {
            setText(R.string.photo_profiles_source_note); textSize = 13f; setTextColor(photoDialogColors(activity).text)
            setPadding(0, dp(4), 0, 0)
        })
        val scroll = ScrollView(context).apply { addView(body) }
        val dialog = OxygenClassicDialog.show(activity, builder.setView(scroll)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(R.string.photo_profiles_save, null), scroll)
        draftProvider = { Draft(PhotoProfile(profile?.id ?: UUID.randomUUID().toString(), name.text.toString(), selected.toList()), scroll.scrollY, creating) }
        editDialog = dialog
        dialog.setOnDismissListener { draftProvider = null; statusVersion.invalidate(); editDialog = null }
        scroll.post { scroll.scrollTo(0, restoredScroll); restoredScroll = 0 }
        render(); updateStatus()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (name.text.toString().isBlank()) { name.error = text(R.string.photo_profiles_name_required); return@setOnClickListener }
            if (selected.isEmpty()) { showSourceError = true; sourceError.visibility = android.view.View.VISIBLE; scroll.post { scroll.smoothScrollTo(0, sourceError.bottom) }; return@setOnClickListener }
            store.save(PhotoProfile(profile?.id ?: UUID.randomUUID().toString(), name.text.toString(), selected.toList()))
            dialog.dismiss(); changed()
        }
    }

    /** One indexing/visibility read for the entire dialog, not one query per row. */
    private fun inspectSources(sources: List<PhotoSource>): Map<PhotoSource, Int> {
        val app = activity.applicationContext
        val config = app.config
        val token = PhotoProfileAccessSessionManager.currentToken(app)
        val noMedia = app.getNoMediaFoldersSync()
        val indexed = MediaFetcher(app, PhotoProfileMediaLoader.MEDIA_TYPES).getFoldersToScan().toSet()
        return sources.associateWith { source -> runCatching {
            if (source.type == PhotoSourceType.FAVORITES) {
                if (PhotoProfileAccessSessionManager.favoritesAuthorized(app, token)) R.string.photo_source_ready
                else if (config.photoCredentialSnapshot().enabled) R.string.photo_source_locked else R.string.photo_source_setup
            } else {
                val path = source.path.orEmpty()
                when {
                    path.contains("/.") || FavoriteFolderRules.hasNoMediaAncestor(path) ||
                        noMedia.any { path == it || path.startsWith("$it/") } -> R.string.photo_source_hidden
                    FavoriteFolderRules.isExcluded(path, config.excludedFolders) -> R.string.photo_source_excluded
                    !PhotoProfileAccessSessionManager.folderAuthorized(app, path, token) ->
                        if (config.photoCredentialSnapshot().enabled) R.string.photo_source_locked else R.string.photo_source_setup
                    !File(path).isDirectory && path !in indexed -> R.string.photo_source_missing
                    else -> R.string.photo_source_ready
                }
            }
        }.getOrDefault(R.string.photo_source_unknown) }
    }

    private fun chooseSources(selected: List<PhotoSource>, done: (List<PhotoSource>) -> Unit) {
        val host = activity as? org.fossify.commons.activities.BaseSimpleActivity ?: return
        val paths = selected.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toMutableSet()
        if (selected.any { it.type == PhotoSourceType.FAVORITES }) paths.add(org.fossify.commons.helpers.FAVORITES)
        org.fossify.gallery.dialogs.PickDirectoryDialog(
            host, "", false, true, false, false,
            org.fossify.gallery.dialogs.PickDirectoryDialog.SourceSelection(PhotoSourceSelectionDraft(paths)) { chosen, available ->
                if (alive()) {
                    val sources = chosen.map { path ->
                        if (path == org.fossify.commons.helpers.FAVORITES) PhotoSource(PhotoSourceType.FAVORITES)
                        else PhotoSource(PhotoSourceType.FOLDER, path)
                    }
                    done(PhotoProfileCodec.sources(sources + selected.filter {
                        it.type == PhotoSourceType.FOLDER && it.path !in available
                    }))
                }
            }, {}
        )
    }
}
