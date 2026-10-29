package org.fossify.gallery.helpers

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerAccessCoordinatorTest {
    @Test fun canceledViewerBatchStopsItsWorker() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val task = ViewerAccessCoordinator.submit {
            entered.countDown()
            try {
                while (!Thread.currentThread().isInterrupted) Thread.sleep(10)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                stopped.countDown()
            }
        }

        assertTrue(entered.await(2, TimeUnit.SECONDS))
        ViewerAccessCoordinator.cancel(task)
        assertTrue(stopped.await(2, TimeUnit.SECONDS))
    }
}
