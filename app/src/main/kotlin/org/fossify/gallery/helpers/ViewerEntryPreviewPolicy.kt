package org.fossify.gallery.helpers

/**
 * Decides whether the first renderer frame may be a proportional Glide preview.
 *
 * The 2026-11-24 device log shows the Viewer entry waiting on the full-size decode:
 * request 8 had `first_pre_draw` at +134 ms, `viewer_first_renderer_signal` at +406 ms
 * and `shared_animation_started` at +429 ms. The renderer-ready gate itself is left
 * untouched here. What changes is *which* resource satisfies it first: for the one
 * entry photo whose identity and file version were validated against the Grid, Glide
 * delivers a size-multiplied preview of the same fit-center image, then upgrades it in
 * place. Because it is the same model, signature, transformation and aspect, the proxy
 * and the final renderer resolve to the same geometry; no separate guessed target is
 * introduced and no handoff shortcut is taken.
 *
 * Every other media path - neighbouring pages, rotations, subsampled images, GIF,
 * video, content URIs and unverified sources - keeps the original one-shot decode.
 */
internal object ViewerEntryPreviewPolicy {
    /**
     * Linear size multiplier: half the display size. A quarter would shave another few
     * milliseconds but its downsampled aspect can round a fitted pixel box by two pixels,
     * which is exactly the tolerance the shared proxy handoff checks. Half keeps the
     * preview geometrically indistinguishable from the final decode while still cutting
     * the decoded area to a quarter.
     */
    const val PREVIEW_SIZE_MULTIPLIER = 0.5f

    fun allow(
        isEntryMedia: Boolean,
        hasVerifiedGridSource: Boolean,
        rotationDegrees: Int,
        subsamplingVisible: Boolean,
    ): Boolean = isEntryMedia && hasVerifiedGridSource && rotationDegrees == 0 && !subsamplingVisible
}
