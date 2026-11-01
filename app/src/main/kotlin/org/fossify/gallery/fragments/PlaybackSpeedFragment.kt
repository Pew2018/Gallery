package org.fossify.gallery.fragments

import org.fossify.gallery.helpers.OxygenClassicDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.helpers.ClassicCommonsHooks
import org.fossify.commons.extensions.*
import org.fossify.commons.views.MySeekBar
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.FragmentPlaybackSpeedBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.interfaces.PlaybackSpeedListener

class PlaybackSpeedFragment : DialogFragment() {
    private val MIN_PLAYBACK_SPEED = 0.25f
    private val MAX_PLAYBACK_SPEED = 3f
    private val MAX_PROGRESS = (MAX_PLAYBACK_SPEED * 100 + MIN_PLAYBACK_SPEED * 100).toInt()
    private val HALF_PROGRESS = MAX_PROGRESS / 2
    private val STEP = 0.05f

    private var seekBar: MySeekBar? = null
    private var listener: PlaybackSpeedListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val inflater = requireActivity().layoutInflater
        val config = requireContext().config
        val binding = FragmentPlaybackSpeedBinding.inflate(inflater)
        binding.apply {
            seekBar = playbackSpeedSeekbar
            requireContext().updateTextColors(playbackSpeedHolder)
            playbackSpeedSlow.applyColorFilter(requireContext().getProperTextColor())
            playbackSpeedFast.applyColorFilter(requireContext().getProperTextColor())
            playbackSpeedTitle.textSize = 20f
            listOf(playbackSpeedSlow, playbackSpeedFast).forEach {
                it.background = OxygenClassicDialog.ripple(requireContext().getProperTextColor(), resources.displayMetrics.density)
                it.minimumWidth = (48f * resources.displayMetrics.density).toInt()
                it.minimumHeight = (48f * resources.displayMetrics.density).toInt()
            }
            playbackSpeedSlow.setOnClickListener { reduceSpeed() }
            playbackSpeedFast.setOnClickListener { increaseSpeed() }
            initSeekbar(playbackSpeedSeekbar, playbackSpeedLabel, config)
        }

        val alert = OxygenClassicDialog.builder(requireActivity(), "")
            .setView(binding.root)
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .create()
        ClassicCommonsHooks.beforeDialogShow(requireActivity(), alert, binding.root)
        return alert
    }

    override fun onStart() {
        super.onStart()
        val alert = dialog as? AlertDialog ?: return
        OxygenClassicDialog.apply(requireActivity(), alert, alert.window!!.decorView)
        ClassicCommonsHooks.onDialogShown(requireActivity(), alert, null)
    }

    override fun onDestroyView() {
        seekBar = null
        super.onDestroyView()
    }

    private fun initSeekbar(seekbar: MySeekBar, speedLabel: MyTextView, config: Config) {
        val formattedValue = formatPlaybackSpeed(config.playbackSpeed)
        speedLabel.text = "${formattedValue}x"
        seekbar.max = MAX_PROGRESS

        val playbackSpeedProgress = config.playbackSpeedProgress
        if (playbackSpeedProgress == -1) {
            config.playbackSpeedProgress = HALF_PROGRESS
        }
        seekbar.progress = config.playbackSpeedProgress

        var lastUpdatedProgress = config.playbackSpeedProgress
        var lastUpdatedFormattedValue = formattedValue

        seekbar.onSeekBarChangeListener { progress ->
            val playbackSpeed = getPlaybackSpeed(progress)
            if (playbackSpeed.toString() != lastUpdatedFormattedValue) {
                lastUpdatedProgress = progress
                lastUpdatedFormattedValue = playbackSpeed.toString()
                config.playbackSpeed = playbackSpeed
                config.playbackSpeedProgress = progress

                speedLabel.text = "${formatPlaybackSpeed(playbackSpeed)}x"
                listener?.updatePlaybackSpeed(playbackSpeed)
            } else {
                seekbar.progress = lastUpdatedProgress
            }
        }
    }

    private fun getPlaybackSpeed(progress: Int): Float {
        var playbackSpeed = when {
            progress < HALF_PROGRESS -> {
                val lowerProgressPercent = progress / HALF_PROGRESS.toFloat()
                val lowerProgress = (1 - MIN_PLAYBACK_SPEED) * lowerProgressPercent + MIN_PLAYBACK_SPEED
                lowerProgress
            }

            progress > HALF_PROGRESS -> {
                val upperProgressPercent = progress / HALF_PROGRESS.toFloat() - 1
                val upperDiff = MAX_PLAYBACK_SPEED - 1
                upperDiff * upperProgressPercent + 1
            }

            else -> 1f
        }
        playbackSpeed = Math.min(Math.max(playbackSpeed, MIN_PLAYBACK_SPEED), MAX_PLAYBACK_SPEED)
        val stepMultiplier = 1 / STEP
        return Math.round(playbackSpeed * stepMultiplier) / stepMultiplier
    }

    private fun reduceSpeed() {
        var currentProgress = seekBar?.progress ?: return
        val currentSpeed = requireContext().config.playbackSpeed
        while (currentProgress > 0) {
            val newSpeed = getPlaybackSpeed(--currentProgress)
            if (newSpeed != currentSpeed) {
                seekBar!!.progress = currentProgress
                break
            }
        }
    }

    private fun increaseSpeed() {
        var currentProgress = seekBar?.progress ?: return
        val currentSpeed = requireContext().config.playbackSpeed
        while (currentProgress < MAX_PROGRESS) {
            val newSpeed = getPlaybackSpeed(++currentProgress)
            if (newSpeed != currentSpeed) {
                seekBar!!.progress = currentProgress
                break
            }
        }
    }

    private fun formatPlaybackSpeed(value: Float) = String.format("%.2f", value)

    fun setListener(playbackSpeedListener: PlaybackSpeedListener) {
        listener = playbackSpeedListener
    }
}
