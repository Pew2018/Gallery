package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.Rect
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
    @Test fun returningProxyClipsAgainstScreenToolbarWithoutMovingItsEndpoint() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val parent = RelativeLayout(controller.get())
            controller.get().setContentView(parent)
            val surface = MediaSharedSurface(parent)
            val proxy = requireNotNull(surface.prepare(ColorDrawable(Color.BLUE), RectF(5f, 20f, 105f, 120f)))
            proxy.clipBounds = Rect(2, 3, 98, 97)
            proxy.pivotY = 0f
            proxy.scaleY = .5f
            proxy.translationY = 17f
            val location = IntArray(2).also(proxy::getLocationOnScreen)
            val clip = MediaReturnToolbarClip(proxy)

            clip.update(location[1] + 23)
            assertEquals(Rect(2, 46, 98, 97), proxy.clipBounds)
            assertEquals(20, proxy.top)
            assertEquals(120, proxy.bottom)
            assertEquals(17f, proxy.translationY, 0f)

            clip.update(location[1] + 100)
            assertTrue(requireNotNull(proxy.clipBounds).isEmpty)
            proxy.translationY += 35f
            clip.update(location[1] + 23)
            assertEquals(Rect(2, 3, 98, 97), proxy.clipBounds)

            clip.update(location[1] + 55)
            assertEquals(Rect(2, 40, 98, 97), proxy.clipBounds)
            clip.update(null)
            clip.update(null)
            assertEquals(Rect(2, 3, 98, 97), proxy.clipBounds)
        } finally { controller.pause().stop().destroy() }
    }

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

    @Test fun longPhotoReturnUsesUniformScaleAndContinuouslyChangingAperture() =
        verifyLongPhotoReturn(forceCrop = false)

    @Test fun bakedRoundedCropReturnsContinuouslyEvenWhenGridUsesFitCenter() =
        verifyLongPhotoReturn(forceCrop = true)

    private fun verifyLongPhotoReturn(forceCrop: Boolean) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val parent = RelativeLayout(activity)
            activity.setContentView(parent)
            val image = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(Bitmap.createBitmap(100, 3000, Bitmap.Config.ARGB_8888))
            }
            parent.addView(image)
            image.layout(0, 0, 100, 3000)
            val transition = OxygenClassicActivityMotion.mediaTransition(
                240L, cropMediaToBounds = true, forceMediaCropToBounds = { forceCrop },
            )
                as android.transition.TransitionSet
            val crop = transition.getTransitionAt(2)
            val start = android.transition.TransitionValues(image)
            crop.captureStartValues(start)
            image.layout(0, 0, 670, 670)
            if (forceCrop) image.scaleType = ImageView.ScaleType.FIT_CENTER
            val end = android.transition.TransitionValues(image)
            crop.captureEndValues(end)
            val animator = requireNotNull(crop.createAnimator(parent, start, end)) as android.animation.ValueAnimator
            animator.duration = 1000L
            animator.interpolator = android.view.animation.LinearInterpolator()
            animator.start()
            for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
                val width = (100 + 570 * fraction).toInt()
                val height = (3000 - 2330 * fraction).toInt()
                image.layout(0, 0, width, height)
                animator.currentPlayTime = (fraction * 1000).toLong()
                val actual = FloatArray(9).also(image.imageMatrix::getValues)
                val expectedScale = maxOf((100 + 570 * fraction) / 100f, (3000 - 2330 * fraction) / 3000f)
                assertEquals("uniform horizontal scale at $fraction", expectedScale, actual[Matrix.MSCALE_X], .001f)
                assertEquals("uniform vertical scale at $fraction", expectedScale, actual[Matrix.MSCALE_Y], .001f)
            }
            animator.end()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun staticEntryDrawsRealGlideCropAndThenTheFullRendererPixels() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val parent = RelativeLayout(activity)
            activity.setContentView(parent)
            val photo = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint()
            android.graphics.Canvas(photo).apply {
                paint.color = Color.RED
                drawRect(0f, 0f, 80f, 100f, paint)
                paint.color = Color.GREEN
                drawRect(80f, 0f, 120f, 100f, paint)
                paint.color = Color.BLUE
                drawRect(120f, 0f, 200f, 100f, paint)
            }
            val bitmap = TransformationUtils.centerCrop(BitmapPoolAdapter(), photo, 100, 100)
            val screen = StaticPhotoViewGeometry.localToScreen(parent)
            fun onScreen(rect: RectF): PhotoBox = PhotoBox(rect.apply { screen.mapRect(this) })
            val tile = onScreen(RectF(0f, 0f, 100f, 100f))
            val partial = onScreen(RectF(0f, 20f, 100f, 100f))
            val full = onScreen(RectF(0f, 100f, 200f, 200f))
            val window = PhotoWindowGeometry(full, emptyList())
            val thumbnail = StaticPhotoThumbnail(100, 100, 100, 100, true, 0)
            val source = StaticPhotoSource("item", "v1", 1, thumbnail, PhotoViewGeometry(tile, tile, partial, window))
            val target = PhotoViewGeometry(full, full, full, window)
            val crop = requireNotNull(thumbnail.project(tile, StaticPhotoHeader(200, 100, 1, jpeg = false)))
            val surface = MediaSharedSurface(parent)
            val renderer = ImageView(activity)
            val proxy = requireNotNull(surface.prepareStaticEntry(
                BitmapDrawable(parent.resources, photo), StaticPhotoEntryGeometry(source, target, crop), renderer,
            ))
            assertTrue(surface.takeOver(proxy))

            proxy.layout(0, 0, 100, 100)
            proxy.showFrame(0f)
            val first = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            proxy.draw(android.graphics.Canvas(first))
            assertEquals(Color.TRANSPARENT, first.getPixel(50, 10))
            for (x in listOf(10, 29, 31, 50, 69, 71, 90)) {
                assertEquals("crop pixel at x=$x", bitmap.getPixel(x, 50), first.getPixel(x, 50))
            }

            proxy.layout(0, 100, 200, 200)
            proxy.showFrame(1f)
            val last = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            proxy.draw(android.graphics.Canvas(last))
            for (x in listOf(10, 79, 81, 119, 121, 190)) {
                assertEquals("Glide handoff pixel at x=$x", photo.getPixel(x, 50), last.getPixel(x, 50))
            }
            surface.clear()
            assertEquals(1f, renderer.alpha)
            assertNull(proxy.parent)
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
    @Test fun enteringProxyWaitsForCaptureAndRestoresOriginalVisibilityAndAlpha() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val parent = RelativeLayout(controller.get())
            controller.get().setContentView(parent)
            val renderer = View(controller.get()).apply { alpha = .7f }
            val tiles = View(controller.get()).apply {
                alpha = .4f
                visibility = View.INVISIBLE
            }
            parent.addView(renderer)
            parent.addView(tiles)
            val surface = MediaSharedSurface(parent)
            val bounds = RectF(15f, 80f, 315f, 280f)
            val image = surface.prepare(ColorDrawable(Color.RED), bounds, renderer, tiles, deferTakeover = true)!!
            assertEquals(.7f, renderer.alpha)
            assertEquals(.4f, tiles.alpha)
            assertEquals(0f, image.alpha)
            assertEquals(View.VISIBLE, image.visibility)
            assertEquals(bounds, RectF(image.left.toFloat(), image.top.toFloat(), image.right.toFloat(), image.bottom.toFloat()))
            assertFalse(surface.takeOver(View(controller.get())))
            assertEquals(.7f, renderer.alpha)
            assertTrue(surface.takeOver(image))
            assertTrue(surface.takeOver(image))
            assertEquals(1f, image.alpha)
            assertEquals(0f, renderer.alpha)
            assertEquals(View.INVISIBLE, tiles.visibility)
            surface.clear()
            assertFalse(image.isAttachedToWindow)
            assertEquals(.7f, renderer.alpha)
            assertEquals(.4f, tiles.alpha)
            assertEquals(View.INVISIBLE, tiles.visibility)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun rendererAlphaUpdatesCannotDrawOverTheMovingProxy() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val parent = RelativeLayout(controller.get())
            controller.get().setContentView(parent)
            val renderer = View(parent.context).apply {
                alpha = .75f
                transitionAlpha = .8f
            }
            parent.addView(renderer)
            val surface = MediaSharedSurface(parent)
            val proxy = surface.prepare(ColorDrawable(Color.BLUE), RectF(0f, 100f, 200f, 250f), renderer)!!
            renderer.alpha = 1f // Simulate an asynchronous renderer update after the proxy takes over.
            assertEquals(0f, renderer.transitionAlpha, 0f)
            assertEquals(1f, proxy.alpha, 0f)
            assertSame(parent, proxy.parent)
            surface.clear()
            assertEquals(.75f, renderer.alpha, 0f)
            assertEquals(.8f, renderer.transitionAlpha, 0f)
            assertNull(proxy.drawable)
        } finally { controller.pause().stop().destroy() }
    }

}
