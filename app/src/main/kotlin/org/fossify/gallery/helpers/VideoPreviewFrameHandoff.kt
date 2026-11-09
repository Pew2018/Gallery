package org.fossify.gallery.helpers

import android.view.View

/** Covers an empty TextureView until the active player's first decoded frame is rendered. */
internal class VideoPreviewFrameHandoff(private val cover: View) {
    private var activePlayer: Any? = null
    private var frameReady = false
    private var transitionRunning = false

    fun setTransitionRunning(running: Boolean) {
        transitionRunning = running
        publish()
    }

    private fun publish() {
        cover.visibility = if (frameReady && !transitionRunning) View.GONE else View.VISIBLE
    }

    fun bind(player: Any) {
        activePlayer = player
        frameReady = false
        publish()
    }

    fun onRenderedFirstFrame(player: Any) {
        if (player === activePlayer) { frameReady = true; publish() }
    }

    fun keepCoverForError(player: Any) {
        if (player === activePlayer) { frameReady = false; publish() }
    }

    fun clear() {
        activePlayer = null
    }
}
