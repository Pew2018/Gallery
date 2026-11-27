package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.3 A, request-ownership tests (C03-C11).
 *
 * These drive the same [ViewerPrefetchLease] the production Activity uses. The
 * scheduler/clearer are injected so the exact instant of a terminal callback and of
 * a deferred release is deterministic; real Glide re-entrancy is covered separately
 * in [ViewerPrefetchGlideIntegrationTest] with the project's actual Glide 5.0.7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ViewerPrefetchLeaseTest {

    private class Fixture {
        val releases = ArrayDeque<Runnable>()
        val cleared = mutableListOf<CustomTarget<Drawable>>()
        val outcomes = mutableListOf<Triple<ViewerPrefetchLease, ViewerPrefetchLease.Outcome, Boolean>>()
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val owner = Any()

        fun lease(
            generation: Long = 1L,
            session: Long = 1L,
            requestId: Long = generation,
            path: String = "/photos/a.jpg",
            key: Any = "a-key",
        ): Pair<ViewerPrefetchLease, CustomTarget<Drawable>> {
            lateinit var lease: ViewerPrefetchLease
            val target = object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: com.bumptech.glide.request.transition.Transition<in Drawable>?) = Unit
                override fun onLoadCleared(placeholder: Drawable?) = Unit
            }
            lease = ViewerPrefetchLease(
                generation = generation,
                viewerSessionId = session,
                requestId = requestId,
                path = path,
                key = key,
                owner = owner,
                target = target,
                reserve = reserve,
                scheduler = { releases.addLast(it) },
                clearer = { cleared.add(it) },
                onResolved = { resolved, outcome, stored -> outcomes.add(Triple(resolved, outcome, stored)) },
            )
            return lease to target
        }

        fun pixels(size: Int = 64) = BitmapDrawable(null as android.content.res.Resources?,
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888))

        fun drain() {
            while (releases.isNotEmpty()) releases.removeFirst().run()
        }
    }

    private fun ViewerPrefetchLease.Outcome.countIn(f: Fixture) = f.outcomes.count { it.second == this }

    // C03 / C08: path and key are equal for both A requests, only generation and
    // session distinguish them. A late first-A callback must not settle the second A.
    @Test
    fun c03SamePathAndKeyFromAnOlderGenerationCannotSettleTheNewRequest() {
        val f = Fixture()
        val (oldA, _) = f.lease(generation = 1L, session = 1L)
        val (_, targetB) = f.lease(generation = 2L, session = 1L, path = "/photos/b.jpg", key = "b-key")
        val (newA, newATarget) = f.lease(generation = 3L, session = 2L)

        assertTrue(oldA.matches(1L, 1L, "/photos/a.jpg", "a-key", oldA.target))
        assertFalse(oldA.matches(3L, 2L, "/photos/a.jpg", "a-key", oldA.target))
        assertFalse(newA.matches(3L, 1L, "/photos/a.jpg", "a-key", newATarget))
        assertNotSame(oldA.target, newATarget)

        oldA.ready(f.pixels())
        assertEquals(ViewerPrefetchLease.Outcome.READY, oldA.outcome)
        assertNull("new A is untouched by the old callback", newA.outcome)
        assertEquals(1, ViewerPrefetchLease.Outcome.READY.countIn(f))

        newA.ready(f.pixels(48))
        assertEquals(2, ViewerPrefetchLease.Outcome.READY.countIn(f))
        f.drain()
        assertEquals(2, f.cleared.size)
    }

    @Test
    fun c08LateFailureAndLateCancellationAreCountedButIgnored() {
        val f = Fixture()
        val (lease, _) = f.lease()
        lease.ready(f.pixels())
        lease.failed()
        lease.cancel("late_page_change")
        assertEquals(2, lease.staleCallbacks)
        assertEquals(ViewerPrefetchLease.Outcome.READY, lease.outcome)
        assertEquals(1, ViewerPrefetchLease.Outcome.READY.countIn(f))
        assertEquals("exactly one release is scheduled", 1, f.releases.size)
        f.drain()
        assertEquals("the target is cleared exactly once", 1, f.cleared.size)
    }

    // C04 / C05: leaving the Viewer and destroying the Activity both cancel the
    // same lease; the terminal transition and the release stay single.
    @Test
    fun c04CancellingWhileTheRequestIsInFlightHasOneTerminalAndOneClear() {
        val f = Fixture()
        val (lease, target) = f.lease()
        lease.cancel("page_changed")
        lease.cancel("session_ended")
        lease.ready(f.pixels())
        assertEquals(ViewerPrefetchLease.Outcome.CANCELLED, lease.outcome)
        assertEquals("page_changed", lease.cancelReason)
        assertEquals(1, ViewerPrefetchLease.Outcome.CANCELLED.countIn(f))
        assertEquals(2, lease.staleCallbacks)
        assertFalse(lease.storedPixels)
        f.drain()
        assertEquals(1, f.cleared.size)
        assertSame(target, f.cleared.single())
    }

    @Test
    fun c05ParentTeardownCannotBeLostInAHandlerMassClear() {
        val f = Fixture()
        val (lease, _) = f.lease()
        lease.cancel("activity_destroy")
        // Animation teardown drops unrelated handler messages. The release queue is
        // independent, so the task is still there and still clears this target.
        val captured = f.releases.toList()
        f.releases.clear()
        captured.forEach { it.run() }
        assertTrue(lease.releaseExecuted)
        assertEquals(1, f.cleared.size)
    }

    // C06: a failed load releases the target instead of leaving a stuck request.
    @Test
    fun c06FailedLoadSchedulesReleaseWithoutStoringPixels() {
        val f = Fixture()
        val (lease, _) = f.lease()
        lease.failed()
        assertEquals(ViewerPrefetchLease.Outcome.FAILED, lease.outcome)
        assertEquals(1, ViewerPrefetchLease.Outcome.FAILED.countIn(f))
        assertFalse(lease.storedPixels)
        f.drain()
        assertTrue(lease.releaseExecuted)
    }

    // C07: an old target's delayed clear must never touch the new target.
    @Test
    fun c07OldTargetDelayedReleaseDoesNotClearTheNewTarget() {
        val f = Fixture()
        val (oldLease, oldTarget) = f.lease(generation = 1L, path = "/a", key = "a")
        val (newLease, newTarget) = f.lease(generation = 2L, session = 2L, path = "/b", key = "b")
        oldLease.cancel("superseded_by_current")
        newLease.ready(f.pixels())

        // Run only the old task first, as a late main-queue delivery would.
        f.releases.removeFirst().run()
        assertEquals(listOf(oldTarget), f.cleared)
        assertFalse(newLease.releaseExecuted)
        f.drain()
        assertEquals(listOf(oldTarget, newTarget), f.cleared)
    }

    // C09: the reserve owns a copy, so consuming it after the Glide clear is safe.
    @Test
    fun c09CopiedPixelsSurviveTheDeferredGlideClear() {
        val f = Fixture()
        val key = "a-key"
        val (lease, _) = f.lease(key = key)
        lease.ready(f.pixels(64))
        assertTrue(lease.storedPixels)
        assertEquals("BitmapDrawable", lease.drawableType)
        assertEquals("64x64", lease.intrinsicSize)
        f.drain() // Glide clears the source drawable here.
        assertTrue(lease.releaseExecuted)
        val served = f.reserve.take(key, 64, 64)
        assertNotNull("the defensive copy must still be servable", served)
        assertEquals(0, f.reserve.size)
    }

    @Test
    fun c09bAnUnusableDrawableIsNotReservedButTheRequestStillReleases() {
        val f = Fixture()
        val (lease, _) = f.lease()
        val hardware = BitmapDrawable(null as android.content.res.Resources?,
            Bitmap.createBitmap(64, 64, Bitmap.Config.HARDWARE))
        lease.ready(hardware)
        assertFalse(lease.storedPixels)
        f.drain()
        assertTrue(lease.releaseExecuted)
        assertEquals(0, f.reserve.size)
    }

    // C10: 100 rapid page changes leave at most one unsettled lease and a bounded
    // reserve; every request reaches exactly one terminal and one release.
    @Test
    fun c10RapidPagingLeavesBoundedOutstandingRequestsAndReserve() {
        val f = Fixture()
        repeat(100) { index ->
            val (lease, _) = f.lease(
                generation = index.toLong() + 1L,
                path = "/photos/$index.jpg",
                key = "key-$index",
            )
            lease.cancel("page_changed")
            f.drain()
            assertTrue(lease.isSettled)
            assertTrue(lease.releaseExecuted)
            assertTrue(f.reserve.size <= ViewerReturnPixelReserve.DEFAULT_CAPACITY)
            assertTrue(f.reserve.reservedBytes <= ViewerReturnPixelReserve.MAX_TOTAL_BYTES)
        }
        assertEquals(100, f.cleared.size)
        assertEquals(100, ViewerPrefetchLease.Outcome.CANCELLED.countIn(f))
    }

    @Test
    fun c11ReleaseIsScheduledInsideTheCallbackAndRunsOnlyWhenDrained() {
        val f = Fixture()
        val (lease, _) = f.lease()
        lease.ready(f.pixels())
        assertTrue("the callback must schedule, not execute, the clear", lease.releaseScheduled)
        assertFalse(lease.releaseExecuted)
        assertTrue(f.cleared.isEmpty())
        f.drain()
        assertTrue(lease.releaseExecuted)
        assertEquals(1, f.cleared.size)
    }

    @Test
    fun settleStillReleasesWhenTheObserverThrows() {
        val throws = Fixture().apply {
            // Mirror the production contract with an observer that fails: the clear
            // must still happen because diagnostics must not own the target.
        }
        lateinit var lease: ViewerPrefetchLease
        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: com.bumptech.glide.request.transition.Transition<in Drawable>?) = Unit
            override fun onLoadCleared(placeholder: Drawable?) = Unit
        }
        lease = ViewerPrefetchLease(1L, 1L, 1L, "/a", "a", throws.owner, target, throws.reserve,
            scheduler = { throws.releases.addLast(it) },
            clearer = { throws.cleared.add(it) },
            onResolved = { _, _, _ -> throw IllegalStateException("diagnostics failed") })
        try {
            lease.ready(throws.pixels())
        } catch (_: IllegalStateException) {
            // expected: the observer's failure propagates, release still scheduled
        }
        assertTrue(lease.releaseScheduled)
        throws.drain()
        assertEquals(1, throws.cleared.size)
    }
}
