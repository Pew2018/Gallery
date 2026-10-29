package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class MediaStoreFolderKeyTest {
    @Test fun caseDistinctAndUnicodeFoldersKeepSeparateIndexedMedia() {
        val roots = listOf(
            "/storage/emulated/0/Pictures/Travel",
            "/storage/emulated/0/Pictures/travel",
            "/storage/emulated/0/Pictures/旅行 #1"
        )
        val indexed = HashMap<String, MutableList<String>>()
        (1..450).forEach { n ->
            val parent = roots[(n - 1) % roots.size]
            indexed.getOrPut(MediaStoreFolderKey.of(parent)) { mutableListOf() }
                .add("$parent/image_$n.jpg")
        }

        assertEquals(3, indexed.size)
        for (root in roots) {
            val media = indexed[MediaStoreFolderKey.of(root)]
            assertEquals(150, media?.size)
            assertEquals(150, media?.distinct()?.size)
            assertFalse(media.orEmpty().any { !it.startsWith("$root/") })
        }
        assertNotEquals(MediaStoreFolderKey.of(roots[0]), MediaStoreFolderKey.of(roots[1]))
        assertEquals(roots[0], MediaStoreFolderKey.of(roots[0] + "/./"))
        assertEquals(roots[2], MediaStoreFolderKey.of(roots[2] + "/"))
    }

    @Test fun directoryIndexIsIndependentOfDeviceLocale() {
        val initial = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("/Pictures/IMAGES", MediaStoreFolderKey.of("/Pictures/IMAGES"))
            assertNotEquals(
                MediaStoreFolderKey.of("/Pictures/IMAGES"),
                MediaStoreFolderKey.of("/Pictures/images")
            )
        } finally {
            Locale.setDefault(initial)
        }
    }
}
