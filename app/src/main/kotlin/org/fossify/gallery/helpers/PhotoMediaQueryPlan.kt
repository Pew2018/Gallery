package org.fossify.gallery.helpers

/** Bounded queries; even large profiles never degrade into a full-library cursor. */
internal object PhotoMediaQueryPlan {
    private const val FOLDER_BATCH_SIZE = 40 // two bindings per direct-directory predicate
    private const val PATH_BATCH_SIZE = 400
    data class Query(val selection: String?, val args: Array<String>?)

    private fun escapeLike(path: String): String = path.replace("\\", "\\\\")
        .replace("%", "\\%").replace("_", "\\_")

    fun selection(folders: Set<String>, dataColumn: String): Pair<String?, Array<String>?> {
        require(folders.isNotEmpty() && folders.size <= FOLDER_BATCH_SIZE)
        return Pair(
            folders.joinToString(" OR ") { "($dataColumn LIKE ? ESCAPE '\\' AND $dataColumn NOT LIKE ? ESCAPE '\\')" },
            folders.flatMap { folder ->
                val prefix = escapeLike(folder.trimEnd('/'))
                listOf("$prefix/%", "$prefix/%/%")
            }.toTypedArray()
        )
    }

    fun folderQueries(folders: Set<String>, dataColumn: String): List<Query> =
        folders.toList().chunked(FOLDER_BATCH_SIZE).map { batch ->
            val (sql, args) = selection(batch.toSet(), dataColumn)
            Query(sql, args)
        }

    fun pathQueries(paths: Set<String>, dataColumn: String): List<Query> =
        paths.toList().chunked(PATH_BATCH_SIZE).map { batch ->
            Query("$dataColumn COLLATE BINARY IN (${batch.joinToString(",") { "?" }})", batch.toTypedArray())
        }

    /** Keep an exact, case-sensitive check even when a provider uses case-insensitive LIKE. */
    fun includesDirectFile(path: String, folders: Set<String>): Boolean =
        MediaStoreFolderKey.of(path.substringBeforeLast('/', "")) in folders
}
