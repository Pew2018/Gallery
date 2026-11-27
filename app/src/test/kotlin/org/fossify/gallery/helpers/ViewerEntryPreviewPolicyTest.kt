package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entry preview is a renderer frame, not a second shared-element path.
 *
 * It is only allowed for the single photo whose Grid identity and file version were
 * verified, at the untransformed fit-center renderer, so the proxy still gets its target
 * geometry from the real view and the renderer-ready gate remains in force.
 */
class ViewerEntryPreviewPolicyTest {
    @Test
    fun theVerifiedEntryPhotoGetsAProportionalRendererPreview() {
        assertTrue(ViewerEntryPreviewPolicy.allow(
            isEntryMedia = true, hasVerifiedGridSource = true, rotationDegrees = 0,
            subsamplingVisible = false))
        assertEquals(0.5f, ViewerEntryPreviewPolicy.PREVIEW_SIZE_MULTIPLIER, 0f)
    }

    @Test
    fun neighbouringPagesKeepTheSingleDecode() {
        listOf(false, true).forEach { verified ->
            assertFalse(ViewerEntryPreviewPolicy.allow(
                isEntryMedia = false, hasVerifiedGridSource = verified,
                rotationDegrees = 0, subsamplingVisible = false))
        }
    }

    @Test
    fun anUnverifiedSourceCannotShortenTheEntry() {
        assertFalse(ViewerEntryPreviewPolicy.allow(
            isEntryMedia = true, hasVerifiedGridSource = false,
            rotationDegrees = 0, subsamplingVisible = false))
    }

    @Test
    fun rotationsAndSubsampledRenderersKeepTheSingleDecode() {
        assertFalse(ViewerEntryPreviewPolicy.allow(
            isEntryMedia = true, hasVerifiedGridSource = true,
            rotationDegrees = 90, subsamplingVisible = false))
        assertFalse(ViewerEntryPreviewPolicy.allow(
            isEntryMedia = true, hasVerifiedGridSource = true,
            rotationDegrees = 0, subsamplingVisible = true))
    }
}
