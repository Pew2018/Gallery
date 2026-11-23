package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Return anchoring is tied to media identity, not just to a screen coordinate.
 * A changed file version or a changed anchor identity must refuse the old target
 * even when the parent Window is still covered.
 */
class ViewerMediaIdentityReturnTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun changedFileVersionInvalidatesTheAnchorEvenAtTheSameRectangle() {
        val file = folder.newFile("identity.jpg").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val version = StaticPhotoFileVersion.read(file.path)
        val source = StaticPhotoSource(
            file.path, "signature", 7,
            StaticPhotoThumbnail(100, 100, 100, 100, cropped = true, radius = 0),
            PhotoViewGeometry(
                PhotoBox(0f, 0f, 100f, 100f), PhotoBox(0f, 0f, 100f, 100f),
                PhotoBox(0f, 0f, 100f, 100f),
                PhotoWindowGeometry(PhotoBox(0f, 0f, 1080f, 1920f), emptyList()),
            ),
            size = version.size, modified = version.modified, invalidation = version.invalidation,
        )
        assertTrue(version.matchesGrid(source))

        // A delete/re-save or an external edit can keep the file name and even
        // the layout, but the old thumbnail must not be recycled.
        file.writeBytes(ByteArray(128) { (it + 1).toByte() })
        assertFalse(StaticPhotoFileVersion.read(file.path).matchesGrid(source))
    }

    @Test
    fun switchedOrResortedMediaIsRejectedByTheAnchorPolicy() {
        val valid = ViewerCachedReturnAnchorEvidence(
            launchMatches = true, scopeMatches = true, pathStillSelected = true,
            adapterVersionMatches = true, mediaIdentityMatches = true,
            fileVersionMatches = true, windowGeometryMatches = true, drawableAvailable = true,
        )
        assertTrue(ViewerCachedReturnAnchorPolicy.decide(valid).accepted)

        // Switch page: the Viewer now asks for another path.
        assertFalse(ViewerCachedReturnAnchorPolicy.decide(
            valid.copy(pathStillSelected = false)
        ).accepted)
        // Delete or reorder: the adapter/media identity no longer matches.
        assertFalse(ViewerCachedReturnAnchorPolicy.decide(
            valid.copy(adapterVersionMatches = false)
        ).accepted)
        assertFalse(ViewerCachedReturnAnchorPolicy.decide(
            valid.copy(mediaIdentityMatches = false)
        ).accepted)
    }
}
