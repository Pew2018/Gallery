package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID

/** Process-private, disposable metadata. Never an authorization cache.
 * Large complete lists are spooled once; only one segment is retained in memory.
 * Cache deletion/eviction is an IO failure, never proof of an end boundary.
 */
internal class SegmentedPhotoCatalog private constructor(private val directory: File, val size: Int) {
    private val positions = object : LinkedHashMap<String, Int>(20_512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 20_512
    }
    private var cachedIndex = -1
    private var cached: List<Medium> = emptyList()

    @Synchronized operator fun get(index: Int): Medium {
        require(index in 0 until size)
        val segment = index / SEGMENT_SIZE
        if (cachedIndex != segment) {
            cached = ObjectInputStream(File(directory, "$segment.bin").inputStream().buffered()).use { input ->
                val count = input.readInt()
                require(count in 1..SEGMENT_SIZE)
                List(count) { input.readObject() as Medium }
            }
            cachedIndex = segment
            cached.forEachIndexed { offset, medium -> positions[medium.path] = segment * SEGMENT_SIZE + offset }
        }
        return cached[index % SEGMENT_SIZE]
    }

    /** No IO: unknown provisional filesystem rows must not trigger a full disk search while sorting. */
    @Synchronized fun knownIndexOf(path: String): Int? = positions[path]

    @Synchronized fun indexOf(path: String): Int {
        positions[path]?.let { return it }
        for (index in 0 until size) {
            if (Thread.currentThread().isInterrupted) throw PhotoLoadInvalidatedException()
            if (get(index).path == path) return index
        }
        return -1
    }

    fun range(first: Int, last: Int): List<Medium> = (first..last).map { get(it) }
    @Synchronized fun close() { cached = emptyList(); positions.clear(); cachedIndex = -1; directory.deleteRecursively() }

    companion object {
        private const val SEGMENT_SIZE = 512
        private const val MAX_BYTES = 128L * 1024 * 1024
        fun write(cacheDir: File, items: List<Medium>): SegmentedPhotoCatalog {
            val directory = File(cacheDir, "photo-viewer-${UUID.randomUUID()}")
            check(directory.mkdirs())
            try {
                var bytes = 0L
                items.chunked(SEGMENT_SIZE).forEachIndexed { index, batch ->
                    if (Thread.currentThread().isInterrupted) throw PhotoLoadInvalidatedException()
                    val file = File(directory, "$index.bin")
                    ObjectOutputStream(file.outputStream().buffered()).use { output ->
                        output.writeInt(batch.size)
                        batch.forEach { output.writeObject(it) }
                    }
                    bytes += file.length()
                    check(bytes <= MAX_BYTES) { "Photo catalog disk budget exceeded" }
                }
                return SegmentedPhotoCatalog(directory, items.size)
            } catch (error: Exception) { directory.deleteRecursively(); throw error }
        }
    }
}
