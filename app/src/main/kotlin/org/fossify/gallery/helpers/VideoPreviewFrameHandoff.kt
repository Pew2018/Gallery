package org.fossify.gallery.helpers

import android.view.View

/** Covers an empty TextureView until the active player's first decoded frame is rendered. */
internal class VideoPreviewFrameHandoff(
    private val cover: View,
    private val onFrameVisible: () -> Unit = {},
    private val postDisplayFrame: ((Runnable) -> Unit) = { callback -> cover.postOnAnimation(callback) },
) {
    private var activePlayer: Any? = null
    private var frameReady = false
    private var transitionRunning = false
    private var frameHandoffGeneration = 0L

    fun setTransitionRunning(running: Boolean) {
        transitionRunning = running
        publish()
    }

    private fun publish() {
        val visibility = if (frameReady && !transitionRunning) View.GONE else View.VISIBLE
        if (cover.visibility == visibility) return
        cover.visibility = visibility
        if (visibility == View.GONE) onFrameVisible()
    }

    fun bind(player: Any) {
        frameHandoffGeneration++
        activePlayer = player
        frameReady = false
        publish()
    }

    fun onRenderedFirstFrame(player: Any) {
        if (player !== activePlayer) return
        val generation = ++frameHandoffGeneration
        // ExoPlayer reports the frame after submitting it to the output surface.
        // Keep the decoded preview over the TextureView for two display frames so
        // SurfaceTexture and the window compositor can latch that buffer before
        // the opaque cover is removed. This prevents an empty-surface flash at
        // the end of a shared-element transition on slower devices.
        postDisplayFrame {
            if (generation != frameHandoffGeneration || player !== activePlayer) return@postDisplayFrame
            postDisplayFrame {
                if (generation == frameHandoffGeneration && player === activePlayer) {
                    frameReady = true
                    publish()
                }
            }
        }
    }

    fun keepCoverForError(player: Any) {
        if (player === activePlayer) {
            frameHandoffGeneration++
            frameReady = false
            publish()
        }
    }

    fun clear() {
        frameHandoffGeneration++
        activePlayer = null
    }
}
