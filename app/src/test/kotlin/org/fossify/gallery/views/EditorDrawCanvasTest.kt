package org.fossify.gallery.views

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EditorDrawCanvasTest {
    @Test
    fun mosaicEditsOnlyTheStrokeAreaAndPreservesExportDimensions() {
        val source = checkerBitmap(96, 64)
        val canvas = canvasWith(source)
        canvas.updateBrushSize(100)
        canvas.setMode(EditorBrushMode.MOSAIC)
        stroke(canvas, 48f, 32f)

        val output = canvas.getBitmap(source)
        assertEquals(source.width, output.width)
        assertEquals(source.height, output.height)
        assertEquals(source.getPixel(2, 2), output.getPixel(2, 2))
        assertTrue(countDifferent(source, output, 28, 12, 68, 52) > 0)
        assertTrue(canvas.canUndo())
    }

    @Test
    fun mixedBrushOperationsUndoInReverseOrderToOriginalPixels() {
        val source = checkerBitmap(96, 64)
        val canvas = canvasWith(source)
        canvas.updateBrushSize(70)
        canvas.setMode(EditorBrushMode.DRAW)
        canvas.updateColor(Color.RED)
        stroke(canvas, 28f, 32f)
        canvas.setMode(EditorBrushMode.MOSAIC)
        stroke(canvas, 48f, 32f)
        canvas.setMode(EditorBrushMode.BLUR)
        stroke(canvas, 68f, 32f)

        val changed = canvas.getBitmap(source)
        assertTrue(countDifferent(source, changed, 18, 22, 38, 42) > 0)
        assertTrue(countDifferent(source, changed, 38, 22, 58, 42) > 0)
        assertTrue(countDifferent(source, changed, 58, 22, 78, 42) > 0)

        repeat(3) { canvas.undo() }
        assertBitmapEquals(source, canvas.getBitmap(source))
        assertTrue(!canvas.canUndo())
        canvas.undo() // Empty history is a no-op.
        assertBitmapEquals(source, canvas.getBitmap(source))
    }

    @Test
    fun previewSizedPathsScaleToTheRequestedExportBitmap() {
        val preview = checkerBitmap(48, 32)
        val export = checkerBitmap(192, 128)
        val canvas = canvasWith(preview)
        canvas.updateBrushSize(100)
        canvas.setMode(EditorBrushMode.DRAW)
        canvas.updateColor(Color.GREEN)
        stroke(canvas, 24f, 16f)

        val rendered = canvas.getBitmap(export)
        assertEquals(192, rendered.width)
        assertEquals(128, rendered.height)
        assertNotEquals(export.getPixel(96, 64), rendered.getPixel(96, 64))
        assertEquals(export.getPixel(4, 4), rendered.getPixel(4, 4))
    }

    @Test
    fun modesAreExclusiveStateAndSwitchingDoesNotEraseHistory() {
        val canvas = canvasWith(checkerBitmap(64, 64))
        assertEquals(EditorBrushMode.DRAW, canvas.getMode())
        canvas.setMode(EditorBrushMode.MOSAIC)
        stroke(canvas, 24f, 24f)
        canvas.setMode(EditorBrushMode.BLUR)
        assertEquals(EditorBrushMode.BLUR, canvas.getMode())
        assertTrue(canvas.canUndo())
        canvas.undo()
        assertTrue(!canvas.canUndo())
    }

    private fun canvasWith(source: Bitmap): EditorDrawCanvas = EditorDrawCanvas(
        RuntimeEnvironment.getApplication(), Robolectric.buildAttributeSet().build()
    ).apply { updateBackgroundBitmap(source) }

    private fun stroke(canvas: EditorDrawCanvas, x: Float, y: Float) {
        val down = MotionEvent.obtain(0, 1, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 2, MotionEvent.ACTION_UP, x, y, 0)
        canvas.onTouchEvent(down)
        canvas.onTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun checkerBitmap(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            setPixel(x, y, if ((x / 2 + y / 2) % 2 == 0) Color.BLACK else Color.WHITE)
        }
    }

    private fun assertBitmapEquals(expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(expected.width * expected.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        assertTrue(expectedPixels.contentEquals(actualPixels))
    }

    private fun countDifferent(expected: Bitmap, actual: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in left until right) {
            if (expected.getPixel(x, y) != actual.getPixel(x, y)) count++
        }
        return count
    }
}
