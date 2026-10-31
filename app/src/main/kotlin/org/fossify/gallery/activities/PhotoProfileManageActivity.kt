package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.adapters.PhotoProfileManageAdapter
import org.fossify.gallery.adapters.PhotoProfileManageRow
import org.fossify.gallery.databinding.ActivityPhotoProfileManageBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenThemeColors
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.gallery.helpers.PhotoProfile
import org.fossify.gallery.helpers.PhotoProfileCodec
import org.fossify.gallery.helpers.PhotoProfileRepository
import org.fossify.gallery.helpers.PhotoProfileState
import org.fossify.gallery.helpers.photoProfileStateChanges

class PhotoProfileManageActivity : SimpleActivity() {
    companion object {
        const val REQUEST_CODE = 8402
        const val EXTRA_RELOAD_MEDIA = "photo_profile_manage_reload_media"
        const val EXTRA_REFRESH_CURRENT_DISPLAY = "photo_profile_manage_refresh_display"
        private const val STATE_ENTRY = "photo_profile_manage_entry_state"
        private const val ACTION_USE = 1
        private const val ACTION_EDIT = 2
        private const val ACTION_RENAME = 3
        private const val ACTION_DELETE = 4

        fun launch(activity: android.app.Activity) {
            activity.startActivityForResult(Intent(activity, PhotoProfileManageActivity::class.java), REQUEST_CODE)
        }
    }

    private val binding by viewBinding(ActivityPhotoProfileManageBinding::inflate)
    private val store by lazy { PhotoProfileRepository.of(this) }
    private lateinit var entryState: PhotoProfileState
    private lateinit var profilesAdapter: PhotoProfileManageAdapter
    private var resultPrepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        entryState = savedInstanceState?.getString(STATE_ENTRY)?.let {
            runCatching { PhotoProfileCodec.decode(it) }.getOrNull()
        } ?: store.state()

        setupEdgeToEdge(
            padTopSystem = listOf(binding.photoProfileManageAppbar),
            padBottomSystem = listOf(binding.photoProfileManageContent)
        )
        binding.photoProfileManageToolbar.apply {
            title = getString(R.string.photo_profiles_manage)
            setNavigationOnClickListener { finish() }
        }
        profilesAdapter = PhotoProfileManageAdapter(::useProfile, ::showActions)
        binding.photoProfileManageList.apply {
            layoutManager = LinearLayoutManager(this@PhotoProfileManageActivity)
            adapter = profilesAdapter
        }
        binding.photoProfileManageCreate.setOnClickListener {
            PhotoProfileEditActivity.launch(this, null, creating = true)
        }
        renderTheme()
        refreshProfiles()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            binding.photoProfileManageAppbar,
            NavigationIcon.Arrow,
            topBarColor = OxygenThemeEngine.toolbarSurface(this, config)
        )
        binding.photoProfileManageToolbar.setNavigationOnClickListener { finish() }
        OxygenToolbarStyle.applyToolbar(this, binding.photoProfileManageToolbar)
        OxygenSystemBars.apply(this)
        if (::profilesAdapter.isInitialized) refreshProfiles()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::entryState.isInitialized) outState.putString(STATE_ENTRY, PhotoProfileCodec.encode(entryState))
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PhotoProfileEditActivity.REQUEST_CODE) refreshProfiles()
    }

    override fun finish() {
        prepareResult()
        super.finish()
    }

    private fun prepareResult() {
        if (resultPrepared || !::entryState.isInitialized) return
        resultPrepared = true
        val changes = photoProfileStateChanges(entryState, store.state())
        if (!changes.changed) {
            setResult(RESULT_CANCELED)
            return
        }
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_RELOAD_MEDIA, changes.reloadMedia)
            putExtra(EXTRA_REFRESH_CURRENT_DISPLAY, changes.refreshCurrentDisplay)
        })
    }

    private fun renderTheme() {
        val colors = OxygenThemeEngine.snapshot(this, config)
        binding.root.setBackgroundColor(colors.pageBackground)
        binding.photoProfileManageContent.setBackgroundColor(colors.pageBackground)
        binding.photoProfileManageCreate.background = OxygenClassicDialog.ripple(
            colors.interactiveAccent, resources.displayMetrics.density
        )
        binding.photoProfileManageCreateIcon.imageTintList =
            android.content.res.ColorStateList.valueOf(colors.interactiveAccent)
        binding.photoProfileManageCreateLabel.setTextColor(colors.primaryText)
        binding.photoProfileManageCreateDivider.setBackgroundColor(
            OxygenThemeColors.blend(colors.pageBackground, colors.primaryText, 0.10)
        )
        binding.photoProfileManageEmptyTitle.setTextColor(colors.primaryText)
        binding.photoProfileManageEmptyDescription.setTextColor(colors.secondaryText)
    }

    private fun refreshProfiles() {
        if (isFinishing || isDestroyed || !::profilesAdapter.isInitialized) return
        val state = store.state()
        val currentId = state.current?.id
        profilesAdapter.submitList(state.profiles.map { profile ->
            PhotoProfileManageRow(profile.id, profile.name, profile.sources.size, profile.id == currentId)
        })
        binding.photoProfileManageList.beVisibleIf(state.profiles.isNotEmpty())
        binding.photoProfileManageEmpty.beVisibleIf(state.profiles.isEmpty())
    }

    private fun latestProfile(id: String): PhotoProfile? = store.state().profiles.firstOrNull { it.id == id }

    private fun useProfile(id: String) {
        val profile = latestProfile(id)
        if (profile == null) {
            refreshProfiles()
            return
        }
        store.select(profile.id)
        if (store.state().selectedId != profile.id) {
            refreshProfiles()
            return
        }
        finish()
    }

    private fun showActions(id: String, anchor: View) {
        val state = store.state()
        val profile = state.profiles.firstOrNull { it.id == id } ?: run {
            refreshProfiles()
            return
        }
        val isCurrent = state.current?.id == profile.id
        val dark = OxygenThemeEngine.snapshot(this, config).dark
        val popup = PopupMenu(
            ContextThemeWrapper(
                this,
                if (dark) R.style.OxygenClassicPopupTheme_Dark else R.style.OxygenClassicPopupTheme_Light
            ), anchor, Gravity.END
        )
        if (!isCurrent) popup.menu.add(0, ACTION_USE, 0, R.string.photo_profiles_use)
        popup.menu.add(0, ACTION_EDIT, 1, R.string.photo_profiles_edit)
        popup.menu.add(0, ACTION_RENAME, 2, R.string.photo_profiles_rename)
        popup.menu.add(0, ACTION_DELETE, 3, R.string.photo_profiles_delete)
        popup.setOnMenuItemClickListener { item ->
            // Resolve the UUID again after the menu has been open; adapter positions and
            // profile snapshots can both become stale while the popup is visible.
            val latest = latestProfile(id)
            if (latest == null) {
                refreshProfiles()
                return@setOnMenuItemClickListener true
            }
            when (item.itemId) {
                ACTION_USE -> useProfile(latest.id)
                ACTION_EDIT -> PhotoProfileEditActivity.launch(this, latest, creating = false)
                ACTION_RENAME -> renameProfile(latest)
                ACTION_DELETE -> confirmDelete(latest.id, latest.name)
            }
            true
        }
        popup.show()
    }

    private fun renameProfile(profile: PhotoProfile) {
        val context = OxygenClassicDialog.builder(this, "").context
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val field = EditText(context).apply {
            setText(profile.name)
            setSingleLine()
            hint = getString(R.string.photo_profiles_name)
        }
        OxygenClassicDialog.styleInput(this, field)
        body.addView(field, LinearLayout.LayoutParams(-1, -2))
        val dialog = OxygenClassicDialog.show(
            this,
            OxygenClassicDialog.builder(this, getString(R.string.photo_profiles_rename))
                .setView(body)
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .setPositiveButton(R.string.photo_profiles_save, null),
            field
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = field.text.toString().trim()
            if (name.isEmpty()) {
                field.error = getString(R.string.photo_profiles_name_required)
                return@setOnClickListener
            }
            val latest = latestProfile(profile.id)
            if (latest == null) {
                field.error = getString(R.string.photo_profiles_edit_target_missing)
                refreshProfiles()
                return@setOnClickListener
            }
            val update = runCatching { store.updateExisting(latest.copy(name = name)) }
            if (update.isFailure) {
                field.error = getString(R.string.photo_profiles_edit_target_missing)
                refreshProfiles()
                return@setOnClickListener
            }
            dialog.dismiss()
            refreshProfiles()
        }
    }

    private fun confirmDelete(id: String, name: String) {
        OxygenClassicDialog.show(
            this,
            OxygenClassicDialog.builder(this, getString(R.string.photo_profiles_delete))
                .setMessage(getString(R.string.photo_profiles_delete_confirm, name))
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .setPositiveButton(R.string.photo_profiles_delete) { _, _ ->
                    if (latestProfile(id) != null) store.delete(id)
                    refreshProfiles()
                }
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

}
