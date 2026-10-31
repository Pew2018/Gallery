package org.fossify.gallery.helpers

/** Build a bounded MediaStore selection, retaining exact directory membership. */
internal object PhotoMediaQueryPlan {
    private const val MAX_BINDINGS = 80

    fun selection(folders: Set<String>, dataColumn: String): Pair<String?, Array<String>?> {
        val bounded = folders.takeIf { it.size in 1..MAX_BINDINGS }
        return Pair(
            bounded?.joinToString(" OR ") { "$dataColumn LIKE ?" },
            bounded?.map { "$it/%" }?.toTypedArray()
        )
    }

    /** SQL LIKE is broad for %, _ and nested files; never trust it for membership. */
    fun includesDirectFile(path: String, folders: Set<String>): Boolean {
        return MediaStoreFolderKey.of(path.substringBeforeLast('/', "")) in folders
    }
}
