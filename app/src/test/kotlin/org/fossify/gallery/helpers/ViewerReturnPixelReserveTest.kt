package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bounded pixel reserve for the Viewer return target.
 *
 * The contract these tests pin down is what makes reusing the Grid's already-decoded
 * pixels safe: identity-keyed (the Grid's own request list), size-checked, consumed on
 * a hit, bounded, and never resurrected after it has been released.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class ViewerReturnPixelReserveTest {
    private fun pixels(size: Int): BitmapDrawable = BitmapDrawable(
        null as android.content.res.Resources?,
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888),
    )

    /** The Grid's request list, reproduced: value equality is the cache identity. */
    private fun requestKey(path: String, signature: String, crop: Boolean = true) =
        listOf(path, signature, 1, crop, 1, false, false, false)

    @Test
    fun aHitIsServedForTheSameRequestIdentity() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val drawable = pixels(670)
        reserve.remember(requestKey("/a.jpg", "sig-1"), drawable)
        assertTrue(reserve.contains(requestKey("/a.jpg", "sig-1"), 670, 670))
        // A later bind builds an equal list, not the same instance.
        assertSame(drawable, reserve.take(requestKey("/a.jpg", "sig-1"), 670, 670))
        assertEquals("a hit is consumed", 0, reserve.size)
        assertNull(reserve.take(requestKey("/a.jpg", "sig-1"), 670, 670))
    }

    @Test
    fun aDifferentSignatureOrCropMisses() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        reserve.remember(requestKey("/a.jpg", "sig-1"), pixels(670))
        assertNull("an edited file must not reuse the old pixels",
            reserve.take(requestKey("/a.jpg", "sig-2"), 670, 670))
        assertNull("a different crop is a different image",
            reserve.take(requestKey("/a.jpg", "sig-1", crop = false), 670, 670))
        assertEquals("a miss leaves the entry in place", 1, reserve.size)
    }

    @Test
    fun aDrawableDecodedForAnotherGridSizeIsRejected() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        reserve.remember(requestKey("/a.jpg", "sig-1"), pixels(670))
        assertNull("a 3-column grid must not receive a 4-column bitmap",
            reserve.take(requestKey("/a.jpg", "sig-1"), 500, 500))
        assertTrue("and the entry is still awaiting a matching holder",
            reserve.contains(requestKey("/a.jpg", "sig-1"), 670, 670))
    }

    @Test
    fun theCapacityIsHardBounded() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        (1..6).forEach { reserve.remember(requestKey("/$it.jpg", "sig"), pixels(670)) }
        assertEquals(3, reserve.size)
        assertFalse("the eldest entry was evicted",
            reserve.contains(requestKey("/1.jpg", "sig"), 670, 670))
        assertTrue(reserve.contains(requestKey("/6.jpg", "sig"), 670, 670))
    }

    @Test
    fun forgettingOnePathClearsOnlyThatPath() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        reserve.remember(requestKey("/a.jpg", "sig"), pixels(670))
        reserve.remember(requestKey("/b.jpg", "sig"), pixels(670))
        reserve.forget(requestKey("/a.jpg", "sig"))
        assertEquals(1, reserve.size)
        assertNull(reserve.take(requestKey("/a.jpg", "sig"), 670, 670))
        assertTrue(reserve.contains(requestKey("/b.jpg", "sig"), 670, 670))
    }

    @Test
    fun releasingTheSessionEmptiesTheReserve() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        reserve.remember(requestKey("/a.jpg", "sig"), pixels(670))
        reserve.remember(requestKey("/b.jpg", "sig"), pixels(670))
        reserve.clear()
        assertEquals(0, reserve.size)
        assertNull(reserve.take(requestKey("/a.jpg", "sig"), 670, 670))
    }

    @Test
    fun aBrokenIdentityIsNeverStored() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        reserve.remember(null, pixels(670))
        // A colour fill reports a non-positive intrinsic size: nothing to reuse.
        reserve.remember(requestKey("/a.jpg", "sig"), android.graphics.drawable.ColorDrawable(0xFFFF0000.toInt()))
        assertEquals(0, reserve.size)
        assertNull(reserve.take(requestKey("/a.jpg", "sig"), 670, 670))
    }
}
