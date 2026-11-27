package org.fossify.gallery.helpers

/**
 * What the Viewer is currently showing, published for the Grid's bounded return
 * prefetch (Phase 2.1 follow-up).
 *
 * The Grid Activity is stopped while the Viewer is open, so it cannot observe pager
 * events directly. The Viewer reports the selected path here on every settled page
 * change; the Grid registers an observer for the duration of one Viewer session and
 * removes it when the session ends. Paths are process-internal state; nothing here
 * ever reaches diagnostics logging.
 */
internal object ViewerCurrentMediaRegistry {
    private val lock = Any()
    private val observers = LinkedHashMap<Any, (String?) -> Unit>()
    private var currentPath: String? = null

    /** The Viewer calls this whenever its selected media changes (null = detached). */
    fun report(path: String?) {
        val notify: List<(String?) -> Unit>
        synchronized(lock) {
            if (currentPath == path) return
            currentPath = path
            notify = observers.values.toList()
        }
        notify.forEach { it(path) }
    }

    /** The Grid observes while one Viewer session is live. Last registration wins. */
    fun observe(owner: Any, observer: (String?) -> Unit) {
        synchronized(lock) { observers[owner] = observer }
    }

    fun remove(owner: Any) {
        synchronized(lock) { observers.remove(owner) }
    }

    fun current(): String? = synchronized(lock) { currentPath }

    /** Drops every observer and the current path; used when the Viewer goes away. */
    fun reset() {
        synchronized(lock) {
            observers.clear()
            currentPath = null
        }
    }
}
