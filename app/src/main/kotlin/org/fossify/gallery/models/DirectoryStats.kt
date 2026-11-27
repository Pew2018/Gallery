package org.fossify.gallery.models

import androidx.room.ColumnInfo

/** Direct members only. Taken dates use Gallery's existing custom > indexed > modified fallback.
 * A zero is data; the bit mask, never the numeric value, defines completeness.
 * Empty scope/zero mask identifies legacy previews, not authoritative statistics.
 * scannedAt is the last persisted changed scan; no-op reads need not rewrite the row.
 */
data class DirectoryStats(
    @ColumnInfo(defaultValue = "0") val count: Int = 0,
    @ColumnInfo(defaultValue = "0") val bytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val minModified: Long = 0,
    @ColumnInfo(defaultValue = "0") val maxModified: Long = 0,
    @ColumnInfo(defaultValue = "0") val minTaken: Long = 0,
    @ColumnInfo(defaultValue = "0") val maxTaken: Long = 0,
    @ColumnInfo(defaultValue = "0") val types: Int = 0,
    @ColumnInfo(defaultValue = "0") val takenFallbackCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val known: Int = 0,
    @ColumnInfo(defaultValue = "''") val scope: String = "",
    @ColumnInfo(defaultValue = "0") val scannedAt: Long = 0
) {
    fun has(field: Int) = known and field == field

    companion object {
        const val COUNT = 1
        const val SIZE = 2
        const val MODIFIED = 4
        const val TAKEN = 8
        const val ALL = COUNT or SIZE or MODIFIED or TAKEN
        fun scope(filter: Int, hidden: Boolean) = "v1:$filter:$hidden"

        /** One pass, without media sorting. Flags describe the read, including empty results. */
        fun calculate(media: List<Medium>, scope: String, known: Int, scannedAt: Long = System.currentTimeMillis()): DirectoryStats {
            var count = 0
            var bytes = 0L
            var minModified = Long.MAX_VALUE
            var maxModified = Long.MIN_VALUE
            var minTaken = Long.MAX_VALUE
            var maxTaken = Long.MIN_VALUE
            var types = 0
            var fallbackCount = 0
            for (item in media) {
                count++
                if (item.takenIsFallback) fallbackCount++
                bytes += item.size
                minModified = minOf(minModified, item.modified)
                maxModified = maxOf(maxModified, item.modified)
                minTaken = minOf(minTaken, item.taken)
                maxTaken = maxOf(maxTaken, item.taken)
                types = types or item.type
            }
            return DirectoryStats(count, bytes, if (count == 0) 0 else minModified,
                if (count == 0) 0 else maxModified, if (count == 0) 0 else minTaken,
                if (count == 0) 0 else maxTaken, types, fallbackCount, known, scope, scannedAt)
        }

        /** A partial field cannot erase a previously known value for the SAME scope.
         * Missing fields remain incomplete for the new revision even when retaining preview values.
         */
        fun retainUnknown(fresh: DirectoryStats, old: DirectoryStats): DirectoryStats {
            if (fresh.scope != old.scope) return fresh
            return fresh.copy(
                bytes = if (fresh.has(SIZE)) fresh.bytes else old.bytes,
                minModified = if (fresh.has(MODIFIED)) fresh.minModified else old.minModified,
                maxModified = if (fresh.has(MODIFIED)) fresh.maxModified else old.maxModified,
                minTaken = if (fresh.has(TAKEN)) fresh.minTaken else old.minTaken,
                maxTaken = if (fresh.has(TAKEN)) fresh.maxTaken else old.maxTaken
            )
        }
    }
}
