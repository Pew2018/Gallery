package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class ExportFilenameTest {
    @Test fun supportedFormatsNormalizeExactlyOneSuffixForBothOutputRoutes() {
        for (extension in listOf(".txt", ".json", ".zip")) {
            for (stem in listOf("backup", "中文备份", "backup_2026-11-02")) {
                for (input in listOf(stem, stem + extension, stem + extension.uppercase(), stem + extension + extension)) {
                    val name = ExportFilename.normalize(input, extension)
                    assertEquals(stem + extension, name)
                    // These are the same name passed to EXTRA_TITLE and to the legacy path.
                    assertEquals("/exports/$stem$extension", "/exports/$name")
                }
            }
            for (wrong in listOf(".txt", ".json", ".zip", ".pdf").filter { it != extension }) {
                assertNull(ExportFilename.normalize("backup$wrong", extension))
            }
        }
    }
    @Test fun emptyNamesSuffixOnlyAndInvalidPathsAreRejected() {
        for (name in listOf("", " ", ".json", "backup/part", "backup\\part", "bad:name", "bad\nname", ".", "..")) {
            assertNull(name, ExportFilename.normalize(name, ".json"))
        }
    }
}
