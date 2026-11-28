package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Static wiring checks for audit stages that are awkward to drive through full Activities. */
class AuditFixSourceWiringTest {
    private val source = File("src/main/kotlin/org/fossify/gallery")

    @Test fun videoReadyGuardsMatchAdjacentListenerCallbacks() {
        val text = source.resolve("fragments/VideoFragment.kt").readText()
        assertTrue(text.contains("mExoPlayer === listenerPlayer && view != null"))
        assertTrue(text.contains("videoPrepared(listenerPlayer)"))
        assertTrue(text.contains("if (isAdded && view != null)"))
        assertFalse(
            Regex("""STATE_READY\s*->\s*videoPrepared\(\)""").containsMatchIn(text),
        )
    }

    @Test fun externalVideoIntentLaunchesOnMainThread() {
        val text = source.resolve("activities/ViewPagerActivity.kt").readText()
        val function = text.substringAfter("override fun launchViewVideoIntent(path: String)")
            .substringBefore("private fun checkSystemUI()")
        assertTrue(function.contains("ensureBackgroundThread"))
        assertTrue(function.contains("runOnUiThread"))
        assertTrue(function.contains("if (isFinishing || isDestroyed) return@runOnUiThread"))
        assertTrue(function.indexOf("runOnUiThread") < function.indexOf("startActivityForResult"))
        assertTrue(function.indexOf("runOnUiThread") < function.indexOf("toast("))
    }

    @Test fun cancelledSharedReturnAbortsBeforeAnotherClose() {
        val text = source.resolve("activities/ViewPagerActivity.kt").readText()
        assertTrue(text.contains("transitionSession.abortCancelledReturn(operation)"))
        assertTrue(text.contains("returnLeases.releaseAll()"))
    }

    @Test fun homeFastScrollerAppliesBottomInsets() {
        val text = source.resolve("activities/MainActivity.kt").readText()
        val function = text.substringAfter("private fun styleFastScroller()")
            .substringBefore("private fun checkInvalidDirectories")
        assertTrue(function.contains("applyGalleryBottomInsets"))
        assertTrue(function.contains("installGalleryTouchHandling"))
    }

    @Test fun pickerTouchHandlingIsThumbOnly() {
        val text = source.resolve("helpers/OxygenFastScroller.kt").readText()
        val function = text.substringAfter("fun installPickerTouchHandling")
            .substringBefore("private class PickerScrollState")
        assertTrue(function.contains("thumb.setOnTouchListener(listener)"))
        assertTrue(function.contains("track.setOnTouchListener { _, _ -> false }"))
        assertTrue(function.contains("if (!state.canScroll()) return@OnTouchListener false"))
        assertFalse(function.contains("track.setOnTouchListener(listener)"))
    }

    @Test fun videoChromeRestoresAfterSharedEntryOnNeighborPages() {
        val text = source.resolve("fragments/VideoFragment.kt").readText()
        assertTrue(text.contains("restoreIdleVideoChrome()"))
        assertTrue(text.contains("fun finishSharedTransition()"))
        val finish = text.substringAfter("fun finishSharedTransition()").substringBefore("fun setSharedChromeAlpha")
        assertTrue(finish.contains("restoreIdleVideoChrome()"))
        val visibility = text.substringAfter("override fun setMenuVisibility(menuVisible: Boolean)")
            .substringBefore("override fun onConfigurationChanged")
        assertTrue(visibility.contains("restoreIdleVideoChrome()"))
    }
}
