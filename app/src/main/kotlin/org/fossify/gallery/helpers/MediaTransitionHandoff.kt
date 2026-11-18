package org.fossify.gallery.helpers

import android.content.Intent
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.Token
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** One-use, in-process rendezvous. Both Activities retain their own cleanup actions. */
internal object MediaTransitionHandoff {
    private const val SESSION = "org.fossify.gallery.extra.TRANSITION_SESSION"
    private const val GENERATION = "org.fossify.gallery.extra.TRANSITION_GENERATION"
    private const val OPERATION = "org.fossify.gallery.extra.TRANSITION_OPERATION"
    private val pending = WeakHashMap<String, WeakReference<MediaTransitionSessionCoordinator>>()

    fun create(): MediaTransitionSessionCoordinator {
        val observer = if (GalleryDiagnosticLogger.isEnabled()) {
            { event: String, token: Token, detail: String ->
                GalleryDiagnosticLogger.observe {
                    GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "ViewerTransition", event, fields = mapOf(
                        "runtimeSessionId" to token.sessionId,
                        "generation" to token.generation,
                        "operationId" to token.operationId,
                        "detail" to detail,
                    ))
                }
            }
        } else null
        return MediaTransitionSessionCoordinator(observer)
    }

    fun offer(intent: Intent, session: MediaTransitionSessionCoordinator) {
        session.attachGrid()
        pending[session.entry.sessionId] = WeakReference(session)
        write(intent, session.entry)
    }

    fun claim(intent: Intent): MediaTransitionSessionCoordinator? {
        val token = read(intent) ?: return null
        val session = pending[token.sessionId]?.get() ?: return null
        if (token != session.entry || !session.attachViewer()) return null
        pending.remove(token.sessionId)
        return session
    }

    fun withdraw(session: MediaTransitionSessionCoordinator) {
        pending.remove(session.entry.sessionId)
        session.detachGrid()
    }

    fun write(intent: Intent, token: Token): Intent = intent.putExtra(SESSION, token.sessionId)
        .putExtra(GENERATION, token.generation).putExtra(OPERATION, token.operationId)

    fun read(intent: Intent?): Token? {
        val id = intent?.getStringExtra(SESSION) ?: return null
        return Token(id, intent.getLongExtra(GENERATION, 0L), intent.getLongExtra(OPERATION, 0L))
    }
}
