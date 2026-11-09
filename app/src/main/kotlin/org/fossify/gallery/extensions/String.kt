package org.fossify.gallery.extensions

import android.os.Environment
import org.fossify.commons.helpers.NOMEDIA
import java.io.File
import java.io.IOException
import java.util.Locale

private fun String.withinFolder(root: String) = this == root || startsWith("${root.trimEnd('/')}/")

private fun String.matchesFolderRoots(roots: Set<String>, exact: Boolean = false): Boolean {
    if (roots.isEmpty()) return false
    if (roots.any { if (exact) this == it else withinFolder(it) }) return true
    val identity = getDistinctPath()
    return roots.any { root ->
        val parent = root.getDistinctPath()
        if (exact) identity == parent else identity.withinFolder(parent)
    }
}

fun String.isThisOrParentIncluded(includedPaths: MutableSet<String>) = matchesFolderRoots(includedPaths)

fun String.isThisOrParentExcluded(excludedPaths: MutableSet<String>) = matchesFolderRoots(excludedPaths)

// cache which folders contain .nomedia files to avoid checking them over and over again
fun String.shouldFolderBeVisible(
    excludedPaths: MutableSet<String>, includedPaths: MutableSet<String>, showHidden: Boolean,
    folderNoMediaStatuses: HashMap<String, Boolean>, callback: (path: String, hasNoMedia: Boolean) -> Unit
): Boolean {
    if (isEmpty()) {
        return false
    }

    val file = File(this)
    val filename = file.name
    if (filename.startsWith("img_", true) && file.isDirectory) {
        val files = file.list()
        if (files != null) {
            if (files.any { it.contains("burst", true) }) {
                return false
            }
        }
    }

    if (!showHidden && filename.startsWith('.')) {
        return false
    } else if (matchesFolderRoots(includedPaths, exact = true)) {
        return true
    }

    val containsNoMedia = if (showHidden) {
        false
    } else {
        val marker = "$this/$NOMEDIA"
        folderNoMediaStatuses[marker] ?: File(this, NOMEDIA).isFile.also { callback(marker, it) }
    }

    return if (!showHidden && containsNoMedia) {
        false
    } else if (matchesFolderRoots(excludedPaths, exact = true)) {
        false
    } else if (isThisOrParentIncluded(includedPaths)) {
        true
    } else if (isThisOrParentExcluded(excludedPaths)) {
        false
    } else if (!showHidden) {
        var containsNoMediaOrDot = containsNoMedia || contains("/.")
        if (!containsNoMediaOrDot) {
            var curPath = this
            for (i in 0 until count { it == '/' } - 1) {
                curPath = curPath.substringBeforeLast('/')
                val pathToCheck = "$curPath/$NOMEDIA"
                if (folderNoMediaStatuses.contains(pathToCheck)) {
                    if (folderNoMediaStatuses[pathToCheck] == true) {
                        containsNoMediaOrDot = true
                        break
                    }
                } else {
                    val noMediaExists = folderNoMediaStatuses.getOrElse(pathToCheck, { false }) || File(pathToCheck).isFile
                    callback(pathToCheck, noMediaExists)
                    if (noMediaExists) {
                        containsNoMediaOrDot = true
                        break
                    }
                }
            }
        }
        !containsNoMediaOrDot
    } else {
        true
    }
}

// recognize /sdcard/DCIM as the same folder as /storage/emulated/0/DCIM
fun String.getDistinctPath(): String {
    return try {
        File(this).canonicalPath
    } catch (e: IOException) {
        this
    }
}

fun String.isDownloadsFolder() = equals(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString(), true)

fun String.isThisOrParentFolderHidden(): Boolean {
    var curFile = File(this)
    while (true) {
        if (curFile.isHidden) {
            return true
        }

        curFile = curFile.parentFile ?: break
        if (curFile.absolutePath == "/") {
            break
        }
    }
    return false
}
