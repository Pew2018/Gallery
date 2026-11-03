package org.fossify.gallery.helpers

import android.os.Handler
import android.os.Looper

/** UI deadline includes queue wait. Timeout retires a request; it never labels partial data complete. */
internal class RefreshWatchdog(private val handler: Handler = Handler(Looper.getMainLooper())) {
    companion object { const val TIMEOUT_MS = 45_000L }
    private var pending: Runnable? = null
    fun arm(timeoutMs: Long = TIMEOUT_MS, expired: () -> Unit) {
        if (pending != null) return
        val task = Runnable { pending = null; expired() }
        pending = task
        handler.postDelayed(task, timeoutMs)
    }
    fun stop() { pending?.let(handler::removeCallbacks); pending = null }
}
