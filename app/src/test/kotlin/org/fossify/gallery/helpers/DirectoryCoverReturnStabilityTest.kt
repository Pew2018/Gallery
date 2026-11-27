package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.signature.ObjectKey
import com.squareup.picasso.Picasso
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.R
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Home-grid cover stability while the folder page returns.
 *
 * `DirectoryThumbnailBinder.bind` is the only place a visible album cover can lose its
 * frame. Its request key covers path, signature (folder modified time), crop, animation,
 * scroll axis and corner style; a rebind whose key differs used to fall through to
 * `OxygenThumbnailPlaceholder.apply` and start a fresh Glide request, and
 * `Context.loadImageBase` installs a *transparent* placeholder the moment that request
 * begins. The item's grey rounded background is therefore exposed for at least one frame
 * even though nothing about the media changed - the "cover shows the placeholder colour,
 * then the thumbnail comes back" timeline seen right after leaving a folder.
 *
 * The timeline these tests pin:
 *   first bind (no frame)      -> placeholder painted, request started
 *   rebind, same path, loaded  -> nothing at all (request-key guard)
 *   rebind, same path, new key -> frame kept, placeholder not repainted
 *   recycled to another path   -> frame cleared, placeholder painted (unchanged)
 *
 * Robolectric cannot decode the fixture's bitmap, so the assertions cover the frame the
 * view holds immediately after the call, which is the frame the user sees on that draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DirectoryCoverReturnStabilityTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    @Before
    fun setUp() {
        // App.onCreate installs the singleton; a plain Application does not.
        try {
            Picasso.setSingletonInstance(Picasso.Builder(application).build())
        } catch (_: IllegalStateException) {
        }
    }

    @After
    fun tearDown() {
        activity.finish()
    }

    @Test
    fun firstBindPaintsThePlaceholderBecauseThereIsNothingToKeep() {
        val image = target()
        bind(image, COVER, ObjectKey("$COVER-1"))
        assertTrue("a folder with no frame yet must show the placeholder", image.background is GradientDrawable)
    }

    @Test
    fun rebindOfTheSameCoverKeepsTheFrameOnScreen() {
        val image = target().loaded(COVER)
        val frame = image.drawable
        // A different signature is exactly what a new Directory row publishes when the
        // folder's modified time changes; the request key differs and the guard misses.
        bind(image, COVER, ObjectKey("$COVER-2"))
        assertSame("a reload must not blank the cover", frame, image.drawable)
        assertNull("a visible cover must not be repainted with the placeholder", image.background)
    }

    @Test
    fun alreadyLoadedCoverIsNotRequestedAgain() {
        val image = target().loaded(COVER)
        val signature = ObjectKey("$COVER-1")
        val key = listOf(COVER, signature, true, false, false, ROUNDED_CORNERS_BIG)
        image.setTag(R.id.thumbnail_request_key, key)
        image.setTag(R.id.thumbnail_loaded_key, key)
        val frame = image.drawable
        bind(image, COVER, signature)
        assertSame(frame, image.drawable)
        assertNull(image.background)
    }

    @Test
    fun recycledHolderStillClearsTheOldCover() {
        val image = target().loaded("/other/folder.jpg")
        bind(image, COVER, ObjectKey("$COVER-1"))
        assertNull("a holder rebound to another folder must not keep the old frame", image.drawable)
        assertTrue(image.background is GradientDrawable)
    }

    @Test
    fun emptyCoverPathKeepsTheSharedFolderIcon() {
        val image = target().loaded(COVER)
        val frame = image.drawable
        bind(image, "", ObjectKey("-0"))
        assertTrue("an empty cover path must replace the stale thumbnail", image.drawable !== frame)
        assertNull("the shared folder icon is not bound to a path", image.tag)
    }

    private fun target() = MySquareImageView(activity)

    private fun MySquareImageView.loaded(path: String): MySquareImageView {
        setImageDrawable(ColorDrawable(Color.BLUE))
        tag = path
        setTag(R.id.thumbnail_request_key, listOf(path, ObjectKey("$path-1"), true, false, false, ROUNDED_CORNERS_BIG))
        setTag(R.id.thumbnail_loaded_key, listOf(path, ObjectKey("$path-1"), true, false, false, ROUNDED_CORNERS_BIG))
        return this
    }

    private fun bind(image: MySquareImageView, path: String, signature: ObjectKey) {
        try {
            DirectoryThumbnailBinder.bind(
                activity = activity,
                target = image,
                path = path,
                signature = signature,
                crop = true,
                animateGifs = false,
                horizontalScroll = false,
                corners = ROUNDED_CORNERS_BIG,
                placeholderColor = Color.GRAY,
                onError = {}
            )
        } catch (_: LinkageError) {
            // The JVM suite cannot load Glide's webp native decoder; Context.loadImageBase
            // touches WebpBitmapFactory before the request is built. Every view mutation
            // this binder performs happens before that line, which is what these tests
            // observe, so the native decoder is the only thing being skipped here.
        }
    }

    private companion object {
        const val COVER = "/storage/emulated/0/DCIM/Camera/cover.jpg"
    }
}
