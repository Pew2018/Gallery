package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StaticPhotoEntryGeometryTest {
    private val square = PhotoBox(0f, 0f, 100f, 100f)
    private val fit = StaticPhotoThumbnail(100, 50, 100, 100, false, 0)
    private val crop = StaticPhotoThumbnail(100, 100, 100, 100, true, 6)

    @Test fun fitRetainsTheWholeOrientedPhotoWhileCropUsesGlideRounding() {
        val header = StaticPhotoHeader(400, 200, 1, jpeg = true)
        val fitted = PhotoBox(0f, 25f, 100f, 75f)
        assertEquals(fitted, fit.project(fitted, header))
        assertEquals(PhotoBox(-49f, 0f, 151f, 100f), crop.project(square, header))
    }

    @Test fun exifTransposeAndTransverseUseTheAlreadyOrientedBitmapWithoutAnotherRotation() {
        for (orientation in 1..8) {
            val header = StaticPhotoHeader(400, 200, orientation, jpeg = true)
            val expected = if (orientation in 5..8) PhotoBox(0f, -49f, 100f, 151f)
                else PhotoBox(-49f, 0f, 151f, 100f)
            assertEquals("EXIF $orientation", expected, crop.project(square, header))
        }
    }

    @Test fun incompleteCropEvidenceCannotSupplyAnEndpoint() {
        val header = StaticPhotoHeader(400, 200, 1, jpeg = false)
        assertNull(crop.copy(requestWidth = 101).project(square, header))
        assertNull(crop.project(square, header.copy(width = 0)))
    }

    @Test fun aPartialClipMovesIndependentlyOfTheFullPhotoAndRoundedMask() {
        val window = PhotoWindowGeometry(PhotoBox(0f, 0f, 300f, 600f), listOf(0, 24, 0, 0))
        val source = StaticPhotoSource("image", "version", 1, crop,
            PhotoViewGeometry(square, square, PhotoBox(0f, 35f, 100f, 100f), window))
        val targetBox = PhotoBox(0f, 225f, 300f, 375f)
        val target = PhotoViewGeometry(targetBox, targetBox, targetBox, window)
        val fullSource = PhotoBox(-49f, 0f, 151f, 100f)
        val geometry = StaticPhotoEntryGeometry(source, target, fullSource)
        assertEquals(fullSource, geometry.pixelsAt(0f))
        assertEquals(35f, geometry.clipAt(0f).top)
        assertEquals(square, geometry.maskAt(0f))
        assertEquals(130f, geometry.clipAt(0.5f).top)
        assertEquals(3f, geometry.radiusAt(0.5f))
        assertEquals(targetBox, geometry.pixelsAt(1f))
        assertEquals(targetBox, geometry.clipAt(1f))
        assertEquals(0f, geometry.radiusAt(1f))
    }

    /** A proportional preview and its final decode can round to neighbouring pixels. */
    @Test fun handoffToleranceAcceptsPreviewRoundingButNotAGeometryChange() {
        val window = PhotoWindowGeometry(square, emptyList())
        val proxy = PhotoViewGeometry(square, square, square, window)
        val rounded = PhotoBox(square.left + 1f, square.top - 2f, square.right - 1f, square.bottom + 2f)
        val preview = PhotoViewGeometry(rounded, rounded, rounded, window)
        assertFalse("exact geometry checks stay strict", proxy.near(preview))
        assertTrue("the handoff accepts preview rounding", proxy.nearForHandoff(preview))
        val changed = PhotoBox(square.left + 8f, square.top, square.right, square.bottom)
        assertFalse("a real geometry change is still rejected",
            proxy.nearForHandoff(PhotoViewGeometry(changed, changed, changed, window)))
    }

    @Test fun earlyViewerCanReuseOnlyTheGridVersionOfTheSameFile() {
        val view = PhotoViewGeometry(square, square, square, PhotoWindowGeometry(square, emptyList()))
        val source = StaticPhotoSource("image", "version", 1, crop, view, 125L, 3_000L, 4L)
        val file = StaticPhotoFileVersion(125L, 3_901L, 4L)
        assertTrue(file.matchesGrid(source))
        assertFalse(file.copy(size = 126L).matchesGrid(source))
        assertFalse(file.copy(modified = 4_000L).matchesGrid(source))
        assertFalse(file.copy(invalidation = 5L).matchesGrid(source))
    }

    @Test fun sourceCaptureRequiresCurrentTokenAndMatchingPublication() {
        val session = MediaTransitionSessionCoordinator()
        val geometry = PhotoViewGeometry(square, square, square, PhotoWindowGeometry(square, emptyList()))
        val source = StaticPhotoSource("image", "version", 1, crop, geometry)
        session.publishStaticPhotoSource(session.entry, source)
        assertNull(session.readStaticPhotoSource(session.entry, requireConfirmed = true))
        session.confirmStaticPhotoSource(session.entry.copy(generation = 0), source)
        assertNull(session.readStaticPhotoSource(session.entry, requireConfirmed = true))
        session.confirmStaticPhotoSource(session.entry, source)
        assertNotNull(session.readStaticPhotoSource(session.entry, requireConfirmed = true))
        val later = source.copy(adapterVersion = 2)
        session.confirmStaticPhotoSource(session.entry, later)
        assertEquals(later, session.readStaticPhotoSource(session.entry, requireConfirmed = true))
        session.confirmStaticPhotoSource(session.entry, later.copy(signature = "changed"))
        assertNull(session.readStaticPhotoSource(session.entry))
    }
}
