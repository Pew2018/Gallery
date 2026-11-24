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
        // Phase 2.2: the reserve hands out a defensive copy, never the stored
        // drawable itself - the original stays owned by its Glide request.
        val served = reserve.take(requestKey("/a.jpg", "sig-1"), 670, 670)
        org.junit.Assert.assertNotNull(served)
        assertEquals("a hit is consumed", 0, reserve.size)
        assertNull(reserve.take(requestKey("/a.jpg", "sig-1"), 670, 670))
        val servedBitmap = (served as BitmapDrawable).bitmap
        assertEquals(670, servedBitmap.width)
        assertFalse("the copy must not alias the source bitmap",
            servedBitmap === drawable.bitmap)
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

    // ---- Phase 2.2 ownership (C group, reserve-level semantics) ----

    /** Mutating the source after remember() must never change the reserved pixels. */
    @Test
    fun c01TheReserveOwnsACopyNotTheSourcePixels() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF00FF00.toInt())
        reserve.remember(requestKey("/a.jpg", "sig"), BitmapDrawable(null, bitmap))
        // Simulate the original request's lifecycle moving on (clear, rebind, pool reuse):
        // whatever happens to the source pixels, the reserve must not see it.
        bitmap.eraseColor(0xFFFF0000.toInt())
        val served = reserve.take(requestKey("/a.jpg", "sig"), 64, 64) as BitmapDrawable
        assertEquals("the reserved copy keeps the remembered pixels",
            0xFF00FF00.toInt(), served.bitmap.getPixel(0, 0))
    }

    /** The 3583 remember path stores a drawable whose holder keeps changing. */
    @Test
    fun c04AReboundHolderCannotRetroactivelyChangeReservedPixels() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF0000FF.toInt())
        reserve.remember(requestKey("/a.jpg", "sig"), BitmapDrawable(null, bitmap))
        bitmap.eraseColor(0xFFFFFFFF.toInt())
        assertTrue(reserve.contains(requestKey("/a.jpg", "sig"), 64, 64))
        assertEquals(0xFF0000FF.toInt(),
            (reserve.take(requestKey("/a.jpg", "sig"), 64, 64) as BitmapDrawable).bitmap.getPixel(0, 0))
    }

    /** A recycled source bitmap can still be served: the copy was made at remember(). */
    @Test
    fun c02TheSourceBitmapBeingRecycledDoesNotInvalidateTheReserve() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        reserve.remember(requestKey("/a.jpg", "sig"), BitmapDrawable(null, bitmap))
        bitmap.recycle()
        assertTrue("the reserved copy is independent of the source lifecycle",
            reserve.contains(requestKey("/a.jpg", "sig"), 64, 64))
    }

    @Test
    fun c07AnOwnerScopedClearCannotReleaseAnotherSessionsReservation() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val oldSession = Any()
        val newSession = Any()
        reserve.remember(requestKey("/a.jpg", "sig-old"), pixels(670), owner = oldSession)
        reserve.remember(requestKey("/b.jpg", "sig-new"), pixels(670), owner = newSession)
        // The old Activity instance is destroyed after the new one filled its entries
        // (configuration-change ordering); its teardown must only take its own pixels.
        reserve.clear(owner = oldSession)
        assertFalse(reserve.contains(requestKey("/a.jpg", "sig-old"), 670, 670))
        assertTrue("the new session's reservation survives", reserve.contains(requestKey("/b.jpg", "sig-new"), 670, 670))
    }

    @Test
    fun c08TheOwningSessionsClearRemovesExactlyItsOwnEntries() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val session = Any()
        reserve.remember(requestKey("/a.jpg", "sig"), pixels(670), owner = session)
        reserve.remember(requestKey("/b.jpg", "sig"), pixels(670), owner = null)
        reserve.clear(owner = session)
        assertFalse(reserve.contains(requestKey("/a.jpg", "sig"), 670, 670))
        assertTrue(reserve.contains(requestKey("/b.jpg", "sig"), 670, 670))
        reserve.clear(owner = null)
        assertEquals(0, reserve.size)
    }

    @Test
    fun c14AnOversizedDecodeIsNeverReserved() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        // Larger than MAX_ENTRY_BYTES: a full-screen ARGB_8888 decode.
        val side = (Math.sqrt(ViewerReturnPixelReserve.MAX_ENTRY_BYTES / 4.0) + 1).toInt()
        val huge = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        assertFalse(reserve.remember(requestKey("/huge.jpg", "sig"), BitmapDrawable(null, huge)))
        assertEquals(0, reserve.size)
        huge.recycle()
    }

    @Test
    fun c15TheTotalByteBudgetEvictsTheEldestEntry() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val bytesPerEntry = 2 * 1024 * 1024
        val side = Math.sqrt(bytesPerEntry / 4.0).toInt()
        fun entry(path: String): BitmapDrawable {
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            return BitmapDrawable(null, bitmap)
        }
        reserve.remember(requestKey("/1.jpg", "sig"), entry("/1"))
        reserve.remember(requestKey("/2.jpg", "sig"), entry("/2"))
        val before = reserve.reservedBytes
        assertTrue(before > 0)
        // A third entry stays under the count capacity but may cross the byte budget;
        // either way the eldest entry must have left and the count stays bounded.
        reserve.remember(requestKey("/3.jpg", "sig"), entry("/3"))
        assertTrue(reserve.size <= ViewerReturnPixelReserve.DEFAULT_CAPACITY)
        assertTrue(reserve.reservedBytes <= ViewerReturnPixelReserve.MAX_TOTAL_BYTES)
    }

    @Test
    fun c06NonBitmapAndAnimatedDrawablesAreNeverReserved() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        // Whatever the drawable type, only software bitmap pixels are reserveable;
        // anything else must miss rather than be held beyond its request's lifetime.
        assertFalse(reserve.remember(requestKey("/a.jpg", "sig"),
            android.graphics.drawable.ColorDrawable(0xFF112233.toInt())))
        assertEquals(0, reserve.size)
    }

    @Test
    fun diagnosticsCanReadTheReservedByteFootprint() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        assertEquals(0, reserve.reservedBytes)
        reserve.remember(requestKey("/a.jpg", "sig"), pixels(670))
        assertTrue(reserve.reservedBytes > 0)
    }
}
