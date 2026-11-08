package org.fossify.gallery.helpers

import android.view.View

/** Covers an empty TextureView until the active player's first decoded frame is rendered. */
internal class VideoPreviewFrameHandoff(private val cover: View) {
    private var activePlayer: Any? = null

    fun bind(player: Any) {
        activePlayer = player
        cover.visibility = View.VISIBLE
    }

    fun onRenderedFirstFrame(player: Any) {
        if (player === activePlayer) cover.visibility = View.GONE
    }

    fun keepCoverForError(player: Any) {
        if (player === activePlayer) cover.visibility = View.VISIBLE
    }

    fun clear() {
        activePlayer = null
    }
}
