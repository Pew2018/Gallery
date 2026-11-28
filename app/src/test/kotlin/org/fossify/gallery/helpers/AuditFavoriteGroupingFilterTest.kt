package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AuditFavoriteGroupingFilterTest {
    @Test fun filteredFavoritesRegroupWithoutOrphanDateSections() {
        val context = RuntimeEnvironment.getApplication()
        context.config.groupBy = GROUP_BY_DATE_TAKEN_DAILY
        val fetcher = MediaFetcher(context)
        val kept = Medium().apply {
            name = "keep.jpg"; path = "/a/keep.jpg"; parentPath = "/a"
            type = TYPE_IMAGES; taken = 1_700_000_000_000L; modified = taken
        }
        val removed = Medium().apply {
            name = "gone.jpg"; path = "/b/gone.jpg"; parentPath = "/b"
            type = TYPE_IMAGES; taken = 1_600_000_000_000L; modified = taken
        }
        val allowed = setOf(kept.path)
        val grouped = fetcher.groupMedia(
            arrayListOf(kept, removed).filter { it.path in allowed }.toCollection(ArrayList()),
            FAVORITES,
        )
        assertEquals(listOf(kept.path), grouped.filterIsInstance<Medium>().map { it.path })
        assertTrue(grouped.filterIsInstance<ThumbnailSection>().isNotEmpty())
        assertTrue(
            grouped.none { item ->
                item is ThumbnailSection && grouped
                    .dropWhile { it !== item }
                    .drop(1)
                    .takeWhile { it !is ThumbnailSection }
                    .none { it is Medium }
            },
        )
    }
}
