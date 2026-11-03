package org.fossify.gallery.helpers

import java.io.File

/** Disk observations live for one refresh only; a directory named .nomedia is not a marker file. */
internal class NoMediaSnapshot {
    private val observations = hashMapOf<String, Boolean>()
    /** Retain validated provider hints where File metadata is inaccessible; never mark a real directory. */
    fun seedMarkerFolders(paths: Collection<String>) {
        paths.filter { !File(it, ".nomedia").isDirectory }.forEach { observations[it] = true }
    }
    fun markerFolders(path: String): List<String> = generateSequence(File(path).absoluteFile) { it.parentFile }
        .filter { folder -> observations.getOrPut(folder.path) { File(folder, ".nomedia").isFile } }
        .map { it.path }.toList()
    fun contains(path: String): Boolean = markerFolders(path).isNotEmpty()
}

/** Category switches are independent; opting into .nomedia never opts into dot files. */
internal data class FolderCategoryPolicy(
    val dots: Boolean, val excluded: Boolean, val noMedia: Boolean, val showProtected: Boolean,
    val includedRoots: Set<String>, val excludedRoots: Set<String>
) {
    fun allows(path: String, hasMarker: Boolean, locked: Boolean): Boolean {
        val included = FavoriteFolderRules.isExcluded(path, includedRoots)
        return (!FavoriteFolderRules.isDotHidden(path) || dots) &&
            (!FavoriteFolderRules.isExcluded(path, excludedRoots) || excluded || included) &&
            (!hasMarker || noMedia || included) && (!locked || showProtected)
    }
}
