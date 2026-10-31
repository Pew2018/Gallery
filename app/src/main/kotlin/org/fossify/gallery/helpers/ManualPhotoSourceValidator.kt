package org.fossify.gallery.helpers

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.extensions.config
import java.io.File

internal enum class ManualPhotoSourceIssue {
    INVALID, MISSING, NOT_DIRECTORY, NO_ACCESS, HIDDEN, EXCLUDED, UNSUPPORTED
}

/** Validates a directly readable filesystem folder without scanning its contents. */
internal object ManualPhotoSourceValidator {
    fun validate(activity: BaseSimpleActivity, rawPath: String): ManualPhotoSourceIssue? {
        val raw = rawPath.trim()
        if (!raw.startsWith('/') || raw.startsWith("content://") || raw.isBlank()) return ManualPhotoSourceIssue.INVALID
        val file = runCatching { File(raw).absoluteFile }.getOrNull()
            ?: return ManualPhotoSourceIssue.INVALID
        val path = file.path.trimEnd('/').ifEmpty { "/" }
        if (path == "/") return ManualPhotoSourceIssue.INVALID
        fun hiddenSegment(value: String) = value.split('/').any { it.startsWith('.') && it != "." && it != ".." }
        if (hiddenSegment(path) || FavoriteFolderRules.hasNoMediaAncestor(path)) return ManualPhotoSourceIssue.HIDDEN
        if (runCatching { activity.isRestrictedWithSAFSdk30(path) && !activity.isInDownloadDir(path) }.getOrDefault(true)) {
            return ManualPhotoSourceIssue.UNSUPPORTED
        }
        if (!File(path).exists()) return ManualPhotoSourceIssue.MISSING
        if (!File(path).isDirectory) return ManualPhotoSourceIssue.NOT_DIRECTORY
        if (!File(path).canRead()) return ManualPhotoSourceIssue.NO_ACCESS
        // An empty array is a valid empty folder; null means the directory cannot
        // actually be listed by this app. The caller runs this check off-main-thread.
        if (runCatching { File(path).listFiles() }.getOrNull() == null) return ManualPhotoSourceIssue.NO_ACCESS

        val canonical = runCatching { File(path).canonicalPath }.getOrElse { return ManualPhotoSourceIssue.NO_ACCESS }
        if (hiddenSegment(path) || hiddenSegment(canonical)) return ManualPhotoSourceIssue.HIDDEN
        val config = activity.config
        if (FavoriteFolderRules.hasNoMediaAncestor(path) || FavoriteFolderRules.hasNoMediaAncestor(canonical)) {
            return ManualPhotoSourceIssue.HIDDEN
        }
        if (FavoriteFolderRules.isExcluded(path, config.excludedFolders) ||
            FavoriteFolderRules.isExcluded(canonical, config.excludedFolders)) return ManualPhotoSourceIssue.EXCLUDED
        return null
    }
}
