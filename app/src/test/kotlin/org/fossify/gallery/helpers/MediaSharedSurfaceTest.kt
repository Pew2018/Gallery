package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter
import com.bumptech.glide.load.engine.bitmap_recycle.LruArrayPool
import com.bumptech.glide.load.resource.bitmap.DefaultImageHeaderParser
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaSharedSurfaceTest {
    @Test fun fitUsesTheAvailableCanvasAndNeverChangesAspectRatio() {
        assertEquals(RectF(0f, 300f, 400f, 500f), MediaTransitionGeometry.fit(400, 800, 2f))
        assertEquals(RectF(100f, 0f, 300f, 800f), MediaTransitionGeometry.fit(400, 800, .25f))
        assertEquals(RectF(0f, 200f, 400f, 600f), MediaTransitionGeometry.fit(400, 800, 1f))
        assertEquals(RectF(0f, 350f, 400f, 450f), MediaTransitionGeometry.fit(400, 800, 4f))
        assertNull(MediaTransitionGeometry.fit(0, 800, 2f))
        assertNull(MediaTransitionGeometry.fit(400, 800, 0f))
    }

    @Test fun decodedPortraitUsesOrientedDimensionsAndActualImageMatrix() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val jpeg = File(activity.cacheDir, "quarter-turn.jpg")
            jpeg.outputStream().use {
                assertTrue(Bitmap.createBitmap(800, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
            ExifInterface(jpeg).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val orientation = jpeg.inputStream().use {
                DefaultImageHeaderParser().getOrientation(it, LruArrayPool())
            }
            val oriented = TransformationUtils.rotateImageExif(
                BitmapPoolAdapter(), BitmapFactory.decodeFile(jpeg.path), orientation
            )
            assertEquals(200, oriented.width)
            assertEquals(800, oriented.height)
            val view = ImageView(activity).apply {
                setImageBitmap(oriented)
                scaleType = ImageView.ScaleType.MATRIX
                imageMatrix = Matrix().apply { setTranslate(100f, 0f) }
                layout(3, 5, 403, 805)
            }
            val visible = MediaTransitionGeometry.imageBounds(view)!!
            assertEquals(RectF(103f, 5f, 303f, 805f), visible)
            assertTrue(MediaTransitionGeometry.sameBounds(visible, RectF(103.5f, 5f, 303.5f, 805f)))
            assertFalse(MediaTransitionGeometry.sameBounds(visible, RectF(3f, 305f, 403f, 405f)))
            assertEquals(RectF(0f, 399.5f, 400f, 400.5f), MediaTransitionGeometry.fit(400, 800, 400f))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun bothGridModesMapToTheSameEndpointInEitherDirectionWithoutStretching() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val parent = RelativeLayout(controller.get())
            controller.get().setContentView(parent)
            val drawable = BitmapDrawable(parent.resources, Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888))
            fun gridThumbnail(mode: ImageView.ScaleType) = ImageView(parent.context).apply {
                setImageDrawable(drawable.constantState!!.newDrawable())
                scaleType = mode
                layout(0, 0, 100, 100)
            }
            val fitGrid = gridThumbnail(ImageView.ScaleType.FIT_CENTER)
            assertEquals(RectF(0f, 25f, 100f, 75f), MediaTransitionGeometry.imageBounds(fitGrid))
            val grid = gridThumbnail(ImageView.ScaleType.CENTER_CROP)
            assertEquals(RectF(-50f, 0f, 150f, 100f), MediaTransitionGeometry.imageBounds(grid))

            val surface = MediaSharedSurface(parent)
            val expected = RectF(0f, 300f, 400f, 500f)
            val entry = surface.prepare(drawable, expected)!!
            assertEquals(expected, MediaTransitionGeometry.imageBounds(entry))
            surface.clear()
            val exit = surface.prepare(drawable, expected)!!
            assertEquals(expected, MediaTransitionGeometry.imageBounds(exit))

            // Reenter applies the viewer ScaleType to the local grid bitmap.
            // Even baked-in square crops must retain uniform scaling.
            grid.setImageBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))
            grid.scaleType = exit.scaleType
            grid.layout(0, 300, 400, 500)
            assertEquals(RectF(0f, 200f, 400f, 600f), MediaTransitionGeometry.imageBounds(grid))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun decodedVideoAndCoverUseOneViewportAsDisplayAspectChanges() {
        val preview = MediaTransitionGeometry.fit(400, 800, 1f)!!
        val decoded = MediaTransitionGeometry.fit(400, 800, 2f)!!
        for ((fraction, expectedHeight) in listOf(0f to 400, .25f to 350, .5f to 300, 1f to 200)) {
            val (width, height) = MediaTransitionGeometry.viewportSize(preview, decoded, fraction)
            assertEquals(400, width)
            assertEquals(expectedHeight, height)
            val buffer = RectF(0f, 0f, width.toFloat(), height.toFloat())
            MediaTransitionGeometry.textureCrop(width, height, 2f)!!.mapRect(buffer)
            assertEquals(2f, buffer.width() / buffer.height(), .001f)
            assertEquals(width / 2f, buffer.centerX(), .001f)
            assertEquals(height / 2f, buffer.centerY(), .001f)
            assertTrue(buffer.contains(RectF(0f, 0f, width.toFloat(), height.toFloat())))
        }
    }

    @Test fun proxySharesOnlyContentAndRestoresRenderersOnEveryExit() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val parent = RelativeLayout(controller.get())
            controller.get().setContentView(parent)
            val renderer = View(controller.get()).apply { alpha = .7f; setBackgroundColor(Color.BLACK) }
            parent.addView(renderer)
            val surface = MediaSharedSurface(parent)
            assertNull(surface.prepare(ColorDrawable(Color.RED), RectF(), renderer))
            assertNull(surface.prepare(ColorDrawable(Color.RED), RectF(Float.NaN, 0f, 2f, 2f), renderer))
            assertEquals(.7f, renderer.alpha)
            val image = surface.prepare(ColorDrawable(Color.RED), RectF(0f, 300f, 400f, 500f), renderer)!!
            assertEquals(400, image.width)
            assertEquals(200, image.height)
            assertEquals(300, image.top)
            assertNull(image.background)
            assertEquals(0f, renderer.alpha)
            assertSame(image, surface.prepare(ColorDrawable(Color.BLUE), RectF(0f, 0f, 20f, 20f), renderer))
            surface.clear()
            surface.clear()
            assertEquals(.7f, renderer.alpha)
            assertEquals(1, parent.childCount)
        } finally { controller.pause().stop().destroy() }
    }
}
