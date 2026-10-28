package org.fossify.gallery.activities

import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.gallery.R
import org.fossify.gallery.adapters.ManageFoldersAdapter
import org.fossify.gallery.databinding.ActivityManageFoldersBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenSystemBars

class IncludedFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateFolders()
        setupOptionsMenu()
        binding.manageFoldersToolbar.title = getString(R.string.include_folders)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.manageFoldersAppbar),
            padBottomSystem = listOf(binding.manageFoldersList)
        )
        // Keep the shared accent toolbar flat; Commons' scroll animator restores page colors.
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageFoldersAppbar, NavigationIcon.Arrow,
            topBarColor = OxygenThemeEngine.toolbarSurface(this, config))
        val foreground = OxygenThemeEngine.toolbarForeground(this, config)
        binding.manageFoldersToolbar.setTitleTextColor(foreground)
        binding.manageFoldersToolbar.navigationIcon?.mutate()?.setTint(foreground)
        binding.manageFoldersToolbar.overflowIcon?.mutate()?.setTint(foreground)
        org.fossify.gallery.helpers.OxygenToolbarStyle.applyToolbar(this, binding.manageFoldersToolbar)
        OxygenSystemBars.apply(this)
    }

    private fun updateFolders() {
        val folders = ArrayList<String>()
        config.includedFolders.mapTo(folders) { it }
        binding.manageFoldersPlaceholder.apply {
            text = getString(R.string.included_activity_placeholder)
            beVisibleIf(folders.isEmpty())
            setTextColor(getProperTextColor())
        }

        val adapter = ManageFoldersAdapter(this, folders, false, this, binding.manageFoldersList) {}
        binding.manageFoldersList.adapter = adapter
    }

    private fun setupOptionsMenu() {
        binding.manageFoldersToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_folder -> addFolder()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun refreshItems() {
        updateFolders()
    }

    private fun addFolder() {
        showAddIncludedFolderDialog {
            updateFolders()
        }
    }
}
