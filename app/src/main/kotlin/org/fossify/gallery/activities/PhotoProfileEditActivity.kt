package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityPhotoProfileEditBinding
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import java.io.File
import java.util.UUID

class PhotoProfileEditActivity : SimpleActivity() {
    companion object {
        const val EXTRA_PROFILE_ID = "photo_profile_edit_id"
        const val EXTRA_CREATING = "photo_profile_edit_creating"
        const val REQUEST_CODE = 8401
        private const val STATE_DRAFT = "photo_profile_edit_draft"
        private const val STATE_SOURCE_ERROR = "photo_profile_edit_source_error"
        fun launch(activity: android.app.Activity, profile: PhotoProfile? = null, creating: Boolean = profile == null) {
            activity.startActivityForResult(Intent(activity, PhotoProfileEditActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, profile?.id)
                putExtra(EXTRA_CREATING, creating)
            }, REQUEST_CODE)
        }
    }

    private val binding by viewBinding(ActivityPhotoProfileEditBinding::inflate)
    private val store by lazy { PhotoProfileRepository.of(this) }
    private var creating = true
    private var showSourceError = false
    private lateinit var draft: PhotoProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        creating = intent.getBooleanExtra(EXTRA_CREATING, true)
        val original = intent.getStringExtra(EXTRA_PROFILE_ID)?.let { id -> store.state().profiles.firstOrNull { it.id == id } }
        draft = savedInstanceState?.getString(STATE_DRAFT)?.let { runCatching { com.google.gson.Gson().fromJson(it, PhotoProfile::class.java) }.getOrNull() }
            ?: original ?: PhotoProfile(UUID.randomUUID().toString(), "", emptyList())
        showSourceError = savedInstanceState?.getBoolean(STATE_SOURCE_ERROR) ?: false
        setupEdgeToEdge(padTopSystem = listOf(binding.photoProfileEditAppbar), padBottomImeAndSystem = listOf(binding.photoProfileEditScroll))
        binding.photoProfileEditToolbar.apply {
            title = getString(if (creating) R.string.photo_profiles_create else R.string.photo_profiles_edit)
            inflateMenu(R.menu.menu_photo_profile_edit)
            setOnMenuItemClickListener { item -> if (item.itemId == R.id.photo_profile_edit_save) { saveDraft(); true } else false }
        }
        binding.photoProfileEditToolbar.setNavigationOnClickListener { finish() }
        val palette = OxygenThemeEngine.snapshot(this, config)
        binding.photoProfileEditAddSource.setTextColor(palette.interactiveAccent)
        binding.photoProfileEditAddSource.background = OxygenClassicDialog.ripple(palette.interactiveAccent, resources.displayMetrics.density)
        binding.photoProfileEditName.setText(draft.name)
        OxygenClassicDialog.styleInput(this, binding.photoProfileEditName)
        binding.photoProfileEditSourcesTitle.setTextColor(palette.interactiveAccent)
        binding.photoProfileEditError.setTextColor(photoDialogColors(this).error)
        binding.photoProfileEditAddSource.setOnClickListener { chooseSources() }
        renderSources()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.photoProfileEditAppbar, NavigationIcon.Arrow, topBarColor = OxygenThemeEngine.toolbarSurface(this, config))
        OxygenToolbarStyle.applyToolbar(this, binding.photoProfileEditToolbar)
        OxygenSystemBars.apply(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureName()
        outState.putString(STATE_DRAFT, com.google.gson.Gson().toJson(draft))
        outState.putBoolean(STATE_SOURCE_ERROR, showSourceError)
        super.onSaveInstanceState(outState)
    }

    private fun captureName() { if (::draft.isInitialized) draft = draft.copy(name = binding.photoProfileEditName.text.toString()) }

    private fun renderSources() {
        val colors = OxygenThemeEngine.snapshot(this, config)
        binding.photoProfileEditSources.removeAllViews()
        draft.sources.forEach { source ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(72); setPadding(0, dp(6), 0, dp(6))
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            labels.addView(TextView(this).apply {
                text = if (source.type == PhotoSourceType.FAVORITES) getString(R.string.photo_profiles_favorites) else File(source.path.orEmpty()).name.ifBlank { source.path.orEmpty() }
                textSize = 16f; setTextColor(colors.primaryText); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            labels.addView(TextView(this).apply {
                text = source.path ?: getString(R.string.photo_profiles_favorites)
                textSize = 12f; setTextColor(colors.secondaryText); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageButton(this).apply {
                setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
                imageTintList = android.content.res.ColorStateList.valueOf(colors.primaryText)
                contentDescription = getString(R.string.photo_profiles_remove_source)
                background = OxygenClassicDialog.ripple(colors.interactiveAccent, resources.displayMetrics.density)
                setOnClickListener { draft = draft.copy(sources = draft.sources.filterNot { it == source }); renderSources() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            binding.photoProfileEditSources.addView(row)
            binding.photoProfileEditSources.addView(View(this).apply {
                setBackgroundColor(OxygenThemeColors.blend(colors.pageBackground, colors.primaryText, 0.10))
            }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        binding.photoProfileEditError.visibility = if (showSourceError && draft.sources.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun chooseSources() {
        val paths = draft.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toMutableSet()
        if (draft.sources.any { it.type == PhotoSourceType.FAVORITES }) paths.add(org.fossify.commons.helpers.FAVORITES)
        PickDirectoryDialog(this, "", false, true, false, false,
            PickDirectoryDialog.SourceSelection(PhotoSourceSelectionDraft(paths)) { chosen, available ->
                val selected = chosen.map { path -> if (path == org.fossify.commons.helpers.FAVORITES) PhotoSource(PhotoSourceType.FAVORITES) else PhotoSource(PhotoSourceType.FOLDER, path) }
                val unavailable = draft.sources.filter { it.type == PhotoSourceType.FOLDER && it.path !in available }
                draft = draft.copy(sources = PhotoProfileCodec.sources(selected + unavailable))
                showSourceError = false
                renderSources()
            }, {})
    }

    private fun saveDraft() {
        captureName()
        val name = draft.name.trim()
        if (name.isEmpty()) { binding.photoProfileEditName.error = getString(R.string.photo_profiles_name_required); return }
        if (draft.sources.isEmpty()) { showSourceError = true; binding.photoProfileEditError.visibility = View.VISIBLE; binding.photoProfileEditScroll.smoothScrollTo(0, binding.photoProfileEditSourcesTitle.bottom); return }
        runCatching { store.save(draft.copy(name = name)) }.onSuccess {
            setResult(RESULT_OK)
            finish()
        }.onFailure { binding.photoProfileEditName.error = getString(R.string.photo_profiles_name_required) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
