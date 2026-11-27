package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Looper
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.bumptech.glide.request.target.CustomTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Phase 2.3 A/C01/C02/C12: integration with the project's **real** Glide 5.0.7.
 *
 * No Glide fake is used here: the callbacks below are invoked by Glide's own
 * `SingleRequest`. What is injected is only the *scheduling* of the clear, which is
 * the production behaviour under test. This test first proves that a synchronous
 * clear inside `onResourceReady` is rejected by the real dependency, then proves
 * that the lease's posted clear succeeds on the same real Glide instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    shadows = [ViewerPrefetchGlideIntegrationTest.HostWebpFactory::class,
        ViewerPrefetchGlideIntegrationTest.HostJxlCoder::class])
class ViewerPrefetchGlideIntegrationTest {

    /** Android's bundled ARM WebP library cannot load in a host JVM (same as the prefetch policy test). */
    @Implements(className = "com.bumptech.glide.integration.webp.WebpBitmapFactory", isInAndroidSdk = false)
    class HostWebpFactory {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    /** The JXL integration's static initializer loads libjxlcoder, absent on a host JVM. */
    @Implements(className = "com.awxkee.jxlcoder.JxlCoder", isInAndroidSdk = false)
    class HostJxlCoder {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    @Before fun pauseMain() = Shadows.shadowOf(Looper.getMainLooper()).pause()

    private val context get() = org.robolectric.RuntimeEnvironment.getApplication() as Application

    private fun bitmap() = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

    /** Warms Glide's memory cache so the next identical request is a synchronous hit. */
    private fun warm(bitmap: Bitmap, signature: ObjectKey) {
        // Glide forbids Future.get() on the main thread; use a real worker thread.
        val future = Glide.with(context).load(bitmap).signature(signature)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .submit()
        val worker = Thread {
            try {
                future.get(10, TimeUnit.SECONDS)
            } catch (t: Throwable) {
                println("GLIDE_WARMUP_FAILURE: ${t.javaClass.name}: ${t.message}")
            }
        }
        worker.start()
        worker.join(11_000L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun target(onReady: (Drawable) -> Unit, onCleared: () -> Unit = {}) =
        object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable,
                transition: com.bumptech.glide.request.transition.Transition<in Drawable>?) = onReady(resource)
            override fun onLoadCleared(placeholder: Drawable?) = onCleared()
        }

    // C01: a memory-cache hit is delivered synchronously on the main thread while
    // SingleRequest.isCallingCallbacks is true. This is the exact production shape
    // that produced the five 2026-11-25 crashes.
    @Test
    fun c01GlideMemoryHitDeliversTheCallbackSynchronouslyOnMainThread() {
        val bitmap = bitmap()
        val signature = ObjectKey("c01-memory")
        warm(bitmap, signature)
        var onMain = false
        Glide.with(context).load(bitmap).signature(signature)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(target({ onMain = Looper.myLooper() == Looper.getMainLooper() }))
        assertTrue("a memory hit must exercise the synchronous callback path", onMain)
    }

    // C12: prove the hazard is real with the actual dependency, not a permissive fake.
    @Test
    fun c12RealGlideRejectsASynchronousClearInsideTheResourceCallback() {
        val bitmap = bitmap()
        val signature = ObjectKey("c12-reentrant")
        warm(bitmap, signature)
        var rejected = false
        lateinit var reentrantTarget: CustomTarget<Drawable>
        reentrantTarget = target({
            try {
                Glide.with(context).clear(reentrantTarget)
            } catch (_: IllegalStateException) {
                // SingleRequest.assertNotCallingCallbacks() - the crash in the device log.
                rejected = true
            }
        })
        Glide.with(context).load(bitmap).signature(signature)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(reentrantTarget)
        assertTrue("Glide 5.0.7 must reject a synchronous clear in onResourceReady", rejected)
    }

    // C12 production path: the same real callback, but the lease only copies pixels,
    // publishes a terminal outcome and posts the clear. Draining the main queue runs
    // the clear after the callback guard is gone - without an exception.
    @Test
    fun c12TheLeaseClearsTheRealTargetOnlyAfterTheCallbackReturns() {
        val bitmap = bitmap()
        val signature = ObjectKey("c12-lease")
        warm(bitmap, signature)
        val reserve = ViewerReturnPixelReserve(3)
        val posted = ArrayDeque<Runnable>()
        val outcomes = mutableListOf<ViewerPrefetchLease.Outcome>()
        val owner = Any()
        lateinit var lease: ViewerPrefetchLease
        var clearedDuringCallback = false
        val target = target({ resource ->
            lease.ready(resource)
            assertFalse("the callback must not clear synchronously", clearedDuringCallback)
        }, { clearedDuringCallback = true })

        lease = ViewerPrefetchLease(1L, 1L, 1L, "/photos/a.jpg", listOf("a", signature),
            owner, target, reserve,
            scheduler = { posted.addLast(it) },
            clearer = { completed -> Glide.with(context).clear(completed) },
            onResolved = { _, outcome, _ -> outcomes.add(outcome) })

        Glide.with(context).load(bitmap).signature(signature)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(target)

        assertTrue(lease.isSettled)
        assertEquals(listOf(ViewerPrefetchLease.Outcome.READY), outcomes)
        assertTrue(reserve.contains(listOf("a", signature), 64, 64))
        assertEquals(1, posted.size)
        assertFalse("no clear before the queue is drained", clearedDuringCallback)

        posted.removeFirst().run() // real Glide clear, outside the callback
        assertTrue(lease.releaseExecuted)
        assertTrue("Glide must have delivered onLoadCleared", clearedDuringCallback)
        assertNotNull("the defensive copy is still servable", reserve.take(listOf("a", signature), 64, 64))
    }

    // C02: a cache miss goes through Glide's background decode and is delivered via
    // the main handler. The lease must work unchanged when the callback arrives late.
    @Test
    fun c02AnAsynchronousDecodeIsHandledWhenTheMainQueueRuns() {
        val bitmap = bitmap()
        val signature = ObjectKey("c02-async-${System.nanoTime()}")
        var ready = false
        val target = target({ ready = true })
        Glide.with(context).load(bitmap).signature(signature)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(target)

        assertFalse("a cache miss must not deliver during into()", ready)
        val deadline = System.currentTimeMillis() + 8000L
        while (!ready && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("real Glide must eventually deliver the async resource", ready)
    }
}
