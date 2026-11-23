package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pre-decode entry preview must place the cropped grid bitmap on the same
 * screen box the grid showed, and keep that box attached to the interpolated
 * full-photo box. These are the pure endpoints the view uses; the actual pixels
 * still need device acceptance.
 */
class GridPhotoEntryPreviewTest {
    @Test fun sourceFrameProjectsTheRetainedCropExactlyWhereTheGridHadIt() {
        val full = PhotoBox(0f, 0f, 400f, 300f)
        val retained = PhotoBox(50f, 0f, 350f, 300f)
        val sourcePixels = PhotoBox(0f, 0f, 400f, 300f)
        assertEquals(PhotoBox(50f, 0f, 350f, 300f), retainedScreenBox(sourcePixels, full, retained))
    }

    @Test fun retainedCropTracksTheFullPhotoBoxDuringExpansion() {
        val full = PhotoBox(0f, 0f, 400f, 300f)
        val retained = PhotoBox(50f, 0f, 350f, 300f)
        val expanded = PhotoBox(100f, 200f, 900f, 800f)
        val box = retainedScreenBox(expanded, full, retained)
        // The crop keeps its relative horizontal position and full height.
        assertEquals(200f, box.left, 0.001f)
        assertEquals(800f, box.right, 0.001f)
        assertEquals(200f, box.top, 0.001f)
        assertEquals(800f, box.bottom, 0.001f)
    }

    @Test fun nonCroppedThumbnailCoversTheWholeProjectedPhoto() {
        val full = PhotoBox(10f, 20f, 210f, 120f)
        val retained = full
        val pixels = PhotoBox(0f, 0f, 100f, 50f)
        assertEquals(pixels, retainedScreenBox(pixels, full, retained))
    }

    @Test fun degenerateProjectionCannotProduceAnEmptyOrInvertedBand() {
        val box = retainedScreenBox(PhotoBox(0f, 0f, 100f, 100f), PhotoBox(0f, 0f, 0f, 100f),
            PhotoBox(0f, 0f, 10f, 10f))
        assertTrue(box.width <= 0f && box.height <= 0f)
    }
}
