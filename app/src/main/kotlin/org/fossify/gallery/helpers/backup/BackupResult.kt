package org.fossify.gallery.helpers.backup

/** Every distinct input record has exactly one outcome. Duplicated lines are counted separately. */
data class BackupImportResult(
    val total: Int,
    val imported: Int = 0,
    val existing: Int = 0,
    val unavailable: Int = 0,
    val invalid: Int = 0,
    val protectedSkipped: Int = 0,
    val protectionChanged: Int = 0,
    val duplicates: Int = 0,
    val conflicts: Int = 0
) {
    val skipped: Int get() = unavailable + invalid + protectedSkipped + protectionChanged + duplicates
}
