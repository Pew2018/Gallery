package org.fossify.gallery.helpers

import android.app.Activity
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import java.io.File
import java.util.UUID

/** Classic dialogs, with a private mutable draft until Save is explicitly pressed. */
class PhotoProfileDialogs(private val activity: Activity, private val changed: () -> Unit) {
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
        val profiles = store.state().profiles
        val labels = profiles.map { activity.getString(R.string.photo_profiles_summary, it.name, it.sources.size) }
        val builder = OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_manage))
            .setItems(labels.toTypedArray()) { _, index -> actions(profiles[index]) }
            .setPositiveButton(R.string.photo_profiles_create) { _, _ -> edit() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        OxygenClassicDialog.show(activity, builder)
    }

    private fun actions(profile: PhotoProfile) {
        val labels = intArrayOf(R.string.photo_profiles_use, R.string.photo_profiles_edit, R.string.photo_profiles_rename, R.string.photo_profiles_delete)
        OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, profile.name)
            .setItems(labels.map(::text).toTypedArray()) { _, which ->
                when (which) {
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
        val field = EditText(activity).apply { setText(profile.name); setSingleLine(); hint = text(R.string.photo_profiles_name) }
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_rename))
            .setView(field).setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(R.string.photo_profiles_save, null), field)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (field.text.toString().isBlank()) { field.error = text(R.string.photo_profiles_name_required); return@setOnClickListener }
            store.save(profile.copy(name = field.text.toString()))
            dialog.dismiss(); changed()
        }
    }

    fun edit(profile: PhotoProfile? = null) {
        val builder = OxygenClassicDialog.builder(activity, text(if (profile == null) R.string.photo_profiles_create else R.string.photo_profiles_edit))
        val context = builder.context
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val name = EditText(context).apply {
            hint = text(R.string.photo_profiles_name); setSingleLine(); setText(profile?.name.orEmpty())
        }
        body.addView(name, LinearLayout.LayoutParams(-1, -2))
        val selected = profile?.sources?.toMutableList() ?: mutableListOf()
        val sourceList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(sourceList)
        val sourceStatus = HashMap<PhotoSource, Boolean>()
        fun render() {
            sourceList.removeAllViews()
            selected.toList().forEach { source ->
                val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56) }
                val label = TextView(context).apply {
                    textSize = 16f
                    text = (if (source.type == PhotoSourceType.FAVORITES) text(R.string.photo_profiles_favorites) else
                        "${File(source.path.orEmpty()).name}\n${source.path}") +
                        if (sourceStatus[source] == false) "\n${text(R.string.photo_profiles_unavailable)}" else ""
                    setTextColor(activity.getProperTextColor())
                }
                row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(ImageButton(context).apply {
                    setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
                    imageTintList = android.content.res.ColorStateList.valueOf(activity.getProperTextColor())
                    contentDescription = text(R.string.photo_profiles_remove_source)
                    background = OxygenClassicDialog.ripple(activity.getProperPrimaryColor(), activity.resources.displayMetrics.density)
                    setOnClickListener { selected.remove(source); render() }
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                sourceList.addView(row)
            }
        }
        fun updateStatus() {
            val snapshot = selected.toList()
            ensureBackgroundThread {
                val loader = PhotoProfileMediaLoader(activity.applicationContext)
                val indexedFolders = MediaFetcher(activity.applicationContext, PhotoProfileMediaLoader.IMAGE_TYPES).getFoldersToScan().toSet()
                val statuses = snapshot.associateWith { source ->
                    if (source.type == PhotoSourceType.FAVORITES) loader.favoritesAllowed()
                    else source.path?.let { loader.folderAllowed(it) && (File(it).isDirectory || it in indexedFolders) } == true
                }
                activity.runOnUiThread { if (alive() && body.isAttachedToWindow) { sourceStatus.putAll(statuses); render() } }
            }
        }
        body.addView(Button(context).apply {
            setText(R.string.photo_profiles_add_source)
            setOnClickListener { chooseSources(selected.toList()) { sources -> selected.clear(); selected.addAll(sources); render(); updateStatus() } }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(TextView(context).apply {
            setText(R.string.photo_profiles_source_note); textSize = 13f; setTextColor(activity.getProperTextColor())
        })
        val scroll = ScrollView(context).apply { addView(body) }
        val dialog = OxygenClassicDialog.show(activity, builder.setView(scroll)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(R.string.photo_profiles_save, null), scroll)
        render(); updateStatus()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (name.text.toString().isBlank()) { name.error = text(R.string.photo_profiles_name_required); return@setOnClickListener }
            if (selected.isEmpty()) { Toast.makeText(activity, R.string.photo_profiles_source_required, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            store.save(PhotoProfile(profile?.id ?: UUID.randomUUID().toString(), name.text.toString(), selected.toList()))
            dialog.dismiss(); changed()
        }
    }

    private fun chooseSources(selected: List<PhotoSource>, done: (List<PhotoSource>) -> Unit) {
        val waiting = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_add_source))
            .setMessage(R.string.photo_profiles_loading).setNegativeButton(org.fossify.commons.R.string.cancel, null))
        ensureBackgroundThread {
            val loader = PhotoProfileMediaLoader(activity.applicationContext)
            // Existing media indexing yields real paths, never treating a SAF URI as File.
            val folders = MediaFetcher(activity.applicationContext, PhotoProfileMediaLoader.IMAGE_TYPES).getFoldersToScan()
                .filter { it != org.fossify.commons.helpers.FAVORITES && it != RECYCLE_BIN && it.startsWith('/') }
            val choices = (listOf(PhotoSource(PhotoSourceType.FAVORITES)) + folders.sorted().map { PhotoSource(PhotoSourceType.FOLDER, it) } + selected).distinct()
            val allowed = choices.associateWith {
                if (it.type == PhotoSourceType.FAVORITES) loader.favoritesAllowed() else loader.folderAllowed(it.path.orEmpty())
            }
            activity.runOnUiThread {
                if (!alive() || !waiting.isShowing) return@runOnUiThread
                waiting.dismiss()
                val checked = choices.map { it in selected }.toBooleanArray()
                val labels = choices.map { source ->
                    (if (source.type == PhotoSourceType.FAVORITES) text(R.string.photo_profiles_favorites) else
                        "${File(source.path.orEmpty()).name}\n${source.path}") +
                        if (allowed[source] == false) "\n${text(R.string.photo_profiles_unavailable)}" else ""
                }.toTypedArray()
                OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, text(R.string.photo_profiles_add_source))
                    .setMultiChoiceItems(labels, checked) { _, index, value -> checked[index] = value }
                    .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                    .setPositiveButton(R.string.photo_profiles_save) { _, _ -> done(choices.filterIndexed { index, _ -> checked[index] }) })
            }
        }
    }
}
