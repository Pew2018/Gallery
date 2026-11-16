package org.fossify.gallery.helpers

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, application = Application::class)
class ViewerSourceContractTest {
    private val scope = ViewerSourceScope(
        ViewerScopeType.FOLDER, "scope", 3, 8, "name", "images", 5,
        ViewerMediumIdentity("/fixture/a.jpg", 1), ViewerContentSignature(300, 44, 20, 0), 2
    )

    @Test fun d13ReorderingDoesNotChangeMediaIdentity() {
        val reordered = scope.copy(sortSignature = "modified", committedAdapterVersion = 6)
        assertEquals(scope.mediaIdentity, reordered.mediaIdentity)
        assertEquals(scope.contentSignature, reordered.contentSignature)
        assertFalse(scope.matchesHandoff(reordered, accessCurrent = true))
    }

    @Test fun d14SamePathRequiresMatchingContentRevisionAndContentFacts() {
        assertTrue(scope.matchesHandoff(scope.copy(), accessCurrent = true))
        assertFalse(scope.matchesHandoff(scope.copy(contentRevision = 9), accessCurrent = true))
        assertFalse(scope.matchesHandoff(scope.copy(contentSignature = scope.contentSignature.copy(mediaStoreId = 21)), true))
    }

    @Test fun d20ScopeRequiresFreshAccessAndKnownFacts() {
        assertFalse(scope.matchesHandoff(scope, accessCurrent = false))
        assertFalse(scope.matchesHandoff(scope.copy(accessGeneration = 4), true))
        assertFalse(scope.matchesHandoff(scope.copy(sourceType = ViewerScopeType.NOMEDIA_FOLDER), true))
        assertFalse(scope.matchesHandoff(scope.copy(filterSignature = "videos"), true))
        val unknown = scope.copy(contentSignature = scope.contentSignature.copy(modified = 0))
        assertFalse(unknown.matchesHandoff(unknown, true))
    }

    @Test fun d20ExistingFitGeometryRejectsUnknownDimensionsAndPreservesLetterboxing() {
        val fit = requireNotNull(MediaTransitionGeometry.fit(200, 300, 2f))
        assertEquals(200f, fit.width(), 0f)
        assertEquals(100f, fit.height(), 0f)
        assertEquals(100f, fit.top, 0f)
        assertNull(MediaTransitionGeometry.fit(0, 300, 2f))
        assertNull(MediaTransitionGeometry.fit(200, 300, Float.NaN))
    }
}
