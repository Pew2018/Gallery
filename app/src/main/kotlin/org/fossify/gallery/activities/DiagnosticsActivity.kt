package org.fossify.gallery.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityDiagnosticsBinding
import org.fossify.gallery.helpers.DiagnosticLevel
import org.fossify.gallery.helpers.GalleryDiagnosticLogger
import org.fossify.gallery.helpers.OxygenClassicControls
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.ClassicSettingsText
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.commons.helpers.NavigationIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : SimpleActivity() {
    companion object {
        private const val EXPORT_LOGS_REQUEST = 430
        private const val MAX_COPY_CHARS = 250 * 1024
    }
    private val binding by viewBinding(ActivityDiagnosticsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padTopSystem = listOf(binding.diagnosticsAppbar), padBottomSystem = listOf(binding.diagnosticsScroll))
        binding.diagnosticsToolbar.setBackgroundColor(OxygenThemeEngine.toolbarSurface(this, config))
        setupTopAppBar(binding.diagnosticsAppbar, NavigationIcon.Arrow, topBarColor = OxygenThemeEngine.toolbarSurface(this, config))
        OxygenToolbarStyle.applyToolbar(this, binding.diagnosticsToolbar)
        val colors = OxygenThemeEngine.snapshot(this, config)
        binding.diagnosticsCoordinator.setBackgroundColor(colors.pageBackground)
        binding.diagnosticsHolder.setBackgroundColor(colors.pageBackground)
        OxygenClassicControls.applySwitches(binding.diagnosticsHolder, colors)
        ClassicSettingsText.apply(binding.diagnosticsHolder, colors)
        OxygenToolbarStyle.refresh(this)
        OxygenSystemBars.apply(this)

        binding.diagnosticsEnableSwitch.isChecked = GalleryDiagnosticLogger.syncEnabledFromPreferences()
        binding.diagnosticsEnableHolder.setOnClickListener {
            val active = !binding.diagnosticsEnableSwitch.isChecked
            GalleryDiagnosticLogger.setEnabled(this, active)
            binding.diagnosticsEnableSwitch.isChecked = active
            refreshStatus()
        }
        binding.diagnosticsCopyHolder.setOnClickListener { copyLogs() }
        binding.diagnosticsExportHolder.setOnClickListener { createExportDocument() }
        binding.diagnosticsClearHolder.setOnClickListener { confirmClear() }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        binding.diagnosticsEnableSwitch.isChecked = GalleryDiagnosticLogger.syncEnabledFromPreferences()
        refreshStatus()
    }

    private fun refreshStatus() {
        binding.diagnosticsEnableSwitch.isChecked = GalleryDiagnosticLogger.isEnabled()
        val state = getString(if (GalleryDiagnosticLogger.isEnabled()) R.string.diagnostics_status_on else R.string.diagnostics_status_off)
        GalleryDiagnosticLogger.stats { bytes, last ->
            if (isFinishing || isDestroyed) return@stats
            val size = android.text.format.Formatter.formatFileSize(this, bytes)
            binding.diagnosticsStatus.text = getString(R.string.diagnostics_status_details, size, last ?: "—") + "\n" + state
        }
    }

    private fun copyLogs() {
        GalleryDiagnosticLogger.snapshot { events, crash ->
            val content = buildString {
                if (events.isNotBlank()) append(events)
                if (!crash.isNullOrBlank()) append("\n--- Previous crash summary ---\n").append(crash)
            }
            if (content.isBlank()) {
                Toast.makeText(this, R.string.diagnostics_empty, Toast.LENGTH_SHORT).show()
                return@snapshot
            }
            val truncated = content.length > MAX_COPY_CHARS
            val recent = if (truncated) "[Older events omitted to fit clipboard limit]\n" + content.takeLast(MAX_COPY_CHARS) else content
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_title), recent))
            Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createExportDocument() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, getString(R.string.diagnostics_save_name, stamp))
        }, EXPORT_LOGS_REQUEST)
    }

    @Deprecated("Legacy result API is retained here to match existing Gallery document export flows")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT_LOGS_REQUEST || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        GalleryDiagnosticLogger.snapshot { events, crash ->
            Thread({
                val result = runCatching {
                    val output = contentResolver.openOutputStream(uri) ?: error("Document provider returned no output stream")
                    GalleryDiagnosticLogger.writeExport(output, events, crash, this)
                }
                runOnUiThread {
                    if (result.isSuccess) Toast.makeText(this, R.string.diagnostics_exported, Toast.LENGTH_SHORT).show()
                    else {
                        GalleryDiagnosticLogger.event(DiagnosticLevel.WARN, "Diagnostics", "export_failed", fields = mapOf("type" to result.exceptionOrNull()?.javaClass?.simpleName))
                        Toast.makeText(this, R.string.diagnostics_export_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }, "gallery-diagnostic-export").apply { isDaemon = true }.start()
        }
    }

    private fun confirmClear() {
        val dialog = OxygenClassicDialog.builder(this, getString(R.string.diagnostics_clear_confirm_title))
            .setMessage(R.string.diagnostics_clear_confirm_message)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                GalleryDiagnosticLogger.clear {
                    Toast.makeText(this, R.string.diagnostics_cleared, Toast.LENGTH_SHORT).show()
                    refreshStatus()
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        OxygenClassicDialog.show(this, dialog)
    }
}
