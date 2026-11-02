package org.fossify.gallery.helpers

/** A shared output name for both SAF titles and legacy filesystem exports. */
internal object ExportFilename {
    fun normalize(input: String, extension: String): String? {
        require(extension in setOf(".txt", ".json", ".zip"))
        var stem = input.trim()
        if (stem.isEmpty() || stem.any { it in "/\\:*?\"<>|" || it.code < 32 }) return null
        while (stem.endsWith(extension, ignoreCase = true)) stem = stem.dropLast(extension.length)
        // A different suffix needs correction by the user, rather than a misleading double suffix.
        if (stem.isBlank() || stem.endsWith('.') || stem.substringAfterLast('.', "").isNotEmpty()) return null
        return stem + extension
    }
}
